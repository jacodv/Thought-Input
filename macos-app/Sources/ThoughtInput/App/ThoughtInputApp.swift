import SwiftUI

@main
struct ThoughtInputApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        // No main window — this is a menu bar app.
        // The capture panel and settings window are managed by AppDelegate.
        Settings {
            EmptyView()
        }
    }
}
