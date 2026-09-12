package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.TaskStatus
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.P2pTorrentDisclaimerDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import java.awt.Desktop

@Composable
fun SettingsNetwork(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var showCookiesDialog by remember { mutableStateOf(false) }
    var showManualSolverDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerCoordinates = it },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroupCard(title = "DNS over HTTPS (DoH)") {
            Text(
                "Bypass ISP DNS blocking by encrypting your DNS queries. Changing this will instantly hot-reload the app's networking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            MviSettingsDropdown(
                key = NetworkConfig.PREF_DOH_PROVIDER,
                label = "Provider",
                options = DohProvider.values().mapIndexed { index, provider -> index to provider.title },
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 0,
            )
        }

        SettingsGroupCard(title = "Experimental & Scraper Engine") {
            Text(
                "Advanced network resolution options for providers and scraping.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            MviSettingsToggle(
                key = DesktopDataStore.PREF_ALLOW_CF_BYPASS,
                label = "Experimental Cloudflare Solver",
                subtitle = "Launches an isolated temporary browser window to resolve Turnstile challenges when required by a source. Terminated immediately upon clearance.",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )

            val cfEnabled = uiState.booleanSettings[DesktopDataStore.PREF_ALLOW_CF_BYPASS]
                ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_CF_BYPASS) ?: false)

            if (cfEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Cookies & Clearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Inspect active provider domains, clear stale tokens, or solve Cloudflare challenges manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showManualSolverDialog = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Solve Manually")
                        }

                        Button(
                            onClick = { showCookiesDialog = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Manage Cookies")
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "Peer-to-Peer (Torrent) Streaming") {
            Text(
                "Stream high-bitrate torrents and magnet links directly using the embedded P2P engine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            val p2pEnabled = uiState.booleanSettings[DesktopDataStore.PREF_P2P_ENABLED]
                ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: false)
            var showP2pDisclaimer by remember { mutableStateOf(false) }

            SettingsToggleItem(
                label = "Enable P2P Torrent Streaming",
                subtitle = "Allows playing torrent and magnet streams. When active, your public IP address is visible to other peers in the swarm.",
                checked = p2pEnabled,
                onCheckedChange = { nextVal ->
                    if (nextVal) {
                        showP2pDisclaimer = true
                    } else {
                        viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(DesktopDataStore.PREF_P2P_ENABLED, false))
                    }
                },
            )

            P2pTorrentDisclaimerDialog(
                show = showP2pDisclaimer,
                isSettingsContext = true,
                onDismiss = { showP2pDisclaimer = false },
                onConfirm = {
                    showP2pDisclaimer = false
                    viewModel.onEvent(SettingsUiEvent.OnUpdateBoolean(DesktopDataStore.PREF_P2P_ENABLED, true))
                },
            )

            if (p2pEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_P2P_SHOW_HUD,
                    label = "Show Live P2P Swarm HUD",
                    subtitle = "Display real-time download speed, active seeders, and connected peers on the player overlay.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = true,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val tasks by AppDownloadManager.tasks.collectAsState()
                val torrTask = tasks.firstOrNull { it.id == "torrserver" }
                val engineState = uiState.engineState
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TorrServer Streaming Engine",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when {
                                    torrTask?.status == TaskStatus.RUNNING -> "Downloading engine... ${torrTask.downloadedMB} / ${torrTask.totalMB} (${torrTask.speedFormatted})"
                                    engineState.isInstalled -> "Installed (${com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.getTorrServerInstalledVersion()} • ${String.format(java.util.Locale.ROOT, "%.1f", engineState.fileSizeMB)} MB) • Ready to stream"
                                    else -> "Not Installed (0 MB) • Downloads on first torrent play or install now"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (engineState.updateFeedback != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = engineState.updateFeedback,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    if (torrTask?.status == TaskStatus.RUNNING) {
                        if (torrTask.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { torrTask.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { AppDownloadManager.cancelDownload("torrserver") },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Cancel Download")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!engineState.isInstalled) {
                                Button(
                                    onClick = {
                                        DesktopTorrentEngine.binary.downloadWithManager()
                                    },
                                ) {
                                    Text("Download Engine Now (~28 MB)")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.onEvent(SettingsUiEvent.CheckTorrServerUpdates)
                                    },
                                    enabled = !engineState.isCheckingUpdates,
                                ) {
                                    Text(if (engineState.isCheckingUpdates) "Checking..." else "Check for Updates")
                                }

                                TextButton(
                                    onClick = {
                                        DesktopTorrentEngine.binary.downloadWithManager()
                                    },
                                ) {
                                    Text("Re-download")
                                }

                                TextButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Text("Uninstall")
                                }
                            }

                            TextButton(
                                onClick = {
                                    val parent = DesktopTorrentEngine.binary.getBinaryFile().parentFile
                                    parent.mkdirs()
                                    try {
                                        Desktop.getDesktop().open(parent)
                                    } catch (e: Exception) {
                                        AppLogger.e("Failed to open torrent engine folder", e)
                                        AppToastManager.showError("Unable to open folder in system explorer")
                                    }
                                },
                            ) {
                                Text("Open Folder")
                            }
                        }
                    }
                }

                if (showDeleteConfirmDialog) {
                    CloudstreamAlertDialog(
                        show = true,
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Uninstall Torrent Engine?") },
                        text = { Text("This will delete the 28 MB TorrServer executable from your computer to free up space. You can re-download it at any time.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirmDialog = false
                                    viewModel.onEvent(SettingsUiEvent.DeleteTorrServerBinary)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Uninstall")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }

    ManageCookiesDialog(
        show = showCookiesDialog,
        onDismiss = { showCookiesDialog = false },
        viewModel = viewModel,
    )

    ManualClearanceDialog(
        show = showManualSolverDialog,
        onDismiss = { showManualSolverDialog = false },
        viewModel = viewModel,
    )
}

@Composable
fun SettingsNetworkScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
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
fun ManageCookiesDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel,
) {
    if (!show) return

    val uiState by viewModel.uiState.collectAsState()
    val cookiesMap = uiState.clearanceState.cookiesMap
    var searchQuery by remember { mutableStateOf("") }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) viewModel.onEvent(SettingsUiEvent.RefreshClearanceCookies)
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Saved Cookies & Clearance",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val totalCookies = cookiesMap.values.sumOf { it.size }
                        Text(
                            text = "${cookiesMap.size} domains stored • $totalCookies total cookies",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (cookiesMap.isNotEmpty()) {
                            Button(
                                onClick = { showClearAllConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear All")
                            }
                        }

                        FilledTonalButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Done")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter by provider domain...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                val filtered = remember(cookiesMap, searchQuery) {
                    val query = searchQuery.trim().lowercase()
                    if (query.isEmpty()) {
                        cookiesMap.toList().sortedBy { it.first }
                    } else {
                        cookiesMap.filter { it.key.lowercase().contains(query) }.toList().sortedBy { it.first }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No saved cookies or clearance tokens." else "No domains match '$searchQuery'.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.first }) { (domain, cookies) ->
                            val hasClearance = cookies.any { it.name.equals("cf_clearance", ignoreCase = true) }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = domain,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )

                                            if (hasClearance) {
                                                Surface(
                                                    color = Color(0xFF1B5E20).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                                                ) {
                                                    Text(
                                                        text = "Clearance Active",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF81C784),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.onEvent(SettingsUiEvent.ClearDomainCookies(domain))
                                            },
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Clear domain cookies",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        cookies.forEach { cookie ->
                                            val isCf = cookie.name.equals("cf_clearance", ignoreCase = true)
                                            val now = System.currentTimeMillis()
                                            val hoursLeft = ((cookie.expiresAt - now) / (1000 * 60 * 60)).coerceAtLeast(0)
                                            val expiryLabel = if (cookie.expiresAt > now && hoursLeft < 10000) "${hoursLeft}h left" else "session"

                                            Surface(
                                                color = if (isCf) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isCf) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                ),
                                            ) {
                                                Text(
                                                    text = "${cookie.name} ($expiryLabel)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    CloudstreamAlertDialog(
        show = showClearAllConfirm,
        onDismissRequest = { showClearAllConfirm = false },
        title = { Text("Clear All Cookies?") },
        text = {
            Text("This will remove all saved session cookies and Cloudflare clearance tokens across all providers.")
        },
        confirmButton = {
            Button(
                onClick = {
                    showClearAllConfirm = false
                    viewModel.onEvent(SettingsUiEvent.ClearAllClearanceCookies)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Clear All")
            }
        },
        dismissButton = {
            TextButton(onClick = { showClearAllConfirm = false }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ManualClearanceDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel,
) {
    if (!show) return

    val uiState by viewModel.uiState.collectAsState()
    val isLaunching = uiState.clearanceState.isLaunchingManualBypass
    var urlInput by remember { mutableStateOf("") }

    CloudstreamAlertDialog(
        show = show,
        onDismissRequest = {
            if (!isLaunching) onDismiss()
        },
        title = { Text("Manual Cloudflare Clearance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a provider website or URL to launch an isolated sandbox browser and solve Cloudflare Turnstile on demand. Clearance cookies will be automatically saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://example-provider.com") },
                    singleLine = true,
                    enabled = !isLaunching,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawUrl = urlInput.trim()
                    if (rawUrl.isNotBlank()) {
                        onDismiss()
                        viewModel.onEvent(SettingsUiEvent.LaunchManualClearance(rawUrl))
                    }
                },
                enabled = urlInput.trim().isNotBlank() && !isLaunching,
            ) {
                Text(if (isLaunching) "Launching..." else "Launch Solver")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLaunching,
            ) {
                Text("Cancel")
            }
        },
    )
}
