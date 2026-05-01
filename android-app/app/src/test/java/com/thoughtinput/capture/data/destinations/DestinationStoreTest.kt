package com.thoughtinput.capture.data.destinations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DestinationStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): Pair<DestinationStore, InMemoryKeystore> {
        val file = File(tempFolder.newFolder(), "destinations.json")
        val keystore = InMemoryKeystore()
        return DestinationStore(file, keystore) to keystore
    }

    private fun supabase(name: String, ref: KeychainRef = KeychainRef.create()): Destination =
        Destination(
            name = name,
            type = DestinationType.Supabase("https://x.supabase.co", "captures", ref)
        )

    @Test
    fun `first add becomes active automatically`() {
        val (store, _) = newStore()
        store.add(supabase("First"))
        val list = store.destinations.value
        assertEquals(1, list.size)
        assertTrue(list.first().isActive)
    }

    @Test
    fun `subsequent adds do not auto-activate`() {
        val (store, _) = newStore()
        store.add(supabase("First"))
        store.add(supabase("Second"))
        val list = store.destinations.value
        assertEquals(2, list.size)
        assertEquals("First", list.first { it.isActive }.name)
        assertFalse(list[1].isActive)
    }

    @Test
    fun `setActive enforces single active`() {
        val (store, _) = newStore()
        store.add(supabase("First"))
        store.add(supabase("Second"))
        val second = store.destinations.value[1]
        store.setActive(second)
        val list = store.destinations.value
        assertEquals(1, list.count { it.isActive })
        assertTrue(list.first { it.id == second.id }.isActive)
    }

    @Test
    fun `delete removes destination and its keychain refs`() {
        val (store, keystore) = newStore()
        val ref = KeychainRef.create()
        keystore.save(ref, "secret")
        val d = supabase("ToDelete", ref)
        store.add(d)
        store.delete(d)

        assertTrue(store.destinations.value.isEmpty())
        assertNull(keystore.loadString(ref))
    }

    @Test
    fun `delete promotes another to active when needed`() {
        val (store, _) = newStore()
        val a = supabase("A")
        val b = supabase("B")
        store.add(a)
        store.add(b)
        val active = store.activeDestination!!
        store.delete(active)

        val remaining = store.destinations.value
        assertEquals(1, remaining.size)
        assertTrue(remaining.first().isActive)
    }

    @Test
    fun `persists across reload`() {
        val file = File(tempFolder.newFolder(), "destinations.json")
        val keystore = InMemoryKeystore()
        val store1 = DestinationStore(file, keystore)
        store1.add(supabase("Persisted"))

        val store2 = DestinationStore(file, keystore)
        val list = store2.destinations.value
        assertEquals(1, list.size)
        assertEquals("Persisted", list.first().name)
        assertTrue(list.first().isActive)
    }

    @Test
    fun `activeDestination returns null when none active`() {
        val (store, _) = newStore()
        assertNull(store.activeDestination)
    }

    @Test
    fun `getById returns destination`() {
        val (store, _) = newStore()
        val d = supabase("Lookup")
        store.add(d)
        assertNotNull(store.getById(d.id))
    }
}
