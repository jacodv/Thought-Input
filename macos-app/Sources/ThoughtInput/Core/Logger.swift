import Foundation
import os

enum CaptureLog {
    private static let subsystem = "com.thoughtinput.capture"

    static let ui = os.Logger(subsystem: subsystem, category: "ui")
    static let network = os.Logger(subsystem: subsystem, category: "network")
    static let speech = os.Logger(subsystem: subsystem, category: "speech")
    static let shortcut = os.Logger(subsystem: subsystem, category: "shortcut")
    static let store = os.Logger(subsystem: subsystem, category: "store")
    static let auth = os.Logger(subsystem: subsystem, category: "auth")

    /// True when launched with `--debug` argument or `THOUGHT_INPUT_DEBUG=1` env var.
    /// When enabled, all log calls also print to stdout for easy console capture.
    static let isDebug: Bool = {
        ProcessInfo.processInfo.arguments.contains("--debug")
            || ProcessInfo.processInfo.environment["THOUGHT_INPUT_DEBUG"] == "1"
    }()

    /// Prints to stdout only when debug mode is active.
    /// Usage: `CaptureLog.debug("ui", "Settings menu tapped")`
    static func debug(_ category: String, _ message: String, file: String = #fileID, line: Int = #line) {
        guard isDebug else { return }
        let timestamp = ISO8601DateFormatter().string(from: Date())
        print("[TI-DEBUG] [\(timestamp)] [\(category)] \(file):\(line) — \(message)")
    }

    /// Always prints to stdout regardless of debug flag.
    /// Use sparingly — only for startup banner and critical errors.
    static func always(_ message: String) {
        print("[ThoughtInput] \(message)")
    }
}
