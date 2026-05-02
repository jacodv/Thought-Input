using Microsoft.Win32;

namespace ThoughtInput;

/// <summary>
/// Manages the HKCU Run-key entry that starts ThoughtInput hidden at login.
/// </summary>
internal static class AutoLaunch
{
    private const string RunKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "ThoughtInput";
    private const string LaunchArgs = "--hidden";

    public static bool IsEnabled()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: false);
        return key?.GetValue(ValueName) is string;
    }

    public static void Enable()
    {
        using var key = Registry.CurrentUser.CreateSubKey(RunKeyPath, writable: true)
            ?? throw new InvalidOperationException("Could not open HKCU Run key");
        var exe = Environment.ProcessPath
            ?? System.Reflection.Assembly.GetEntryAssembly()?.Location
            ?? throw new InvalidOperationException("Could not resolve executable path");
        key.SetValue(ValueName, $"\"{exe}\" {LaunchArgs}", RegistryValueKind.String);
    }

    public static void Disable()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKeyPath, writable: true);
        key?.DeleteValue(ValueName, throwOnMissingValue: false);
    }
}
