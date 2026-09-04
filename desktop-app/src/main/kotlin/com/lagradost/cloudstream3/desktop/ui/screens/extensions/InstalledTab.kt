package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ExtensionOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingsDialog
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.runtime.loader.ExtensionLoader

@Composable
fun InstalledTab(
    viewModel: ExtensionsViewModel,
    syncGeneration: Int,
    onNavigateToBrowse: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val installedPlugins = uiState.installedPlugins
    var selectedPlugins by remember { mutableStateOf(setOf<LocalPlugin>()) }
    val remoteIcons = uiState.remotePluginIcons

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsupportedWarning by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
    }

    if (showUnsupportedWarning) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showUnsupportedWarning = false },
            title = { Text("Unsupported Feature") },
            text = { Text("Custom Android settings UI (Layer 3) is not supported on Desktop.\n\nPlease go to Settings -> Extensions from the sidebar to configure this plugin.") },
            confirmButton = {
                TextButton(onClick = { showUnsupportedWarning = false }) {
                    Text("OK")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Uninstall") },
            text = { Text("Are you sure you want to uninstall ${selectedPlugins.size} plugins? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val toDelete = selectedPlugins.toList()
                        if (toDelete.isNotEmpty()) {
                            viewModel.onEvent(ExtensionsUiEvent.OnUninstallPlugins(toDelete))
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        val sourceFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                            title = "Load Local Plugin (.cs3 / .jar)",
                            allowedExtensions = listOf(".cs3", ".jar"),
                            category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.EXTENSIONS,
                        )
                        if (sourceFile != null && sourceFile.exists()) {
                            viewModel.onEvent(ExtensionsUiEvent.OnLoadLocalPlugin(sourceFile))
                        }
                    },
                ) {
                    Text("Load Local")
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedPlugins.isNotEmpty(),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally(),
                ) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Uninstall (${selectedPlugins.size})")
                    }
                }
            }
        }

        val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
        val extMinSize = (posterWidthDp * 2.2f).coerceAtLeast(320f).dp

        if (installedPlugins.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ExtensionOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                        Text(
                            "Nothing Installed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "You haven't installed any extensions yet. Browse the catalog to find extensions to install.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onNavigateToBrowse,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Browse Catalog", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
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
                    val hasSchemaSettings = com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName, plugin.name)
                    val showSettings = hasSchemaSettings || instance?.openSettings != null

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
                        onUninstallClick = { viewModel.onEvent(ExtensionsUiEvent.OnUninstallPlugins(listOf(plugin))) },
                        description = plugin.description,
                        fileSize = plugin.fileSize,
                        onRepoClick = { viewModel.onEvent(ExtensionsUiEvent.OnInspectRepository(plugin.repoName)) },
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
                            showDynamicSettings = true
                            if (instance?.openSettings != null) {
                                try {
                                    instance.openSettings?.invoke(android.content.DesktopContextProvider.context)
                                } catch (e: Throwable) {
                                    com.lagradost.common.logging.AppLogger.i("Executed openSettings fallback: ${e.message}")
                                }
                            }
                            if (!com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName, plugin.name) && instance?.openSettings == null) {
                                showUnsupportedWarning = true
                            }
                        },
                    )

                    if (showDynamicSettings) {
                        PluginSettingsDialog(
                            pluginName = plugin.name,
                            prefName = prefName,
                            jarFile = plugin.file,
                            onDismiss = {
                                showDynamicSettings = false
                                kotlin.concurrent.thread {
                                    try {
                                        ExtensionLoader.unloadPlugin(plugin.file.absolutePath)
                                        ExtensionLoader.loadAndInit(plugin.file, forceBypassSecurity = true)
                                        com.lagradost.common.logging.AppLogger.i("Reloaded plugin ${plugin.name} after settings update")
                                    } catch (e: Throwable) {
                                        com.lagradost.common.logging.AppLogger.e("Failed to reload plugin ${plugin.name}", e)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
