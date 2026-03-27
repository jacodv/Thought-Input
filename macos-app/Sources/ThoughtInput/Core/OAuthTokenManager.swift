import Foundation

@MainActor
final class OAuthTokenManager {
    static let shared = OAuthTokenManager()

    private var tokenCache: [UUID: OAuthToken] = [:]

    func validToken(for destination: Destination) async throws -> String {
        if let cached = tokenCache[destination.id], !cached.isExpired {
            return cached.accessToken
        }

        // Try refresh if we have a refresh token
        if let cached = tokenCache[destination.id], let existingRefreshToken = cached.refreshToken {
            if let refreshed = try? await refreshToken(existingRefreshToken, for: destination) {
                tokenCache[destination.id] = refreshed
                return refreshed.accessToken
            }
        }

        let token = try await fetchToken(for: destination)
        tokenCache[destination.id] = token
        return token.accessToken
    }

    func clearToken(for destinationID: UUID) {
        tokenCache.removeValue(forKey: destinationID)
    }

    // MARK: - Token Exchange

    private func fetchToken(for destination: Destination) async throws -> OAuthToken {
        switch destination.type {
        case .restOAuthPassword(let config):
            return try await passwordGrant(config: config)
        case .restOAuthClientCredentials(let config):
            return try await clientCredentialsGrant(config: config)
        default:
            CaptureLog.auth.error("fetchToken called for non-OAuth destination type")
            throw OAuthError.missingCredentials
        }
    }

    private func passwordGrant(config: RESTOAuthPasswordConfig) async throws -> OAuthToken {
        guard let username = try KeychainService.loadString(ref: config.usernameRef),
              let password = try KeychainService.loadString(ref: config.passwordRef) else {
            throw OAuthError.missingCredentials
        }

        let body = "grant_type=password&username=\(urlEncode(username))&password=\(urlEncode(password))"
        return try await exchangeToken(tokenURL: config.tokenURL, body: body)
    }

    private func clientCredentialsGrant(config: RESTOAuthClientCredentialsConfig) async throws -> OAuthToken {
        guard let clientID = try KeychainService.loadString(ref: config.clientIDRef),
              let clientSecret = try KeychainService.loadString(ref: config.clientSecretRef) else {
            throw OAuthError.missingCredentials
        }

        let body = "grant_type=client_credentials&client_id=\(urlEncode(clientID))&client_secret=\(urlEncode(clientSecret))"
        return try await exchangeToken(tokenURL: config.tokenURL, body: body)
    }

    private func refreshToken(_ token: String, for destination: Destination) async throws -> OAuthToken {
        let tokenURL: String
        switch destination.type {
        case .restOAuthPassword(let c): tokenURL = c.tokenURL
        case .restOAuthClientCredentials(let c): tokenURL = c.tokenURL
        default: throw OAuthError.missingCredentials
        }

        let body = "grant_type=refresh_token&refresh_token=\(urlEncode(token))"
        return try await exchangeToken(tokenURL: tokenURL, body: body)
    }

    private func exchangeToken(tokenURL: String, body: String) async throws -> OAuthToken {
        guard let url = URL(string: tokenURL) else {
            throw OAuthError.invalidTokenURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)
        request.timeoutInterval = 15

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw OAuthError.tokenExchangeFailed(statusCode: code)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let accessToken = json?["access_token"] as? String else {
            throw OAuthError.invalidTokenResponse
        }

        let expiresIn = json?["expires_in"] as? TimeInterval
        let refreshToken = json?["refresh_token"] as? String

        return OAuthToken(
            accessToken: accessToken,
            expiresAt: expiresIn.map { Date.now.addingTimeInterval($0) },
            refreshToken: refreshToken
        )
    }

    private static let formURLEncodedAllowed: CharacterSet = {
        var chars = CharacterSet.alphanumerics
        chars.insert(charactersIn: "-._~")
        return chars
    }()

    private func urlEncode(_ string: String) -> String {
        string.addingPercentEncoding(withAllowedCharacters: Self.formURLEncodedAllowed) ?? string
    }
}

enum OAuthError: LocalizedError, Equatable {
    case missingCredentials
    case invalidTokenURL
    case tokenExchangeFailed(statusCode: Int)
    case invalidTokenResponse

    var errorDescription: String? {
        switch self {
        case .missingCredentials: "OAuth credentials not found in Keychain"
        case .invalidTokenURL: "Invalid token URL"
        case .tokenExchangeFailed(let code): "Token exchange failed (status \(code))"
        case .invalidTokenResponse: "Token response missing access_token"
        }
    }
}
