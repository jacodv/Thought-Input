import Foundation
import Speech
import AVFoundation

@MainActor
final class SpeechRecognizer: ObservableObject {
    @Published var transcript = ""
    @Published var isRecording = false
    @Published var isAvailable = false

    private var recognitionTask: SFSpeechRecognitionTask?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private let audioEngine = AVAudioEngine()
    private lazy var speechRecognizer = SFSpeechRecognizer(locale: Locale.current)

    func requestAuthorization() {
        let status = SFSpeechRecognizer.authorizationStatus()
        isAvailable = (status == .authorized) && (speechRecognizer?.isAvailable ?? false)
        CaptureLog.speech.info("Speech authorization status: \(String(describing: status))")
    }

    func startRecording() {
        guard !isRecording, let speechRecognizer, speechRecognizer.isAvailable else {
            CaptureLog.speech.warning("Speech recognizer unavailable")
            return
        }

        stopRecording()

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.recognitionRequest = request

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            isRecording = true
            CaptureLog.speech.info("Recording started")
        } catch {
            CaptureLog.speech.error("Audio engine failed to start: \(error.localizedDescription)")
            return
        }

        recognitionTask = speechRecognizer.recognitionTask(with: request) { result, error in
            DispatchQueue.main.async { [weak self] in
                if let result {
                    self?.transcript = result.bestTranscription.formattedString
                }
                if error != nil || (result?.isFinal ?? false) {
                    self?.stopRecording()
                }
            }
        }
    }

    func stopRecording() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil
        isRecording = false
        CaptureLog.speech.info("Recording stopped")
    }
}
