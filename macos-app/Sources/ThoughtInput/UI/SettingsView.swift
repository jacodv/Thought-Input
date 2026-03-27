import SwiftUI

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
}
