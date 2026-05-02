using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ThoughtInput;

public sealed class AppSettings
{
    [JsonPropertyName("hotkey")] public string Hotkey { get; set; } = "Ctrl+Shift+Space";
    [JsonPropertyName("launchOnLogin")] public bool LaunchOnLogin { get; set; } = true;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
    };

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(AppPaths.SettingsFile))
            {
                var bytes = File.ReadAllBytes(AppPaths.SettingsFile);
                return JsonSerializer.Deserialize<AppSettings>(bytes, JsonOptions) ?? new AppSettings();
            }
        }
        catch
        {
            // fall through to default
        }
        return new AppSettings();
    }

    public void Save()
    {
        AppPaths.EnsureCreated();
        var bytes = JsonSerializer.SerializeToUtf8Bytes(this, JsonOptions);
        var tmp = AppPaths.SettingsFile + ".tmp";
        File.WriteAllBytes(tmp, bytes);
        File.Move(tmp, AppPaths.SettingsFile, overwrite: true);
    }
}
