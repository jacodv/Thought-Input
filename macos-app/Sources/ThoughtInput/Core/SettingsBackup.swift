import Foundation

// MARK: - Backup Models

struct SettingsBackup: Codable {
    let schemaVersion: Int
    let exportedAt: Date
    let sourcePlatform: String
    let destinations: [BackupDestination]

    static let currentSchemaVersion = 1
}

struct BackupDestination: Codable {
    let destination: Destination
    let secrets: [String: String]
}

// MARK: - Errors

enum SettingsBackupError: LocalizedError {
    case unsupportedSchemaVersion(Int)
    case decodingFailed(String)

    var errorDescription: String? {
        switch self {
        case .unsupportedSchemaVersion(let v):
            return "Unsupported backup schema version (\(v)). This file was made by a newer version of the app."
        case .decodingFailed(let message):
            return "Couldn't read the backup file: \(message)"
        }
    }
}

// MARK: - Service

@MainActor
enum SettingsBackupService {

    static func export() throws -> Data {
        let store = DestinationStore.shared
        let backups: [BackupDestination] = store.destinations.map { destination in
            var secrets: [String: String] = [:]
            for ref in destination.type.keychainRefs {
                if let value = try? KeychainService.loadString(ref: ref) {
                    secrets[ref.account] = value
                }
            }
            return BackupDestination(destination: destination, secrets: secrets)
        }

        let backup = SettingsBackup(
            schemaVersion: SettingsBackup.currentSchemaVersion,
            exportedAt: Date(),
            sourcePlatform: "macos",
            destinations: backups
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(backup)
    }

    static func importBackup(_ data: Data) throws {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let backup: SettingsBackup
        do {
            backup = try decoder.decode(SettingsBackup.self, from: data)
        } catch {
            throw SettingsBackupError.decodingFailed(error.localizedDescription)
        }

        guard backup.schemaVersion == SettingsBackup.currentSchemaVersion else {
            throw SettingsBackupError.unsupportedSchemaVersion(backup.schemaVersion)
        }

        let store = DestinationStore.shared

        for existing in store.destinations {
            store.delete(existing)
        }

        var activeID: UUID?
        for entry in backup.destinations {
            for (account, value) in entry.secrets {
                try KeychainService.save(ref: KeychainRef(account: account), string: value)
            }
            var destination = entry.destination
            let wasActive = destination.isActive
            destination.isActive = false
            store.add(destination)
            if wasActive {
                activeID = destination.id
            }
        }

        if let activeID, let active = store.destinations.first(where: { $0.id == activeID }) {
            store.setActive(active)
        }
    }
}
