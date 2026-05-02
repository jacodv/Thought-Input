package com.thoughtinput.capture.data

import com.thoughtinput.capture.data.destinations.Destination
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.KeychainRef
import com.thoughtinput.capture.data.destinations.SecretsKeystore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class SettingsBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String,
    val sourcePlatform: String = "android",
    val destinations: List<BackupDestination>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupDestination(
    val destination: Destination,
    val secrets: Map<String, String>
)

class SettingsBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SettingsBackupService(
    private val store: DestinationStore,
    private val keystore: SecretsKeystore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun export(): String {
        val backups = store.destinations.value.map { destination ->
            val secrets = destination.type.keychainRefs
                .mapNotNull { ref -> keystore.loadString(ref)?.let { ref.account to it } }
                .toMap()
            BackupDestination(destination = destination, secrets = secrets)
        }
        val backup = SettingsBackup(
            exportedAt = Instant.now().toString(),
            destinations = backups
        )
        return json.encodeToString(SettingsBackup.serializer(), backup)
    }

    fun import(text: String) {
        val backup = try {
            json.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw SettingsBackupException("Couldn't read the backup file: ${e.message}", e)
        }

        if (backup.schemaVersion != SettingsBackup.CURRENT_SCHEMA_VERSION) {
            throw SettingsBackupException(
                "Unsupported backup schema version (${backup.schemaVersion}). This file was made by a newer version of the app."
            )
        }

        for (existing in store.destinations.value.toList()) {
            store.delete(existing)
        }

        var activeId: java.util.UUID? = null
        for (entry in backup.destinations) {
            for ((account, value) in entry.secrets) {
                keystore.save(KeychainRef(account), value)
            }
            val wasActive = entry.destination.isActive
            store.add(entry.destination.copy(isActive = false))
            if (wasActive) activeId = entry.destination.id
        }

        if (activeId != null) {
            store.destinations.value.firstOrNull { it.id == activeId }?.let { store.setActive(it) }
        }
    }
}
