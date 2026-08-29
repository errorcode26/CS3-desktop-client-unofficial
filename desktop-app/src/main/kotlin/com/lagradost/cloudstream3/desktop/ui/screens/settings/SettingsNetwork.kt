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
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            SettingsGroupCard(title = "Security & Browser Isolation") {
                Text(
                    "Control how CloudStream interacts with external browsers for CAPTCHA bypasses and trailers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_ALLOW_CF_BYPASS,
                    label = "Allow Experimental Cloudflare Bypass",
                    subtitle = "EXPERIMENTAL AND CURRENTLY BROKEN. Advised to leave OFF until further updates. Uses background browser to solve captchas.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                Spacer(modifier = Modifier.height(8.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER,
                    label = "Allow Opening Trailers & External Links",
                    subtitle = "Allow CloudStream to open official trailers and external web links.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                // Dynamic visibility for dependent settings
                val allowExternalBrowser = uiState.booleanSettings[DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER] ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_EXTERNAL_BROWSER) ?: false)

                if (allowExternalBrowser) {
                    Spacer(modifier = Modifier.height(8.dp))

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_ISOLATED_EXTERNAL_BROWSER,
                        label = "Use Isolated Sandbox for Trailers",
                        subtitle = "Instead of opening your personal browser, click-to-play trailers will open in a strict, disposable, popup-blocked sandboxed window.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MviSettingsToggle(
                        key = DesktopDataStore.PREF_DONT_ASK_EXTERNAL_LINKS,
                        label = "Don't Ask Again Before Opening Trailers",
                        subtitle = "Automatically open external links in your preferred browser without showing the confirmation dialog.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )
                }
            }

            SettingsGroupCard(title = "Peer-to-Peer (Torrent) Streaming") {
                Text(
                    "Stream high-bitrate torrents and magnet links directly in MPV using the embedded TorrServer daemon without requiring external debrid accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                val coroutineScope = rememberCoroutineScope()
                var isCheckingStatus by remember { mutableStateOf(false) }
                var daemonRunning by remember { mutableStateOf(false) }
                var statusMessage by remember { mutableStateOf<String?>(null) }
                val binaryExists = remember {
                    try {
                        com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.binary.getBinaryFile().exists()
                    } catch (_: Exception) { false }
                }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        daemonRunning = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.binary.isRunning()
                    }
                }

                // Daemon Live Status Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (daemonRunning) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else if (binaryExists) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (daemonRunning) Color(0xFF10B981)
                        else if (binaryExists) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (daemonRunning) Color(0xFF10B981)
                                        else if (binaryExists) Color(0xFF9E9E9E)
                                        else MaterialTheme.colorScheme.error,
                                        shape = CircleShape,
                                    ),
                            )
                            Column {
                                Text(
                                    text = if (daemonRunning) "TorrServer Daemon: Running (Active)"
                                    else if (binaryExists) "TorrServer Daemon: Standby (Auto-Starts on Play)"
                                    else "TorrServer Daemon: Not Installed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = statusMessage ?: if (daemonRunning) "Listening on http://127.0.0.1:8091"
                                    else if (binaryExists) "Executable ready in %APPDATA%/CloudStreamDesktop/torrserver/bin"
                                    else "Binary will download automatically when playing a torrent",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (isCheckingStatus) return@OutlinedButton
                                    isCheckingStatus = true
                                    statusMessage = "Testing daemon..."
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            val start = System.currentTimeMillis()
                                            try {
                                                com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.binary.start()
                                                val running = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.binary.isRunning()
                                                val elapsed = System.currentTimeMillis() - start
                                                daemonRunning = running
                                                statusMessage = if (running) "Online (HTTP 200 in ${elapsed}ms)" else "Failed to start"
                                            } catch (e: Exception) {
                                                statusMessage = "Error: ${e.message}"
                                            } finally {
                                                isCheckingStatus = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isCheckingStatus,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(if (isCheckingStatus) "Testing..." else "Test Daemon")
                            }

                            if (daemonRunning) {
                                Button(
                                    onClick = {
                                        try {
                                            java.awt.Desktop.getDesktop().browse(java.net.URI("http://127.0.0.1:8091"))
                                        } catch (_: Exception) {}
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Open Web UI")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                MviSettingsToggle(
                    key = DesktopDataStore.PREF_P2P_ENABLED,
                    label = "Enable P2P Torrent Streaming",
                    subtitle = "Automatically boots the embedded TorrServer engine when playing magnet or torrent streams.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = true,
                )

                val p2pEnabled = uiState.booleanSettings[DesktopDataStore.PREF_P2P_ENABLED] ?: (DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: true)
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
                }
            }
        }
    }
