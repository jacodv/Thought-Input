import Foundation

@MainActor
final class DestinationStore: ObservableObject {
    static let shared = DestinationStore()

    @Published private(set) var destinations: [Destination] = []

    private let fileURL: URL

    init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first ?? FileManager.default.temporaryDirectory
        let dir = appSupport.appendingPathComponent("ThoughtInput", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        } catch {
            CaptureLog.store.error("Failed to create destination store directory: \(error.localizedDescription)")
        }
        fileURL = dir.appendingPathComponent("destinations.json")
        CaptureLog.debug("store", "DestinationStore init, fileURL=\(fileURL.path)")
        load()
    }

    var activeDestination: Destination? {
        destinations.first(where: \.isActive)
    }

    func add(_ destination: Destination) {
        var dest = destination
        if destinations.isEmpty {
            dest.isActive = true
        }
        destinations.append(dest)
        save()
    }

    func update(_ destination: Destination) {
        guard let index = destinations.firstIndex(where: { $0.id == destination.id }) else { return }
        destinations[index] = destination
        save()
    }

    func delete(_ destination: Destination) {
        for ref in destination.type.keychainRefs {
            try? KeychainService.delete(ref: ref)
        }
        destinations.removeAll { $0.id == destination.id }
        if activeDestination == nil, !destinations.isEmpty {
            destinations[destinations.startIndex].isActive = true
        }
        save()
    }

    func setActive(_ destination: Destination) {
        for i in destinations.indices {
            destinations[i].isActive = (destinations[i].id == destination.id)
        }
        save()
    }

    // MARK: - Persistence

    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            CaptureLog.debug("store", "No destinations file found at \(fileURL.path)")
            return
        }
        do {
            let data = try Data(contentsOf: fileURL)
            destinations = try JSONDecoder().decode([Destination].self, from: data)
            CaptureLog.debug("store", "Loaded \(destinations.count) destinations")
        } catch {
            CaptureLog.store.error("Failed to load destinations: \(error.localizedDescription)")
            destinations = []
        }
    }

    private func save() {
        do {
            let data = try JSONEncoder().encode(destinations)
            try data.write(to: fileURL, options: .atomic)
            CaptureLog.debug("store", "Saved \(destinations.count) destinations")
        } catch {
            CaptureLog.store.error("Failed to save destinations: \(error.localizedDescription)")
        }
    }
}
