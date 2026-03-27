package com.thoughtinput.capture

import com.thoughtinput.capture.data.CapturePayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePayloadTest {

    @Test
    fun `create typed payload has correct fields`() {
        val payload = CapturePayload.create("Hello world", CapturePayload.CaptureMethod.TYPED, deviceName = "Test Device")

        assertEquals("Hello world", payload.text)
        assertEquals("android", payload.sourcePlatform)
        assertEquals("typed", payload.captureMethod.value)
        assertTrue(payload.idempotencyKey.isNotEmpty())
        assertTrue(payload.timestamp.isNotEmpty())
        assertEquals("Test Device", payload.deviceName)
    }

    @Test
    fun `create voice payload has correct method`() {
        val payload = CapturePayload.create("Voice note", CapturePayload.CaptureMethod.VOICE, deviceName = "Test Device")

        assertEquals("voice", payload.captureMethod.value)
    }

    @Test
    fun `toJson produces valid JSON with snake_case keys`() {
        val payload = CapturePayload.create("JSON test", CapturePayload.CaptureMethod.TYPED, deviceName = "Test Device")
        val json = JSONObject(payload.toJson())

        assertEquals("JSON test", json.getString("text"))
        assertEquals("android", json.getString("source_platform"))
        assertEquals("0.1.0", json.getString("client_version"))
        assertEquals("typed", json.getString("capture_method"))
        assertNotNull(json.getString("idempotency_key"))
        assertNotNull(json.getString("timestamp"))
        assertEquals("Test Device", json.getString("device_name"))
    }

    @Test
    fun `fromJson round-trips correctly`() {
        val original = CapturePayload.create("Round trip", CapturePayload.CaptureMethod.VOICE, deviceName = "Test Device")
        val json = original.toJson()
        val restored = CapturePayload.fromJson(json)

        assertEquals(original.text, restored.text)
        assertEquals(original.sourcePlatform, restored.sourcePlatform)
        assertEquals(original.captureMethod, restored.captureMethod)
        assertEquals(original.idempotencyKey, restored.idempotencyKey)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.deviceName, restored.deviceName)
    }

    @Test
    fun `idempotency keys are unique`() {
        val payload1 = CapturePayload.create("First", CapturePayload.CaptureMethod.TYPED, deviceName = "Test Device")
        val payload2 = CapturePayload.create("Second", CapturePayload.CaptureMethod.TYPED, deviceName = "Test Device")

        assertNotEquals(payload1.idempotencyKey, payload2.idempotencyKey)
    }
}
