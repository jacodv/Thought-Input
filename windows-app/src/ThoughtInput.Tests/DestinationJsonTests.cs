using System.Text.Json;
using FluentAssertions;
using ThoughtInput.Core;
using Xunit;

namespace ThoughtInput.Tests;

public class DestinationJsonTests
{
    [Fact]
    public void Supabase_RoundTripsThroughSwiftCodableShape()
    {
        var original = new Destination
        {
            Id = Guid.Parse("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"),
            Name = "My Supabase",
            IsActive = true,
            Type = new SupabaseDestinationType(new SupabaseConfig(
                ProjectURL: "https://example.supabase.co",
                TableName: "thoughts",
                ApiKeyRef: new KeychainRef("11111111-1111-1111-1111-111111111111"))),
        };

        var json = JsonSerializer.Serialize(original, DestinationJson.DefaultOptions);

        // Swift Codable shape: type wraps the case in {"<case>": {"_0": <config>}}
        json.Should().Contain("\"type\":{\"supabase\":{\"_0\":{");
        json.Should().Contain("\"projectURL\":\"https://example.supabase.co\"");
        json.Should().Contain("\"tableName\":\"thoughts\"");
        json.Should().Contain("\"account\":\"11111111-1111-1111-1111-111111111111\"");
        json.Should().Contain("\"id\":\"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"");

        var roundTripped = JsonSerializer.Deserialize<Destination>(json, DestinationJson.DefaultOptions);
        roundTripped.Should().NotBeNull();
        roundTripped!.Id.Should().Be(original.Id);
        roundTripped.Name.Should().Be(original.Name);
        roundTripped.IsActive.Should().BeTrue();
        var supa = roundTripped.Type.Should().BeOfType<SupabaseDestinationType>().Subject;
        supa.Config.Should().BeEquivalentTo(((SupabaseDestinationType)original.Type).Config);
    }

    [Fact]
    public void RestApiKey_RoundTrips()
    {
        var original = new Destination
        {
            Id = Guid.Parse("12345678-1234-1234-1234-123456789ABC"),
            Name = "API",
            IsActive = false,
            Type = new RestApiKeyDestinationType(new RestApiKeyConfig(
                EndpointURL: "https://api.example.com/captures",
                HeaderName: "X-API-Key",
                ApiKeyRef: new KeychainRef("KEYREF"))),
        };

        var json = JsonSerializer.Serialize(original, DestinationJson.DefaultOptions);
        json.Should().Contain("\"restApiKey\":{\"_0\":{");

        var roundTripped = JsonSerializer.Deserialize<Destination>(json, DestinationJson.DefaultOptions);
        roundTripped.Should().NotBeNull();
        roundTripped!.Type.Should().BeOfType<RestApiKeyDestinationType>();
    }

    [Fact]
    public void OAuthClientCredentials_RoundTrips()
    {
        var original = new Destination
        {
            Name = "OAuth",
            Type = new RestOAuthClientCredentialsDestinationType(new RestOAuthClientCredentialsConfig(
                EndpointURL: "https://api.example.com/c",
                TokenURL: "https://auth.example.com/token",
                ClientIDRef: new KeychainRef("CID"),
                ClientSecretRef: new KeychainRef("CSEC"))),
        };

        var json = JsonSerializer.Serialize(original, DestinationJson.DefaultOptions);
        var roundTripped = JsonSerializer.Deserialize<Destination>(json, DestinationJson.DefaultOptions);
        roundTripped.Should().NotBeNull();
        var t = roundTripped!.Type.Should().BeOfType<RestOAuthClientCredentialsDestinationType>().Subject;
        t.Config.ClientIDRef.Account.Should().Be("CID");
        t.Config.ClientSecretRef.Account.Should().Be("CSEC");
    }

    [Fact]
    public void GuidsAreUpperCase_MatchingSwiftUUID()
    {
        var d = new Destination
        {
            Id = Guid.Parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            Name = "x",
            Type = new RestNoAuthDestinationType(new RestNoAuthConfig("https://x")),
        };
        var json = JsonSerializer.Serialize(d, DestinationJson.DefaultOptions);
        json.Should().Contain("\"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE\"");
        json.Should().NotContain("aaaaaaaa-bbbb");
    }

    [Fact]
    public void BackupOptions_SortsKeysAlphabetically()
    {
        var d = new Destination
        {
            Id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
            Name = "z",
            IsActive = true,
            Type = new RestNoAuthDestinationType(new RestNoAuthConfig("https://x")),
        };
        var json = JsonSerializer.Serialize(d, DestinationJson.BackupOptions);

        // Top-level fields must appear in alphabetical order: id, isActive, name, type
        var idIdx = json.IndexOf("\"id\"", StringComparison.Ordinal);
        var isActiveIdx = json.IndexOf("\"isActive\"", StringComparison.Ordinal);
        var nameIdx = json.IndexOf("\"name\"", StringComparison.Ordinal);
        var typeIdx = json.IndexOf("\"type\"", StringComparison.Ordinal);

        idIdx.Should().BeLessThan(isActiveIdx);
        isActiveIdx.Should().BeLessThan(nameIdx);
        nameIdx.Should().BeLessThan(typeIdx);
    }
}
