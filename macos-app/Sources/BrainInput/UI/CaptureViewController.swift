import Cocoa
import SwiftUI

final class CaptureViewController: NSHostingController<CaptureView> {
    var onDismiss: (() -> Void)?

    convenience init(onDismiss: @escaping () -> Void) {
        var captureView = CaptureView()
        captureView.onDismiss = onDismiss
        self.init(rootView: captureView)
        self.onDismiss = onDismiss
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        // Transparent background so the NSVisualEffectView vibrancy shows through
        view.wantsLayer = true
        view.layer?.backgroundColor = .clear
    }
}
