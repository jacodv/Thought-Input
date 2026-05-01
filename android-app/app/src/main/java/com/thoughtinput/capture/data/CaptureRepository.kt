package com.thoughtinput.capture.data

import android.content.Context
import com.thoughtinput.capture.data.destinations.CaptureHttpException
import com.thoughtinput.capture.data.destinations.Destination
import com.thoughtinput.capture.data.destinations.DestinationSender
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.DestinationType
import com.thoughtinput.capture.data.destinations.EncryptedKeystore
import com.thoughtinput.capture.data.destinations.OAuthError
import com.thoughtinput.capture.data.destinations.OAuthTokenManager
import com.thoughtinput.capture.data.destinations.PendingCapture
import com.thoughtinput.capture.data.destinations.SecretsKeystore
import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class SubmitResult {
    data object Success : SubmitResult()
    data object NoDestination : SubmitResult()
    data class QueuedOffline(val reason: String) : SubmitResult()
    data class Failed(val reason: String) : SubmitResult()
}

class CaptureRepository(
    context: Context,
    val destinationStore: DestinationStore = DestinationStore.get(context),
    private val keystore: SecretsKeystore = EncryptedKeystore(context),
    private val sender: DestinationSender = DestinationSender(
        keystore,
        OAuthTokenManager(keystore)
    )
) {
    private val pendingStore = PendingCaptureStore(context)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun submit(text: String, method: CapturePayload.CaptureMethod): SubmitResult {
        val payload = CapturePayload.create(text, method)
        _isSending.value = true
        _lastError.value = null

        try {
            val destination = destinationStore.activeDestination
            if (destination == null) {
                CaptureLog.network("No active destination configured")
                _lastError.value = "No destination configured"
                return SubmitResult.NoDestination
            }

            return try {
                sender.send(payload, destination)
                CaptureLog.network("Capture submitted: ${payload.idempotencyKey}")
                SubmitResult.Success
            } catch (e: Exception) {
                CaptureLog.error("Network", "Submit failed: ${e.message}", e)
                val pending = PendingCapture(payload, destination.id, destination)
                if (pendingStore.save(pending)) {
                    val reason = humanReason(e)
                    _lastError.value = reason
                    SubmitResult.QueuedOffline(reason)
                } else {
                    val reason = "Failed to save capture offline"
                    _lastError.value = reason
                    SubmitResult.Failed(reason)
                }
            }
        } finally {
            _isSending.value = false
        }
    }

    suspend fun retryPending() {
        val pending = pendingStore.loadAll()
        if (pending.isEmpty()) return

        for (capture in pending) {
            val live = destinationStore.getById(capture.destinationID)
            val destination: Destination = live ?: capture.destinationSnapshot

            try {
                sender.send(capture.payload, destination)
                pendingStore.remove(capture.payload.idempotencyKey)
                CaptureLog.network("Retry succeeded: ${capture.payload.idempotencyKey}")
            } catch (e: OAuthError.MissingCredentials) {
                pendingStore.remove(capture.payload.idempotencyKey)
                CaptureLog.error(
                    "Network",
                    "Abandoned ${capture.payload.idempotencyKey}: credentials no longer available"
                )
            } catch (e: Exception) {
                CaptureLog.network("Retry failed for ${capture.payload.idempotencyKey}: ${e.message}")
            }
        }
    }

    val pendingCount: Int get() = pendingStore.pendingCount

    private fun humanReason(e: Exception): String = when (e) {
        is CaptureHttpException -> if (e.statusCode > 0) "Server returned ${e.statusCode}" else "Network error"
        is OAuthError -> e.message ?: "OAuth error"
        else -> e.message ?: "Unknown error"
    }
}
