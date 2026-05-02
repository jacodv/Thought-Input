package com.thoughtinput.capture.data

import com.thoughtinput.capture.data.destinations.Destination
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.DestinationType
import com.thoughtinput.capture.data.destinations.InMemoryKeystore
import com.thoughtinput.capture.data.destinations.KeychainRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

class SettingsBackupServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val globalTimeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    fun `round trip preserves all destination types and resolved secrets`() {
        val (originStore, originKeystore) = newStore()

        val supabaseRef = KeychainRef.create()
        originKeystore.save(supabaseRef, "sb-secret")
        val supabase = Destination(
            name = "Supabase",
            type = DestinationType.Supabase("https://x.supabase.co", "captures", supabaseRef)
        )

        val noAuth = Destination(
            name = "Local",
            type = DestinationType.RestNoAuth("http://localhost:3000/c")
        )

        val apiKeyRef = KeychainRef.create()
        originKeystore.save(apiKeyRef, "wh-12345")
        val apiKey = Destination(
            name = "Webhook",
            type = DestinationType.RestApiKey("https://api.x/c", "X-API-Key", apiKeyRef)
        )

        val userRef = KeychainRef.create()
        val pwdRef = KeychainRef.create()
        originKeystore.save(userRef, "alice")
        originKeystore.save(pwdRef, "p@ss")
        val oauthPwd = Destination(
            name = "OAuth Pwd",
            type = DestinationType.RestOAuthPassword("https://api.x/c", "https://api.x/t", userRef, pwdRef)
        )

        val cidRef = KeychainRef.create()
        val csRef = KeychainRef.create()
        originKeystore.save(cidRef, "cid")
        originKeystore.save(csRef, "csecret")
        val oauthCC = Destination(
            name = "OAuth CC",
            type = DestinationType.RestOAuthClientCredentials(
                "https://api.x/c", "https://api.x/t", cidRef, csRef
            )
        )

        originStore.add(supabase)
        originStore.add(noAuth)
        originStore.add(apiKey)
        originStore.add(oauthPwd)
        originStore.add(oauthCC)
        originStore.setActive(originStore.destinations.value.first { it.id == apiKey.id })

        val originalActiveId = originStore.activeDestination!!.id
        val originalIds = originStore.destinations.value.map { it.id }
        val originService = SettingsBackupService(originStore, originKeystore)
        val exported = originService.export()

        val (restoreStore, restoreKeystore) = newStore()
        SettingsBackupService(restoreStore, restoreKeystore).import(exported)

        val restored = restoreStore.destinations.value
        assertEquals(5, restored.size)
        assertEquals(originalIds.toSet(), restored.map { it.id }.toSet())
        assertEquals(originalActiveId, restoreStore.activeDestination!!.id)

        // Secrets re-populated under same KeychainRef accounts.
        assertEquals("sb-secret", restoreKeystore.loadString(supabaseRef))
        assertEquals("wh-12345", restoreKeystore.loadString(apiKeyRef))
        assertEquals("alice", restoreKeystore.loadString(userRef))
        assertEquals("p@ss", restoreKeystore.loadString(pwdRef))
        assertEquals("cid", restoreKeystore.loadString(cidRef))
        assertEquals("csecret", restoreKeystore.loadString(csRef))
    }

    @Test
    fun `import wipes existing destinations and their secrets`() {
        val (store, keystore) = newStore()
        val staleRef = KeychainRef.create()
        keystore.save(staleRef, "stale-value")
        store.add(
            Destination(
                name = "Stale",
                type = DestinationType.RestApiKey("https://stale.example", "X", staleRef)
            )
        )

        val emptyBackup = """{
            "schemaVersion": 1,
            "exportedAt": "2026-05-02T00:00:00Z",
            "sourcePlatform": "android",
            "destinations": []
        }""".trimIndent()

        SettingsBackupService(store, keystore).import(emptyBackup)

        assertTrue(store.destinations.value.isEmpty())
        assertNull(keystore.loadString(staleRef))
        assertNull(store.activeDestination)
    }

    @Test
    fun `import rejects malformed json`() {
        val (store, keystore) = newStore()
        val service = SettingsBackupService(store, keystore)
        assertThrows(SettingsBackupException::class.java) {
            service.import("{ this is not json")
        }
    }

    @Test
    fun `import rejects unsupported schema version`() {
        val (store, keystore) = newStore()
        val service = SettingsBackupService(store, keystore)
        val future = """{
            "schemaVersion": 999,
            "exportedAt": "2026-05-02T00:00:00Z",
            "sourcePlatform": "android",
            "destinations": []
        }""".trimIndent()
        val ex = assertThrows(SettingsBackupException::class.java) { service.import(future) }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("999"))
    }

    @Test
    fun `export of empty store produces parseable backup with no destinations`() {
        val (store, keystore) = newStore()
        val service = SettingsBackupService(store, keystore)
        val text = service.export()

        val (otherStore, otherKeystore) = newStore()
        SettingsBackupService(otherStore, otherKeystore).import(text)

        assertTrue(otherStore.destinations.value.isEmpty())
    }

    private fun newStore(): Pair<DestinationStore, InMemoryKeystore> {
        val file = File(tempFolder.newFolder(), "destinations.json")
        val keystore = InMemoryKeystore()
        return DestinationStore(file, keystore) to keystore
    }
}
