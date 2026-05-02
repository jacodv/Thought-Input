using System.IO;
using System.IO.Pipes;

namespace ThoughtInput;

/// <summary>
/// Ensures only one instance of the app runs. If a second instance is launched,
/// it sends a "show" message to the first via a named pipe and exits.
/// </summary>
internal sealed class SingleInstance : IDisposable
{
    private const string MutexName = @"Global\ThoughtInput-SingleInstance";
    private const string PipeName = "ThoughtInput-IPC";
    private const string ShowCommand = "SHOW";

    private readonly Mutex _mutex;
    private CancellationTokenSource? _serverCts;
    private Task? _serverTask;

    public bool IsFirstInstance { get; }

    public event Action? ShowRequested;

    public SingleInstance()
    {
        _mutex = new Mutex(initiallyOwned: true, MutexName, out var createdNew);
        IsFirstInstance = createdNew;
    }

    public void StartServer()
    {
        if (!IsFirstInstance) return;
        _serverCts = new CancellationTokenSource();
        _serverTask = Task.Run(() => RunServerAsync(_serverCts.Token));
    }

    public void NotifyExisting()
    {
        try
        {
            using var client = new NamedPipeClientStream(".", PipeName, PipeDirection.Out);
            client.Connect(timeout: 1000);
            using var writer = new StreamWriter(client) { AutoFlush = true };
            writer.WriteLine(ShowCommand);
        }
        catch
        {
            // The first instance may be quitting or unresponsive — best effort.
        }
    }

    private async Task RunServerAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await using var server = new NamedPipeServerStream(
                    PipeName, PipeDirection.In, maxNumberOfServerInstances: 1,
                    PipeTransmissionMode.Byte, PipeOptions.Asynchronous);
                await server.WaitForConnectionAsync(ct).ConfigureAwait(false);

                using var reader = new StreamReader(server);
                var message = await reader.ReadLineAsync(ct).ConfigureAwait(false);
                if (string.Equals(message, ShowCommand, StringComparison.Ordinal))
                {
                    ShowRequested?.Invoke();
                }
            }
            catch (OperationCanceledException) { return; }
            catch
            {
                // Resilient to malformed or interrupted connections.
            }
        }
    }

    public void Dispose()
    {
        _serverCts?.Cancel();
        try { _serverTask?.Wait(500); } catch { }
        _serverCts?.Dispose();
        if (IsFirstInstance)
        {
            try { _mutex.ReleaseMutex(); } catch { }
        }
        _mutex.Dispose();
    }
}
