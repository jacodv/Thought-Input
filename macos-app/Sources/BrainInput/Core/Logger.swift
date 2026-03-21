import Foundation
import os

enum CaptureLog {
    private static let subsystem = "com.braininput.capture"

    static let ui = os.Logger(subsystem: subsystem, category: "ui")
    static let network = os.Logger(subsystem: subsystem, category: "network")
    static let speech = os.Logger(subsystem: subsystem, category: "speech")
    static let shortcut = os.Logger(subsystem: subsystem, category: "shortcut")
    static let store = os.Logger(subsystem: subsystem, category: "store")
}
