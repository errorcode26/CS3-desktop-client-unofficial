package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingsDialog
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.runtime.loader.ExtensionLoader

@Composable
fun InstalledTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    var selectedPlugins by remember { mutableStateOf(setOf<LocalPlugin>()) }
    val remoteIcons by DesktopRepositoryManager.remotePluginIcons.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsupportedWarning by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        viewModel.refreshInstalled()
    }

    if (showUnsupportedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsupportedWarning = false },
            title = { Text("Unsupported Feature") },
            text = { Text("Custom Android settings UI (Layer 3) is not supported on Desktop.\n\nPlease go to Settings -> Plugins from the sidebar to configure this plugin.") },
            confirmButton = {
                TextButton(onClick = { showUnsupportedWarning = false }) {
                    Text("OK")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Uninstall") },
            text = { Text("Are you sure you want to uninstall ${selectedPlugins.size} plugins? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val toDelete = selectedPlugins.toList()
                        if (toDelete.isNotEmpty()) {
                            viewModel.uninstallPlugins(toDelete)
                            selectedPlugins = emptySet()
                        }
                    },
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Installed Plugins (${installedPlugins.size})", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load Local Plugin (.cs3 / .jar)", java.awt.FileDialog.LOAD)
                        dialog.file = "*.cs3;*.jar"
                        dialog.isVisible = true
                        if (dialog.file != null) {
                            val sourceFile = java.io.File(dialog.directory, dialog.file)
                            viewModel.loadLocalPlugin(sourceFile)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text("Load Local Plugin")
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    enabled = selectedPlugins.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Uninstall Selected (${selectedPlugins.size})")
                }
            }
        }

        val gridScale by AppearanceConfig.gridScale.collectAsState()
        val extMinSize = when (gridScale) {
            "Compact" -> 280.dp
            "Large" -> 400.dp
            else -> 340.dp
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = extMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(installedPlugins, key = { it.file.absolutePath }) { plugin ->
                val finalIcon = plugin.iconUrl
                    ?: remoteIcons[plugin.internalName]
                    ?: remoteIcons[plugin.name]

                val prefName = plugin.internalName + "_"
                var showDynamicSettings by remember { mutableStateOf(false) }

                val instance = remember(plugin) {
                    ExtensionLoader.getPlugin(plugin.file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
                }
                val hasSchemaSettings = com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName)
                val showSettings = instance?.openSettings != null || hasSchemaSettings

                ExtensionCard(
                    name = plugin.name,
                    internalName = plugin.internalName,
                    version = plugin.version,
                    repoName = plugin.repoName,
                    language = plugin.language,
                    tvTypes = plugin.tvTypes,
                    iconUrl = finalIcon,
                    isInstalled = true,
                    installStatus = "Installed",
                    isInstalling = false,
                    onInstallClick = { },
                    showCheckbox = true,
                    isChecked = selectedPlugins.contains(plugin),
                    onCheckedChange = { isChecked ->
                        selectedPlugins = if (isChecked) {
                            selectedPlugins + plugin
                        } else {
                            selectedPlugins - plugin
                        }
                    },
                    showSettings = showSettings,
                    onSettingsClick = {
                        if (hasSchemaSettings) {
                            showDynamicSettings = true
                        } else {
                            showUnsupportedWarning = true
                        }
                    },
                )

                if (showDynamicSettings) {
                    PluginSettingsDialog(
                        pluginName = plugin.name,
                        prefName = prefName,
                        onDismiss = { showDynamicSettings = false },
                    )
                }
            }
        }
    }
}
