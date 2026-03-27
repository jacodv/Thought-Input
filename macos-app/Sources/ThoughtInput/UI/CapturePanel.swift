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
        ignoresMouseEvents = false
        acceptsMouseMovedEvents = true

        // Rounded corners with vibrancy (Spotlight-like)
        if let cv = contentView {
            vibrancyView.frame = cv.bounds
        }
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
        contentView?.layer?.masksToBounds = false

        centerOnScreen()
    }

    /// Call after contentViewController is set, since that replaces the contentView
    func applyVisualStyle() {
        guard let cv = contentView else {
            CaptureLog.debug("ui", "applyVisualStyle: contentView is nil, skipping")
            return
        }
        vibrancyView.frame = cv.bounds
        cv.addSubview(vibrancyView, positioned: .below, relativeTo: nil)
        cv.wantsLayer = true
        cv.layer?.cornerRadius = 12
        cv.layer?.masksToBounds = false

        // Theme-aware border and background
        let isDark = NSApp.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        cv.layer?.borderWidth = 1
        cv.layer?.borderColor = isDark
            ? NSColor.white.withAlphaComponent(0.2).cgColor
            : NSColor.black.withAlphaComponent(0.15).cgColor
        cv.layer?.backgroundColor = isDark
            ? NSColor.black.withAlphaComponent(0.85).cgColor
            : NSColor.white.withAlphaComponent(0.85).cgColor

        CaptureLog.debug("ui", "applyVisualStyle complete (isDark=\(isDark))")
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
        CaptureLog.debug("ui", "showCapture: centering and making key")
        centerOnScreen()
        makeKeyAndOrderFront(nil)
        NSApp.activate()
        CaptureLog.ui.info("Capture panel shown")
        CaptureLog.debug("ui", "showCapture: panel frame=\(frame), isVisible=\(isVisible)")
    }

    func dismissCapture() {
        CaptureLog.debug("ui", "dismissCapture called")
        resignKey()
        orderOut(nil)
        CaptureLog.ui.info("Capture panel dismissed")
    }
}
