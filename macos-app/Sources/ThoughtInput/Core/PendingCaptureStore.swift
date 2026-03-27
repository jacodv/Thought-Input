import Foundation

final class PendingCaptureStore: Sendable {
    private let directory: URL

    init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first ?? FileManager.default.temporaryDirectory
        directory = appSupport.appendingPathComponent("ThoughtInput/pending", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        } catch {
            CaptureLog.store.error("Failed to create pending captures directory: \(error.localizedDescription)")
        }
    }

    @discardableResult
    func save(_ capture: PendingCapture) -> Bool {
        let file = directory.appendingPathComponent("\(capture.payload.idempotencyKey).json")
        do {
            let data = try JSONEncoder().encode(capture)
            try data.write(to: file, options: .atomic)
            CaptureLog.store.info("Saved pending capture: \(capture.payload.idempotencyKey)")
            return true
        } catch {
            CaptureLog.store.error("Failed to save pending capture: \(error.localizedDescription)")
            return false
        }
    }

    func loadAll() -> [PendingCapture] {
        do {
            let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
                .filter { $0.pathExtension == "json" }
            return files.compactMap { file in
                guard let data = try? Data(contentsOf: file) else { return nil }
                // Try new format first, fall back to legacy bare CapturePayload
                if let pending = try? JSONDecoder().decode(PendingCapture.self, from: data) {
                    return pending
                }
                // Legacy format: wrap bare payload with a nil destination (will use active destination on retry)
                if let payload = try? JSONDecoder().decode(CapturePayload.self, from: data) {
                    return PendingCapture(payload: payload, destinationID: UUID(), destinationSnapshot: Destination(
                        name: "Legacy",
                        isActive: true,
                        type: .restNoAuth(RESTNoAuthConfig(endpointURL: ""))
                    ))
                }
                return nil
            }
        } catch {
            CaptureLog.store.error("Failed to load pending captures: \(error.localizedDescription)")
            return []
        }
    }

    func remove(idempotencyKey: String) {
        let file = directory.appendingPathComponent("\(idempotencyKey).json")
        try? FileManager.default.removeItem(at: file)
    }

    var pendingCount: Int {
        (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }.count) ?? 0
    }
}
