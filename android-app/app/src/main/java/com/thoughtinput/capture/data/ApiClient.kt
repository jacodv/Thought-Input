package com.thoughtinput.capture.data

import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {

    sealed class Result {
        data object Success : Result()
        data class Error(val message: String, val statusCode: Int = -1) : Result()
    }

    suspend fun post(payload: CapturePayload, endpointUrl: String): Result = withContext(Dispatchers.IO) {
        try {
            val url = URL(endpointUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
            }

            connection.outputStream.use { output ->
                output.write(payload.toJson().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            connection.disconnect()

            if (statusCode in 200..299) {
                CaptureLog.network("Capture submitted: ${payload.idempotencyKey}")
                Result.Success
            } else {
                CaptureLog.network("Server returned $statusCode for ${payload.idempotencyKey}")
                Result.Error("Server returned $statusCode", statusCode)
            }
        } catch (e: java.io.IOException) {
            CaptureLog.error("Network", "Network error: ${e.message}", e)
            Result.Error(e.message ?: "Unknown network error")
        }
    }
}
