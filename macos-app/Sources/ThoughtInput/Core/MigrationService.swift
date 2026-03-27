import Foundation

enum MigrationService {
    @MainActor
    static func migrateIfNeeded(store: DestinationStore) {
        guard let endpoint = UserDefaults.standard.string(forKey: "apiEndpoint"),
              !endpoint.isEmpty else { return }

        // Don't migrate if destinations.json already exists (even if it failed to decode)
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            CaptureLog.store.error("Migration: could not resolve Application Support directory")
            return
        }
        let destFile = appSupport.appendingPathComponent("ThoughtInput/destinations.json")
        if FileManager.default.fileExists(atPath: destFile.path) {
            CaptureLog.network.info("Skipping migration: destinations.json already exists")
            return
        }

        guard store.destinations.isEmpty else { return }

        let destination = Destination(
            name: "Migrated Endpoint",
            isActive: true,
            type: .restNoAuth(RESTNoAuthConfig(endpointURL: endpoint))
        )
        store.add(destination)

        // Only remove old key after successful save
        if !store.destinations.isEmpty {
            UserDefaults.standard.removeObject(forKey: "apiEndpoint")
            CaptureLog.network.info("Migrated legacy apiEndpoint to destination")
        }
    }
}
