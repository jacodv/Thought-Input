import SwiftUI

struct SettingsView: View {
    @AppStorage("apiEndpoint") private var apiEndpoint = ""
    @AppStorage("shortcutDisplay") private var shortcutDisplay = "⌘⇧Space"

    var body: some View {
        Form {
            Section("API Configuration") {
                TextField("Endpoint URL", text: $apiEndpoint)
                    .textFieldStyle(.roundedBorder)
                Text("POST requests with JSON payload will be sent to this URL.")
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

            Section("About") {
                LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0")
            }
        }
        .formStyle(.grouped)
        .frame(width: 400, height: 300)
    }
}
