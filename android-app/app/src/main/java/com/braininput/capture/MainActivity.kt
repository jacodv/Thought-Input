package com.braininput.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.braininput.capture.data.CaptureRepository
import com.braininput.capture.ui.SettingsScreen
import com.braininput.capture.ui.theme.BrainInputTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: CaptureRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CaptureRepository(applicationContext)

        setContent {
            BrainInputTheme {
                SettingsScreen(
                    currentEndpoint = repository.apiEndpoint,
                    onEndpointChanged = { repository.setApiEndpoint(it) }
                )
            }
        }
    }
}
