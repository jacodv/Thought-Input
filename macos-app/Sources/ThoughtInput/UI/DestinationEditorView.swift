import SwiftUI

struct DestinationEditorView: View {
    @ObservedObject var store: DestinationStore
    let existing: Destination?
    let onDismiss: () -> Void

    @State private var name: String = ""
    @State private var selectedType: DestinationTypeChoice = .supabase

    // Supabase fields
    @State private var supabaseProjectURL = ""
    @State private var supabaseTableName = ""
    @State private var supabaseAPIKey = ""

    // REST No Auth
    @State private var restNoAuthURL = ""

    // REST API Key
    @State private var restApiKeyURL = ""
    @State private var restApiKeyHeaderName = "X-API-Key"
    @State private var restApiKeyValue = ""

    // REST OAuth Password
    @State private var oauthPasswordEndpointURL = ""
    @State private var oauthPasswordTokenURL = ""
    @State private var oauthPasswordUsername = ""
    @State private var oauthPasswordPassword = ""

    // REST OAuth Client Credentials
    @State private var oauthClientEndpointURL = ""
    @State private var oauthClientTokenURL = ""
    @State private var oauthClientID = ""
    @State private var oauthClientSecret = ""

    // State
    @State private var isTesting = false
    @State private var testResult: TestResult?
    @State private var isSaving = false

    enum DestinationTypeChoice: String, CaseIterable {
        case supabase = "Supabase"
        case restNoAuth = "REST (No Auth)"
        case restApiKey = "REST (API Key)"
        case restOAuthPassword = "REST (OAuth Password)"
        case restOAuthClientCredentials = "REST (OAuth Client Credentials)"
    }

    enum TestResult {
        case success
        case failure(String)
    }

