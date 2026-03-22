import XCTest
@testable import ThoughtInput

final class PendingCaptureStoreTests: XCTestCase {

    private var store: PendingCaptureStore!

    override func setUp() {
        super.setUp()
        store = PendingCaptureStore()
    }

    override func tearDown() {
        // Clean up any test data
        let pending = store.loadAll()
        for payload in pending {
            store.remove(idempotencyKey: payload.idempotencyKey)
        }
        super.tearDown()
    }

    func testSaveAndLoad() {
        let payload = CapturePayload.create(text: "Test pending", method: .typed)
        store.save(payload)

        let loaded = store.loadAll()
        XCTAssertTrue(loaded.contains(where: { $0.idempotencyKey == payload.idempotencyKey }))
    }

    func testRemove() {
        let payload = CapturePayload.create(text: "Remove me", method: .voice)
        store.save(payload)
        store.remove(idempotencyKey: payload.idempotencyKey)

        let loaded = store.loadAll()
        XCTAssertFalse(loaded.contains(where: { $0.idempotencyKey == payload.idempotencyKey }))
    }

    func testPendingCount() {
        let initialCount = store.pendingCount
        let payload = CapturePayload.create(text: "Count test", method: .typed)
        store.save(payload)

        XCTAssertEqual(store.pendingCount, initialCount + 1)

        store.remove(idempotencyKey: payload.idempotencyKey)
        XCTAssertEqual(store.pendingCount, initialCount)
    }
}
