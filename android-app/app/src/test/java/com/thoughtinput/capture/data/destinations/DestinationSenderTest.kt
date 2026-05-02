package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import com.thoughtinput.capture.data.CapturePayload
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DestinationSenderTest {

    private lateinit var server: MockWebServer
    private lateinit var keystore: InMemoryKeystore
    private lateinit var apiClient: ApiClient
    private lateinit var tokenManager: OAuthTokenManager
    private lateinit var sender: DestinationSender

    private fun samplePayload() =
        CapturePayload.create("hello", CapturePayload.CaptureMethod.TYPED)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        keystore = InMemoryKeystore()
        apiClient = ApiClient()
        tokenManager = OAuthTokenManager(keystore, apiClient)
        sender = DestinationSender(keystore, tokenManager, apiClient)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `supabase request hits rest v1 path with required headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val ref = KeychainRef.create()
        keystore.save(ref, "anon-key-123")
        val dest = Destination(
            name = "sb",
            type = DestinationType.Supabase(
                projectURL = server.url("/").toString().trimEnd('/'),
                tableName = "captures",
                apiKeyRef = ref
            )
        )

        sender.send(samplePayload(), dest)

        val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/captures", request.path)
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("return=minimal", request.getHeader("Prefer"))
        assertEquals("anon-key-123", request.getHeader("apikey"))
        assertEquals("Bearer anon-key-123", request.getHeader("Authorization"))

        val body = JSONObject(request.body.readUtf8())
        assertEquals("hello", body.getString("text"))
        assertEquals("android", body.getString("source_platform"))
    }

    @Test
    fun `rest no auth posts plain JSON`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val dest = Destination(
            name = "rb",
            type = DestinationType.RestNoAuth(server.url("/hook").toString())
        )

        sender.send(samplePayload(), dest)

        val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/hook", request.path)
        assertEquals("application/json", request.getHeader("Content-Type"))
        // No auth headers
        assertEquals(null, request.getHeader("Authorization"))
        assertEquals(null, request.getHeader("apikey"))
    }

    @Test
    fun `rest api key adds custom header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))

        val ref = KeychainRef.create()
        keystore.save(ref, "secret-key")
        val dest = Destination(
            name = "ak",
            type = DestinationType.RestApiKey(
                endpointURL = server.url("/in").toString(),
                headerName = "X-API-Key",
                apiKeyRef = ref
            )
        )

        sender.send(samplePayload(), dest)

        val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("secret-key", request.getHeader("X-API-Key"))
    }

    @Test
    fun `oauth client credentials exchange and bearer auth`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"access_token":"abc","expires_in":3600}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val idRef = KeychainRef.create(); keystore.save(idRef, "client-id")
        val secRef = KeychainRef.create(); keystore.save(secRef, "client-secret")
        val dest = Destination(
            name = "oa",
            type = DestinationType.RestOAuthClientCredentials(
                endpointURL = server.url("/data").toString(),
                tokenURL = server.url("/token").toString(),
                clientIDRef = idRef,
                clientSecretRef = secRef
            )
        )

        sender.send(samplePayload(), dest)

        val tokenReq = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/token", tokenReq.path)
        assertEquals("application/x-www-form-urlencoded", tokenReq.getHeader("Content-Type"))
        val body = tokenReq.body.readUtf8()
        assertTrue(body.contains("grant_type=client_credentials"))
        assertTrue(body.contains("client_id=client-id"))
        assertTrue(body.contains("client_secret=client-secret"))

        val captureReq = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/data", captureReq.path)
        assertEquals("Bearer abc", captureReq.getHeader("Authorization"))
    }

    @Test
    fun `oauth password 401 triggers single refresh and retry`() = runTest {
        // 1. token exchange
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"old","expires_in":3600}"""))
        // 2. capture with stale token returns 401
        server.enqueue(MockResponse().setResponseCode(401))
        // 3. fresh token exchange
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"fresh","expires_in":3600}"""))
        // 4. retry succeeds
        server.enqueue(MockResponse().setResponseCode(204))

        val userRef = KeychainRef.create(); keystore.save(userRef, "alice")
        val passRef = KeychainRef.create(); keystore.save(passRef, "pw")
        val dest = Destination(
            name = "pw",
            type = DestinationType.RestOAuthPassword(
                endpointURL = server.url("/data").toString(),
                tokenURL = server.url("/token").toString(),
                usernameRef = userRef,
                passwordRef = passRef
            )
        )

        sender.send(samplePayload(), dest)

        // Assert sequence
        val r1 = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!; assertEquals("/token", r1.path)
        val r2 = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!; assertEquals("/data", r2.path)
        assertEquals("Bearer old", r2.getHeader("Authorization"))
        val r3 = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!; assertEquals("/token", r3.path)
        val r4 = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!; assertEquals("/data", r4.path)
        assertEquals("Bearer fresh", r4.getHeader("Authorization"))

        // Verify the retry token request was a fresh password grant (not refresh, since no refresh_token in first response)
        assertTrue(r3.body.readUtf8().contains("grant_type=password"))
    }

    @Test
    fun `non-401 server error is propagated`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val dest = Destination(
            name = "rb",
            type = DestinationType.RestNoAuth(server.url("/").toString())
        )

        try {
            sender.send(samplePayload(), dest)
            fail("Expected CaptureHttpException")
        } catch (e: CaptureHttpException) {
            assertEquals(500, e.statusCode)
        }
    }

    @Test
    fun `testConnection sends connection test payload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val dest = Destination(
            name = "rb",
            type = DestinationType.RestNoAuth(server.url("/").toString())
        )

        sender.testConnection(dest)

        val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Connection test", body.getString("text"))
        assertEquals("typed", body.getString("capture_method"))
        assertNotNull(body.getString("idempotency_key"))
    }
}
