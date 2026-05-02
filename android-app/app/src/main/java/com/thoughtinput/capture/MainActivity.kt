package com.thoughtinput.capture

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thoughtinput.capture.data.ApiClient
import com.thoughtinput.capture.data.SettingsBackupException
import com.thoughtinput.capture.data.SettingsBackupService
import com.thoughtinput.capture.data.destinations.DestinationSender
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.EncryptedKeystore
import com.thoughtinput.capture.data.destinations.OAuthTokenManager
import com.thoughtinput.capture.ui.destinations.DestinationEditorScreen
import com.thoughtinput.capture.ui.destinations.DestinationListScreen
import com.thoughtinput.capture.ui.settings.SettingsScreen
import com.thoughtinput.capture.ui.theme.ThoughtInputTheme
import com.thoughtinput.capture.util.CaptureLog
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DestinationStore.get(applicationContext)
        val keystore = EncryptedKeystore(applicationContext)
        val apiClient = ApiClient()
        val tokenManager = OAuthTokenManager(keystore, apiClient)
        val sender = DestinationSender(keystore, tokenManager, apiClient)
        val backupService = SettingsBackupService(store, keystore)

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
                            },
                            onSettings = { nav.navigate("settings") }
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
                    composable("settings") {
                        SettingsHost(
                            backupService = backupService,
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHost(
    backupService: SettingsBackupService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = backupService.export()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: throw IllegalStateException("Couldn't open the destination file for writing.")
            Toast.makeText(context, "Settings exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            CaptureLog.error("SettingsBackup", "Export failed: ${e.message}", e)
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingImportUri = uri
    }

    SettingsScreen(
        onBack = onBack,
        onExport = { exportLauncher.launch("thought-input-settings.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) }
    )

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Replace all destinations?") },
            text = {
                Text("Importing will delete every existing destination and its stored secrets, then load the contents of the selected file. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        } ?: throw IllegalStateException("Couldn't read the selected file.")
                        backupService.import(text)
                        Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
                    } catch (e: SettingsBackupException) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        CaptureLog.error("SettingsBackup", "Import failed: ${e.message}", e)
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }
}
