using System.Collections.Concurrent;
using System.Net.Http.Headers;
using System.Text.Json;

namespace ThoughtInput.Core;

public enum OAuthErrorKind
{
    MissingCredentials,
    InvalidTokenURL,
    TokenExchangeFailed,
    InvalidTokenResponse,
}

public sealed class OAuthException(OAuthErrorKind kind, string message, int statusCode = 0)
    : Exception(message)
{
    public OAuthErrorKind Kind { get; } = kind;
    public int StatusCode { get; } = statusCode;
}

public sealed class OAuthTokenManager(HttpClient httpClient, ISecretStore secretStore)
{
    private readonly ConcurrentDictionary<Guid, OAuthToken> _cache = new();

    public async Task<string> GetValidTokenAsync(Destination destination, CancellationToken ct = default)
    {
        if (_cache.TryGetValue(destination.Id, out var cached) && !cached.IsExpired)
            return cached.AccessToken;

        if (cached?.RefreshToken is { } refresh)
        {
            try
            {
                var refreshed = await RefreshAsync(destination, refresh, ct).ConfigureAwait(false);
                _cache[destination.Id] = refreshed;
                return refreshed.AccessToken;
            }
            catch
            {
                // fall through to fresh grant
            }
        }

        var token = await FetchAsync(destination, ct).ConfigureAwait(false);
        _cache[destination.Id] = token;
        return token.AccessToken;
    }

    public void ClearToken(Guid destinationId) => _cache.TryRemove(destinationId, out _);

    private Task<OAuthToken> FetchAsync(Destination destination, CancellationToken ct) => destination.Type switch
    {
        RestOAuthPasswordDestinationType p => PasswordGrant(p.Config, ct),
        RestOAuthClientCredentialsDestinationType c => ClientCredentialsGrant(c.Config, ct),
        _ => throw new OAuthException(OAuthErrorKind.MissingCredentials, "Destination is not an OAuth type"),
    };

    private async Task<OAuthToken> PasswordGrant(RestOAuthPasswordConfig config, CancellationToken ct)
    {
        var username = secretStore.LoadString(config.UsernameRef.Account);
        var password = secretStore.LoadString(config.PasswordRef.Account);
        if (string.IsNullOrEmpty(username) || string.IsNullOrEmpty(password))
            throw new OAuthException(OAuthErrorKind.MissingCredentials, "Username/password not stored");

        var body = $"grant_type=password&username={Uri.EscapeDataString(username)}&password={Uri.EscapeDataString(password)}";
        return await ExchangeAsync(config.TokenURL, body, ct).ConfigureAwait(false);
    }

    private async Task<OAuthToken> ClientCredentialsGrant(RestOAuthClientCredentialsConfig config, CancellationToken ct)
    {
        var clientId = secretStore.LoadString(config.ClientIDRef.Account);
        var clientSecret = secretStore.LoadString(config.ClientSecretRef.Account);
        if (string.IsNullOrEmpty(clientId) || string.IsNullOrEmpty(clientSecret))
            throw new OAuthException(OAuthErrorKind.MissingCredentials, "Client id/secret not stored");

        var body = $"grant_type=client_credentials&client_id={Uri.EscapeDataString(clientId)}&client_secret={Uri.EscapeDataString(clientSecret)}";
        return await ExchangeAsync(config.TokenURL, body, ct).ConfigureAwait(false);
    }

    private async Task<OAuthToken> RefreshAsync(Destination destination, string refreshToken, CancellationToken ct)
    {
        var tokenUrl = destination.Type switch
        {
            RestOAuthPasswordDestinationType p => p.Config.TokenURL,
            RestOAuthClientCredentialsDestinationType c => c.Config.TokenURL,
            _ => throw new OAuthException(OAuthErrorKind.MissingCredentials, "Not an OAuth destination"),
        };
        var body = $"grant_type=refresh_token&refresh_token={Uri.EscapeDataString(refreshToken)}";
        return await ExchangeAsync(tokenUrl, body, ct).ConfigureAwait(false);
    }

    private async Task<OAuthToken> ExchangeAsync(string tokenUrl, string body, CancellationToken ct)
    {
        if (!Uri.TryCreate(tokenUrl, UriKind.Absolute, out var uri))
            throw new OAuthException(OAuthErrorKind.InvalidTokenURL, $"Invalid token URL: {tokenUrl}");

        using var request = new HttpRequestMessage(HttpMethod.Post, uri)
        {
            Content = new StringContent(body, System.Text.Encoding.UTF8, "application/x-www-form-urlencoded"),
        };

        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(TimeSpan.FromSeconds(15));

        using var response = await httpClient.SendAsync(request, cts.Token).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
            throw new OAuthException(OAuthErrorKind.TokenExchangeFailed,
                $"Token exchange failed (status {(int)response.StatusCode})", (int)response.StatusCode);

        await using var stream = await response.Content.ReadAsStreamAsync(cts.Token).ConfigureAwait(false);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cts.Token).ConfigureAwait(false);

        var root = doc.RootElement;
        if (!root.TryGetProperty("access_token", out var accessProp) || accessProp.ValueKind != JsonValueKind.String)
            throw new OAuthException(OAuthErrorKind.InvalidTokenResponse, "Token response missing access_token");

        DateTimeOffset? expiresAt = null;
        if (root.TryGetProperty("expires_in", out var expiresInProp) && expiresInProp.TryGetDouble(out var seconds))
            expiresAt = DateTimeOffset.UtcNow.AddSeconds(seconds);

        string? refreshToken = null;
        if (root.TryGetProperty("refresh_token", out var refreshProp) && refreshProp.ValueKind == JsonValueKind.String)
            refreshToken = refreshProp.GetString();

        return new OAuthToken(accessProp.GetString()!, expiresAt, refreshToken);
    }
}
