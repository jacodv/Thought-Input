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
}
