import Foundation

final class PendingCaptureStore: Sendable {
    private let directory: URL

    init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        directory = appSupport.appendingPathComponent("ThoughtInput/pending", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func save(_ payload: CapturePayload) {
        let file = directory.appendingPathComponent("\(payload.idempotencyKey).json")
        do {
            let data = try JSONEncoder().encode(payload)
            try data.write(to: file, options: .atomic)
            CaptureLog.store.info("Saved pending capture: \(payload.idempotencyKey)")
        } catch {
            CaptureLog.store.error("Failed to save pending capture: \(error.localizedDescription)")
        }
    }

    func loadAll() -> [CapturePayload] {
        do {
            let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
                .filter { $0.pathExtension == "json" }
            return files.compactMap { file in
                guard let data = try? Data(contentsOf: file) else { return nil }
                return try? JSONDecoder().decode(CapturePayload.self, from: data)
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
