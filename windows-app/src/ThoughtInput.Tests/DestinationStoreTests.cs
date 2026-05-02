using FluentAssertions;
using ThoughtInput.Core;
using Xunit;

namespace ThoughtInput.Tests;

public class DestinationStoreTests : IDisposable
{
    private readonly string _dir;
    private readonly string _file;

    public DestinationStoreTests()
    {
        _dir = Path.Combine(Path.GetTempPath(), "ti-store-" + Guid.NewGuid().ToString("N"));
        _file = Path.Combine(_dir, "destinations.json");
    }

    public void Dispose()
    {
        try { Directory.Delete(_dir, recursive: true); } catch { }
    }

    private static Destination NewRest(string name = "n", string url = "https://x")
        => new()
        {
            Name = name,
            Type = new RestNoAuthDestinationType(new RestNoAuthConfig(url)),
        };

    [Fact]
    public void Add_FirstDestination_IsActive()
    {
        var store = new DestinationStore(_file, new InMemorySecretStore());
        store.Add(NewRest());
        store.Destinations.Single().IsActive.Should().BeTrue();
    }

    [Fact]
    public void SetActive_OnlyOneIsActive()
    {
        var store = new DestinationStore(_file, new InMemorySecretStore());
        var a = NewRest("a");
        var b = NewRest("b");
        store.Add(a);
        store.Add(b);

        store.SetActive(b);
        store.Destinations.Single(d => d.IsActive).Name.Should().Be("b");
    }

    [Fact]
    public void Delete_RemovesSecretsFromStore()
    {
        var secrets = new InMemorySecretStore();
        var keyRef = KeychainRef.Create();
        secrets.SaveString(keyRef.Account, "secret");

        var store = new DestinationStore(_file, secrets);
        var d = new Destination
        {
            Name = "API",
            Type = new RestApiKeyDestinationType(new RestApiKeyConfig("https://x", "X-Key", keyRef)),
        };
        store.Add(d);

        store.Delete(d);

        secrets.LoadString(keyRef.Account).Should().BeNull();
        store.Destinations.Should().BeEmpty();
    }

    [Fact]
    public void Delete_PromotesSurvivingDestination()
    {
        var store = new DestinationStore(_file, new InMemorySecretStore());
        var a = NewRest("a");
        var b = NewRest("b");
        store.Add(a);
        store.Add(b);
        store.Delete(a); // a was active

        store.Destinations.Should().ContainSingle(d => d.IsActive && d.Name == "b");
    }

    [Fact]
    public void Persistence_RoundTripsAcrossInstances()
    {
        {
            var store = new DestinationStore(_file, new InMemorySecretStore());
            store.Add(NewRest("first"));
            store.Add(NewRest("second"));
        }
        {
            var reopened = new DestinationStore(_file, new InMemorySecretStore());
            reopened.Destinations.Select(d => d.Name).Should().Equal("first", "second");
            reopened.ActiveDestination!.Name.Should().Be("first");
        }
    }
}
