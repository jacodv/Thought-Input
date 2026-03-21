import SwiftUI

struct CaptureView: View {
    @StateObject private var captureService = CaptureService()
    @StateObject private var speechRecognizer = SpeechRecognizer()
    @State private var text = ""
    @State private var showSuccess = false
    @FocusState private var isTextFieldFocused: Bool

    var onDismiss: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                TextField("Capture a thought...", text: $text)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16))
                    .focused($isTextFieldFocused)
                    .onSubmit {
                        submitCapture()
                    }

                Button(action: toggleVoice) {
                    Image(systemName: speechRecognizer.isRecording ? "mic.fill" : "mic")
                        .foregroundColor(speechRecognizer.isRecording ? .red : .secondary)
                        .font(.system(size: 16))
                }
                .buttonStyle(.plain)
                .help(speechRecognizer.isRecording ? "Stop dictation" : "Start dictation")
                .disabled(!speechRecognizer.isAvailable)

                Button(action: submitCapture) {
                    Image(systemName: "arrow.up.circle.fill")
                        .foregroundColor(text.isEmpty ? .secondary : .accentColor)
                        .font(.system(size: 20))
                }
                .buttonStyle(.plain)
                .disabled(text.isEmpty || captureService.isSending)
                .help("Submit (Enter)")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if captureService.isSending {
                ProgressView()
                    .scaleEffect(0.6)
                    .frame(height: 8)
            } else if showSuccess {
                Label("Captured", systemImage: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundColor(.green)
                    .frame(height: 8)
            } else if let error = captureService.lastError {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundColor(.orange)
                    .frame(height: 8)
            }
        }
        .padding(8)
        .frame(width: 400, height: 100)
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
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        let method: CapturePayload.CaptureMethod = speechRecognizer.transcript.isEmpty ? .typed : .voice
        let capturedText = text

        speechRecognizer.stopRecording()

        Task {
            let success = await captureService.submit(text: capturedText, method: method)
            if success {
                showSuccess = true
                text = ""
                // Brief pause to show success, then dismiss
                try? await Task.sleep(for: .milliseconds(400))
                dismiss()
            }
        }
    }

    private func dismiss() {
        speechRecognizer.stopRecording()
        text = ""
        showSuccess = false
        onDismiss?()
    }
}
