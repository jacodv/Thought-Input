import Cocoa

final class CapturePanel: NSPanel {
    init() {
        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 420, height: 120),
            styleMask: [.nonactivatingPanel, .titled, .closable, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )

        isFloatingPanel = true
        level = .floating
        titleVisibility = .hidden
        titlebarAppearsTransparent = true
        isMovableByWindowBackground = true
        isReleasedWhenClosed = false
        animationBehavior = .utilityWindow
        backgroundColor = .windowBackgroundColor
        hasShadow = true

        // Allow the panel to become key so the text field receives keyboard input
        becomesKeyOnlyIfNeeded = false

        centerOnScreen()
    }

    override var canBecomeKey: Bool { true }

    func centerOnScreen() {
        guard let screen = NSScreen.main else { return }
        let screenFrame = screen.visibleFrame
        let x = screenFrame.midX - frame.width / 2
        let y = screenFrame.maxY - frame.height - 80 // Top-centered, slightly below top
        setFrameOrigin(NSPoint(x: x, y: y))
    }

    func showCapture() {
        centerOnScreen()
        makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        CaptureLog.ui.info("Capture panel shown")
    }

    func dismissCapture() {
        orderOut(nil)
        CaptureLog.ui.info("Capture panel dismissed")
    }
}
