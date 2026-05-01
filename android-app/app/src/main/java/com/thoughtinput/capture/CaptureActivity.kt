package com.thoughtinput.capture

import android.Manifest
import android.content.Intent
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
import androidx.core.view.WindowCompat
import com.thoughtinput.capture.data.CapturePayload
import com.thoughtinput.capture.data.CaptureRepository
import com.thoughtinput.capture.data.SubmitResult
import com.thoughtinput.capture.speech.SpeechRecognizerManager
import com.thoughtinput.capture.ui.CaptureScreen
import com.thoughtinput.capture.ui.theme.ThoughtInputTheme
import com.thoughtinput.capture.util.CaptureLog
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        repository = CaptureRepository(applicationContext)
        speechManager = SpeechRecognizerManager(this)

        val startWithVoice = intent.getBooleanExtra("start_voice", false)
        CaptureLog.ui("CaptureActivity created (voice=$startWithVoice)")

        setContent {
            ThoughtInputTheme {
                var isSending by remember { mutableStateOf(false) }
                var showSuccess by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var requireSetup by remember { mutableStateOf(false) }
                val isRecording by speechManager.isRecording.collectAsState()
                val transcript by speechManager.transcript.collectAsState()
                val scope = rememberCoroutineScope()

                CaptureScreen(
                    isSending = isSending,
                    showSuccess = showSuccess,
                    errorMessage = errorMessage,
                    requireSetup = requireSetup,
                    isRecording = isRecording,
                    speechAvailable = speechManager.isAvailable,
                    speechTranscript = transcript,
                    onSubmit = { text ->
                        scope.launch {
                            isSending = true
                            errorMessage = null
                            requireSetup = false
                            val method = if (transcript.isNotEmpty())
                                CapturePayload.CaptureMethod.VOICE
                            else
                                CapturePayload.CaptureMethod.TYPED

                            speechManager.stopListening()
                            when (val result = repository.submit(text, method)) {
                                is SubmitResult.Success -> {
                                    showSuccess = true
                                    delay(350)
                                    finish()
                                }
                                is SubmitResult.NoDestination -> {
                                    isSending = false
                                    requireSetup = true
                                    errorMessage = "Set up a destination in Settings"
                                }
                                is SubmitResult.QueuedOffline -> {
                                    isSending = false
                                    errorMessage = "Saved offline – ${result.reason}"
                                    delay(1200)
                                    finish()
                                }
                                is SubmitResult.Failed -> {
                                    isSending = false
                                    errorMessage = result.reason
                                    delay(1500)
                                    finish()
                                }
                            }
                        }
                    },
                    onToggleVoice = {
                        if (isRecording) speechManager.stopListening() else requestMicAndListen()
                    },
                    onOpenSettings = ::openSettings,
                    onDismiss = { finish() }
                )
            }
        }

        if (startWithVoice) requestMicAndListen()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
