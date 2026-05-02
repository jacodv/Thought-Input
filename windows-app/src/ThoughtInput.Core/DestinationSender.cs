using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace ThoughtInput.Core;

public sealed class ServerErrorException(int statusCode, string? body = null)
    : Exception($"Server returned status {statusCode}")
{
    public int StatusCode { get; } = statusCode;
    public string? Body { get; } = body;
}

public enum ConnectionTestResult { Ok, TableMissing }

public sealed class DestinationSender(HttpClient httpClient, ISecretStore secretStore, OAuthTokenManager tokenManager)
{
    private static readonly TimeSpan DefaultTimeout = TimeSpan.FromSeconds(10);

    public async Task SendAsync(CapturePayload payload, Destination destination, CancellationToken ct = default)
    {
        var json = JsonSerializer.SerializeToUtf8Bytes(payload, DestinationJson.DefaultOptions);

        switch (destination.Type)
        {
            case SupabaseDestinationType s:
                await SendSupabaseAsync(json, s.Config, ct).ConfigureAwait(false);
                break;
            case RestNoAuthDestinationType r:
                await SendRestAsync(json, r.Config.EndpointURL, headers: null, ct).ConfigureAwait(false);
                break;
            case RestApiKeyDestinationType r:
                {
                    var key = secretStore.LoadString(r.Config.ApiKeyRef.Account) ?? "";
                    var headers = new Dictionary<string, string> { [r.Config.HeaderName] = key };
                    await SendRestAsync(json, r.Config.EndpointURL, headers, ct).ConfigureAwait(false);
                    break;
                }
            case RestOAuthPasswordDestinationType _:
            case RestOAuthClientCredentialsDestinationType _:
                await SendWithOAuthAsync(json, destination, ct).ConfigureAwait(false);
                break;
            default:
                throw new InvalidOperationException($"Unhandled destination type: {destination.Type.GetType().Name}");
        }
    }

    public async Task<ConnectionTestResult> TestConnectionAsync(Destination destination, CancellationToken ct = default)
    {
        if (destination.Type is SupabaseDestinationType s)
            return await TestSupabaseAsync(s.Config, ct).ConfigureAwait(false);

        var probe = CapturePayload.Create("Connection test", CaptureMethod.Typed);
        await SendAsync(probe, destination, ct).ConfigureAwait(false);
        return ConnectionTestResult.Ok;
    }

    private async Task<ConnectionTestResult> TestSupabaseAsync(SupabaseConfig config, CancellationToken ct)
    {
        var baseUrl = config.ProjectURL.TrimEnd('/');
        var url = $"{baseUrl}/rest/v1/{config.TableName}?limit=0";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
            throw new ServerErrorException(-1);

        var apiKey = secretStore.LoadString(config.ApiKeyRef.Account) ?? "";

        using var request = new HttpRequestMessage(HttpMethod.Get, uri);
        request.Headers.TryAddWithoutValidation("apikey", apiKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(DefaultTimeout);

        using var response = await httpClient.SendAsync(request, cts.Token).ConfigureAwait(false);
        if (response.IsSuccessStatusCode) return ConnectionTestResult.Ok;

        var status = (int)response.StatusCode;
        if (status is >= 400 and < 500)
        {
            var body = await response.Content.ReadAsStringAsync(cts.Token).ConfigureAwait(false);
            if (body.Contains("PGRST205", StringComparison.Ordinal) ||
                body.Contains("could not find", StringComparison.OrdinalIgnoreCase))
            {
                return ConnectionTestResult.TableMissing;
            }
        }
        throw new ServerErrorException(status);
    }

    private async Task SendSupabaseAsync(byte[] json, SupabaseConfig config, CancellationToken ct)
    {
        var url = $"{config.ProjectURL.TrimEnd('/')}/rest/v1/{config.TableName}";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
            throw new ServerErrorException(-1);

        var apiKey = secretStore.LoadString(config.ApiKeyRef.Account) ?? "";

        using var request = new HttpRequestMessage(HttpMethod.Post, uri)
        {
            Content = new ByteArrayContent(json),
        };
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json");
        request.Headers.TryAddWithoutValidation("Prefer", "return=minimal");
        request.Headers.TryAddWithoutValidation("apikey", apiKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

        await ExecuteAsync(request, ct).ConfigureAwait(false);
    }

    private async Task SendRestAsync(byte[] json, string url, IDictionary<string, string>? headers, CancellationToken ct)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
            throw new ServerErrorException(-1);

        using var request = new HttpRequestMessage(HttpMethod.Post, uri)
        {
            Content = new ByteArrayContent(json),
        };
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json");
        if (headers is not null)
        {
            foreach (var (k, v) in headers)
                request.Headers.TryAddWithoutValidation(k, v);
        }

        await ExecuteAsync(request, ct).ConfigureAwait(false);
    }

    private async Task SendWithOAuthAsync(byte[] json, Destination destination, CancellationToken ct)
    {
        var endpointUrl = destination.Type switch
        {
            RestOAuthPasswordDestinationType p => p.Config.EndpointURL,
            RestOAuthClientCredentialsDestinationType c => c.Config.EndpointURL,
            _ => throw new InvalidOperationException("Not an OAuth destination"),
        };

        var token = await tokenManager.GetValidTokenAsync(destination, ct).ConfigureAwait(false);

        try
        {
            await SendRestAsync(json, endpointUrl, new Dictionary<string, string>
            {
                ["Authorization"] = $"Bearer {token}",
            }, ct).ConfigureAwait(false);
        }
        catch (ServerErrorException ex) when (ex.StatusCode == 401)
        {
            tokenManager.ClearToken(destination.Id);
            var fresh = await tokenManager.GetValidTokenAsync(destination, ct).ConfigureAwait(false);
            await SendRestAsync(json, endpointUrl, new Dictionary<string, string>
            {
                ["Authorization"] = $"Bearer {fresh}",
            }, ct).ConfigureAwait(false);
        }
    }

    private async Task ExecuteAsync(HttpRequestMessage request, CancellationToken ct)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(DefaultTimeout);

        using var response = await httpClient.SendAsync(request, cts.Token).ConfigureAwait(false);
        if (response.IsSuccessStatusCode) return;

        var body = await response.Content.ReadAsStringAsync(cts.Token).ConfigureAwait(false);
        throw new ServerErrorException((int)response.StatusCode, body);
    }
}
