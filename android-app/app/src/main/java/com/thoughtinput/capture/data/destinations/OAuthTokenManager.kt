package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

sealed class OAuthError(message: String) : Exception(message) {
    object MissingCredentials : OAuthError("OAuth credentials not found in keystore")
    object InvalidTokenURL : OAuthError("Invalid token URL")
    class TokenExchangeFailed(val statusCode: Int) : OAuthError("Token exchange failed (status $statusCode)")
    object InvalidTokenResponse : OAuthError("Token response missing access_token")
}

class OAuthTokenManager(
    private val keystore: SecretsKeystore,
    private val apiClient: ApiClient = ApiClient()
) {
    private val tokenCache = mutableMapOf<UUID, OAuthToken>()
    private val mutex = Mutex()

    suspend fun validToken(destination: Destination): String = mutex.withLock {
        tokenCache[destination.id]?.let { cached ->
            if (!cached.isExpired) return@withLock cached.accessToken
            cached.refreshToken?.let { rt ->
                runCatching { refreshToken(rt, destination) }.getOrNull()?.let { refreshed ->
                    tokenCache[destination.id] = refreshed
                    return@withLock refreshed.accessToken
                }
            }
        }

        val fresh = fetchToken(destination)
        tokenCache[destination.id] = fresh
        fresh.accessToken
    }

    suspend fun clearToken(destinationID: UUID) = mutex.withLock {
        tokenCache.remove(destinationID)
        Unit
    }

    private suspend fun fetchToken(destination: Destination): OAuthToken {
        return when (val type = destination.type) {
            is DestinationType.RestOAuthPassword -> passwordGrant(type)
            is DestinationType.RestOAuthClientCredentials -> clientCredentialsGrant(type)
            else -> {
                CaptureLog.error("Auth", "fetchToken called for non-OAuth destination type")
                throw OAuthError.MissingCredentials
            }
        }
    }

    private suspend fun passwordGrant(config: DestinationType.RestOAuthPassword): OAuthToken {
        val username = keystore.loadString(config.usernameRef)
            ?: throw OAuthError.MissingCredentials
        val password = keystore.loadString(config.passwordRef)
            ?: throw OAuthError.MissingCredentials

        val body = "grant_type=password" +
            "&username=${urlEncode(username)}" +
            "&password=${urlEncode(password)}"
        return exchangeToken(config.tokenURL, body)
    }

    private suspend fun clientCredentialsGrant(config: DestinationType.RestOAuthClientCredentials): OAuthToken {
        val clientID = keystore.loadString(config.clientIDRef)
            ?: throw OAuthError.MissingCredentials
        val clientSecret = keystore.loadString(config.clientSecretRef)
            ?: throw OAuthError.MissingCredentials

        val body = "grant_type=client_credentials" +
            "&client_id=${urlEncode(clientID)}" +
            "&client_secret=${urlEncode(clientSecret)}"
        return exchangeToken(config.tokenURL, body)
    }

    private suspend fun refreshToken(token: String, destination: Destination): OAuthToken {
        val tokenURL = when (val t = destination.type) {
            is DestinationType.RestOAuthPassword -> t.tokenURL
            is DestinationType.RestOAuthClientCredentials -> t.tokenURL
            else -> throw OAuthError.MissingCredentials
        }
        val body = "grant_type=refresh_token&refresh_token=${urlEncode(token)}"
        return exchangeToken(tokenURL, body)
    }

    private suspend fun exchangeToken(tokenURL: String, body: String): OAuthToken {
        if (tokenURL.isBlank()) throw OAuthError.InvalidTokenURL

        val result = apiClient.post(
            url = tokenURL,
            headers = emptyMap(),
            body = body.toByteArray(Charsets.UTF_8),
            contentType = "application/x-www-form-urlencoded",
            timeoutMs = 15_000
        )

        return when (result) {
            is ApiClient.Result.Success -> parseTokenResponse(result.body)
            is ApiClient.Result.HttpError -> throw OAuthError.TokenExchangeFailed(result.statusCode)
            is ApiClient.Result.IoError -> throw OAuthError.TokenExchangeFailed(-1)
        }
    }

    private fun parseTokenResponse(body: String): OAuthToken {
        val json = try { JSONObject(body) } catch (e: Exception) {
            throw OAuthError.InvalidTokenResponse
        }
        val accessToken = json.optString("access_token", "").ifBlank {
            throw OAuthError.InvalidTokenResponse
        }
        val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", -1L).takeIf { it > 0 } else null
        val refreshToken = if (json.has("refresh_token")) json.optString("refresh_token", "").ifBlank { null } else null
        return OAuthToken(
            accessToken = accessToken,
            expiresAt = expiresIn?.let { System.currentTimeMillis() + it * 1000L },
            refreshToken = refreshToken
        )
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
