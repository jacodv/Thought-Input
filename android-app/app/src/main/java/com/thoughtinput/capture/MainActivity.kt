package com.thoughtinput.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.thoughtinput.capture.data.CaptureRepository
import com.thoughtinput.capture.ui.SettingsScreen
import com.thoughtinput.capture.ui.theme.ThoughtInputTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: CaptureRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CaptureRepository(applicationContext)

        setContent {
            ThoughtInputTheme {
                SettingsScreen(
                    currentEndpoint = repository.apiEndpoint,
                    onEndpointChanged = { repository.setApiEndpoint(it) }
                )
            }
        }
    }
}
