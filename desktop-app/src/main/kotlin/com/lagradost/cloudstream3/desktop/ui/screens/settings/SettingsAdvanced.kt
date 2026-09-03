package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun getDirectorySizeBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    return try {
        dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    } catch (_: Exception) {
        0L
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun StorageTelemetryCard(
    title: String,
    sizeText: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    isActionLoading: Boolean = false,
    isDestructive: Boolean = false,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                FilledTonalButton(
                    onClick = onAction,
                    enabled = !isActionLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = if (isDestructive) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAdvanced(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    // ── Storage Telemetry States ──────────────────────────────────────
    var imageCacheBytes by remember { mutableStateOf<Long?>(null) }
    var networkCacheBytes by remember { mutableStateOf<Long?>(null) }
    var databaseBytes by remember { mutableStateOf<Long?>(null) }
    var logsBytes by remember { mutableStateOf<Long?>(null) }
    var isOptimizingDb by remember { mutableStateOf(false) }

    val imageCacheDir = remember { File(PlatformPaths.appDataDir, "image_cache") }
    val networkCacheDir = remember { PlatformPaths.cacheDir }
    val dbFile = remember { File(PlatformPaths.dataDir, "cloudstream.db") }
    val logsDir = remember { PlatformPaths.logsDir }

    suspend fun refreshStorageMetrics() {
        withContext(Dispatchers.IO) {
            imageCacheBytes = getDirectorySizeBytes(imageCacheDir)
            networkCacheBytes = getDirectorySizeBytes(networkCacheDir)
            databaseBytes = if (dbFile.exists()) dbFile.length() else 0L
            logsBytes = getDirectorySizeBytes(logsDir)
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageMetrics()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerCoordinates = it }
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── 1. Storage & Cache Telemetry ──────────────────────────────
        val totalFootprint = (imageCacheBytes ?: 0L) + (networkCacheBytes ?: 0L) + (databaseBytes ?: 0L) + (logsBytes ?: 0L)

        SettingsGroupCard(title = "Storage & Cache Telemetry") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Total Storage Footprint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Calculated across SQLite database, media posters, HTTP caches, and runtime logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatBytes(totalFootprint),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { scope.launch { refreshStorageMetrics() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageTelemetryCard(
                        title = "Image Cache",
                        sizeText = imageCacheBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Artwork & posters",
                        icon = Icons.Default.Image,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            scope.launch(Dispatchers.IO) {
                                if (imageCacheDir.exists()) {
                                    imageCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                }
                                refreshStorageMetrics()
                                AppToastManager.showInfo("Image cache cleared")
                            }
                        },
                    )

                    StorageTelemetryCard(
                        title = "Network Cache",
                        sizeText = networkCacheBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "HTTP responses & staging",
                        icon = Icons.Default.CloudSync,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            scope.launch(Dispatchers.IO) {
                                if (networkCacheDir.exists()) {
                                    networkCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                }
                                refreshStorageMetrics()
                                AppToastManager.showInfo("Network cache cleared")
                            }
                        },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageTelemetryCard(
                        title = "Database",
                        sizeText = databaseBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Bookmarks & metadata",
                        icon = Icons.Default.Storage,
                        actionLabel = "Optimize",
                        isActionLoading = isOptimizingDb,
                        isDestructive = false,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            scope.launch(Dispatchers.IO) {
                                isOptimizingDb = true
                                try {
                                    if (dbFile.exists()) {
                                        java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                                            conn.createStatement().use { stmt ->
                                                stmt.execute("VACUUM;")
                                            }
                                        }
                                    }
                                    refreshStorageMetrics()
                                    AppToastManager.showInfo("Database defragmented and compacted")
                                } catch (e: Exception) {
                                    AppToastManager.showError("Optimization failed: ${e.message}")
                                } finally {
                                    isOptimizingDb = false
                                }
                            }
                        },
                    )

                    StorageTelemetryCard(
                        title = "Diagnostic Logs",
                        sizeText = logsBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Application runtime logs",
                        icon = Icons.Default.Terminal,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            scope.launch(Dispatchers.IO) {
                                if (logsDir.exists()) {
                                    logsDir.listFiles()?.forEach { it.deleteRecursively() }
                                }
                                refreshStorageMetrics()
                                AppToastManager.showInfo("Logs cleared")
                            }
                        },
                    )
                }
            }
        }

        // ── 2. Storage Directories ────────────────────────────────────
        SettingsGroupCard(title = "Storage Directories") {
            Text(
                "CloudStream stores its settings, caches, and extensions dynamically based on your operating system.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

            @Composable
            fun PathRow(title: String, file: File, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(file.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().open(file)
                            } catch (e: Exception) {
                                AppToastManager.showError("Failed to open directory: ${e.message}")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            PathRow("App Data & Config", PlatformPaths.appDataDir, Icons.Default.Folder)
            PathRow("Extensions & Plugins", PlatformPaths.extensionsDir, Icons.Default.Extension)
            PathRow("Cache Data", PlatformPaths.cacheDir, Icons.Default.Cached)
            PathRow("System Logs", PlatformPaths.logsDir, Icons.Default.Terminal)
        }

        // ── 3. Cloned Sites & Custom URLs ─────────────────────────────
        com.lagradost.cloudstream3.desktop.ui.screens.settings.advanced.SettingsAdvancedClonedSites(
            viewModel = viewModel,
        )

        SettingsGroupCard(title = "Danger Zone") {
            var showResetDialog by remember { mutableStateOf(false) }
            var showSoftResetDialog by remember { mutableStateOf(false) }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Tier 1: Soft Reset (Settings & Preferences)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Settings to Default", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Reverts interface, playback, theme, and font configurations to defaults. Your watch history, bookmarks, and installed extensions will NOT be deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = { showSoftResetDialog = true },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Preferences")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Tier 2: Hard Nuclear Factory Reset
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Factory Reset App", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Deletes all extensions, watch history, bookmarks, settings, and cached data. This cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    FilledTonalButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wipe All Data")
                    }
                }
            }

            if (showSoftResetDialog) {
                CloudstreamAlertDialog(
                    show = true,
                    onDismissRequest = { showSoftResetDialog = false },
                    title = { Text("Reset Settings?") },
                    text = { Text("Are you sure you want to reset all settings to defaults? Your bookmarks and watch history will be preserved.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        if (dbFile.exists()) {
                                            java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                                                conn.createStatement().use { stmt ->
                                                    stmt.execute("DELETE FROM KeyValueStore;")
                                                }
                                            }
                                        }
                                        DesktopDataStore.rawKeyCache.clear()
                                        AppearanceConfig.reloadFromDataStore()
                                        AppToastManager.showInfo("Settings restored to defaults")
                                    } catch (e: Exception) {
                                        AppToastManager.showError("Failed to reset settings: ${e.message}")
                                    }
                                    showSoftResetDialog = false
                                }
                            },
                        ) { Text("Reset") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSoftResetDialog = false }) { Text("Cancel") }
                    },
                )
            }

            if (showResetDialog) {
                CloudstreamAlertDialog(
                    show = true,
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Factory Reset") },
                    text = { Text("Are you absolutely sure? This will permanently wipe all your data, plugins, and settings. The app will immediately close to perform the wipe.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val target = PlatformPaths.appDataDir

                                    // 1. Unload all plugins and close URLClassLoaders to release JAR file locks
                                    try {
                                        com.lagradost.runtime.loader.ExtensionLoader.unloadAllPlugins()
                                    } catch (_: Throwable) {}

                                    // 2. Immediate in-process recursive deletion attempt
                                    if (target.exists()) {
                                        try {
                                            target.deleteRecursively()
                                        } catch (_: Throwable) {}
                                    }

                                    // 3. Detached OS process to wipe any residual files after JVM process termination
                                    try {
                                        val isWindows = PlatformPaths.currentOS == PlatformPaths.OS.WINDOWS
                                        if (isWindows) {
                                            ProcessBuilder(
                                                "cmd.exe", "/c", "timeout /t 1 /nobreak > nul & rmdir /s /q \"${target.absolutePath}\""
                                            ).start()
                                        } else {
                                            ProcessBuilder(
                                                "sh", "-c", "sleep 1 && rm -rf \"${target.absolutePath}\""
                                            ).start()
                                        }
                                    } catch (_: Throwable) {}

                                    kotlin.system.exitProcess(0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Yes, wipe everything") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsNetworkScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsNetwork(viewModel = viewModel)
        }
    }
}

@Composable
fun SettingsAdvancedScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsAdvanced(viewModel = viewModel)
        }
    }
}

// Legacy combined screen — kept for reference but no longer used by the router
@Composable
fun SettingsAdvancedAndNetwork(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsAdvanced(viewModel = viewModel)
            SettingsNetwork(viewModel = viewModel)
        }
    }
}
