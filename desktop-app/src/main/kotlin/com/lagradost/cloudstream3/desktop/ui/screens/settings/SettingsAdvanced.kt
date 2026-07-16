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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun SettingsAdvanced(onErrorLogs: () -> Unit) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Search Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("When enabled, searching will query all available providers simultaneously instead of just your selected provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Advanced Global Search", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    var isGlobalSearch by remember { mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>("global_search_enabled") ?: false) }
                    Switch(
                        checked = isGlobalSearch,
                        onCheckedChange = {
                            isGlobalSearch = it
                            com.lagradost.common.storage.DesktopDataStore.setKey("global_search_enabled", it)
                        },
                    )
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Storage Directories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("CloudStream stores its settings, caches, and extensions dynamically based on your operating system.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                @Composable
                fun PathRow(title: String, file: java.io.File) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(file.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
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
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                var imageCacheSize by remember { mutableStateOf("Calculating...") }
                val imageCacheDir = java.io.File(com.lagradost.common.platform.PlatformPaths.appDataDir, "image_cache")

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val size = if (imageCacheDir.exists()) imageCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024) else 0
                        imageCacheSize = "$size MB"
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear Image Cache", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(imageCacheSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            if (imageCacheDir.exists()) {
                                imageCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            }
                            val newSize = if (imageCacheDir.exists()) imageCacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024) else 0
                            imageCacheSize = "$newSize MB"
                        }
                    }) {
                        Text("Clear")
                    }
                }
            }
        }
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

        if (showAddCloneDialog) {
            var selectedProvider by remember { mutableStateOf<com.lagradost.cloudstream3.MainAPI?>(null) }
            var nameInput by remember { mutableStateOf("") }
            var urlInput by remember { mutableStateOf("") }
            var langInput by remember { mutableStateOf("") }
            var expanded by remember { mutableStateOf(false) }

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

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cloned Sites / Custom URLs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You can clone an existing provider and override its URL. This is useful if a site changes its domain.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                clonedSites.forEach { site ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(site.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(site.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val newList = clonedSites.filter { it != site }
                            clonedSites = newList
                            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                            com.lagradost.common.storage.DesktopDataStore.setKey("USER_PROVIDER_API", mapper.writeValueAsString(newList))
                            // Instruct user to restart to remove completely
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showAddCloneDialog = true }) {
                    Text("Add Cloned Site")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Logs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("View internal application logs for troubleshooting and bug reporting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onErrorLogs) {
                    Text("View Application Logs")
                }
            }
        }
    }
}
