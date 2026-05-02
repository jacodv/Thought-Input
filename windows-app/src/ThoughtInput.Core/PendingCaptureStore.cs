using System.Text.Json;

namespace ThoughtInput.Core;

public sealed class PendingCaptureStore
{
    private readonly string _directory;

    public PendingCaptureStore(string directory)
    {
        _directory = directory;
        Directory.CreateDirectory(_directory);
    }

    public bool Save(PendingCapture capture)
    {
        try
        {
            var bytes = JsonSerializer.SerializeToUtf8Bytes(capture, DestinationJson.DefaultOptions);
            var path = Path.Combine(_directory, $"{capture.Payload.IdempotencyKey}.json");
            var tmp = path + ".tmp";
            File.WriteAllBytes(tmp, bytes);
            File.Move(tmp, path, overwrite: true);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public IReadOnlyList<PendingCapture> LoadAll()
    {
        if (!Directory.Exists(_directory)) return [];
        var results = new List<PendingCapture>();
        foreach (var path in Directory.EnumerateFiles(_directory, "*.json"))
        {
            byte[] bytes;
            try { bytes = File.ReadAllBytes(path); }
            catch { continue; }

            try
            {
                var pending = JsonSerializer.Deserialize<PendingCapture>(bytes, DestinationJson.DefaultOptions);
                // System.Text.Json fills missing record params with null rather than throwing,
                // so reject incomplete shapes here and fall through to the legacy reader.
                if (pending is { Payload: not null, DestinationSnapshot: not null })
                {
                    results.Add(pending);
                    continue;
                }
            }
            catch { /* fall through to legacy handling */ }

            // Legacy fallback: bare CapturePayload (mirrors macOS behaviour).
            try
            {
                var payload = JsonSerializer.Deserialize<CapturePayload>(bytes, DestinationJson.DefaultOptions);
                if (payload is not null)
                {
                    results.Add(new PendingCapture(
                        payload,
                        DestinationID: Guid.Empty,
                        DestinationSnapshot: new Destination
                        {
                            Name = "Legacy",
                            IsActive = false,
                            Type = new RestNoAuthDestinationType(new RestNoAuthConfig("")),
                        }));
                }
            }
            catch
            {
                // Unreadable — skip.
            }
        }
        return results;
    }

    public void Remove(string idempotencyKey)
    {
        var path = Path.Combine(_directory, $"{idempotencyKey}.json");
        try { if (File.Exists(path)) File.Delete(path); } catch { }
    }

    public int PendingCount =>
        Directory.Exists(_directory)
            ? Directory.EnumerateFiles(_directory, "*.json").Count()
            : 0;
}
