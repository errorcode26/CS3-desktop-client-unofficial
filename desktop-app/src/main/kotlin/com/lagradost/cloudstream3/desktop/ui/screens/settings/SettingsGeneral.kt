package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthData
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsGeneral() {
    val scope = rememberCoroutineScope()
    var selectedApiForLogin by remember { mutableStateOf<AuthAPI?>(null) }
    var accountsUpdated by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        
        // --- Group 1: Updates & About ---
        SettingsGroupCard(title = "Updates & About") {
            val latestRelease by com.lagradost.cloudstream3.desktop.AppUpdater.latestRelease.collectAsState()
            var isChecking by remember { mutableStateOf(false) }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CloudStream Desktop Client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Current Version: v${com.lagradost.cloudstream3.desktop.AppConfig.APP_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (latestRelease == null) {
                    FilledTonalButton(
                        onClick = { 
                            scope.launch {
                                isChecking = true
                                com.lagradost.cloudstream3.desktop.AppUpdater.checkForUpdates(force = true)
                                kotlinx.coroutines.delay(500)
                                isChecking = false
                            }
                        },
                        enabled = !isChecking
                    ) {
                        Text(if (isChecking) "Checking..." else "Check for Updates")
                    }
                } else {
                    Button(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(latestRelease!!.html_url))
                            } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Download v${latestRelease!!.tag_name.removePrefix("v")}")
                    }
                }
            }
            
            if (latestRelease != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("What's New in ${latestRelease!!.name}:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(latestRelease!!.body ?: "No release notes provided.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // --- Group 2: Accounts & Integrations ---
        SettingsGroupCard(title = "Accounts & Integrations") {
            accountsUpdated.hashCode() // Trigger recompose on change
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountManager.allApis.forEach { api ->
                    val accounts = AccountManager.cachedAccounts[api.idPrefix] ?: emptyArray()
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
                                    accountsUpdated++
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
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("The Movie Database (TMDB)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("If TMDB stops working in the future, this is an optional key in case the default key fails or gets rate limited.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var tmdbApiKey by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<String>("tmdb_api_key") ?: "") }
                    TextField(
                        value = tmdbApiKey,
                        onValueChange = { 
                            tmdbApiKey = it
                            com.lagradost.common.storage.DesktopDataStore.setKey("tmdb_api_key", it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Leave blank to use default key") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        }

        // --- Group 2: Search Settings ---
        SettingsGroupCard(title = "Search Settings") {
            var isGlobalSearch by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>("global_search_enabled") ?: false) }
            SettingsToggleItem(
                label = "Enable Advanced Global Search",
                subtitle = "Query all available providers simultaneously instead of just your selected provider.",
                checked = isGlobalSearch,
                onCheckedChange = {
                    isGlobalSearch = it
                    com.lagradost.common.storage.DesktopDataStore.setKey("global_search_enabled", it)
                },
            )
        }

        // --- Group 3: Storage Directories ---
        SettingsGroupCard(title = "Storage Directories") {
            Text("CloudStream stores its settings, caches, and extensions dynamically based on your operating system.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            @Composable
            fun PathRow(title: String, file: java.io.File) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(file.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = {
                        try {
                            java.awt.Desktop.getDesktop().open(file)
                        } catch (e: Exception) {}
                    }) {
                        Text("Open")
                    }
                }
            }

            PathRow("App Data & Config", com.lagradost.common.platform.PlatformPaths.appDataDir)
            PathRow("Extensions", com.lagradost.common.platform.PlatformPaths.extensionsDir)
            PathRow("Cache Data", com.lagradost.common.platform.PlatformPaths.cacheDir)
            PathRow("System Logs", com.lagradost.common.platform.PlatformPaths.logsDir)
        }

        // --- Group 4: Data Management ---
        SettingsGroupCard(title = "Data Management") {
            var imageCacheSize by remember { mutableStateOf("Calculating...") }
            val imageCacheDir = java.io.File(com.lagradost.common.platform.PlatformPaths.appDataDir, "image_cache")

            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val size = if (imageCacheDir.exists()) imageCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024) else 0
                    imageCacheSize = "$size MB"
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear Image Cache", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(imageCacheSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            if (imageCacheDir.exists()) {
                                imageCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            }
                            val newSize = if (imageCacheDir.exists()) imageCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024) else 0
                            imageCacheSize = "$newSize MB"
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text("Clear")
                }
            }
        }

        // Clone Dialog State
        var showAddCloneDialog by remember { mutableStateOf(false) }
        var clonedSites by remember {
            mutableStateOf(
                try {
                    val json = com.lagradost.common.storage.DesktopDataStore.getKey<String>("USER_PROVIDER_API")
                    if (json != null) {
                        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        mapper.readValue<List<com.lagradost.cloudstream3.desktop.models.CustomSite>>(
                            json,
                            object : com.fasterxml.jackson.core.type.TypeReference<List<com.lagradost.cloudstream3.desktop.models.CustomSite>>() {},
                        )
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                },
            )
        }

        // --- Group 5: Cloned Sites ---
        SettingsGroupCard(title = "Cloned Sites & Custom URLs") {
            Text("You can clone an existing provider and override its URL. This is useful if a site changes its domain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            clonedSites.forEach { site ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(site.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val newList = clonedSites.filter { it != site }
                        clonedSites = newList
                        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        com.lagradost.common.storage.DesktopDataStore.setKey("USER_PROVIDER_API", mapper.writeValueAsString(newList))
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Button(onClick = { showAddCloneDialog = true }) {
                Text("Add Cloned Site")
            }
        }

        // --- Clone Dialog Implementation ---
        if (showAddCloneDialog) {
            var selectedProvider by remember { mutableStateOf<com.lagradost.cloudstream3.MainAPI?>(null) }
            var nameInput by remember { mutableStateOf("") }
            var urlInput by remember { mutableStateOf("") }
            var langInput by remember { mutableStateOf("") }

            val availableProviders = remember {
                com.lagradost.cloudstream3.APIHolder.allProviders.distinctBy { it::class.java.simpleName }.sortedBy { it.name }
            }

            Dialog(onDismissRequest = { showAddCloneDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: Provider Selection
                        Column(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                            Text("Select Base Provider", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))

                            var searchQuery by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search providers...") },
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))

                            val filtered = availableProviders.filter { it.name.contains(searchQuery, true) }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered) { provider ->
                                    val isSelected = selectedProvider == provider
                                    val clonesCount = clonedSites.count { it.parentJavaClass == provider.javaClass.simpleName }

                                    Surface(
                                        onClick = {
                                            selectedProvider = provider
                                            nameInput = provider.name + " Clone"
                                            urlInput = provider.mainUrl
                                            langInput = provider.lang
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(provider.name, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            if (clonesCount > 0) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    shape = MaterialTheme.shapes.small,
                                                ) {
                                                    Text("$clonesCount", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Right Pane: Configuration
                        Column(modifier = Modifier.weight(1.5f).fillMaxHeight().padding(24.dp)) {
                            Text("Configure Clone", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(24.dp))

                            if (selectedProvider == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Select a provider from the left to configure it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    label = { Text("Display Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("Override URL") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = langInput,
                                    onValueChange = { langInput = it },
                                    label = { Text("Language Code (e.g. en)") },
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(Modifier.weight(1f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showAddCloneDialog = false }) {
                                        Text("Cancel")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = {
                                        val provider = selectedProvider
                                        if (provider != null && nameInput.isNotBlank() && urlInput.isNotBlank()) {
                                            val newSite = com.lagradost.cloudstream3.desktop.models.CustomSite(
                                                parentJavaClass = provider.javaClass.simpleName,
                                                name = nameInput,
                                                url = urlInput,
                                                lang = langInput.ifBlank { provider.lang },
                                            )
                                            val newList = clonedSites + newSite
                                            clonedSites = newList

                                            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                                            com.lagradost.common.storage.DesktopDataStore.setKey("USER_PROVIDER_API", mapper.writeValueAsString(newList))

                                            try {
                                                val clone = provider.javaClass.getDeclaredConstructor().newInstance()
                                                clone.name = newSite.name
                                                clone.lang = newSite.lang
                                                clone.mainUrl = newSite.url.trimEnd('/')
                                                clone.canBeOverridden = false
                                                com.lagradost.cloudstream3.APIHolder.allProviders.add(clone)
                                                com.lagradost.cloudstream3.APIHolder.addPluginMapping(clone)
                                            } catch (e: Exception) {
                                                com.lagradost.common.logging.AppLogger.e("Failed to clone provider", e)
                                            }

                                            showAddCloneDialog = false
                                        }
                                    }) {
                                        Text("Save Clone")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Group 6: Danger Zone ---
        SettingsGroupCard(title = "Danger Zone") {
            var showResetDialog by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Factory Reset App", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Deletes all extensions, watch history, settings, and cached data. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Wipe Data")
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Factory Reset") },
                    text = { Text("Are you absolutely sure? This will permanently wipe all your data, plugins, and settings. The app will immediately close to perform the wipe.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val target = com.lagradost.common.platform.PlatformPaths.appDataDir
                                if (target.exists()) {
                                    // Delete everything we can right now
                                    target.deleteRecursively()
                                    // Register anything locked (like plugin JARs) to be deleted when the JVM exits
                                    target.walkBottomUp().forEach { it.deleteOnExit() }
                                }
                                kotlin.system.exitProcess(0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Yes, wipe everything") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                    }
                )
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

    Dialog(onDismissRequest = onDismiss) {
        val isApiKeyOnly = req != null && req.apiKey && !req.username && !req.password && !req.email && !req.server

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(400.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                    }) { Text("Login") }
                }
            }
        }
    }
}
