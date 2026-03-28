import XCTest
@testable import ThoughtInput

final class PendingCaptureStoreTests: XCTestCase {

    private var store: PendingCaptureStore!

    private func makePendingCapture(text: String, method: CapturePayload.CaptureMethod) -> PendingCapture {
        let payload = CapturePayload.create(text: text, method: method)
        let destination = Destination(
            name: "Test",
            isActive: true,
            type: .restNoAuth(RESTNoAuthConfig(endpointURL: "https://example.com"))
        )
        return PendingCapture(payload: payload, destinationID: destination.id, destinationSnapshot: destination)
    }

    override func setUp() {
        super.setUp()
        store = PendingCaptureStore()
    }

    override func tearDown() {
        let pending = store.loadAll()
        for capture in pending {
            store.remove(idempotencyKey: capture.payload.idempotencyKey)
        }
        super.tearDown()
    }

    func testSaveAndLoad() {
        let capture = makePendingCapture(text: "Test pending", method: .typed)
        store.save(capture)

        let loaded = store.loadAll()
        XCTAssertTrue(loaded.contains(where: { $0.payload.idempotencyKey == capture.payload.idempotencyKey }))
    }

    func testRemove() {
        let capture = makePendingCapture(text: "Remove me", method: .voice)
        store.save(capture)
        store.remove(idempotencyKey: capture.payload.idempotencyKey)

        let loaded = store.loadAll()
        XCTAssertFalse(loaded.contains(where: { $0.payload.idempotencyKey == capture.payload.idempotencyKey }))
    }

    func testPendingCount() {
        let initialCount = store.pendingCount
        let capture = makePendingCapture(text: "Count test", method: .typed)
        store.save(capture)

        XCTAssertEqual(store.pendingCount, initialCount + 1)

        store.remove(idempotencyKey: capture.payload.idempotencyKey)
        XCTAssertEqual(store.pendingCount, initialCount)
    }
}
