namespace ThoughtInput.Core;

public sealed class CaptureService(
    DestinationStore destinationStore,
    PendingCaptureStore pendingStore,
    DestinationSender sender)
{
    public bool IsSending { get; private set; }
    public string? LastError { get; private set; }

    public async Task<bool> SubmitAsync(string text, CaptureMethod method, CancellationToken ct = default)
    {
        var payload = CapturePayload.Create(text, method);
        IsSending = true;
        LastError = null;

        try
        {
            var destination = destinationStore.ActiveDestination;
            if (destination is null)
            {
                var saved = pendingStore.Save(new PendingCapture(
                    payload,
                    Guid.Empty,
                    new Destination
                    {
                        Name = "None",
                        IsActive = false,
                        Type = new RestNoAuthDestinationType(new RestNoAuthConfig("")),
                    }));
                LastError = saved ? "No destination configured" : "Failed to save capture offline";
                return false;
            }

            try
            {
                await sender.SendAsync(payload, destination, ct).ConfigureAwait(false);
                return true;
            }
            catch (Exception ex)
            {
                var pending = new PendingCapture(payload, destination.Id, destination);
                if (!pendingStore.Save(pending))
                {
                    LastError = "Failed to save capture offline";
                }
                else
                {
                    LastError = ex.Message;
                }
                return false;
            }
        }
        finally
        {
            IsSending = false;
        }
    }

    public async Task RetryPendingAsync(CancellationToken ct = default)
    {
        var pending = pendingStore.LoadAll();
        if (pending.Count == 0) return;

        foreach (var capture in pending)
        {
            var destination = ResolveDestination(capture);
            if (destination is null) continue;

            try
            {
                await sender.SendAsync(capture.Payload, destination, ct).ConfigureAwait(false);
                pendingStore.Remove(capture.Payload.IdempotencyKey);
            }
            catch (OAuthException ex) when (ex.Kind == OAuthErrorKind.MissingCredentials)
            {
                // Credentials gone — abandon to avoid infinite retry (matches macOS).
                pendingStore.Remove(capture.Payload.IdempotencyKey);
            }
            catch
            {
                // Leave the file for the next retry pass.
            }
        }
    }

    private Destination? ResolveDestination(PendingCapture capture)
    {
        var live = destinationStore.Destinations.FirstOrDefault(d => d.Id == capture.DestinationID);
        if (live is not null) return live;

        // Legacy capture with no real destination — fall back to active.
        if (capture.DestinationSnapshot.Type is RestNoAuthDestinationType { Config.EndpointURL: "" })
            return destinationStore.ActiveDestination;

        return capture.DestinationSnapshot;
    }
}
