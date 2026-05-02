package com.thoughtinput.capture.ui.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thoughtinput.capture.data.destinations.Destination
import com.thoughtinput.capture.data.destinations.DestinationSender
import com.thoughtinput.capture.data.destinations.DestinationStore
import com.thoughtinput.capture.data.destinations.DestinationType
import com.thoughtinput.capture.data.destinations.KeychainRef
import com.thoughtinput.capture.data.destinations.OAuthTokenManager
import com.thoughtinput.capture.data.destinations.SecretsKeystore
import kotlinx.coroutines.launch

private enum class TypeChoice(val label: String) {
    SUPABASE("Supabase"),
    REST_NO_AUTH("REST (No Auth)"),
    REST_API_KEY("REST (API Key)"),
    OAUTH_PASSWORD("REST (OAuth Password)"),
    OAUTH_CLIENT("REST (OAuth Client Credentials)")
}

private sealed class TestState {
    data object Idle : TestState()
    data object Running : TestState()
    data object Success : TestState()
    data object TableMissing : TestState()
    data class Failure(val message: String) : TestState()

    val isConnected: Boolean
        get() = this is Success || this is TableMissing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationEditorScreen(
    store: DestinationStore,
    keystore: SecretsKeystore,
    tokenManager: OAuthTokenManager,
    sender: DestinationSender,
    existing: Destination?,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var typeChoice by remember { mutableStateOf(typeChoiceFor(existing?.type) ?: TypeChoice.SUPABASE) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    // Supabase
    var supabaseProjectURL by remember { mutableStateOf("") }
    var supabaseTableName by remember { mutableStateOf("") }
    var supabaseAPIKey by remember { mutableStateOf("") }

    // REST No Auth
    var restNoAuthURL by remember { mutableStateOf("") }

    // REST API Key
    var restApiKeyURL by remember { mutableStateOf("") }
    var restApiKeyHeader by remember { mutableStateOf("X-API-Key") }
    var restApiKeyValue by remember { mutableStateOf("") }

    // OAuth Password
    var oauthPwEndpoint by remember { mutableStateOf("") }
    var oauthPwTokenURL by remember { mutableStateOf("") }
    var oauthPwUsername by remember { mutableStateOf("") }
    var oauthPwPassword by remember { mutableStateOf("") }

    // OAuth Client
    var oauthClientEndpoint by remember { mutableStateOf("") }
    var oauthClientTokenURL by remember { mutableStateOf("") }
    var oauthClientID by remember { mutableStateOf("") }
    var oauthClientSecret by remember { mutableStateOf("") }

    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    var saving by remember { mutableStateOf(false) }
    var showingInitDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(existing?.id) {
        existing?.let { hydrate(it, keystore,
            setProj = { supabaseProjectURL = it },
            setTable = { supabaseTableName = it },
            setSupKey = { supabaseAPIKey = it },
            setNoAuthUrl = { restNoAuthURL = it },
            setApiUrl = { restApiKeyURL = it },
            setApiHeader = { restApiKeyHeader = it },
            setApiKey = { restApiKeyValue = it },
            setPwEndpoint = { oauthPwEndpoint = it },
            setPwTokenURL = { oauthPwTokenURL = it },
            setPwUser = { oauthPwUsername = it },
            setPwPassword = { oauthPwPassword = it },
            setClientEndpoint = { oauthClientEndpoint = it },
            setClientTokenURL = { oauthClientTokenURL = it },
            setClientID = { oauthClientID = it },
            setClientSecret = { oauthClientSecret = it }
        ) }
    }

    val isValid = isValid(
        name, typeChoice,
        supabaseProjectURL, supabaseTableName, supabaseAPIKey,
        restNoAuthURL,
        restApiKeyURL, restApiKeyHeader, restApiKeyValue,
        oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
        oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New destination" else "Edit destination") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = {
                            store.delete(existing)
                            onDone()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = typeChoice.label,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Type") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { typeMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    TypeChoice.entries.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = {
                                typeChoice = choice
                                typeMenuExpanded = false
                                testState = TestState.Idle
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            when (typeChoice) {
                TypeChoice.SUPABASE -> SupabaseFields(
                    supabaseProjectURL, { supabaseProjectURL = it },
                    supabaseTableName, { supabaseTableName = it },
                    supabaseAPIKey, { supabaseAPIKey = it }
                )
                TypeChoice.REST_NO_AUTH -> RestNoAuthFields(
                    restNoAuthURL, { restNoAuthURL = it }
                )
                TypeChoice.REST_API_KEY -> RestApiKeyFields(
                    restApiKeyURL, { restApiKeyURL = it },
                    restApiKeyHeader, { restApiKeyHeader = it },
                    restApiKeyValue, { restApiKeyValue = it }
                )
                TypeChoice.OAUTH_PASSWORD -> OAuthPasswordFields(
                    oauthPwEndpoint, { oauthPwEndpoint = it },
                    oauthPwTokenURL, { oauthPwTokenURL = it },
                    oauthPwUsername, { oauthPwUsername = it },
                    oauthPwPassword, { oauthPwPassword = it }
                )
                TypeChoice.OAUTH_CLIENT -> OAuthClientFields(
                    oauthClientEndpoint, { oauthClientEndpoint = it },
                    oauthClientTokenURL, { oauthClientTokenURL = it },
                    oauthClientID, { oauthClientID = it },
                    oauthClientSecret, { oauthClientSecret = it }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = isValid && testState !is TestState.Running,
                    onClick = {
                        testState = TestState.Running
                        scope.launch {
                            val result = runTest(
                                existing, typeChoice, name, keystore, sender,
                                supabaseProjectURL, supabaseTableName, supabaseAPIKey,
                                restNoAuthURL,
                                restApiKeyURL, restApiKeyHeader, restApiKeyValue,
                                oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
                                oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret,
                                tokenManager
                            )
                            testState = result
                        }
                    }
                ) { Text("Test connection") }

                when (val s = testState) {
                    is TestState.Idle -> Unit
                    is TestState.Running -> CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp), strokeWidth = 2.dp)
                    is TestState.Success -> AssistChip(
                        onClick = {},
                        label = { Text("Connected") },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                    is TestState.TableMissing -> AssistChip(
                        onClick = {},
                        label = { Text("Connected — table missing") },
                        leadingIcon = { Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.tertiary) }
                    )
                    is TestState.Failure -> AssistChip(
                        onClick = {},
                        label = { Text(s.message, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            if (typeChoice == TypeChoice.SUPABASE && testState.isConnected) {
                OutlinedButton(
                    onClick = { showingInitDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Initialize Database…") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDone) { Text("Cancel") }
                Button(
                    enabled = isValid && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            commit(
                                store, keystore, tokenManager, existing, name, typeChoice,
                                supabaseProjectURL, supabaseTableName, supabaseAPIKey,
                                restNoAuthURL,
                                restApiKeyURL, restApiKeyHeader, restApiKeyValue,
                                oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
                                oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret
                            )
                            saving = false
                            onDone()
                        }
                    }
                ) { Text(if (existing == null) "Add" else "Save") }
            }
        }
    }

    if (showingInitDialog) {
        InitializeDatabaseDialog(
            projectURL = supabaseProjectURL,
            tableName = supabaseTableName,
            onDismiss = { showingInitDialog = false },
            onCompleted = {
                showingInitDialog = false
                testState = TestState.Running
                scope.launch {
                    val result = runTest(
                        existing, typeChoice, name, keystore, sender,
                        supabaseProjectURL, supabaseTableName, supabaseAPIKey,
                        restNoAuthURL,
                        restApiKeyURL, restApiKeyHeader, restApiKeyValue,
                        oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
                        oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret,
                        tokenManager
                    )
                    testState = result
                }
            }
        )
    }
}

// ===== Field groups =====

@Composable
private fun SupabaseFields(
    projectURL: String, onProjectURL: (String) -> Unit,
    tableName: String, onTableName: (String) -> Unit,
    apiKey: String, onAPIKey: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = projectURL, onValueChange = onProjectURL,
            label = { Text("Project URL") },
            placeholder = { Text("https://xxx.supabase.co") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tableName, onValueChange = onTableName,
            label = { Text("Table name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = onAPIKey,
            label = { Text("API key (anon or service-role)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
        HelperText("Sends POST {projectURL}/rest/v1/{table} with apikey + Authorization headers.")
    }
}

@Composable
private fun RestNoAuthFields(url: String, onUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = url, onValueChange = onUrl,
            label = { Text("Endpoint URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth())
        HelperText("POST request with JSON payload, no authentication.")
    }
}

@Composable
private fun RestApiKeyFields(
    url: String, onUrl: (String) -> Unit,
    header: String, onHeader: (String) -> Unit,
    apiKey: String, onAPIKey: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = url, onValueChange = onUrl,
            label = { Text("Endpoint URL") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = header, onValueChange = onHeader,
            label = { Text("Header name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = onAPIKey,
            label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OAuthPasswordFields(
    endpoint: String, onEndpoint: (String) -> Unit,
    tokenURL: String, onTokenURL: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = endpoint, onValueChange = onEndpoint,
            label = { Text("Endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tokenURL, onValueChange = onTokenURL,
            label = { Text("Token URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = onUsername,
            label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = onPassword,
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
        HelperText("Exchanges username/password for an access token automatically.")
    }
}

@Composable
private fun OAuthClientFields(
    endpoint: String, onEndpoint: (String) -> Unit,
    tokenURL: String, onTokenURL: (String) -> Unit,
    clientID: String, onClientID: (String) -> Unit,
    clientSecret: String, onClientSecret: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = endpoint, onValueChange = onEndpoint,
            label = { Text("Endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tokenURL, onValueChange = onTokenURL,
            label = { Text("Token URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = clientID, onValueChange = onClientID,
            label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = clientSecret, onValueChange = onClientSecret,
            label = { Text("Client secret") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HelperText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ===== Validation =====

private fun isValid(
    name: String, type: TypeChoice,
    supabaseProjectURL: String, supabaseTableName: String, supabaseAPIKey: String,
    restNoAuthURL: String,
    restApiKeyURL: String, restApiKeyHeader: String, restApiKeyValue: String,
    oauthPwEndpoint: String, oauthPwTokenURL: String, oauthPwUsername: String, oauthPwPassword: String,
    oauthClientEndpoint: String, oauthClientTokenURL: String, oauthClientID: String, oauthClientSecret: String
): Boolean {
    if (name.isBlank()) return false
    return when (type) {
        TypeChoice.SUPABASE -> supabaseProjectURL.isNotBlank() && supabaseTableName.isNotBlank() && supabaseAPIKey.isNotBlank()
        TypeChoice.REST_NO_AUTH -> restNoAuthURL.isNotBlank()
        TypeChoice.REST_API_KEY -> restApiKeyURL.isNotBlank() && restApiKeyHeader.isNotBlank() && restApiKeyValue.isNotBlank()
        TypeChoice.OAUTH_PASSWORD -> oauthPwEndpoint.isNotBlank() && oauthPwTokenURL.isNotBlank() && oauthPwUsername.isNotBlank() && oauthPwPassword.isNotBlank()
        TypeChoice.OAUTH_CLIENT -> oauthClientEndpoint.isNotBlank() && oauthClientTokenURL.isNotBlank() && oauthClientID.isNotBlank() && oauthClientSecret.isNotBlank()
    }
}

// ===== Hydrate from existing =====

private fun typeChoiceFor(type: DestinationType?): TypeChoice? = when (type) {
    is DestinationType.Supabase -> TypeChoice.SUPABASE
    is DestinationType.RestNoAuth -> TypeChoice.REST_NO_AUTH
    is DestinationType.RestApiKey -> TypeChoice.REST_API_KEY
    is DestinationType.RestOAuthPassword -> TypeChoice.OAUTH_PASSWORD
    is DestinationType.RestOAuthClientCredentials -> TypeChoice.OAUTH_CLIENT
    null -> null
}

private fun hydrate(
    existing: Destination,
    keystore: SecretsKeystore,
    setProj: (String) -> Unit,
    setTable: (String) -> Unit,
    setSupKey: (String) -> Unit,
    setNoAuthUrl: (String) -> Unit,
    setApiUrl: (String) -> Unit,
    setApiHeader: (String) -> Unit,
    setApiKey: (String) -> Unit,
    setPwEndpoint: (String) -> Unit,
    setPwTokenURL: (String) -> Unit,
    setPwUser: (String) -> Unit,
    setPwPassword: (String) -> Unit,
    setClientEndpoint: (String) -> Unit,
    setClientTokenURL: (String) -> Unit,
    setClientID: (String) -> Unit,
    setClientSecret: (String) -> Unit
) {
    when (val t = existing.type) {
        is DestinationType.Supabase -> {
            setProj(t.projectURL); setTable(t.tableName)
            setSupKey(keystore.loadString(t.apiKeyRef).orEmpty())
        }
        is DestinationType.RestNoAuth -> setNoAuthUrl(t.endpointURL)
        is DestinationType.RestApiKey -> {
            setApiUrl(t.endpointURL); setApiHeader(t.headerName)
            setApiKey(keystore.loadString(t.apiKeyRef).orEmpty())
        }
        is DestinationType.RestOAuthPassword -> {
            setPwEndpoint(t.endpointURL); setPwTokenURL(t.tokenURL)
            setPwUser(keystore.loadString(t.usernameRef).orEmpty())
            setPwPassword(keystore.loadString(t.passwordRef).orEmpty())
        }
        is DestinationType.RestOAuthClientCredentials -> {
            setClientEndpoint(t.endpointURL); setClientTokenURL(t.tokenURL)
            setClientID(keystore.loadString(t.clientIDRef).orEmpty())
            setClientSecret(keystore.loadString(t.clientSecretRef).orEmpty())
        }
    }
}

// ===== Build / Test / Commit =====

private fun buildType(
    typeChoice: TypeChoice,
    existing: Destination?,
    keystore: SecretsKeystore,
    persistSecrets: Boolean,
    supabaseProjectURL: String, supabaseTableName: String, supabaseAPIKey: String,
    restNoAuthURL: String,
    restApiKeyURL: String, restApiKeyHeader: String, restApiKeyValue: String,
    oauthPwEndpoint: String, oauthPwTokenURL: String, oauthPwUsername: String, oauthPwPassword: String,
    oauthClientEndpoint: String, oauthClientTokenURL: String, oauthClientID: String, oauthClientSecret: String
): DestinationType {
    fun reuseOrCreate(extract: (DestinationType) -> KeychainRef?): KeychainRef =
        existing?.type?.let(extract) ?: KeychainRef.create()

    return when (typeChoice) {
        TypeChoice.SUPABASE -> {
            val ref = reuseOrCreate { (it as? DestinationType.Supabase)?.apiKeyRef }
            if (persistSecrets) keystore.save(ref, supabaseAPIKey)
            DestinationType.Supabase(supabaseProjectURL.trim(), supabaseTableName.trim(), ref)
        }
        TypeChoice.REST_NO_AUTH -> DestinationType.RestNoAuth(restNoAuthURL.trim())
        TypeChoice.REST_API_KEY -> {
            val ref = reuseOrCreate { (it as? DestinationType.RestApiKey)?.apiKeyRef }
            if (persistSecrets) keystore.save(ref, restApiKeyValue)
            DestinationType.RestApiKey(restApiKeyURL.trim(), restApiKeyHeader.trim(), ref)
        }
        TypeChoice.OAUTH_PASSWORD -> {
            val userRef = reuseOrCreate { (it as? DestinationType.RestOAuthPassword)?.usernameRef }
            val passRef = reuseOrCreate { (it as? DestinationType.RestOAuthPassword)?.passwordRef }
            if (persistSecrets) {
                keystore.save(userRef, oauthPwUsername)
                keystore.save(passRef, oauthPwPassword)
            }
            DestinationType.RestOAuthPassword(oauthPwEndpoint.trim(), oauthPwTokenURL.trim(), userRef, passRef)
        }
        TypeChoice.OAUTH_CLIENT -> {
            val idRef = reuseOrCreate { (it as? DestinationType.RestOAuthClientCredentials)?.clientIDRef }
            val secretRef = reuseOrCreate { (it as? DestinationType.RestOAuthClientCredentials)?.clientSecretRef }
            if (persistSecrets) {
                keystore.save(idRef, oauthClientID)
                keystore.save(secretRef, oauthClientSecret)
            }
            DestinationType.RestOAuthClientCredentials(oauthClientEndpoint.trim(), oauthClientTokenURL.trim(), idRef, secretRef)
        }
    }
}

private suspend fun runTest(
    existing: Destination?,
    typeChoice: TypeChoice,
    name: String,
    keystore: SecretsKeystore,
    sender: DestinationSender,
    supabaseProjectURL: String, supabaseTableName: String, supabaseAPIKey: String,
    restNoAuthURL: String,
    restApiKeyURL: String, restApiKeyHeader: String, restApiKeyValue: String,
    oauthPwEndpoint: String, oauthPwTokenURL: String, oauthPwUsername: String, oauthPwPassword: String,
    oauthClientEndpoint: String, oauthClientTokenURL: String, oauthClientID: String, oauthClientSecret: String,
    tokenManager: OAuthTokenManager
): TestState {
    val existingRefs = existing?.type?.keychainRefs?.map { it.account }?.toSet() ?: emptySet()
    val testType = buildType(
        typeChoice, existing, keystore, persistSecrets = true,
        supabaseProjectURL, supabaseTableName, supabaseAPIKey,
        restNoAuthURL,
        restApiKeyURL, restApiKeyHeader, restApiKeyValue,
        oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
        oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret
    )
    val testDestination = Destination(name = name, isActive = false, type = testType)
    val newRefs = testType.keychainRefs.filter { it.account !in existingRefs }

    return try {
        when (sender.testConnection(testDestination)) {
            DestinationSender.TestResult.Ok -> TestState.Success
            DestinationSender.TestResult.TableMissing -> TestState.TableMissing
        }
    } catch (e: Exception) {
        TestState.Failure(e.message?.take(60) ?: "Failed")
    } finally {
        // For tested but not saved, clean any keystore entries we added that weren't in the existing destination
        if (existing == null) {
            for (ref in newRefs) keystore.delete(ref)
        }
    }
}

private suspend fun commit(
    store: DestinationStore,
    keystore: SecretsKeystore,
    tokenManager: OAuthTokenManager,
    existing: Destination?,
    name: String,
    typeChoice: TypeChoice,
    supabaseProjectURL: String, supabaseTableName: String, supabaseAPIKey: String,
    restNoAuthURL: String,
    restApiKeyURL: String, restApiKeyHeader: String, restApiKeyValue: String,
    oauthPwEndpoint: String, oauthPwTokenURL: String, oauthPwUsername: String, oauthPwPassword: String,
    oauthClientEndpoint: String, oauthClientTokenURL: String, oauthClientID: String, oauthClientSecret: String
) {
    // Clean up orphaned secrets if type changed
    val newType = buildType(
        typeChoice, existing, keystore, persistSecrets = true,
        supabaseProjectURL, supabaseTableName, supabaseAPIKey,
        restNoAuthURL,
        restApiKeyURL, restApiKeyHeader, restApiKeyValue,
        oauthPwEndpoint, oauthPwTokenURL, oauthPwUsername, oauthPwPassword,
        oauthClientEndpoint, oauthClientTokenURL, oauthClientID, oauthClientSecret
    )

    if (existing != null) {
        val oldAccounts = existing.type.keychainRefs.map { it.account }.toSet()
        val newAccounts = newType.keychainRefs.map { it.account }.toSet()
        val orphaned = oldAccounts - newAccounts
        for (account in orphaned) keystore.delete(KeychainRef(account))
        if (orphaned.isNotEmpty()) tokenManager.clearToken(existing.id)
    }

    val destination = Destination(
        id = existing?.id ?: java.util.UUID.randomUUID(),
        name = name.trim(),
        isActive = existing?.isActive ?: false,
        type = newType
    )
    if (existing != null) store.update(destination) else store.add(destination)
}
