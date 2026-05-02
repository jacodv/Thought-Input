import XCTest
@testable import ThoughtInput

final class SettingsBackupTests: XCTestCase {

    func testRoundTripAcrossAllDestinationTypes() throws {
        let supabase = makeBackup(
            name: "Supabase Prod",
            isActive: true,
            type: .supabase(SupabaseConfig(
                projectURL: "https://example.supabase.co",
                tableName: "thoughts",
                apiKeyRef: KeychainRef(account: "supabase-key")
            )),
            secrets: ["supabase-key": "sb-secret-abc"]
        )

        let restNoAuth = makeBackup(
            name: "Local",
            isActive: false,
            type: .restNoAuth(RESTNoAuthConfig(endpointURL: "http://localhost:3000/captures")),
            secrets: [:]
        )

        let apiKey = makeBackup(
            name: "Webhook",
            isActive: false,
            type: .restApiKey(RESTApiKeyConfig(
                endpointURL: "https://api.example.com/captures",
                headerName: "X-API-Key",
                apiKeyRef: KeychainRef(account: "webhook-key")
            )),
            secrets: ["webhook-key": "wh-12345"]
        )

        let oauthPwd = makeBackup(
            name: "OAuth Password",
            isActive: false,
            type: .restOAuthPassword(RESTOAuthPasswordConfig(
                endpointURL: "https://api.example.com/captures",
                tokenURL: "https://api.example.com/token",
                usernameRef: KeychainRef(account: "user-ref"),
                passwordRef: KeychainRef(account: "pwd-ref")
            )),
            secrets: ["user-ref": "alice", "pwd-ref": "p@ssw0rd"]
        )

        let oauthCC = makeBackup(
            name: "OAuth CC",
            isActive: false,
            type: .restOAuthClientCredentials(RESTOAuthClientCredentialsConfig(
                endpointURL: "https://api.example.com/captures",
                tokenURL: "https://api.example.com/token",
                clientIDRef: KeychainRef(account: "cid-ref"),
                clientSecretRef: KeychainRef(account: "cs-ref")
            )),
            secrets: ["cid-ref": "client-id-xyz", "cs-ref": "client-secret-xyz"]
        )

        let backup = SettingsBackup(
            schemaVersion: SettingsBackup.currentSchemaVersion,
            exportedAt: Date(timeIntervalSince1970: 1_700_000_000),
            sourcePlatform: "macos",
            destinations: [supabase, restNoAuth, apiKey, oauthPwd, oauthCC]
        )

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(backup)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let decoded = try decoder.decode(SettingsBackup.self, from: data)

        XCTAssertEqual(decoded.schemaVersion, backup.schemaVersion)
        XCTAssertEqual(decoded.sourcePlatform, "macos")
        XCTAssertEqual(decoded.destinations.count, backup.destinations.count)

        for (a, b) in zip(backup.destinations, decoded.destinations) {
            XCTAssertEqual(a.destination.id, b.destination.id)
            XCTAssertEqual(a.destination.name, b.destination.name)
            XCTAssertEqual(a.destination.isActive, b.destination.isActive)
            XCTAssertEqual(a.secrets, b.secrets)
            XCTAssertEqual(a.destination.type.keychainRefs.map(\.account),
                           b.destination.type.keychainRefs.map(\.account))
        }
    }

    func testDecodingMalformedJsonThrows() {
        let bogus = Data("{ not really json".utf8)
        let decoder = JSONDecoder()
        XCTAssertThrowsError(try decoder.decode(SettingsBackup.self, from: bogus))
    }

    // MARK: - Helpers

    private func makeBackup(name: String,
                            isActive: Bool,
                            type: DestinationType,
                            secrets: [String: String]) -> BackupDestination {
        BackupDestination(
            destination: Destination(name: name, isActive: isActive, type: type),
            secrets: secrets
        )
    }
}
