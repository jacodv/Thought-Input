import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct SettingsView: View {
    @ObservedObject private var store = DestinationStore.shared
    @AppStorage("shortcutDisplay") private var shortcutDisplay = "⌘⇧Space"

    var body: some View {
        Form {
            Section("Destinations") {
                DestinationListView(store: store)
            }
            .onAppear {
                CaptureLog.debug("ui", "SettingsView appeared, destinations=\(store.destinations.count)")
            }

            Section("Import / Export") {
                HStack {
                    Button("Export Settings…") { exportSettings() }
                    Button("Import Settings…") { importSettings() }
                    Spacer()
                }
                Text("The exported file contains your destinations and their plaintext API keys. Don't share or commit it.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Keyboard Shortcut") {
                HStack {
                    Text("Capture trigger:")
                    Spacer()
                    Text(shortcutDisplay)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.secondary.opacity(0.2))
                        .cornerRadius(4)
                }
                Text("Default: ⌘⇧Space. Requires Accessibility permission.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Setup Guide") {
                Text("See the setup guide for instructions on creating your Supabase table or REST endpoint before first use.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Button("Open Setup Guide") {
                    let guideURL = setupGuideURL()
                    NSWorkspace.shared.open(guideURL)
                }
            }

            Section("About") {
                LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0")
            }
        }
        .formStyle(.grouped)
        .frame(width: 500, height: 520)
    }

    private func setupGuideURL() -> URL {
        // Look for the guide relative to the app bundle, fall back to GitHub repo
        if let bundlePath = Bundle.main.url(forResource: "SETUP-GUIDE", withExtension: "md") {
            return bundlePath
        }
        // Fallback: open from the repo docs folder on GitHub
        return URL(string: "https://github.com/jacodv/Thought-Input/blob/main/docs/SETUP-GUIDE.md")!
    }

    // MARK: - Import / Export

    private func exportSettings() {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.json]
        panel.nameFieldStringValue = "thought-input-settings.json"
        panel.title = "Export Thought Input Settings"
        panel.canCreateDirectories = true

        guard panel.runModal() == .OK, let url = panel.url else { return }

        do {
            let data = try SettingsBackupService.export()
            try data.write(to: url, options: .atomic)
        } catch {
            presentError("Export failed", error: error)
        }
    }

    private func importSettings() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.json]
        panel.allowsMultipleSelection = false
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.title = "Import Thought Input Settings"

        guard panel.runModal() == .OK, let url = panel.url else { return }

        let confirm = NSAlert()
        confirm.messageText = "Replace all destinations?"
        confirm.informativeText = "Importing will delete every existing destination and its stored secrets, then load the contents of the selected file. This cannot be undone."
        confirm.alertStyle = .warning
        confirm.addButton(withTitle: "Replace")
        confirm.addButton(withTitle: "Cancel")
        guard confirm.runModal() == .alertFirstButtonReturn else { return }

        do {
            let data = try Data(contentsOf: url)
            try SettingsBackupService.importBackup(data)
        } catch {
            presentError("Import failed", error: error)
        }
    }

    private func presentError(_ title: String, error: Error) {
        let alert = NSAlert()
        alert.messageText = title
        alert.informativeText = error.localizedDescription
        alert.alertStyle = .warning
        alert.addButton(withTitle: "OK")
        alert.runModal()
    }
}
