import SwiftUI

struct CaptureView: View {
    @ObservedObject private var captureService = CaptureService.shared
    @StateObject private var speechRecognizer = SpeechRecognizer()
    @State private var text = ""
    @State private var feedbackIcon: String?
    @FocusState private var isTextFieldFocused: Bool

    var onDismiss: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            // Left icon — brain/search style
            Image(systemName: feedbackIcon ?? "brain.head.profile")
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(feedbackIcon == "checkmark.circle.fill" ? .green : .secondary)
                .frame(width: 24)
                .animation(.easeInOut(duration: 0.2), value: feedbackIcon)

            // Main text field — large, like Spotlight
            TextField("Type or speak a thought...", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 22, weight: .light))
                .focused($isTextFieldFocused)
                .onSubmit {
                    submitCapture()
                }

            if captureService.isSending {
                ProgressView()
                    .scaleEffect(0.7)
                    .frame(width: 24)
            } else {
                // Mic button
                Button(action: toggleVoice) {
                    Image(systemName: speechRecognizer.isRecording ? "mic.fill" : "mic")
                        .font(.system(size: 18))
                        .foregroundColor(speechRecognizer.isRecording ? .red : .secondary)
                        .frame(width: 24)
                }
                .buttonStyle(.plain)
                .help(speechRecognizer.isRecording ? "Stop dictation" : "Start dictation")
                .disabled(!speechRecognizer.isAvailable)
            }

            // Close button
            Button(action: { dismiss() }) {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.tertiary)
                    .frame(width: 16, height: 16)
            }
            .buttonStyle(.plain)
            .help("Close")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(width: 680, height: 52)
        .contentShape(Rectangle())
        .onAppear {
            isTextFieldFocused = true
            speechRecognizer.requestAuthorization()
        }
        .onKeyPress(.escape) {
            dismiss()
            return .handled
        }
        .onChange(of: speechRecognizer.transcript) { _, newValue in
            if !newValue.isEmpty {
                text = newValue
            }
        }
    }

    private func toggleVoice() {
        if speechRecognizer.isRecording {
            speechRecognizer.stopRecording()
        } else {
            speechRecognizer.startRecording()
        }
    }

    private func submitCapture() {
        CaptureLog.debug("ui", "submitCapture called, text='\(text.prefix(20))...'")
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        let method: CapturePayload.CaptureMethod = speechRecognizer.transcript.isEmpty ? .typed : .voice
        let capturedText = text

        speechRecognizer.stopRecording()

        Task {
            let success = await captureService.submit(text: capturedText, method: method)
            if success {
                feedbackIcon = "checkmark.circle.fill"
                text = ""
                try? await Task.sleep(for: .milliseconds(350))
                dismiss()
            } else {
                feedbackIcon = "exclamationmark.triangle.fill"
                try? await Task.sleep(for: .milliseconds(800))
                feedbackIcon = nil
            }
        }
    }

    private func dismiss() {
        CaptureLog.debug("ui", "CaptureView dismiss called")
        speechRecognizer.stopRecording()
        text = ""
        feedbackIcon = nil
        onDismiss?()
    }
}
