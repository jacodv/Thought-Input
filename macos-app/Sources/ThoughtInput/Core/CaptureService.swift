import Foundation

@MainActor
final class CaptureService: ObservableObject {
    static let shared = CaptureService()

    @Published var isSending = false
    @Published var lastError: String?

    private let pendingStore = PendingCaptureStore()

    func submit(text: String, method: CapturePayload.CaptureMethod) async -> Bool {
        let payload = CapturePayload.create(text: text, method: method)
        isSending = true
        lastError = nil

        defer { isSending = false }

        guard let url = apiEndpointURL() else {
            CaptureLog.network.error("No API endpoint configured")
            if !pendingStore.save(payload) {
                lastError = "Failed to save capture offline"
            } else {
                lastError = "No API endpoint configured"
            }
            return false
        }

        do {
            try await send(payload: payload, to: url)
            CaptureLog.network.info("Capture submitted: \(payload.idempotencyKey)")
            return true
        } catch {
            CaptureLog.network.error("Capture failed: \(error.localizedDescription)")
            if !pendingStore.save(payload) {
                lastError = "Failed to save capture offline"
            } else {
                lastError = error.localizedDescription
            }
            return false
        }
    }

    func retryPending() async {
        guard let url = apiEndpointURL() else {
            let count = pendingStore.pendingCount
            if count > 0 {
                CaptureLog.network.warning("Cannot retry \(count) pending captures: no API endpoint configured")
            }
            return
        }
        let pending = pendingStore.loadAll()
        for payload in pending {
            do {
                try await send(payload: payload, to: url)
                pendingStore.remove(idempotencyKey: payload.idempotencyKey)
                CaptureLog.network.info("Retry succeeded: \(payload.idempotencyKey)")
            } catch {
                CaptureLog.network.warning("Retry failed for \(payload.idempotencyKey): \(error.localizedDescription)")
            }
        }
    }

    private func send(payload: CapturePayload, to url: URL) async throws {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(payload)
        request.timeoutInterval = 10

        let (_, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw CaptureError.serverError(statusCode: statusCode)
        }
    }

    private func apiEndpointURL() -> URL? {
        guard let urlString = UserDefaults.standard.string(forKey: "apiEndpoint"),
              !urlString.isEmpty else {
            return nil
        }
        guard let url = URL(string: urlString) else {
            CaptureLog.network.error("Invalid API endpoint URL: \(urlString)")
            return nil
        }
        return url
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
