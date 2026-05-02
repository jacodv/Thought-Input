using System.Diagnostics;
using System.Windows;

namespace ThoughtInput;

/// <summary>
/// Verifies the .NET 10 Desktop Runtime is present. If the app is running,
/// it obviously is — but we keep this around for diagnostics. The harder case
/// (runtime missing) is caught by Windows itself before <c>App</c> ever starts;
/// in that case the OS shows a popup pointing to the runtime download URL.
/// </summary>
internal static class RuntimeCheck
{
    public static string DownloadUrl =>
        "https://dotnet.microsoft.com/download/dotnet/10.0";

    public static string CurrentRuntime => Environment.Version.ToString();

    public static void OpenDownloadPage()
    {
        try
        {
            Process.Start(new ProcessStartInfo(DownloadUrl) { UseShellExecute = true });
        }
        catch
        {
            MessageBox.Show($"Open this URL in your browser:\n\n{DownloadUrl}",
                "Thought Input", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }
}
