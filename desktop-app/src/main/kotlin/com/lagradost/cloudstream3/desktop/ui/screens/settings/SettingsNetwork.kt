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
import com.lagradost.cloudstream3.desktop.ui.components.P2pTorrentDisclaimerDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop

@Composable
fun SettingsNetwork(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

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
                    subtitle = "Attempts automated browser-based clearance when providers encounter Turnstile challenges. Recommended off.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )
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
                    val scope = rememberCoroutineScope()
                    var isInstalled by remember { mutableStateOf(DesktopTorrentEngine.binary.isInstalled()) }
                    var fileSizeMB by remember { mutableStateOf(DesktopTorrentEngine.binary.getFileSizeMB()) }
                    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                    var isCheckingUpdates by remember { mutableStateOf(false) }
                    var updateFeedbackText by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(torrTask?.status) {
                        if (torrTask?.status == TaskStatus.COMPLETED || torrTask == null) {
                            isInstalled = DesktopTorrentEngine.binary.isInstalled()
                            fileSizeMB = DesktopTorrentEngine.binary.getFileSizeMB()
                        }
                    }

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
                                        isInstalled -> "Installed (${com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.getTorrServerInstalledVersion()} • ${String.format(java.util.Locale.ROOT, "%.1f", fileSizeMB)} MB) • Ready to stream"
                                        else -> "Not Installed (0 MB) • Downloads on first torrent play or install now"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (updateFeedbackText != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = updateFeedbackText!!,
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
                                if (!isInstalled) {
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
                                            isCheckingUpdates = true
                                            updateFeedbackText = null
                                            scope.launch {
                                                val update = com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.checkTorrServerUpdate(force = true)
                                                isCheckingUpdates = false
                                                if (update != null) {
                                                    com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.showDialogForUpdate(update)
                                                } else {
                                                    updateFeedbackText = "✓ TorrServer is up to date (${com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.getTorrServerInstalledVersion()})"
                                                }
                                            }
                                        },
                                        enabled = !isCheckingUpdates,
                                    ) {
                                        Text(if (isCheckingUpdates) "Checking..." else "Check for Updates")
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
                                        scope.launch(Dispatchers.IO) {
                                            DesktopTorrentEngine.binary.deleteBinary()
                                            isInstalled = DesktopTorrentEngine.binary.isInstalled()
                                            fileSizeMB = DesktopTorrentEngine.binary.getFileSizeMB()
                                        }
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
    }
