import Cocoa
import SwiftUI

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private var capturePanel: CapturePanel!
    private let shortcutManager = GlobalShortcutManager.shared

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupMenuBar()
        setupCapturePanel()
        setupGlobalShortcut()
        retryPendingCaptures()

        CaptureLog.ui.info("Thought Input launched")
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false // Keep running as menu bar app
    }

    // MARK: - Menu Bar

    private func setupMenuBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)

        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "brain.head.profile", accessibilityDescription: "Thought Input")
            button.action = #selector(toggleCapture)
            button.target = self
        }

        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "Capture", action: #selector(showCapture), keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Settings…", action: #selector(showSettings), keyEquivalent: ","))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit Thought Input", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))

        statusItem.menu = menu
    }

    // MARK: - Capture Panel

    private func setupCapturePanel() {
        capturePanel = CapturePanel()
        let viewController = CaptureViewController { [weak self] in
            self?.capturePanel.dismissCapture()
        }
        capturePanel.contentViewController = viewController
    }

    @objc private func toggleCapture() {
        if capturePanel.isVisible {
            capturePanel.dismissCapture()
        } else {
            showCapture()
        }
    }

    @objc private func showCapture() {
        capturePanel.showCapture()
    }

    // MARK: - Settings

    @objc private func showSettings() {
        let settingsWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 400, height: 300),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        settingsWindow.title = "Thought Input Settings"
        settingsWindow.contentView = NSHostingView(rootView: SettingsView())
        settingsWindow.center()
        settingsWindow.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
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
            let service = CaptureService()
            await service.retryPending()
        }
    }
}
