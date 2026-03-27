package com.thoughtinput.capture.data

import android.os.Build
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class CapturePayload(
    val text: String,
    val timestamp: String,
    val sourcePlatform: String = "android",
    val clientVersion: String = "0.1.0",
    val captureMethod: CaptureMethod,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val deviceName: String = Build.MODEL
) {
    enum class CaptureMethod(val value: String) {
        TYPED("typed"),
        VOICE("voice")
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("text", text)
            put("timestamp", timestamp)
            put("source_platform", sourcePlatform)
            put("client_version", clientVersion)
            put("capture_method", captureMethod.value)
            put("idempotency_key", idempotencyKey)
            put("device_name", deviceName)
        }.toString()
    }

    companion object {
        fun create(
            text: String,
            method: CaptureMethod,
            deviceName: String = Build.MODEL
        ): CapturePayload {
            return CapturePayload(
                text = text,
                timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                captureMethod = method,
                deviceName = deviceName
            )
        }

        fun fromJson(json: String): CapturePayload {
            val obj = JSONObject(json)
            return CapturePayload(
                text = obj.getString("text"),
                timestamp = obj.getString("timestamp"),
                sourcePlatform = obj.getString("source_platform"),
                clientVersion = obj.getString("client_version"),
                captureMethod = CaptureMethod.entries.firstOrNull { it.value == obj.getString("capture_method") }
                    ?: CaptureMethod.TYPED,
                idempotencyKey = obj.getString("idempotency_key"),
                deviceName = obj.getString("device_name")
            )
        }
    }
}