    var body: some View {
        VStack(spacing: 0) {
            Form {
                Section("General") {
                    TextField("Name", text: $name)
                    Picker("Type", selection: $selectedType) {
                        ForEach(DestinationTypeChoice.allCases, id: \.self) { type in
                            Text(type.rawValue).tag(type)
                        }
                    }
                }

                switch selectedType {
                case .supabase:
                    supabaseSection
                case .restNoAuth:
                    restNoAuthSection
                case .restApiKey:
                    restApiKeySection
                case .restOAuthPassword:
                    oauthPasswordSection
                case .restOAuthClientCredentials:
                    oauthClientCredentialsSection
                }

            }
            .formStyle(.grouped)

            Divider()

            HStack {
                Button("Test Connection") {
                    Task { await testConnection() }
                }
                .disabled(isTesting || !isValid)

                if isTesting {
                    ProgressView()
                        .controlSize(.small)
                }

                if let testResult {
                    switch testResult {
                    case .success:
                        Label("Connection successful", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                    case .failure(let message):
                        Label(message, systemImage: "xmark.circle.fill")
                            .foregroundColor(.red)
                            .font(.caption)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }

                Spacer()

                Button("Cancel") { onDismiss() }
                    .keyboardShortcut(.cancelAction)

                Button("Save") {
                    Task { await saveDestination() }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(!isValid || isSaving)
            }
            .padding()
        }
        .frame(width: 480, height: 400)
        .onAppear { loadExisting() }
    }

    // MARK: - Sections

    private var supabaseSection: some View {
        Section("Supabase Configuration") {
            TextField("Project URL", text: $supabaseProjectURL)
                .textFieldStyle(.roundedBorder)
            TextField("Table Name", text: $supabaseTableName)
                .textFieldStyle(.roundedBorder)
            SecureField("API Key", text: $supabaseAPIKey)
                .textFieldStyle(.roundedBorder)
            Text("Uses anon or service-role key for authentication.")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    private var restNoAuthSection: some View {
        Section("REST Endpoint") {
            TextField("Endpoint URL", text: $restNoAuthURL)
                .textFieldStyle(.roundedBorder)
            Text("POST requests with JSON payload, no authentication.")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    private var restApiKeySection: some View {
        Section("REST Endpoint + API Key") {
            TextField("Endpoint URL", text: $restApiKeyURL)
                .textFieldStyle(.roundedBorder)
            TextField("Header Name", text: $restApiKeyHeaderName)
                .textFieldStyle(.roundedBorder)
            SecureField("API Key", text: $restApiKeyValue)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var oauthPasswordSection: some View {
        Section("REST Endpoint + OAuth (Password)") {
            TextField("Endpoint URL", text: $oauthPasswordEndpointURL)
                .textFieldStyle(.roundedBorder)
            TextField("Token URL", text: $oauthPasswordTokenURL)
                .textFieldStyle(.roundedBorder)
            TextField("Username", text: $oauthPasswordUsername)
                .textFieldStyle(.roundedBorder)
            SecureField("Password", text: $oauthPasswordPassword)
                .textFieldStyle(.roundedBorder)
            Text("Exchanges username/password for an access token automatically.")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    private var oauthClientCredentialsSection: some View {
        Section("REST Endpoint + OAuth (Client Credentials)") {
            TextField("Endpoint URL", text: $oauthClientEndpointURL)
                .textFieldStyle(.roundedBorder)
            TextField("Token URL", text: $oauthClientTokenURL)
                .textFieldStyle(.roundedBorder)
            TextField("Client ID", text: $oauthClientID)
                .textFieldStyle(.roundedBorder)
            SecureField("Client Secret", text: $oauthClientSecret)
                .textFieldStyle(.roundedBorder)
        }
    }

    // MARK: - Validation

    private var isValid: Bool {
        guard !name.isEmpty else { return false }
        switch selectedType {
        case .supabase:
            return !supabaseProjectURL.isEmpty && !supabaseTableName.isEmpty && !supabaseAPIKey.isEmpty
        case .restNoAuth:
            return !restNoAuthURL.isEmpty
        case .restApiKey:
            return !restApiKeyURL.isEmpty && !restApiKeyHeaderName.isEmpty && !restApiKeyValue.isEmpty
        case .restOAuthPassword:
            return !oauthPasswordEndpointURL.isEmpty && !oauthPasswordTokenURL.isEmpty
                && !oauthPasswordUsername.isEmpty && !oauthPasswordPassword.isEmpty
        case .restOAuthClientCredentials:
            return !oauthClientEndpointURL.isEmpty && !oauthClientTokenURL.isEmpty
                && !oauthClientID.isEmpty && !oauthClientSecret.isEmpty
        }
    }

    // MARK: - Load Existing

    private func loadExisting() {
        guard let dest = existing else { return }
        name = dest.name

        switch dest.type {
        case .supabase(let config):
            selectedType = .supabase
            supabaseProjectURL = config.projectURL
            supabaseTableName = config.tableName
            supabaseAPIKey = (try? KeychainService.loadString(ref: config.apiKeyRef)) ?? ""

        case .restNoAuth(let config):
            selectedType = .restNoAuth
            restNoAuthURL = config.endpointURL

        case .restApiKey(let config):
            selectedType = .restApiKey
            restApiKeyURL = config.endpointURL
            restApiKeyHeaderName = config.headerName
            restApiKeyValue = (try? KeychainService.loadString(ref: config.apiKeyRef)) ?? ""

        case .restOAuthPassword(let config):
            selectedType = .restOAuthPassword
            oauthPasswordEndpointURL = config.endpointURL
            oauthPasswordTokenURL = config.tokenURL
            oauthPasswordUsername = (try? KeychainService.loadString(ref: config.usernameRef)) ?? ""
            oauthPasswordPassword = (try? KeychainService.loadString(ref: config.passwordRef)) ?? ""

        case .restOAuthClientCredentials(let config):
            selectedType = .restOAuthClientCredentials
            oauthClientEndpointURL = config.endpointURL
            oauthClientTokenURL = config.tokenURL
            oauthClientID = (try? KeychainService.loadString(ref: config.clientIDRef)) ?? ""
            oauthClientSecret = (try? KeychainService.loadString(ref: config.clientSecretRef)) ?? ""
        }
    }

    // MARK: - Save

    private func saveDestination() async {
        isSaving = true
        defer { isSaving = false }

        // Clean up old keychain refs if the destination type changed
        if let old = existing {
            let oldRefs = Set(old.type.keychainRefs.map(\.account))
            let newRefs = Set(refsForCurrentType().map(\.account))
            let orphanedAccounts = oldRefs.subtracting(newRefs)
            for account in orphanedAccounts {
                try? KeychainService.delete(ref: KeychainRef(account: account))
            }
            if !orphanedAccounts.isEmpty {
                OAuthTokenManager.shared.clearToken(for: old.id)
            }
        }

        let destination = buildDestinationAndSaveSecrets()
        if existing != nil {
            store.update(destination)
        } else {
            store.add(destination)
        }
        onDismiss()
    }

    /// Builds a Destination and writes secrets to Keychain. Used only on Save.
    private func buildDestinationAndSaveSecrets() -> Destination {
        let id = existing?.id ?? UUID()
        let isActive = existing?.isActive ?? false
        let destType: DestinationType

        switch selectedType {
        case .supabase:
            let ref = existingRef(for: \.supabase, keyPath: \.apiKeyRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: ref, string: supabaseAPIKey)
            destType = .supabase(SupabaseConfig(projectURL: supabaseProjectURL, tableName: supabaseTableName, apiKeyRef: ref))

        case .restNoAuth:
            destType = .restNoAuth(RESTNoAuthConfig(endpointURL: restNoAuthURL))

        case .restApiKey:
            let ref = existingRef(for: \.restApiKey, keyPath: \.apiKeyRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: ref, string: restApiKeyValue)
            destType = .restApiKey(RESTApiKeyConfig(endpointURL: restApiKeyURL, headerName: restApiKeyHeaderName, apiKeyRef: ref))

        case .restOAuthPassword:
            let usernameRef = existingRef(for: \.restOAuthPassword, keyPath: \.usernameRef) ?? KeychainRef.create()
            let passwordRef = existingRef(for: \.restOAuthPassword, keyPath: \.passwordRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: usernameRef, string: oauthPasswordUsername)
            try? KeychainService.save(ref: passwordRef, string: oauthPasswordPassword)
            destType = .restOAuthPassword(RESTOAuthPasswordConfig(
                endpointURL: oauthPasswordEndpointURL, tokenURL: oauthPasswordTokenURL,
                usernameRef: usernameRef, passwordRef: passwordRef
            ))

        case .restOAuthClientCredentials:
            let clientIDRef = existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientIDRef) ?? KeychainRef.create()
            let clientSecretRef = existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientSecretRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: clientIDRef, string: oauthClientID)
            try? KeychainService.save(ref: clientSecretRef, string: oauthClientSecret)
            destType = .restOAuthClientCredentials(RESTOAuthClientCredentialsConfig(
                endpointURL: oauthClientEndpointURL, tokenURL: oauthClientTokenURL,
                clientIDRef: clientIDRef, clientSecretRef: clientSecretRef
            ))
        }

        return Destination(id: id, name: name, isActive: isActive, type: destType)
    }

    /// Builds a Destination for testing without writing to Keychain.
    /// Uses existing keychain refs when available, or temporary refs with inline secrets.
    private func buildTestDestination() -> Destination {
        let id = existing?.id ?? UUID()
        let destType: DestinationType

        switch selectedType {
        case .supabase:
            let ref = existingRef(for: \.supabase, keyPath: \.apiKeyRef) ?? KeychainRef.create()
            // Write to a temporary keychain entry for the test, cleaned up after
            try? KeychainService.save(ref: ref, string: supabaseAPIKey)
            destType = .supabase(SupabaseConfig(projectURL: supabaseProjectURL, tableName: supabaseTableName, apiKeyRef: ref))

        case .restNoAuth:
            destType = .restNoAuth(RESTNoAuthConfig(endpointURL: restNoAuthURL))

        case .restApiKey:
            let ref = existingRef(for: \.restApiKey, keyPath: \.apiKeyRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: ref, string: restApiKeyValue)
            destType = .restApiKey(RESTApiKeyConfig(endpointURL: restApiKeyURL, headerName: restApiKeyHeaderName, apiKeyRef: ref))

        case .restOAuthPassword:
            let usernameRef = existingRef(for: \.restOAuthPassword, keyPath: \.usernameRef) ?? KeychainRef.create()
            let passwordRef = existingRef(for: \.restOAuthPassword, keyPath: \.passwordRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: usernameRef, string: oauthPasswordUsername)
            try? KeychainService.save(ref: passwordRef, string: oauthPasswordPassword)
            destType = .restOAuthPassword(RESTOAuthPasswordConfig(
                endpointURL: oauthPasswordEndpointURL, tokenURL: oauthPasswordTokenURL,
                usernameRef: usernameRef, passwordRef: passwordRef
            ))

        case .restOAuthClientCredentials:
            let clientIDRef = existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientIDRef) ?? KeychainRef.create()
            let clientSecretRef = existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientSecretRef) ?? KeychainRef.create()
            try? KeychainService.save(ref: clientIDRef, string: oauthClientID)
            try? KeychainService.save(ref: clientSecretRef, string: oauthClientSecret)
            destType = .restOAuthClientCredentials(RESTOAuthClientCredentialsConfig(
                endpointURL: oauthClientEndpointURL, tokenURL: oauthClientTokenURL,
                clientIDRef: clientIDRef, clientSecretRef: clientSecretRef
            ))
        }

        return Destination(id: id, name: name, isActive: false, type: destType)
    }

    /// Returns the keychain refs that would be used for the currently selected type (reusing existing when possible).
    private func refsForCurrentType() -> [KeychainRef] {
        switch selectedType {
        case .supabase:
            return [existingRef(for: \.supabase, keyPath: \.apiKeyRef) ?? KeychainRef(account: "")]
        case .restNoAuth:
            return []
        case .restApiKey:
            return [existingRef(for: \.restApiKey, keyPath: \.apiKeyRef) ?? KeychainRef(account: "")]
        case .restOAuthPassword:
            return [
                existingRef(for: \.restOAuthPassword, keyPath: \.usernameRef) ?? KeychainRef(account: ""),
                existingRef(for: \.restOAuthPassword, keyPath: \.passwordRef) ?? KeychainRef(account: ""),
            ]
        case .restOAuthClientCredentials:
            return [
                existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientIDRef) ?? KeychainRef(account: ""),
                existingRef(for: \.restOAuthClientCredentials, keyPath: \.clientSecretRef) ?? KeychainRef(account: ""),
            ]
        }
    }

    // Helper to reuse existing keychain refs when editing
    private func existingRef<C>(for casePath: (DestinationType) -> C?, keyPath: KeyPath<C, KeychainRef>) -> KeychainRef? {
        guard let existing, let config = casePath(existing.type) else { return nil }
        return config[keyPath: keyPath]
    }

    // MARK: - Test Connection

    private func testConnection() async {
        isTesting = true
        testResult = nil
        defer { isTesting = false }

        let destination = buildTestDestination()
        let tempRefs: [KeychainRef]
        // Track refs that were created just for this test (not from an existing destination)
        if existing == nil {
            tempRefs = destination.type.keychainRefs
        } else {
            let existingAccounts = Set((existing?.type.keychainRefs ?? []).map(\.account))
            tempRefs = destination.type.keychainRefs.filter { !existingAccounts.contains($0.account) }
        }

        do {
            try await DestinationSender.testConnection(destination: destination, tokenManager: OAuthTokenManager.shared)
            testResult = .success
        } catch {
            testResult = .failure(error.localizedDescription)
        }

        // Clean up temporary keychain entries created for the test
        for ref in tempRefs {
            try? KeychainService.delete(ref: ref)
        }
    }
}

// MARK: - DestinationType case extraction helpers

private extension DestinationType {
    var supabase: SupabaseConfig? {
        if case .supabase(let c) = self { return c }
        return nil
    }
    var restApiKey: RESTApiKeyConfig? {
        if case .restApiKey(let c) = self { return c }
        return nil
    }
    var restOAuthPassword: RESTOAuthPasswordConfig? {
        if case .restOAuthPassword(let c) = self { return c }
        return nil
    }
    var restOAuthClientCredentials: RESTOAuthClientCredentialsConfig? {
        if case .restOAuthClientCredentials(let c) = self { return c }
        return nil
    }
}
