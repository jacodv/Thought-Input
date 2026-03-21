package com.braininput.capture.data

import android.content.Context
import com.braininput.capture.util.CaptureLog
import java.io.File

class PendingCaptureStore(context: Context) {

    private val pendingDir = File(context.filesDir, "pending_captures").apply { mkdirs() }

    fun save(payload: CapturePayload) {
        try {
            val file = File(pendingDir, "${payload.idempotencyKey}.json")
            file.writeText(payload.toJson())
            CaptureLog.store("Saved pending capture: ${payload.idempotencyKey}")
        } catch (e: Exception) {
            CaptureLog.store("Failed to save pending: ${e.message}")
        }
    }

    fun loadAll(): List<CapturePayload> {
        return try {
            pendingDir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        CapturePayload.fromJson(file.readText())
                    } catch (e: Exception) {
                        CaptureLog.store("Failed to parse ${file.name}: ${e.message}")
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            CaptureLog.store("Failed to load pending captures: ${e.message}")
            emptyList()
        }
    }

    fun remove(idempotencyKey: String) {
        File(pendingDir, "$idempotencyKey.json").delete()
    }

    val pendingCount: Int
        get() = pendingDir.listFiles { file -> file.extension == "json" }?.size ?: 0
}
