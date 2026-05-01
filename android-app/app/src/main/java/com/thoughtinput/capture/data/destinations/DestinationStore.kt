package com.thoughtinput.capture.data.destinations

import android.content.Context
import com.thoughtinput.capture.util.CaptureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DestinationStore internal constructor(
    private val storeFile: File,
    private val keystore: SecretsKeystore
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _destinations = MutableStateFlow<List<Destination>>(emptyList())
    val destinations: StateFlow<List<Destination>> = _destinations.asStateFlow()

    init {
        load()
    }

    val activeDestination: Destination?
        get() = _destinations.value.firstOrNull { it.isActive }

    fun getById(id: UUID): Destination? = _destinations.value.firstOrNull { it.id == id }

    fun add(destination: Destination) {
        val current = _destinations.value
        val toAdd = if (current.isEmpty()) destination.copy(isActive = true) else destination
        _destinations.value = current + toAdd
        save()
    }

    fun update(destination: Destination) {
        _destinations.value = _destinations.value.map {
            if (it.id == destination.id) destination else it
        }
        save()
    }

    fun delete(destination: Destination) {
        for (ref in destination.type.keychainRefs) {
            keystore.delete(ref)
        }
        var remaining = _destinations.value.filterNot { it.id == destination.id }
        if (remaining.none { it.isActive } && remaining.isNotEmpty()) {
            remaining = listOf(remaining.first().copy(isActive = true)) + remaining.drop(1)
        }
        _destinations.value = remaining
        save()
    }

    fun setActive(destination: Destination) {
        _destinations.value = _destinations.value.map {
            it.copy(isActive = it.id == destination.id)
        }
        save()
    }

    private fun load() {
        if (!storeFile.exists()) {
            CaptureLog.store("No destinations file at ${storeFile.path}")
            return
        }
        try {
            val text = storeFile.readText()
            if (text.isBlank()) return
            _destinations.value = json.decodeFromString(ListSerializer(Destination.serializer()), text)
            CaptureLog.store("Loaded ${_destinations.value.size} destinations")
        } catch (e: Exception) {
            CaptureLog.error("Store", "Failed to load destinations: ${e.message}", e)
            _destinations.value = emptyList()
        }
    }

    private fun save() {
        try {
            storeFile.parentFile?.mkdirs()
            val text = json.encodeToString(ListSerializer(Destination.serializer()), _destinations.value)
            storeFile.writeText(text)
            CaptureLog.store("Saved ${_destinations.value.size} destinations")
        } catch (e: Exception) {
            CaptureLog.error("Store", "Failed to save destinations: ${e.message}", e)
        }
    }

    companion object {
        @Volatile private var INSTANCE: DestinationStore? = null

        fun get(context: Context): DestinationStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): DestinationStore {
            val ctx = context.applicationContext
            val file = File(ctx.filesDir, "destinations.json")
            val keystore = EncryptedKeystore(ctx)
            return DestinationStore(file, keystore)
        }
    }
}
