package com.thoughtinput.capture.data

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
data class CapturePayload(
    val text: String,
    val timestamp: String,
    @SerialName("source_platform") val sourcePlatform: String = "android",
    @SerialName("client_version") val clientVersion: String = "0.1.0",
    @SerialName("capture_method") val captureMethod: CaptureMethod,
    @SerialName("idempotency_key") val idempotencyKey: String = UUID.randomUUID().toString(),
    @SerialName("device_name") val deviceName: String = (Build.MODEL ?: "unknown")
) {
    @Serializable
    enum class CaptureMethod(val value: String) {
        @SerialName("typed") TYPED("typed"),
        @SerialName("voice") VOICE("voice")
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun create(
            text: String,
            method: CaptureMethod,
            deviceName: String = (Build.MODEL ?: "unknown")
        ): CapturePayload {
            return CapturePayload(
                text = text,
                timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                captureMethod = method,
                deviceName = deviceName
            )
        }

        fun fromJson(jsonString: String): CapturePayload =
            json.decodeFromString(serializer(), jsonString)
    }
}
