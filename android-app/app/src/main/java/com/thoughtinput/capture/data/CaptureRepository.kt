package com.thoughtinput.capture.data

import android.content.Context
import android.content.SharedPreferences
import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureRepository(context: Context) {

    private val apiClient = ApiClient()
    private val pendingStore = PendingCaptureStore(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("thought_input_settings", Context.MODE_PRIVATE)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val apiEndpoint: String
        get() = prefs.getString("api_endpoint", "") ?: ""

    fun setApiEndpoint(url: String) {
        prefs.edit().putString("api_endpoint", url).apply()
    }

    suspend fun submit(text: String, method: CapturePayload.CaptureMethod): Boolean {
        val payload = CapturePayload.create(text, method)
        val endpoint = apiEndpoint

        _isSending.value = true
        _lastError.value = null

        try {
            if (endpoint.isBlank()) {
                CaptureLog.network("No API endpoint configured")
                if (!pendingStore.save(payload)) {
                    _lastError.value = "Failed to save capture offline"
                } else {
                    _lastError.value = "No API endpoint configured"
                }
                return false
            }

            return when (val result = apiClient.post(payload, endpoint)) {
                is ApiClient.Result.Success -> true
                is ApiClient.Result.Error -> {
                    CaptureLog.error("Network", "Submit failed: ${result.message}")
                    if (!pendingStore.save(payload)) {
                        _lastError.value = "Failed to save capture offline"
                    } else {
                        _lastError.value = result.message
                    }
                    false
                }
            }
        } finally {
            _isSending.value = false
        }
    }

    suspend fun retryPending() {
        val endpoint = apiEndpoint
        if (endpoint.isBlank()) {
            val count = pendingStore.pendingCount
            if (count > 0) {
                CaptureLog.network("Cannot retry $count pending captures: no API endpoint configured")
            }
            return
        }

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
