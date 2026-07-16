package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import kotlinx.coroutines.launch

@Composable
fun SettingsAccounts() {
    val coroutineScope = rememberCoroutineScope()
    var selectedApiForLogin by remember { mutableStateOf<AuthAPI?>(null) }
    
    // To trigger recomposition when accounts update
    var accountsUpdated by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Accounts & Providers", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(AccountManager.allApis.size) { index ->
                val api = AccountManager.allApis[index]
                // Hack to force recomposition
                accountsUpdated.hashCode() 
                val accounts = AccountManager.cachedAccounts[api.idPrefix] ?: emptyArray()
                val currentAccount = accounts.firstOrNull()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(api.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
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
                                        accountsUpdated++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
                                        // Not supported on desktop yet
                                        com.lagradost.common.logging.AppLogger.w("${api.name} login not supported on Desktop yet (missing hasInApp)")
                                    }
                                }
                            ) {
                                Text(if (api.hasInApp) (if (isApiKeyOnly) "Add Key" else "Login") else "Not Supported")
                            }
                        }
                    }
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
                accountsUpdated++
                selectedApiForLogin = null
            }
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
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val req = api.inAppLoginRequirement

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val isApiKeyOnly = req != null && req.apiKey && !req.username && !req.password && !req.email && !req.server

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.width(400.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(if (isApiKeyOnly) "Enter API Key for ${api.name}" else "Login to ${api.name}", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                if (req?.username == true) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (req?.email == true) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (req?.server == true) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Server") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (req?.apiKey == true) {
                    OutlinedTextField(
                        value = apiKeyStr,
                        onValueChange = { apiKeyStr = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isLoading = true
                            errorMsg = null
                            coroutineScope.launch {
                                try {
                                    val form = AuthLoginResponse(
                                        username = username.takeIf { req?.username == true },
                                        password = password.takeIf { req?.password == true },
                                        email = email.takeIf { req?.email == true },
                                        server = server.takeIf { req?.server == true },
                                        apiKey = apiKeyStr.takeIf { req?.apiKey == true }
                                    )
                                    val token = api.login(form)
                                    if (token != null) {
                                        val user = api.user(token)
                                        if (user != null) {
                                            onSuccess(AuthData(user, token))
                                        } else {
                                            errorMsg = if (isApiKeyOnly) "Key successful but failed to fetch user info." else "Login successful but failed to fetch user info."
                                        }
                                    } else {
                                        errorMsg = if (isApiKeyOnly) "Failed: Invalid API Key." else "Login failed: Invalid credentials."
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (isApiKeyOnly) "Submit" else "Login")
                        }
                    }
                }
            }
        }
    }
}
