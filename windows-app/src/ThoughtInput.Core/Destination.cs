using System.Text.Json.Serialization;

namespace ThoughtInput.Core;

/// <summary>Opaque identifier referencing a secret stored in the platform secret store.</summary>
public sealed record KeychainRef(
    [property: JsonPropertyName("account")] string Account)
{
    public static KeychainRef Create() => new(Guid.NewGuid().ToString("D").ToUpperInvariant());
}

/// <summary>
/// Wire-format mirrors macOS Swift Codable:
/// <c>{ "id", "isActive", "name", "type": { &lt;case&gt;: { "_0": &lt;config&gt; } } }</c>.
/// </summary>
public sealed class Destination
{
    [JsonPropertyName("id")]
    public Guid Id { get; init; } = Guid.NewGuid();

    [JsonPropertyName("name")]
    public string Name { get; set; } = "";

    [JsonPropertyName("isActive")]
    public bool IsActive { get; set; }

    [JsonPropertyName("type")]
    [JsonConverter(typeof(DestinationTypeJsonConverter))]
    public DestinationType Type { get; set; } = new RestNoAuthDestinationType(new RestNoAuthConfig(""));

    public IReadOnlyList<KeychainRef> KeychainRefs => Type.KeychainRefs;
}

public abstract record DestinationType
{
    public abstract string DiscriminatorKey { get; }
    public abstract IReadOnlyList<KeychainRef> KeychainRefs { get; }
}

public sealed record SupabaseDestinationType(SupabaseConfig Config) : DestinationType
{
    public override string DiscriminatorKey => "supabase";
    public override IReadOnlyList<KeychainRef> KeychainRefs => [Config.ApiKeyRef];
}

public sealed record RestNoAuthDestinationType(RestNoAuthConfig Config) : DestinationType
{
    public override string DiscriminatorKey => "restNoAuth";
    public override IReadOnlyList<KeychainRef> KeychainRefs => [];
}

public sealed record RestApiKeyDestinationType(RestApiKeyConfig Config) : DestinationType
{
    public override string DiscriminatorKey => "restApiKey";
    public override IReadOnlyList<KeychainRef> KeychainRefs => [Config.ApiKeyRef];
}

public sealed record RestOAuthPasswordDestinationType(RestOAuthPasswordConfig Config) : DestinationType
{
    public override string DiscriminatorKey => "restOAuthPassword";
    public override IReadOnlyList<KeychainRef> KeychainRefs => [Config.UsernameRef, Config.PasswordRef];
}

public sealed record RestOAuthClientCredentialsDestinationType(RestOAuthClientCredentialsConfig Config) : DestinationType
{
    public override string DiscriminatorKey => "restOAuthClientCredentials";
    public override IReadOnlyList<KeychainRef> KeychainRefs => [Config.ClientIDRef, Config.ClientSecretRef];
}

public sealed record SupabaseConfig(
    [property: JsonPropertyName("projectURL")] string ProjectURL,
    [property: JsonPropertyName("tableName")] string TableName,
    [property: JsonPropertyName("apiKeyRef")] KeychainRef ApiKeyRef);

public sealed record RestNoAuthConfig(
    [property: JsonPropertyName("endpointURL")] string EndpointURL);

public sealed record RestApiKeyConfig(
    [property: JsonPropertyName("endpointURL")] string EndpointURL,
    [property: JsonPropertyName("headerName")] string HeaderName,
    [property: JsonPropertyName("apiKeyRef")] KeychainRef ApiKeyRef);

public sealed record RestOAuthPasswordConfig(
    [property: JsonPropertyName("endpointURL")] string EndpointURL,
    [property: JsonPropertyName("tokenURL")] string TokenURL,
    [property: JsonPropertyName("usernameRef")] KeychainRef UsernameRef,
    [property: JsonPropertyName("passwordRef")] KeychainRef PasswordRef);

public sealed record RestOAuthClientCredentialsConfig(
    [property: JsonPropertyName("endpointURL")] string EndpointURL,
    [property: JsonPropertyName("tokenURL")] string TokenURL,
    [property: JsonPropertyName("clientIDRef")] KeychainRef ClientIDRef,
    [property: JsonPropertyName("clientSecretRef")] KeychainRef ClientSecretRef);

public sealed record OAuthToken(
    string AccessToken,
    DateTimeOffset? ExpiresAt,
    string? RefreshToken)
{
    public bool IsExpired => ExpiresAt.HasValue && DateTimeOffset.UtcNow.AddSeconds(30) >= ExpiresAt.Value;
}

public sealed record PendingCapture(
    [property: JsonPropertyName("payload")] CapturePayload Payload,
    [property: JsonPropertyName("destinationID")] Guid DestinationID,
    [property: JsonPropertyName("destinationSnapshot")] Destination DestinationSnapshot);
