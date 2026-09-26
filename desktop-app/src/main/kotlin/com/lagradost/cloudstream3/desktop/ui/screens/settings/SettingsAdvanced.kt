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
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.*
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is SettingsUiEffect.ShowToast -> {
                    if (effect.isError) {
                        AppToastManager.showError(effect.message)
                    } else {
                        AppToastManager.showInfo(effect.message)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerCoordinates = it }
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── 1. Storage & Cache Telemetry ──────────────────────────────
        val totalFootprint = uiState.storageMetrics.totalBytes

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
                        IconButton(onClick = { viewModel.onEvent(SettingsUiEvent.RefreshStorageMetrics) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageTelemetryCard(
                        title = "Image Cache",
                        sizeText = uiState.storageMetrics.imageCacheBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Artwork & posters",
                        icon = Icons.Default.Image,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            viewModel.onEvent(SettingsUiEvent.ClearCache(StorageCacheType.IMAGE))
                        },
                    )

                    StorageTelemetryCard(
                        title = "Network Cache",
                        sizeText = uiState.storageMetrics.networkCacheBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "HTTP responses & staging",
                        icon = Icons.Default.CloudSync,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            viewModel.onEvent(SettingsUiEvent.ClearCache(StorageCacheType.NETWORK))
                        },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageTelemetryCard(
                        title = "Database",
                        sizeText = uiState.storageMetrics.databaseBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Bookmarks & metadata",
                        icon = Icons.Default.Storage,
                        actionLabel = "Optimize",
                        isActionLoading = uiState.isOptimizingDb,
                        isDestructive = false,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            viewModel.onEvent(SettingsUiEvent.VacuumDatabase)
                        },
                    )

                    StorageTelemetryCard(
                        title = "Diagnostic Logs",
                        sizeText = uiState.storageMetrics.logsBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Application runtime logs",
                        icon = Icons.Default.Terminal,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            viewModel.onEvent(SettingsUiEvent.ClearCache(StorageCacheType.LOGS))
                        },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageTelemetryCard(
                        title = "Stream & Temp Cache",
                        sizeText = uiState.storageMetrics.streamCacheBytes?.let { formatBytes(it) } ?: "...",
                        subtitle = "Stream segments & temp files",
                        icon = Icons.Default.PlayCircleOutline,
                        actionLabel = "Clear",
                        isDestructive = true,
                        modifier = Modifier.weight(1f),
                        onAction = {
                            viewModel.onEvent(SettingsUiEvent.ClearCache(StorageCacheType.STREAM))
                        },
                    )

                    Spacer(modifier = Modifier.weight(1f))
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
                            scope.launch(Dispatchers.IO) {
                                try {
                                    java.awt.Desktop.getDesktop().open(file)
                                } catch (e: Exception) {
                                    AppToastManager.showError("Failed to open directory: ${e.message}")
                                }
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
                                showSoftResetDialog = false
                                viewModel.onEvent(SettingsUiEvent.ResetSettingsToDefault)
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
                                showResetDialog = false
                                viewModel.onEvent(SettingsUiEvent.FactoryReset)
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
