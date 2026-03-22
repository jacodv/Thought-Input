package com.thoughtinput.capture.data

import android.content.Context
import android.content.SharedPreferences
import com.thoughtinput.capture.util.CaptureLog

class CaptureRepository(context: Context) {

    private val apiClient = ApiClient()
    private val pendingStore = PendingCaptureStore(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("thought_input_settings", Context.MODE_PRIVATE)

    val apiEndpoint: String
        get() = prefs.getString("api_endpoint", "") ?: ""

    fun setApiEndpoint(url: String) {
        prefs.edit().putString("api_endpoint", url).apply()
    }

    suspend fun submit(text: String, method: CapturePayload.CaptureMethod): Boolean {
        val payload = CapturePayload.create(text, method)
        val endpoint = apiEndpoint

        if (endpoint.isBlank()) {
            CaptureLog.network("No API endpoint configured")
            pendingStore.save(payload)
            return false
        }

        return when (apiClient.post(payload, endpoint)) {
            is ApiClient.Result.Success -> true
            is ApiClient.Result.Error -> {
                pendingStore.save(payload)
                false
            }
        }
    }

    suspend fun retryPending() {
        val endpoint = apiEndpoint
        if (endpoint.isBlank()) return

        for (payload in pendingStore.loadAll()) {
            when (apiClient.post(payload, endpoint)) {
                is ApiClient.Result.Success -> {
                    pendingStore.remove(payload.idempotencyKey)
                    CaptureLog.network("Retry succeeded: ${payload.idempotencyKey}")
                }
                is ApiClient.Result.Error -> {
                    CaptureLog.network("Retry failed: ${payload.idempotencyKey}")
                }
            }
        }
    }
}
