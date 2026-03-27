import Cocoa
import SwiftUI

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    private var statusItem: NSStatusItem?
    private var capturePanel: CapturePanel?
    private var settingsWindow: NSWindow?
    private let shortcutManager = GlobalShortcutManager.shared

    func applicationDidFinishLaunching(_ notification: Notification) {
        CaptureLog.always("Thought Input starting… (debug=\(CaptureLog.isDebug))")
        CaptureLog.always("  To enable debug logging: pass --debug or set THOUGHT_INPUT_DEBUG=1")
        CaptureLog.debug("ui", "applicationDidFinishLaunching started")

        MigrationService.migrateIfNeeded(store: DestinationStore.shared)
        CaptureLog.debug("ui", "Migration check complete")

        setupMenuBar()
        CaptureLog.debug("ui", "Menu bar setup complete")

        setupCapturePanel()
        CaptureLog.debug("ui", "Capture panel setup complete")

        setupGlobalShortcut()
        CaptureLog.debug("ui", "Global shortcut setup complete")

        retryPendingCaptures()

        CaptureLog.ui.info("Thought Input launched")
        CaptureLog.always("Thought Input ready.")
        CaptureLog.debug("ui", "applicationDidFinishLaunching finished")
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false // Keep running as menu bar app
    }

    // MARK: - Menu Bar

    private func setupMenuBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        if let button = statusItem?.button {
            if let image = NSImage(systemSymbolName: "brain.head.profile", accessibilityDescription: "Thought Input") {
                button.image = image
            } else {
                button.title = "💭"
            }
        }

        let menu = NSMenu()

        let captureItem = NSMenuItem(title: "Capture", action: #selector(showCapture), keyEquivalent: "")
        captureItem.target = self
        menu.addItem(captureItem)

        menu.addItem(NSMenuItem.separator())

        let settingsItem = NSMenuItem(title: "Settings…", action: #selector(showSettings), keyEquivalent: ",")
        settingsItem.target = self
        menu.addItem(settingsItem)

        menu.addItem(NSMenuItem.separator())

        let quitItem = NSMenuItem(title: "Quit Thought Input", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        menu.addItem(quitItem)

        statusItem?.menu = menu
    }

    // MARK: - Capture Panel

    private func setupCapturePanel() {
        let panel = CapturePanel()
        let viewController = CaptureViewController { [weak self] in
            self?.capturePanel?.dismissCapture()
        }
        panel.contentViewController = viewController
        panel.applyVisualStyle()
        capturePanel = panel
    }

    @objc private func toggleCapture() {
        CaptureLog.debug("ui", "toggleCapture called, isVisible=\(capturePanel?.isVisible ?? false)")
        guard let panel = capturePanel else {
            CaptureLog.debug("ui", "toggleCapture: capturePanel is nil")
            return
        }
        if panel.isVisible {
            panel.dismissCapture()
        } else {
            showCapture()
        }
    }

    @objc private func showCapture() {
        CaptureLog.debug("ui", "showCapture called")
        capturePanel?.showCapture()
    }

    // MARK: - Settings

    @objc private func showSettings() {
        CaptureLog.debug("ui", "showSettings called")

        // Dismiss capture panel first to avoid responder chain conflicts
        if capturePanel?.isVisible == true {
            capturePanel?.dismissCapture()
        }

        // Temporarily become a regular app so we can properly claim keyboard focus.
        // Without this, keystrokes leak to the previously active app (e.g. Terminal).
        NSApp.setActivationPolicy(.regular)

        // Reuse existing window if it's still around
        if let window = settingsWindow {
            CaptureLog.debug("ui", "Reusing existing settings window")
            window.makeKeyAndOrderFront(nil)
            NSApp.activate()
            return
        }

        // Create settings window directly — the SwiftUI Settings scene + selector approach
        // is unreliable across macOS versions.
        CaptureLog.debug("ui", "Creating new settings window")
        let hostingController = NSHostingController(rootView: SettingsView())
        let window = NSWindow(contentViewController: hostingController)
        window.title = "Thought Input Settings"
        window.styleMask = [.titled, .closable]
        window.setContentSize(NSSize(width: 500, height: 520))
        window.center()
        window.isReleasedWhenClosed = false
        window.delegate = self

        settingsWindow = window
        window.makeKeyAndOrderFront(nil)
        NSApp.activate()
        CaptureLog.debug("ui", "Settings window shown")
    }

    func windowWillClose(_ notification: Notification) {
        if (notification.object as? NSWindow) === settingsWindow {
            CaptureLog.debug("ui", "Settings window closed")
            settingsWindow = nil
            // Return to menu-bar-only mode
            NSApp.setActivationPolicy(.accessory)
        }
    }

    // MARK: - Global Shortcut

    private func setupGlobalShortcut() {
        shortcutManager.onTrigger = { [weak self] in
            self?.toggleCapture()
        }
        shortcutManager.register()
    }

    // MARK: - Pending Captures

    private func retryPendingCaptures() {
        Task {
            await CaptureService.shared.retryPending()
        }
    }
}
