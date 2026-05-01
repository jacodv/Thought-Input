package com.thoughtinput.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thoughtinput.capture.data.ApiClient
import com.thoughtinput.capture.data.destinations.DestinationSender
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.EncryptedKeystore
import com.thoughtinput.capture.data.destinations.OAuthTokenManager
import com.thoughtinput.capture.ui.destinations.DestinationEditorScreen
import com.thoughtinput.capture.ui.destinations.DestinationListScreen
import com.thoughtinput.capture.ui.theme.ThoughtInputTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DestinationStore.get(applicationContext)
        val keystore = EncryptedKeystore(applicationContext)
        val apiClient = ApiClient()
        val tokenManager = OAuthTokenManager(keystore, apiClient)
        val sender = DestinationSender(keystore, tokenManager, apiClient)

        setContent {
            ThoughtInputTheme {
                val nav = rememberNavController()
                val destinations by store.destinations.collectAsState()

                NavHost(navController = nav, startDestination = "list") {
                    composable("list") {
                        DestinationListScreen(
                            store = store,
                            onAdd = { nav.navigate("editor/new") },
                            onEdit = { destination ->
                                nav.navigate("editor/${destination.id}")
                            }
                        )
                    }
                    composable("editor/{id}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id")
                        val existing = if (id == null || id == "new") null else
                            runCatching { UUID.fromString(id) }.getOrNull()
                                ?.let { uuid -> destinations.firstOrNull { it.id == uuid } }
                        DestinationEditorScreen(
                            store = store,
                            keystore = keystore,
                            tokenManager = tokenManager,
                            sender = sender,
                            existing = existing,
                            onDone = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
