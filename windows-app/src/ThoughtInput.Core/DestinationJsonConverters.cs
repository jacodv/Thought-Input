using System.Text.Json;
using System.Text.Json.Serialization;

namespace ThoughtInput.Core;

/// <summary>
/// Mirrors Swift's automatic Codable encoding for enums with associated values:
/// <c>{ "&lt;case&gt;": { "_0": &lt;config&gt; } }</c>.
/// Required so SettingsBackup files round-trip between macOS and Windows.
/// </summary>
public sealed class DestinationTypeJsonConverter : JsonConverter<DestinationType>
{
    private const string AssociatedValueKey = "_0";

    public override DestinationType Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.StartObject)
            throw new JsonException("Expected object for DestinationType");

        reader.Read();
        if (reader.TokenType != JsonTokenType.PropertyName)
            throw new JsonException("Expected discriminator property name");

        var discriminator = reader.GetString() ?? throw new JsonException("Missing discriminator");
        reader.Read();
        if (reader.TokenType != JsonTokenType.StartObject)
            throw new JsonException("Expected wrapper object after discriminator");

        reader.Read();
        if (reader.TokenType != JsonTokenType.PropertyName || reader.GetString() != AssociatedValueKey)
            throw new JsonException($"Expected '{AssociatedValueKey}' key inside discriminator");

        reader.Read();
        DestinationType result = discriminator switch
        {
            "supabase" => new SupabaseDestinationType(JsonSerializer.Deserialize<SupabaseConfig>(ref reader, options)
                ?? throw new JsonException("Null SupabaseConfig")),
            "restNoAuth" => new RestNoAuthDestinationType(JsonSerializer.Deserialize<RestNoAuthConfig>(ref reader, options)
                ?? throw new JsonException("Null RestNoAuthConfig")),
            "restApiKey" => new RestApiKeyDestinationType(JsonSerializer.Deserialize<RestApiKeyConfig>(ref reader, options)
                ?? throw new JsonException("Null RestApiKeyConfig")),
            "restOAuthPassword" => new RestOAuthPasswordDestinationType(JsonSerializer.Deserialize<RestOAuthPasswordConfig>(ref reader, options)
                ?? throw new JsonException("Null RestOAuthPasswordConfig")),
            "restOAuthClientCredentials" => new RestOAuthClientCredentialsDestinationType(
                JsonSerializer.Deserialize<RestOAuthClientCredentialsConfig>(ref reader, options)
                ?? throw new JsonException("Null RestOAuthClientCredentialsConfig")),
            _ => throw new JsonException($"Unknown destination discriminator: {discriminator}")
        };

        reader.Read();
        if (reader.TokenType != JsonTokenType.EndObject)
            throw new JsonException("Expected end of associated-value wrapper");
        reader.Read();
        if (reader.TokenType != JsonTokenType.EndObject)
            throw new JsonException("Expected end of DestinationType object");

        return result;
    }

    public override void Write(Utf8JsonWriter writer, DestinationType value, JsonSerializerOptions options)
    {
        writer.WriteStartObject();
        writer.WritePropertyName(value.DiscriminatorKey);
        writer.WriteStartObject();
        writer.WritePropertyName(AssociatedValueKey);
        switch (value)
        {
            case SupabaseDestinationType s:
                JsonSerializer.Serialize(writer, s.Config, options);
                break;
            case RestNoAuthDestinationType r:
                JsonSerializer.Serialize(writer, r.Config, options);
                break;
            case RestApiKeyDestinationType r:
                JsonSerializer.Serialize(writer, r.Config, options);
                break;
            case RestOAuthPasswordDestinationType r:
                JsonSerializer.Serialize(writer, r.Config, options);
                break;
            case RestOAuthClientCredentialsDestinationType r:
                JsonSerializer.Serialize(writer, r.Config, options);
                break;
            default:
                throw new JsonException($"Unhandled DestinationType subtype: {value.GetType().Name}");
        }
        writer.WriteEndObject();
        writer.WriteEndObject();
    }
}

/// <summary>
/// Writes Guid as upper-case (matches Swift <c>UUID.uuidString</c>) so destination IDs and
/// idempotency keys produced on Windows are byte-identical to those produced on macOS.
/// Reads any case.
/// </summary>
public sealed class UpperCaseGuidJsonConverter : JsonConverter<Guid>
{
    public override Guid Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        var s = reader.GetString();
        return s is null ? throw new JsonException("Expected GUID string") : Guid.Parse(s);
    }

    public override void Write(Utf8JsonWriter writer, Guid value, JsonSerializerOptions options)
        => writer.WriteStringValue(value.ToString("D").ToUpperInvariant());
}

public static class DestinationJson
{
    /// <summary>Default options used by stores and senders.</summary>
    public static JsonSerializerOptions DefaultOptions { get; } = Build(indented: false, sortKeys: false);

    /// <summary>Used by SettingsBackup export — pretty + alphabetically sorted (matches macOS export).</summary>
    public static JsonSerializerOptions BackupOptions { get; } = Build(indented: true, sortKeys: true);

    private static JsonSerializerOptions Build(bool indented, bool sortKeys)
    {
        var opts = new JsonSerializerOptions
        {
            WriteIndented = indented,
            DefaultIgnoreCondition = JsonIgnoreCondition.Never,
        };
        opts.Converters.Add(new UpperCaseGuidJsonConverter());
        if (sortKeys)
        {
            opts.TypeInfoResolver = new SortedKeysTypeInfoResolver();
        }
        return opts;
    }
}

/// <summary>
/// Forces alphabetical property ordering in serialised output, matching Swift's
/// <c>JSONEncoder.OutputFormatting.sortedKeys</c>.
/// </summary>
internal sealed class SortedKeysTypeInfoResolver : System.Text.Json.Serialization.Metadata.DefaultJsonTypeInfoResolver
{
    public override System.Text.Json.Serialization.Metadata.JsonTypeInfo GetTypeInfo(Type type, JsonSerializerOptions options)
    {
        var info = base.GetTypeInfo(type, options);
        if (info.Kind == System.Text.Json.Serialization.Metadata.JsonTypeInfoKind.Object)
        {
            var sorted = info.Properties.OrderBy(p => p.Name, StringComparer.Ordinal).ToList();
            info.Properties.Clear();
            foreach (var p in sorted) info.Properties.Add(p);
        }
        return info;
    }
}
