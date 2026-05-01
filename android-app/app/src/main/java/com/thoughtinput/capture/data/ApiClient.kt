package com.thoughtinput.capture.data

import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {

    sealed class Result {
        data class Success(val statusCode: Int, val body: String) : Result()
        data class HttpError(val statusCode: Int, val body: String) : Result()
        data class IoError(val message: String) : Result()
    }

    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
        contentType: String = "application/json",
        timeoutMs: Int = 10_000
    ): Result = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", contentType)
                for ((k, v) in headers) setRequestProperty(k, v)
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
            }

            connection.outputStream.use { it.write(body) }

            val statusCode = connection.responseCode
            val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
            connection.disconnect()

            if (statusCode in 200..299) {
                Result.Success(statusCode, responseBody)
            } else {
                CaptureLog.network("HTTP $statusCode for $url")
                Result.HttpError(statusCode, responseBody)
            }
        } catch (e: IOException) {
            CaptureLog.error("Network", "Network error: ${e.message}", e)
            Result.IoError(e.message ?: "Unknown network error")
        }
    }
}
