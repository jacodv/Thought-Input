package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import com.thoughtinput.capture.data.CapturePayload

class CaptureHttpException(val statusCode: Int, val responseBody: String = "") :
    Exception("Server returned status $statusCode")

class DestinationSender(
    private val keystore: SecretsKeystore,
    private val tokenManager: OAuthTokenManager,
    private val apiClient: ApiClient = ApiClient()
) {

    suspend fun send(payload: CapturePayload, destination: Destination) {
        val data = payload.toJson().toByteArray(Charsets.UTF_8)
        when (val type = destination.type) {
            is DestinationType.Supabase -> sendSupabase(data, type)
            is DestinationType.RestNoAuth -> sendRest(data, type.endpointURL, emptyMap())
            is DestinationType.RestApiKey -> {
                val key = keystore.loadString(type.apiKeyRef).orEmpty()
                sendRest(data, type.endpointURL, mapOf(type.headerName to key))
            }
            is DestinationType.RestOAuthPassword,
            is DestinationType.RestOAuthClientCredentials -> sendWithOAuth(data, destination)
        }
    }

    suspend fun testConnection(destination: Destination) {
        val test = CapturePayload.create("Connection test", CapturePayload.CaptureMethod.TYPED)
        send(test, destination)
    }

    private suspend fun sendSupabase(data: ByteArray, config: DestinationType.Supabase) {
        val base = config.projectURL.trim().trimEnd('/')
        val url = "$base/rest/v1/${config.tableName}"
        val apiKey = keystore.loadString(config.apiKeyRef).orEmpty()
        val headers = mapOf(
            "Prefer" to "return=minimal",
            "apikey" to apiKey,
            "Authorization" to "Bearer $apiKey"
        )
        execute(url, headers, data)
    }

    private suspend fun sendRest(data: ByteArray, url: String, headers: Map<String, String>) {
        execute(url, headers, data)
    }

    private suspend fun sendWithOAuth(data: ByteArray, destination: Destination) {
        val endpointURL = when (val t = destination.type) {
            is DestinationType.RestOAuthPassword -> t.endpointURL
            is DestinationType.RestOAuthClientCredentials -> t.endpointURL
            else -> throw IllegalStateException("Non-OAuth type in sendWithOAuth")
        }

        val token = tokenManager.validToken(destination)
        try {
            execute(endpointURL, mapOf("Authorization" to "Bearer $token"), data)
        } catch (e: CaptureHttpException) {
            if (e.statusCode == 401) {
                tokenManager.clearToken(destination.id)
                val newToken = tokenManager.validToken(destination)
                execute(endpointURL, mapOf("Authorization" to "Bearer $newToken"), data)
            } else throw e
        }
    }

    private suspend fun execute(url: String, headers: Map<String, String>, data: ByteArray) {
        when (val r = apiClient.post(url, headers, data)) {
            is ApiClient.Result.Success -> Unit
            is ApiClient.Result.HttpError -> throw CaptureHttpException(r.statusCode, r.body)
            is ApiClient.Result.IoError -> throw CaptureHttpException(-1, r.message)
        }
    }
}
