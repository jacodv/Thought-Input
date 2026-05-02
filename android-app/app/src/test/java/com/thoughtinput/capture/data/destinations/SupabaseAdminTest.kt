package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class SupabaseAdminTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var admin: SupabaseAdmin

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        admin = SupabaseAdmin(
            apiClient = ApiClient(),
            managementBase = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `projectRef parses subdomain from supabase url`() {
        assertEquals("abcdefgh", admin.projectRef("https://abcdefgh.supabase.co"))
        assertEquals("abcdefgh", admin.projectRef("https://abcdefgh.supabase.co/"))
        assertEquals("abcdefgh", admin.projectRef("https://abcdefgh.supabase.co/rest/v1/"))
        assertEquals("abcdefgh", admin.projectRef("https://ABCDEFGH.SUPABASE.CO"))
        assertEquals("abcdefgh", admin.projectRef("abcdefgh.supabase.co"))
    }

    @Test
    fun `projectRef returns null for non-supabase urls`() {
        assertNull(admin.projectRef(""))
        assertNull(admin.projectRef("https://example.com"))
        assertNull(admin.projectRef("not-a-url"))
        assertNull(admin.projectRef("https://.supabase.co"))
    }

    @Test
    fun `validateTableName accepts valid identifiers`() {
        assertTrue(admin.validateTableName("captures"))
        assertTrue(admin.validateTableName("_t1"))
        assertTrue(admin.validateTableName("MyTable_2"))
    }

    @Test
    fun `validateTableName rejects invalid identifiers`() {
        assertFalse(admin.validateTableName(""))
        assertFalse(admin.validateTableName("1table"))
        assertFalse(admin.validateTableName("foo bar"))
        assertFalse(admin.validateTableName("; DROP TABLE x;"))
        assertFalse(admin.validateTableName("a".repeat(64)))
    }

    @Test
    fun `sqlFor without drop creates the canonical schema`() {
        val sql = admin.sqlFor("captures", drop = false)
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS \"captures\""))
        assertTrue(sql.contains("idempotency_key uuid        UNIQUE NOT NULL"))
        assertTrue(sql.contains("ALTER TABLE \"captures\" ENABLE ROW LEVEL SECURITY"))
        assertTrue(sql.contains("CREATE POLICY \"Allow anonymous inserts\""))
        assertFalse(sql.contains("DROP TABLE"))
    }

    @Test
    fun `sqlFor with drop prepends drop`() {
        val sql = admin.sqlFor("thoughts", drop = true)
        assertTrue(sql.contains("DROP TABLE IF EXISTS \"thoughts\" CASCADE"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS \"thoughts\""))
        assertTrue(sql.indexOf("DROP TABLE") < sql.indexOf("CREATE TABLE"))
    }

    @Test
    fun `sqlEditorURL builds correct path`() {
        assertEquals(
            "https://supabase.com/dashboard/project/abcdefgh/sql/new",
            admin.sqlEditorURL("abcdefgh")
        )
    }

    @Test
    fun `tableExists parses true response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"exists": true}]"""))
        val result = admin.tableExists("ref", "pat", "captures")
        assertTrue(result)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertRequest(request, "/v1/projects/ref/database/query", "Bearer pat")
        assertTrue(request.body.readUtf8().contains("pg_tables"))
    }

    @Test
    fun `tableExists parses false response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"exists": false}]"""))
        assertFalse(admin.tableExists("ref", "pat", "captures"))
    }

    @Test
    fun `rowCount parses count`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"count": 42}]"""))
        assertEquals(42L, admin.rowCount("ref", "pat", "captures"))
    }

    @Test
    fun `initialize without drop posts create sql`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        admin.initialize("ref", "pat", "captures", drop = false)
        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = req.body.readUtf8()
        assertTrue("expected CREATE in body but got: $body", body.contains("CREATE TABLE IF NOT EXISTS"))
        assertFalse("did not expect DROP in body but got: $body", body.contains("DROP TABLE"))
    }

    @Test
    fun `initialize with drop posts drop+create sql`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        admin.initialize("ref", "pat", "captures", drop = true)
        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = req.body.readUtf8()
        assertTrue(body.contains("DROP TABLE IF EXISTS"))
        assertTrue(body.contains("CREATE TABLE IF NOT EXISTS"))
    }

    @Test
    fun `non-2xx response throws SupabaseAdminException with body`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        val ex = assertThrows(SupabaseAdminException::class.java) {
            runBlocking { admin.tableExists("ref", "bad-pat", "captures") }
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("401"))
        assertTrue(ex.message!!.contains("unauthorized"))
    }

    @Test
    fun `initialize rejects invalid table name without hitting network`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { admin.initialize("ref", "pat", "1bad", drop = false) }
        }
        assertNotNull(ex.message)
    }

    private fun assertRequest(request: RecordedRequest, expectedPath: String, expectedAuth: String) {
        assertEquals("POST", request.method)
        assertEquals(expectedPath, request.path)
        assertEquals(expectedAuth, request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))
    }
}
