import Foundation

// MARK: - Keychain Reference

struct KeychainRef: Codable, Sendable, Hashable {
    let account: String

    static func create() -> KeychainRef {
        KeychainRef(account: UUID().uuidString)
    }
}

// MARK: - Destination

struct Destination: Codable, Sendable, Identifiable {
    let id: UUID
    var name: String
    var isActive: Bool
    var type: DestinationType

    init(id: UUID = UUID(), name: String, isActive: Bool = false, type: DestinationType) {
        self.id = id
        self.name = name
        self.isActive = isActive
        self.type = type
    }
}

// MARK: - Destination Type

enum DestinationType: Codable, Sendable {
    case supabase(SupabaseConfig)
    case restNoAuth(RESTNoAuthConfig)
    case restApiKey(RESTApiKeyConfig)
    case restOAuthPassword(RESTOAuthPasswordConfig)
    case restOAuthClientCredentials(RESTOAuthClientCredentialsConfig)

    var displayName: String {
        switch self {
        case .supabase: "Supabase"
        case .restNoAuth: "REST (No Auth)"
        case .restApiKey: "REST (API Key)"
        case .restOAuthPassword: "REST (OAuth Password)"
        case .restOAuthClientCredentials: "REST (OAuth Client Credentials)"
        }
    }

    var iconName: String {
        switch self {
        case .supabase: "server.rack"
        case .restNoAuth: "network"
        case .restApiKey: "key"
        case .restOAuthPassword: "person.badge.key"
        case .restOAuthClientCredentials: "lock.shield"
        }
    }

    var keychainRefs: [KeychainRef] {
        switch self {
        case .supabase(let c): [c.apiKeyRef]
        case .restNoAuth: []
        case .restApiKey(let c): [c.apiKeyRef]
        case .restOAuthPassword(let c): [c.usernameRef, c.passwordRef]
        case .restOAuthClientCredentials(let c): [c.clientIDRef, c.clientSecretRef]
        }
    }
}

// MARK: - Config Structs

struct SupabaseConfig: Codable, Sendable {
    var projectURL: String
    var tableName: String
    var apiKeyRef: KeychainRef
}

struct RESTNoAuthConfig: Codable, Sendable {
    var endpointURL: String
}

struct RESTApiKeyConfig: Codable, Sendable {
    var endpointURL: String
    var headerName: String
    var apiKeyRef: KeychainRef
}

struct RESTOAuthPasswordConfig: Codable, Sendable {
    var endpointURL: String
    var tokenURL: String
    var usernameRef: KeychainRef
    var passwordRef: KeychainRef
}

struct RESTOAuthClientCredentialsConfig: Codable, Sendable {
    var endpointURL: String
    var tokenURL: String
    var clientIDRef: KeychainRef
    var clientSecretRef: KeychainRef
}

// MARK: - OAuth Token

struct OAuthToken: Codable, Sendable {
    let accessToken: String
    let expiresAt: Date?
    let refreshToken: String?

    var isExpired: Bool {
        guard let expiresAt else { return false }
        return Date.now.addingTimeInterval(30) >= expiresAt
    }
}

// MARK: - Pending Capture

struct PendingCapture: Codable, Sendable {
    let payload: CapturePayload
    let destinationID: UUID
    let destinationSnapshot: Destination
}
