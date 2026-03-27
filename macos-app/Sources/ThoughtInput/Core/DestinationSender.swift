import Foundation

enum DestinationSender {
    static func send(
        payload: CapturePayload,
        destination: Destination,
        tokenManager: OAuthTokenManager
    ) async throws {
        let jsonData = try JSONEncoder().encode(payload)

        switch destination.type {
        case .supabase(let config):
            try await sendSupabase(data: jsonData, config: config)

        case .restNoAuth(let config):
            try await sendREST(data: jsonData, url: config.endpointURL, headers: [:])

        case .restApiKey(let config):
            let apiKey = try KeychainService.loadString(ref: config.apiKeyRef) ?? ""
            try await sendREST(data: jsonData, url: config.endpointURL, headers: [config.headerName: apiKey])

        case .restOAuthPassword, .restOAuthClientCredentials:
            try await sendWithOAuth(data: jsonData, destination: destination, tokenManager: tokenManager)
        }
    }

    // MARK: - Test Connection

    static func testConnection(destination: Destination, tokenManager: OAuthTokenManager) async throws {
        let testPayload = CapturePayload.create(text: "Connection test", method: .typed)
        try await send(payload: testPayload, destination: destination, tokenManager: tokenManager)
    }

    // MARK: - Supabase

    private static func sendSupabase(data: Data, config: SupabaseConfig) async throws {
        let urlString = config.projectURL
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            + "/rest/v1/" + config.tableName

        guard let url = URL(string: urlString) else {
            throw CaptureError.serverError(statusCode: -1)
        }

        let apiKey = try KeychainService.loadString(ref: config.apiKeyRef) ?? ""

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        request.setValue(apiKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = data
        request.timeoutInterval = 10

        try await execute(request)
    }

    // MARK: - REST

    private static func sendREST(data: Data, url: String, headers: [String: String]) async throws {
        guard let requestURL = URL(string: url) else {
            throw CaptureError.serverError(statusCode: -1)
        }

        var request = URLRequest(url: requestURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        request.timeoutInterval = 10

        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }

        try await execute(request)
    }

    // MARK: - OAuth

    private static func sendWithOAuth(
        data: Data,
        destination: Destination,
        tokenManager: OAuthTokenManager
    ) async throws {
        let endpointURL: String
        switch destination.type {
        case .restOAuthPassword(let c): endpointURL = c.endpointURL
        case .restOAuthClientCredentials(let c): endpointURL = c.endpointURL
        default:
            CaptureLog.network.error("sendWithOAuth called for non-OAuth destination type")
            throw CaptureError.serverError(statusCode: -1)
        }

        let token = try await tokenManager.validToken(for: destination)

        do {
            try await sendREST(data: data, url: endpointURL, headers: ["Authorization": "Bearer \(token)"])
        } catch let error as CaptureError {
            // Retry once on 401
            if case .serverError(statusCode: 401) = error {
                await tokenManager.clearToken(for: destination.id)
                let newToken = try await tokenManager.validToken(for: destination)
                try await sendREST(data: data, url: endpointURL, headers: ["Authorization": "Bearer \(newToken)"])
            } else {
                throw error
            }
        }
    }

    // MARK: - Execute

    private static func execute(_ request: URLRequest) async throws {
        let (_, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw CaptureError.serverError(statusCode: code)
        }
    }
}
