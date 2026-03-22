import Cocoa
import Carbon.HIToolbox

final class GlobalShortcutManager {
    static let shared = GlobalShortcutManager()

    private var eventTap: CFMachPort?
    private var runLoopSource: CFRunLoopSource?
    var onTrigger: (() -> Void)?

    // Default: Cmd+Shift+Space
    var modifierFlags: CGEventFlags = [.maskCommand, .maskShift]
    var keyCode: CGKeyCode = CGKeyCode(kVK_Space)

    private init() {}

    func register() {
        let eventMask: CGEventMask = (1 << CGEventType.keyDown.rawValue)

        let callback: CGEventTapCallBack = { _, _, event, refcon in
            guard let refcon else { return Unmanaged.passRetained(event) }
            let manager = Unmanaged<GlobalShortcutManager>.fromOpaque(refcon).takeUnretainedValue()

            if event.getIntegerValueField(.keyboardEventKeycode) == Int64(manager.keyCode) {
                let flags = event.flags
                let required = manager.modifierFlags
                if flags.contains(required) {
                    Task { @MainActor in
                        manager.onTrigger?()
                    }
                    return nil // Consume the event
                }
            }
            return Unmanaged.passRetained(event)
        }

        let refcon = Unmanaged.passUnretained(self).toOpaque()
        guard let tap = CGEvent.tapCreate(
            tap: .cgSessionEventTap,
            place: .headInsertEventTap,
            options: .defaultTap,
            eventsOfInterest: eventMask,
            callback: callback,
            userInfo: refcon
        ) else {
            CaptureLog.shortcut.error("Failed to create event tap. Check Accessibility permissions.")
            return
        }

        eventTap = tap
        runLoopSource = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)
        CFRunLoopAddSource(CFRunLoopGetMain(), runLoopSource, .commonModes)
        CGEvent.tapEnable(tap: tap, enable: true)

        CaptureLog.shortcut.info("Global shortcut registered")
    }

    func unregister() {
        if let tap = eventTap {
            CGEvent.tapEnable(tap: tap, enable: false)
        }
        if let source = runLoopSource {
            CFRunLoopRemoveSource(CFRunLoopGetMain(), source, .commonModes)
        }
        eventTap = nil
        runLoopSource = nil
        CaptureLog.shortcut.info("Global shortcut unregistered")
    }
}
