import Cocoa

final class CapturePanel: NSPanel {
    private let vibrancyView: NSVisualEffectView

    init() {
        vibrancyView = NSVisualEffectView()

        super.init(
            contentRect: NSRect(x: 0, y: 0, width: 680, height: 52),
            styleMask: [.borderless, .nonactivatingPanel, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )

        isFloatingPanel = true
        level = .floating
        isMovableByWindowBackground = true
        isReleasedWhenClosed = false
        animationBehavior = .utilityWindow
        backgroundColor = .clear
        isOpaque = false
        hasShadow = true

        // Allow the panel to become key so the text field receives keyboard input
        becomesKeyOnlyIfNeeded = false

        // Rounded corners with vibrancy (Spotlight-like)
        vibrancyView.frame = contentView!.bounds
        vibrancyView.autoresizingMask = [.width, .height]
        vibrancyView.material = .hudWindow
        vibrancyView.blendingMode = .behindWindow
        vibrancyView.state = .active
        vibrancyView.wantsLayer = true
        vibrancyView.layer?.cornerRadius = 12
        vibrancyView.layer?.masksToBounds = true

        contentView?.addSubview(vibrancyView, positioned: .below, relativeTo: nil)
        contentView?.wantsLayer = true
        contentView?.layer?.cornerRadius = 12
        contentView?.layer?.masksToBounds = true

        centerOnScreen()
    }

    override var canBecomeKey: Bool { true }

    func centerOnScreen() {
        guard let screen = NSScreen.main else { return }
        let screenFrame = screen.frame
        let x = screenFrame.midX - frame.width / 2
        // Position roughly 1/3 from top, like Spotlight
        let y = screenFrame.minY + screenFrame.height * 0.65
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
