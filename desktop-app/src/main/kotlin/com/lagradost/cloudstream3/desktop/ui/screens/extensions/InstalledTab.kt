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
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.screens.PluginSettingsDialog
import com.lagradost.cloudstream3.desktop.ui.screens.ShadowUiHost
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private val jarHasSettingsCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

private fun pluginHasSettings(plugin: LocalPlugin): Boolean {
    val prefName = plugin.internalName + "_"
    if (com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName, plugin.name)) return true
    if (android.content.res.DesktopResourceRegistry.getLayoutXml("settings", plugin.internalName) != null ||
        android.content.res.DesktopResourceRegistry.getLayoutXml("${plugin.internalName}__settings") != null ||
        android.content.res.DesktopResourceRegistry.getLayoutXml("${plugin.internalName}_settings") != null) return true

    val instance = ExtensionLoader.getPlugin(plugin.file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
    if (instance?.openSettings != null) return true

    val jarFile = plugin.file
    val cacheKey = "${jarFile.absolutePath}_${jarFile.lastModified()}"
    return jarHasSettingsCache.getOrPut(cacheKey) {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name.lowercase()
                    (name.startsWith("res/layout/") || name.startsWith("res/xml/")) &&
                        (name.contains("setting") || name.contains("pref")) &&
                        name.endsWith(".xml")
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}

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
    val coroutineScope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var activeSettingsPlugin by remember { mutableStateOf<LocalPlugin?>(null) }

    LaunchedEffect(syncGeneration) {
        viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
    }

    // Host Android plugin shadow dialogs (e.g. M3U playlist addition dialogs)
    ShadowUiHost()

    activeSettingsPlugin?.let { plugin ->
        val prefName = plugin.internalName + "_"
        PluginSettingsDialog(
            pluginName = plugin.name,
            prefName = prefName,
            jarFile = plugin.file,
            onDismiss = {
                activeSettingsPlugin = null
                viewModel.onEvent(ExtensionsUiEvent.OnReloadPluginAfterSettings(plugin.file, plugin.name))
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
                val coroutineScope = rememberCoroutineScope()
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val sourceFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                title = "Load Local Plugin (.cs3 / .jar)",
                                allowedExtensions = listOf(".cs3", ".jar"),
                                category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.EXTENSIONS,
                            )
                            if (sourceFile != null && sourceFile.exists()) {
                                viewModel.onEvent(ExtensionsUiEvent.OnLoadLocalPlugin(sourceFile))
                            }
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

                    val instance = remember(plugin) {
                        ExtensionLoader.getPlugin(plugin.file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
                    }
                    val showSettings = pluginHasSettings(plugin)

                    val hasFailedMarker = remember(plugin, syncGeneration) {
                        val baseName = plugin.file.nameWithoutExtension.substringBefore("-jvm").substringBefore("-secure")
                        java.io.File(plugin.file.parentFile, "$baseName.d2j_failed").exists()
                    }
                    val isLoaded = instance != null || ExtensionLoader.plugins.containsKey(plugin.file.absolutePath)

                    ExtensionCard(
                        name = plugin.name,
                        internalName = plugin.internalName,
                        version = plugin.version,
                        repoName = plugin.repoName,
                        language = plugin.language,
                        tvTypes = plugin.tvTypes,
                        iconUrl = finalIcon,
                        isInstalled = true,
                        installStatus = if (hasFailedMarker) "Failed (Click to Retry)" else if (!isLoaded) "Disabled" else "Installed",
                        isInstalling = false,
                        onInstallClick = {
                            viewModel.onEvent(ExtensionsUiEvent.OnReloadPluginAfterSettings(plugin.file, plugin.name))
                        },
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
                            coroutineScope.launch(Dispatchers.IO) {
                                if (!com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName, plugin.name)) {
                                    ExtensionLoader.scanAllXmlPreferences(plugin.file, plugin.internalName)
                                }
                                if (!ExtensionLoader.isPluginLoaded(plugin.file.absolutePath)) {
                                    com.lagradost.cloudstream3.desktop.init.loadPluginForJar(plugin.file)
                                }
                                val updatedInstance = ExtensionLoader.getPlugin(plugin.file.absolutePath) as? com.lagradost.cloudstream3.plugins.Plugin
                                val hasXmlOrSchema = com.lagradost.common.storage.PluginSettingsSchemaRegistry.hasSettings(prefName, plugin.name) ||
                                    android.content.res.DesktopResourceRegistry.getLayoutXml("settings", plugin.internalName) != null ||
                                    android.content.res.DesktopResourceRegistry.getLayoutXml("${plugin.internalName}__settings") != null ||
                                    android.content.res.DesktopResourceRegistry.getLayoutXml("${plugin.internalName}_settings") != null

                                if (updatedInstance?.openSettings != null && !hasXmlOrSchema) {
                                    ShadowUi.beginSession(plugin.internalName)
                                    try {
                                        updatedInstance.openSettings?.invoke(android.content.DesktopContextProvider.context)
                                    } catch (e: Throwable) {
                                        com.lagradost.common.logging.AppLogger.e("openSettings error", e)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        activeSettingsPlugin = plugin
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
