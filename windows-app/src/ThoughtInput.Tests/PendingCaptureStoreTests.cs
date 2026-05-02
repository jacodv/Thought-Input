using System.Text.Json;
using FluentAssertions;
using ThoughtInput.Core;
using Xunit;

namespace ThoughtInput.Tests;

public class PendingCaptureStoreTests : IDisposable
{
    private readonly string _dir;

    public PendingCaptureStoreTests()
    {
        _dir = Path.Combine(Path.GetTempPath(), "ti-pending-" + Guid.NewGuid().ToString("N"));
    }

    public void Dispose()
    {
        try { Directory.Delete(_dir, recursive: true); } catch { }
    }

    private static PendingCapture MakePending(string text = "thought")
    {
        var payload = CapturePayload.Create(text, CaptureMethod.Typed);
        var dest = new Destination
        {
            Name = "n",
            Type = new RestNoAuthDestinationType(new RestNoAuthConfig("https://example.com")),
        };
        return new PendingCapture(payload, dest.Id, dest);
    }

    [Fact]
    public void Save_LoadAll_Round_Trips()
    {
        var store = new PendingCaptureStore(_dir);
        var p = MakePending();
        store.Save(p).Should().BeTrue();

        var loaded = store.LoadAll();
        loaded.Should().HaveCount(1);
        loaded[0].Payload.IdempotencyKey.Should().Be(p.Payload.IdempotencyKey);
        loaded[0].Payload.Text.Should().Be("thought");
    }

    [Fact]
    public void Remove_DeletesFile()
    {
        var store = new PendingCaptureStore(_dir);
        var p = MakePending();
        store.Save(p);
        store.PendingCount.Should().Be(1);

        store.Remove(p.Payload.IdempotencyKey);
        store.PendingCount.Should().Be(0);
    }

    [Fact]
    public void LoadAll_ReadsLegacyBareCapturePayload()
    {
        Directory.CreateDirectory(_dir);
        var legacyPayload = CapturePayload.Create("legacy", CaptureMethod.Typed);
        var bytes = JsonSerializer.SerializeToUtf8Bytes(legacyPayload, DestinationJson.DefaultOptions);
        File.WriteAllBytes(Path.Combine(_dir, $"{legacyPayload.IdempotencyKey}.json"), bytes);

        var store = new PendingCaptureStore(_dir);
        var loaded = store.LoadAll();

        loaded.Should().HaveCount(1);
        loaded[0].Payload.Text.Should().Be("legacy");
        loaded[0].DestinationSnapshot.Type.Should().BeOfType<RestNoAuthDestinationType>();
    }
}
