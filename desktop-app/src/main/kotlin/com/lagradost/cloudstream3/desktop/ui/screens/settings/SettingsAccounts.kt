package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthData
import kotlinx.coroutines.launch

@Composable
fun SettingsAccounts() {
    val scope = rememberCoroutineScope()
    var selectedApiForLogin by remember { mutableStateOf<AuthAPI?>(null) }
    val cachedAccounts by AccountManager.accountsFlow.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroupCard(title = "Accounts & Integrations") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountManager.allApis.forEach { api ->
                    val accounts = cachedAccounts[api.idPrefix] ?: emptyArray()
                    val currentAccount = accounts.firstOrNull()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(api.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            val isApiKeyOnly = api.inAppLoginRequirement?.let { it.apiKey && !it.username && !it.password && !it.email && !it.server } == true

                            if (currentAccount != null) {
                                if (isApiKeyOnly) {
                                    Text("API Key Active", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                } else {
                                    Text("Logged in as ${currentAccount.user.name}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            } else {
                                if (isApiKeyOnly) {
                                    Text("No API Key", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                } else {
                                    Text("Not logged in", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            }
                        }

                        if (currentAccount != null) {
                            val isApiKeyOnly = api.inAppLoginRequirement?.let { it.apiKey && !it.username && !it.password && !it.email && !it.server } == true
                            Button(
                                onClick = {
                                    AccountManager.updateAccounts(api.idPrefix, emptyArray())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(if (isApiKeyOnly) "Remove Key" else "Logout")
                            }
                        } else if (api.requiresLogin) {
                            val isApiKeyOnly = api.inAppLoginRequirement?.let { it.apiKey && !it.username && !it.password && !it.email && !it.server } == true
                            Button(
                                onClick = {
                                    if (api.hasInApp) {
                                        selectedApiForLogin = api
                                    } else {
                                        com.lagradost.common.logging.AppLogger.w("${api.name} login not supported on Desktop yet (missing hasInApp)")
                                    }
                                },
                            ) {
                                Text(if (api.hasInApp) (if (isApiKeyOnly) "Add Key" else "Login") else "Not Supported")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                // TMDB Custom API Key
                var showTmdbDialog by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("The Movie Database (TMDB)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("If TMDB stops working in the future, this is an optional key in case the default key fails or gets rate limited.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { showTmdbDialog = true }) {
                        Text("Configure Key")
                    }
                }

                if (showTmdbDialog) {
                    var tmdbApiKey by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<String>("tmdb_api_key") ?: "") }
                    CloudstreamAlertDialog(
                        show = showTmdbDialog,
                        onDismissRequest = { showTmdbDialog = false },
                        title = { Text("TMDB API Key") },
                        text = {
                            Column {
                                Text("Enter your custom V3 API Key below:", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(
                                    value = tmdbApiKey,
                                    onValueChange = { tmdbApiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Leave blank to use default key") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.lagradost.common.storage.DesktopDataStore.setKey("tmdb_api_key", tmdbApiKey)
                                }
                                showTmdbDialog = false
                            }) {
                                Text("Save Key")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTmdbDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }

    if (selectedApiForLogin != null) {
        InAppLoginDialog(
            api = selectedApiForLogin!!,
            onDismiss = { selectedApiForLogin = null },
            onSuccess = { authData ->
                AccountManager.updateAccounts(selectedApiForLogin!!.idPrefix, arrayOf(authData))
                selectedApiForLogin = null
            },
        )
    }
}

@Composable
fun InAppLoginDialog(api: AuthAPI, onDismiss: () -> Unit, onSuccess: (AuthData) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var apiKeyStr by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val req = api.inAppLoginRequirement
    val isApiKeyOnly = req != null && req.apiKey && !req.username && !req.password && !req.email && !req.server

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(24.dp).width(400.dp)) {
            Text(if (isApiKeyOnly) "Enter API Key for ${api.name}" else "Login to ${api.name}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            if (req?.username == true) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.email == true) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.password == true) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.server == true) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (req?.apiKey == true) {
                OutlinedTextField(
                    value = apiKeyStr,
                    onValueChange = { apiKeyStr = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val authData = AuthData(
                        user = com.lagradost.cloudstream3.syncproviders.AuthUser(name = if (username.isNotBlank()) username else "User", id = 0, profilePicture = ""),
                        token = com.lagradost.cloudstream3.syncproviders.AuthToken(accessToken = apiKeyStr.ifBlank { "dummy_token" }),
                    )
                    onSuccess(authData)
                }) { Text(if (isApiKeyOnly) "Save Key" else "Login") }
            }
        }
    }
}
