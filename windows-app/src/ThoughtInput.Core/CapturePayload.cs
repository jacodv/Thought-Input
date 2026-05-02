using System.Reflection;
using System.Text.Json.Serialization;

namespace ThoughtInput.Core;

public enum CaptureMethod
{
    [JsonStringEnumMemberName("typed")]
    Typed,
    [JsonStringEnumMemberName("voice")]
    Voice,
}

public sealed record CapturePayload(
    [property: JsonPropertyName("text")] string Text,
    [property: JsonPropertyName("timestamp")] string Timestamp,
    [property: JsonPropertyName("source_platform")] string SourcePlatform,
    [property: JsonPropertyName("client_version")] string ClientVersion,
    [property: JsonPropertyName("capture_method")]
    [property: JsonConverter(typeof(JsonStringEnumConverter<CaptureMethod>))]
    CaptureMethod CaptureMethod,
    [property: JsonPropertyName("idempotency_key")] string IdempotencyKey,
    [property: JsonPropertyName("device_name")] string DeviceName)
{
    public const string SourcePlatformWindows = "windows";

    public static CapturePayload Create(
        string text,
        CaptureMethod method,
        string? clientVersion = null,
        string? deviceName = null,
        DateTimeOffset? now = null,
        Guid? idempotencyKey = null)
    {
        var ts = (now ?? DateTimeOffset.UtcNow).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ");
        var version = clientVersion ?? ResolveClientVersion();
        var device = deviceName ?? Environment.MachineName;
        var key = (idempotencyKey ?? Guid.NewGuid()).ToString("D").ToUpperInvariant();

        return new CapturePayload(
            Text: text,
            Timestamp: ts,
            SourcePlatform: SourcePlatformWindows,
            ClientVersion: version,
            CaptureMethod: method,
            IdempotencyKey: key,
            DeviceName: device);
    }

    private static string ResolveClientVersion()
    {
        var assembly = Assembly.GetEntryAssembly() ?? typeof(CapturePayload).Assembly;
        var info = assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
        if (!string.IsNullOrWhiteSpace(info))
        {
            // Strip metadata suffix (e.g. "0.1.0+abcdef" -> "0.1.0")
            var plus = info.IndexOf('+');
            if (plus >= 0) info = info[..plus];
            // Match the schema pattern ^\d+\.\d+\.\d+$
            var parts = info.Split('.');
            if (parts.Length >= 3 &&
                int.TryParse(parts[0], out _) &&
                int.TryParse(parts[1], out _) &&
                int.TryParse(parts[2].Split('-')[0], out _))
            {
                return $"{parts[0]}.{parts[1]}.{parts[2].Split('-')[0]}";
            }
        }
        var ver = assembly.GetName().Version;
        return ver is null ? "0.1.0" : $"{ver.Major}.{ver.Minor}.{Math.Max(ver.Build, 0)}";
    }
}
