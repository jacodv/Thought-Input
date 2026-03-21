package com.braininput.capture

import com.braininput.capture.data.CapturePayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePayloadTest {

    @Test
    fun `create typed payload has correct fields`() {
        val payload = CapturePayload.create("Hello world", CapturePayload.CaptureMethod.TYPED)

        assertEquals("Hello world", payload.text)
        assertEquals("android", payload.sourcePlatform)
        assertEquals("typed", payload.captureMethod.value)
        assertTrue(payload.idempotencyKey.isNotEmpty())
        assertTrue(payload.timestamp.isNotEmpty())
    }

    @Test
    fun `create voice payload has correct method`() {
        val payload = CapturePayload.create("Voice note", CapturePayload.CaptureMethod.VOICE)

        assertEquals("voice", payload.captureMethod.value)
    }

    @Test
    fun `toJson produces valid JSON with snake_case keys`() {
        val payload = CapturePayload.create("JSON test", CapturePayload.CaptureMethod.TYPED)
        val json = JSONObject(payload.toJson())

        assertEquals("JSON test", json.getString("text"))
        assertEquals("android", json.getString("source_platform"))
        assertEquals("0.1.0", json.getString("client_version"))
        assertEquals("typed", json.getString("capture_method"))
        assertNotNull(json.getString("idempotency_key"))
        assertNotNull(json.getString("timestamp"))
    }

    @Test
    fun `fromJson round-trips correctly`() {
        val original = CapturePayload.create("Round trip", CapturePayload.CaptureMethod.VOICE)
        val json = original.toJson()
        val restored = CapturePayload.fromJson(json)

        assertEquals(original.text, restored.text)
        assertEquals(original.sourcePlatform, restored.sourcePlatform)
        assertEquals(original.captureMethod, restored.captureMethod)
        assertEquals(original.idempotencyKey, restored.idempotencyKey)
        assertEquals(original.timestamp, restored.timestamp)
    }

    @Test
    fun `idempotency keys are unique`() {
        val payload1 = CapturePayload.create("First", CapturePayload.CaptureMethod.TYPED)
        val payload2 = CapturePayload.create("Second", CapturePayload.CaptureMethod.TYPED)

        assertNotEquals(payload1.idempotencyKey, payload2.idempotencyKey)
    }
}
