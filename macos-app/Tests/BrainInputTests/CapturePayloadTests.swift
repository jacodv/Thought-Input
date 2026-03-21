import XCTest
@testable import BrainInput

final class CapturePayloadTests: XCTestCase {

    func testCreateTypedPayload() throws {
        let payload = CapturePayload.create(text: "Hello world", method: .typed)

        XCTAssertEqual(payload.text, "Hello world")
        XCTAssertEqual(payload.sourcePlatform, "macos")
        XCTAssertEqual(payload.captureMethod, .typed)
        XCTAssertFalse(payload.idempotencyKey.isEmpty)
        XCTAssertFalse(payload.timestamp.isEmpty)
    }

    func testCreateVoicePayload() throws {
        let payload = CapturePayload.create(text: "Voice note", method: .voice)

        XCTAssertEqual(payload.captureMethod, .voice)
        XCTAssertEqual(payload.text, "Voice note")
    }

    func testPayloadEncodeDecode() throws {
        let payload = CapturePayload.create(text: "Test encode", method: .typed)
        let data = try JSONEncoder().encode(payload)
        let decoded = try JSONDecoder().decode(CapturePayload.self, from: data)

        XCTAssertEqual(decoded.text, payload.text)
        XCTAssertEqual(decoded.sourcePlatform, payload.sourcePlatform)
        XCTAssertEqual(decoded.captureMethod, payload.captureMethod)
        XCTAssertEqual(decoded.idempotencyKey, payload.idempotencyKey)
        XCTAssertEqual(decoded.timestamp, payload.timestamp)
    }

    func testPayloadJsonKeys() throws {
        let payload = CapturePayload.create(text: "Key test", method: .typed)
        let data = try JSONEncoder().encode(payload)
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        // Verify snake_case keys from CodingKeys
        XCTAssertNotNil(json["source_platform"])
        XCTAssertNotNil(json["client_version"])
        XCTAssertNotNil(json["capture_method"])
        XCTAssertNotNil(json["idempotency_key"])
        XCTAssertNotNil(json["text"])
        XCTAssertNotNil(json["timestamp"])
    }

    func testIdempotencyKeyUniqueness() {
        let payload1 = CapturePayload.create(text: "First", method: .typed)
        let payload2 = CapturePayload.create(text: "Second", method: .typed)

        XCTAssertNotEqual(payload1.idempotencyKey, payload2.idempotencyKey)
    }
}
