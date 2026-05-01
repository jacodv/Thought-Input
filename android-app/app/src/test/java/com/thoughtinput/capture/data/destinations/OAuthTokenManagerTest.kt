package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OAuthTokenManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var keystore: InMemoryKeystore
    private lateinit var manager: OAuthTokenManager

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        keystore = InMemoryKeystore()
        manager = OAuthTokenManager(keystore, ApiClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun passwordDestination(): Destination {
        val u = KeychainRef.create(); keystore.save(u, "alice")
        val p = KeychainRef.create(); keystore.save(p, "pw")
        return Destination(
            name = "pw",
            type = DestinationType.RestOAuthPassword(
                endpointURL = "https://unused/",
                tokenURL = server.url("/token").toString(),
                usernameRef = u,
                passwordRef = p
            )
        )
    }

    @Test
    fun `exchange succeeds and caches token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"abc","expires_in":3600}"""))

        val dest = passwordDestination()
        val token = manager.validToken(dest)
        assertEquals("abc", token)

        // Second call should not hit the network (no enqueue) — would 404 if it did
        val cached = manager.validToken(dest)
        assertEquals("abc", cached)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired token triggers refresh via password grant when no refresh_token`() = runTest {
        // First exchange yields token with very short expiry — already expired by check time
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"first","expires_in":-100}"""))
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"second","expires_in":3600}"""))

        val dest = passwordDestination()
        manager.validToken(dest)
        val refreshed = manager.validToken(dest)
        assertEquals("second", refreshed)
    }

    @Test
    fun `expired token uses refresh_token when present`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"a","expires_in":-100,"refresh_token":"rt-1"}"""))
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"b","expires_in":3600}"""))

        val dest = passwordDestination()
        manager.validToken(dest)
        manager.validToken(dest)

        // First request: password grant
        server.takeRequest()
        // Second request: refresh grant
        val refreshReq = server.takeRequest()
        val body = refreshReq.body.readUtf8()
        assertTrue("expected refresh_token grant, got: $body", body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=rt-1"))
    }

    @Test
    fun `missing credentials throws`() = runTest {
        val dest = Destination(
            name = "broken",
            type = DestinationType.RestOAuthPassword(
                endpointURL = "https://x/",
                tokenURL = server.url("/token").toString(),
                usernameRef = KeychainRef.create(),
                passwordRef = KeychainRef.create()
            )
        )
        try {
            manager.validToken(dest)
            org.junit.Assert.fail("Expected OAuthError.MissingCredentials")
        } catch (_: OAuthError.MissingCredentials) {
            // expected
        }
    }

    @Test
    fun `clearToken forces re-fetch`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"first","expires_in":3600}"""))
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"access_token":"second","expires_in":3600}"""))

        val dest = passwordDestination()
        manager.validToken(dest)
        manager.clearToken(dest.id)
        val again = manager.validToken(dest)
        assertEquals("second", again)
    }
}
