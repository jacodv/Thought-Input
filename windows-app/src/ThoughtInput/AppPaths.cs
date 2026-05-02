using System.IO;

namespace ThoughtInput;

/// <summary>
/// Centralises filesystem layout under the user profile.
/// Settings (roams): %AppData%\ThoughtInput\
/// Local-only state (offline queue): %LocalAppData%\ThoughtInput\
/// </summary>
internal static class AppPaths
{
    public static string SettingsDir { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ThoughtInput");

    public static string LocalDataDir { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ThoughtInput");

    public static string DestinationsFile => Path.Combine(SettingsDir, "destinations.json");

    public static string SettingsFile => Path.Combine(SettingsDir, "settings.json");

    public static string PendingDir => Path.Combine(LocalDataDir, "pending");

    public static void EnsureCreated()
    {
        Directory.CreateDirectory(SettingsDir);
        Directory.CreateDirectory(LocalDataDir);
        Directory.CreateDirectory(PendingDir);
    }
}
