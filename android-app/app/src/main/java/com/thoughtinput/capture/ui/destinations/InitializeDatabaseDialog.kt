package com.thoughtinput.capture.ui.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thoughtinput.capture.data.destinations.SupabaseAdmin
import kotlinx.coroutines.launch

@Composable
fun InitializeDatabaseDialog(
    projectURL: String,
    tableName: String,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    val admin = remember { SupabaseAdmin() }
    val parsedRef = remember(projectURL) { admin.projectRef(projectURL) }
    val tableValid = remember(tableName) { admin.validateTableName(tableName) }

    var manualRef by remember { mutableStateOf("") }
    val effectiveRef = parsedRef ?: manualRef.takeIf { it.isNotBlank() }

    var pat by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    var pendingDropConfirm by remember { mutableStateOf<DropConfirm?>(null) }

    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Initialize Database") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!tableValid) {
                    Text(
                        "Invalid table name. Must start with a letter or underscore and contain only letters, digits, underscores (≤63 chars).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Target table: $tableName" +
                            (effectiveRef?.let { "  •  Project ref: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (parsedRef == null) {
                    OutlinedTextField(
                        value = manualRef,
                        onValueChange = { manualRef = it },
                        label = { Text("Project ref") },
                        placeholder = { Text("subdomain of your project URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Text("Run from this app", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Paste a Supabase Personal Access Token. Used once and discarded — never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text("Personal Access Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        uriHandler.openUri("https://supabase.com/dashboard/account/tokens")
                    }) { Text("Where do I get one?") }

                    Button(
                        enabled = !working && tableValid && pat.isNotBlank() && effectiveRef != null,
                        onClick = {
                            val ref = effectiveRef ?: return@Button
                            working = true
                            status = null
                            scope.launch {
                                runApiPath(
                                    admin = admin,
                                    ref = ref,
                                    pat = pat,
                                    table = tableName,
                                    onConfirm = { rows ->
                                    working = false
                                    pendingDropConfirm = DropConfirm(rows = rows)
                                },
                                    onResult = { msg, err ->
                                        working = false
                                        status = msg
                                        statusIsError = err
                                        if (!err) {
                                            pat = ""
                                            onCompleted()
                                        }
                                    }
                                )
                            }
                        }
                    ) { Text("Run via API") }
                }

                HorizontalDivider()

                Text("Run it yourself", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Copy the SQL and run it in the Supabase SQL Editor. No PAT required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = tableValid,
                        onClick = {
                            clipboard.setText(AnnotatedString(admin.sqlFor(tableName, drop = false)))
                            status = "SQL copied to clipboard."
                            statusIsError = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy SQL") }
                    OutlinedButton(
                        enabled = tableValid,
                        onClick = {
                            clipboard.setText(AnnotatedString(admin.sqlFor(tableName, drop = true)))
                            status = "Drop+Recreate SQL copied to clipboard."
                            statusIsError = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copy (Drop+Recreate)") }
                }
                if (effectiveRef != null) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri(admin.sqlEditorURL(effectiveRef)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open SQL Editor") }
                }

                if (working) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        Text("Working…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )

    pendingDropConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingDropConfirm = null; working = false; status = "Cancelled."; statusIsError = false },
            title = { Text("Drop and recreate '$tableName'?") },
            text = {
                Text(
                    if (confirm.rows > 0)
                        "Table has ${confirm.rows} row${if (confirm.rows == 1L) "" else "s"}. Continuing will drop the table and recreate it. Data will be lost."
                    else
                        "Table is empty. Continuing will drop the table and recreate it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ref = effectiveRef ?: return@TextButton
                    pendingDropConfirm = null
                    working = true
                    status = null
                    scope.launch {
                        try {
                            admin.initialize(ref, pat, tableName, drop = true)
                            working = false
                            status = "Recreated."
                            statusIsError = false
                            pat = ""
                            onCompleted()
                        } catch (e: Exception) {
                            working = false
                            status = e.message ?: "Failed."
                            statusIsError = true
                        }
                    }
                }) { Text("Drop and Recreate") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDropConfirm = null
                    status = "Cancelled."
                    statusIsError = false
                }) { Text("Cancel") }
            }
        )
    }
}

private data class DropConfirm(val rows: Long)

private suspend fun runApiPath(
    admin: SupabaseAdmin,
    ref: String,
    pat: String,
    table: String,
    onConfirm: (Long) -> Unit,
    onResult: (message: String, isError: Boolean) -> Unit
) {
    try {
        val exists = admin.tableExists(ref, pat, table)
        if (exists) {
            val rows = runCatching { admin.rowCount(ref, pat, table) }.getOrDefault(0L)
            onConfirm(rows)
            // Result will be reported by the confirm dialog flow.
        } else {
            admin.initialize(ref, pat, table, drop = false)
            onResult("Initialized.", false)
        }
    } catch (e: Exception) {
        onResult(e.message ?: "Failed.", true)
    }
}
