package com.thoughtinput.capture.data

import android.content.Context
import com.thoughtinput.capture.data.destinations.PendingCapture
import com.thoughtinput.capture.util.CaptureLog
import kotlinx.serialization.json.Json
import java.io.File

class PendingCaptureStore(context: Context) {

    private val pendingDir = File(context.filesDir, "pending_captures").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(capture: PendingCapture): Boolean {
        return try {
            val file = File(pendingDir, "${capture.payload.idempotencyKey}.json")
            file.writeText(json.encodeToString(PendingCapture.serializer(), capture))
            CaptureLog.store("Saved pending capture: ${capture.payload.idempotencyKey}")
            true
        } catch (e: Exception) {
            CaptureLog.error("Store", "Failed to save pending: ${e.message}", e)
            false
        }
    }

    fun loadAll(): List<PendingCapture> {
        return try {
            pendingDir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        json.decodeFromString(PendingCapture.serializer(), file.readText())
                    } catch (e: Exception) {
                        CaptureLog.store("Discarding unreadable pending file ${file.name}: ${e.message}")
                        file.delete()
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            CaptureLog.store("Failed to load pending captures: ${e.message}")
            emptyList()
        }
    }

    fun remove(idempotencyKey: String) {
        val file = File(pendingDir, "$idempotencyKey.json")
        if (file.exists() && !file.delete()) {
            CaptureLog.error("Store", "Failed to delete pending capture: ${file.name}")
        }
    }

    val pendingCount: Int
        get() = pendingDir.listFiles { file -> file.extension == "json" }?.size ?: 0
}
