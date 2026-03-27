import Foundation

struct CapturePayload: Codable, Sendable {
    let text: String
    let timestamp: String
    let sourcePlatform: String
    let clientVersion: String
    let captureMethod: CaptureMethod
    let idempotencyKey: String
    let deviceName: String

    enum CaptureMethod: String, Codable, Sendable {
        case typed
        case voice
    }

    enum CodingKeys: String, CodingKey {
        case text
        case timestamp
        case sourcePlatform = "source_platform"
        case clientVersion = "client_version"
        case captureMethod = "capture_method"
        case idempotencyKey = "idempotency_key"
        case deviceName = "device_name"
    }

    static func create(text: String, method: CaptureMethod) -> CapturePayload {
        CapturePayload(
            text: text,
            timestamp: ISO8601DateFormatter().string(from: Date()),
            sourcePlatform: "macos",
            clientVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0",
            captureMethod: method,
            idempotencyKey: UUID().uuidString,
            deviceName: Host.current().localizedName ?? ProcessInfo.processInfo.hostName
        )
    }
}
