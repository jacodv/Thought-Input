using System.Text.Json;
using FluentAssertions;
using ThoughtInput.Core;
using Xunit;

namespace ThoughtInput.Tests;

public class CapturePayloadTests
{
    [Fact]
    public void Create_FillsAllRequiredFields()
    {
        var payload = CapturePayload.Create("hello", CaptureMethod.Typed);

        payload.Text.Should().Be("hello");
        payload.SourcePlatform.Should().Be("windows");
        payload.CaptureMethod.Should().Be(CaptureMethod.Typed);
        payload.IdempotencyKey.Should().MatchRegex("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");
        payload.ClientVersion.Should().MatchRegex(@"^\d+\.\d+\.\d+$");
        payload.DeviceName.Should().NotBeNullOrEmpty();
        payload.Timestamp.Should().MatchRegex(@"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$");
    }

    [Fact]
    public void Serialize_UsesSnakeCaseFields()
    {
        var payload = CapturePayload.Create(
            "the thought",
            CaptureMethod.Voice,
            clientVersion: "1.2.3",
            deviceName: "Test PC",
            now: DateTimeOffset.Parse("2026-05-02T12:34:56Z"),
            idempotencyKey: Guid.Parse("11111111-2222-3333-4444-555555555555"));

        var json = JsonSerializer.Serialize(payload, DestinationJson.DefaultOptions);

        json.Should().Contain("\"text\":\"the thought\"");
        json.Should().Contain("\"source_platform\":\"windows\"");
        json.Should().Contain("\"client_version\":\"1.2.3\"");
        json.Should().Contain("\"capture_method\":\"voice\"");
        json.Should().Contain("\"idempotency_key\":\"11111111-2222-3333-4444-555555555555\"");
        json.Should().Contain("\"device_name\":\"Test PC\"");
        json.Should().Contain("\"timestamp\":\"2026-05-02T12:34:56Z\"");
    }

    [Fact]
    public void IdempotencyKey_IsUpperCase_MatchingSwift()
    {
        var payload = CapturePayload.Create("x", CaptureMethod.Typed);
        payload.IdempotencyKey.Should().Be(payload.IdempotencyKey.ToUpperInvariant());
    }
}
