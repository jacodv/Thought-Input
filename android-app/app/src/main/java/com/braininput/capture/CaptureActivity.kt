package com.braininput.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.braininput.capture.data.CapturePayload
import com.braininput.capture.data.CaptureRepository
import com.braininput.capture.speech.SpeechRecognizerManager
import com.braininput.capture.ui.CaptureScreen
import com.braininput.capture.ui.theme.BrainInputTheme
import com.braininput.capture.util.CaptureLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CaptureActivity : ComponentActivity() {

    private lateinit var repository: CaptureRepository
    private lateinit var speechManager: SpeechRecognizerManager

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechManager.startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show keyboard immediately
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        repository = CaptureRepository(applicationContext)
        speechManager = SpeechRecognizerManager(this)

        CaptureLog.ui("CaptureActivity created")

        setContent {
            BrainInputTheme {
                var isSending by remember { mutableStateOf(false) }
                var showSuccess by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                val isRecording by speechManager.isRecording.collectAsState()
                val transcript by speechManager.transcript.collectAsState()
                val scope = rememberCoroutineScope()

                CaptureScreen(
                    isSending = isSending,
                    showSuccess = showSuccess,
                    errorMessage = errorMessage,
                    isRecording = isRecording,
                    speechAvailable = speechManager.isAvailable,
                    speechTranscript = transcript,
                    onSubmit = { text ->
                        scope.launch {
                            isSending = true
                            errorMessage = null
                            val method = if (transcript.isNotEmpty())
                                CapturePayload.CaptureMethod.VOICE
                            else
                                CapturePayload.CaptureMethod.TYPED

                            speechManager.stopListening()
                            val success = repository.submit(text, method)

                            if (success) {
                                showSuccess = true
                                delay(400)
                                finish()
                            } else {
                                isSending = false
                                errorMessage = "Saved offline — will retry"
                                delay(1500)
                                finish()
                            }
                        }
                    },
                    onToggleVoice = {
                        if (isRecording) {
                            speechManager.stopListening()
                        } else {
                            requestMicAndListen()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun requestMicAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            speechManager.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
    }
}
