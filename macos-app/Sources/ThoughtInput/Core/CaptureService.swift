import Foundation

@MainActor
final class CaptureService: ObservableObject {
    static let shared = CaptureService()

    @Published var isSending = false
    @Published var lastError: String?

    private let pendingStore = PendingCaptureStore()
    private let tokenManager = OAuthTokenManager.shared

    func submit(text: String, method: CapturePayload.CaptureMethod) async -> Bool {
        CaptureLog.debug("network", "submit called: method=\(method), textLength=\(text.count)")
        let payload = CapturePayload.create(text: text, method: method)
        isSending = true
        lastError = nil

        defer { isSending = false }

        guard let destination = DestinationStore.shared.activeDestination else {
            CaptureLog.network.error("No active destination configured")
            let saved = pendingStore.save(PendingCapture(
                payload: payload,
                destinationID: UUID(),
                destinationSnapshot: Destination(name: "None", isActive: false, type: .restNoAuth(RESTNoAuthConfig(endpointURL: "")))
            ))
            lastError = saved ? "No destination configured" : "Failed to save capture offline"
            return false
        }

        do {
            try await DestinationSender.send(payload: payload, destination: destination, tokenManager: tokenManager)
            CaptureLog.network.info("Capture submitted: \(payload.idempotencyKey)")
            return true
        } catch {
            CaptureLog.network.error("Capture failed: \(error.localizedDescription)")
            let pending = PendingCapture(payload: payload, destinationID: destination.id, destinationSnapshot: destination)
            if !pendingStore.save(pending) {
                lastError = "Failed to save capture offline"
            } else {
                lastError = error.localizedDescription
            }
            return false
        }
    }

    func retryPending() async {
        let pending = pendingStore.loadAll()
        if pending.isEmpty { return }

        for capture in pending {
            // Prefer live destination (may have updated credentials), fall back to snapshot
            let destination: Destination
            if let live = DestinationStore.shared.destinations.first(where: { $0.id == capture.destinationID }) {
                destination = live
            } else if case .restNoAuth(let config) = capture.destinationSnapshot.type, config.endpointURL.isEmpty {
                // Legacy capture with no real destination — try active
                guard let active = DestinationStore.shared.activeDestination else { continue }
                destination = active
            } else {
                destination = capture.destinationSnapshot
            }

            do {
                try await DestinationSender.send(payload: capture.payload, destination: destination, tokenManager: tokenManager)
                pendingStore.remove(idempotencyKey: capture.payload.idempotencyKey)
                CaptureLog.network.info("Retry succeeded: \(capture.payload.idempotencyKey)")
            } catch let error as OAuthError where error == .missingCredentials {
                // Credentials were deleted — abandon this capture to avoid infinite retry
                pendingStore.remove(idempotencyKey: capture.payload.idempotencyKey)
                CaptureLog.network.error("Abandoned capture \(capture.payload.idempotencyKey): credentials no longer available")
            } catch {
                CaptureLog.network.warning("Retry failed for \(capture.payload.idempotencyKey): \(error.localizedDescription)")
            }
        }
    }
}

enum CaptureError: LocalizedError {
    case serverError(statusCode: Int)

    var errorDescription: String? {
        switch self {
        case .serverError(let code):
            return "Server returned status \(code)"
        }
    }
}
