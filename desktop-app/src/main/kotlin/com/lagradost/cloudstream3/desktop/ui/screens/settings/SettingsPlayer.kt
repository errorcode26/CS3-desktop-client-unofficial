package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.common.logging.AppLogger
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// 1. PLAYBACK & VIDEO SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerPlaybackScreen(
    viewModel: SettingsViewModel,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true }
    val skipEnabled = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true
    var showSourcePriorityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        SettingsGroupCard(title = "Hardware Acceleration & Frame Pacing") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_HWDEC,
                label = "Hardware Acceleration",
                subtitle = "Choose how video decoding is handled by your GPU hardware",
                options = listOf(
                    "auto-safe" to "Auto Safe (Recommended)",
                    "auto-copy" to "Auto Copy (Fallback for older GPUs)",
                    "no" to "Software Decoding (CPU Only)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "auto-safe",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_INTERPOLATION,
                label = "Smooth Video (Display Resample)",
                subtitle = "Eliminates frame pacing judder on high-refresh-rate desktop displays",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )
        }

        SettingsGroupCard(title = "Stream Quality & Priority Ranking") {
            SettingsNavigationItem(
                label = "Source & Quality Priorities",
                subtitle = "Customize automatic stream ranking, resolution preferences, and server priorities",
                onClick = { showSourcePriorityDialog = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_PREFERRED_QUALITY,
                label = "Preferred Stream Quality",
                subtitle = "The preferred video quality when playing native streams",
                options = listOf(
                    "Auto" to "Auto / Highest Available",
                    "2160p (4K)" to "2160p (4K)",
                    "1080p" to "1080p (Full HD)",
                    "720p" to "720p (HD)",
                    "480p" to "480p / SD",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "Auto",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_YTDL_FORMAT,
                label = "yt-dlp Default Quality (Advanced)",
                subtitle = "Preferred video resolution when streaming via yt-dlp engine",
                options = listOf(
                    "bestvideo[height<=?1080]+bestaudio/best" to "1080p (Full HD)",
                    "bestvideo[height<=?720]+bestaudio/best" to "720p (HD)",
                    "bestvideo[height<=?480]+bestaudio/best" to "480p (SD)",
                    "best" to "Highest Available",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "bestvideo[height<=?1080]+bestaudio/best",
            )
        }

        SettingsGroupCard(title = "Stream Auto-Play & Timeout") {
            MviSettingsToggle(
                key = PlayerConfig.PREF_AUTO_PLAY,
                label = "Auto-Play Streams",
                subtitle = "Automatically select and stream the highest scoring seekable source when clicking an episode or movie",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = true,
            )

            if (autoPlay) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                MviSettingsDropdown(
                    key = PlayerConfig.PREF_AUTO_PLAY_TIMEOUT,
                    label = "Playback Timeout",
                    subtitle = "How long to wait for a stream to connect before falling back to next provider",
                    options = listOf(
                        "10000" to "10 Seconds",
                        "15000" to "15 Seconds (Default)",
                        "20000" to "20 Seconds",
                        "30000" to "30 Seconds",
                        "60000" to "60 Seconds",
                    ),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "15000",
                )
            }
        }

        SettingsGroupCard(title = "Intro & Outro Skipping (AniSkip)") {
            MviSettingsToggle(
                key = PlayerConfig.PREF_ENABLE_SKIP_INTERVALS,
                label = "Enable Intro & Outro Discovery",
                subtitle = "Discovers openings, endings, and recaps using AniSkip and embedded chapter markers",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = true,
            )

            if (skipEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUTO_SKIP_INTRO,
                    label = "Auto-Skip Openings & Intros",
                    subtitle = "Automatically skips intros without needing to press the on-screen skip button",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUTO_SKIP_OUTRO,
                    label = "Auto-Skip Endings & Outros",
                    subtitle = "Automatically jumps past ending theme songs and credits",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )
            }
        }

        SettingsGroupCard(title = "On-Screen Display & Overlays") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_PAUSE_INFO_MODE,
                label = "Pause Metadata Overlay",
                subtitle = "Control if and when title, episode synopsis, and age rating badges appear on pause",
                options = listOf(
                    "delay_5s" to "After 5 Seconds Idle (Recommended)",
                    "delay_10s" to "After 10 Seconds Idle",
                    "delay_20s" to "After 20 Seconds Idle",
                    "immediate" to "Immediately on Pause",
                    "off" to "Disabled (Always Clean Frame)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "delay_5s",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_PAUSE_SHOW_CAST,
                label = "Show Starring Cast on Pause",
                subtitle = "Displays lead actors and character roles alongside synopsis when paused",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = true,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_SHOW_CLOCK,
                label = "Show Clock in Player",
                subtitle = "Displays current real-world time in the top bar during playback",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_SHOW_END_TIME,
                label = "Show Estimated End Time",
                subtitle = "Displays when the current video will finish playing",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )
        }

        // Quick Launchers (Local Media & Streams)
        SettingsGroupCard(title = "Quick Launchers & Local Media") {
            SettingsNavigationItem(
                label = "Open Local Video File",
                subtitle = "Play an MP4, MKV, WebM, or AVI from your computer (Shortcut: Ctrl+O)",
                onClick = { com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.openLocalFileDialog() },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsNavigationItem(
                label = "Open Network Stream URL",
                subtitle = "Stream a direct HTTP or HLS .m3u8 link (Shortcut: Ctrl+U)",
                onClick = { com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.showNetworkStreamDialog = true },
            )
        }

        // Keyboard Shortcuts Reference
        SettingsGroupCard(title = "Keyboard Shortcuts & Hotkeys") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "Keyboard & Mouse Shortcuts Reference",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Explore gestures, hotkeys, and player playback shortcuts (Press F1 anywhere).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onNavigateToSubScreen(SettingsSubScreen.KEYBOARD_SHORTCUTS) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View Shortcuts")
                }
            }
        }
    }

    com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
        show = showSourcePriorityDialog,
        onDismissRequest = { showSourcePriorityDialog = false },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AUDIO PROCESSING & EQUALIZER SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerAudioScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val audioNorm = uiState.booleanSettings[PlayerConfig.PREF_AUDIO_NORMALIZATION] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false }
    val audioDelay = uiState.floatSettings[PlayerConfig.PREF_AUDIO_DELAY] ?: remember { DesktopDataStore.getKey<Float>(PlayerConfig.PREF_AUDIO_DELAY) ?: 0f }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Language & Track Preferences") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_PREFERRED_AUDIO_LANG,
                label = "Preferred Audio Language",
                subtitle = "Auto-select matching language in multi-audio streams & prioritize dual/multi audio links",
                options = PlayerConfig.GLOBAL_LANGUAGE_OPTIONS,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "auto",
            )
        }

        SettingsGroupCard(title = "Dynamic Range & Equalizer") {
            MviSettingsToggle(
                key = PlayerConfig.PREF_AUDIO_NORMALIZATION,
                label = "Volume Normalization (Stable Audio)",
                subtitle = "Boost quiet dialogue and compress loud explosions (Dynamic Range Compression)",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )

            if (audioNorm) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                MviSettingsDropdown(
                    key = PlayerConfig.PREF_AUDIO_NORM_STRENGTH,
                    label = "Normalization Strength",
                    subtitle = "How aggressive the volume leveling should be",
                    options = listOf(
                        "Low" to "Low (Subtle Compression)",
                        "Medium" to "Medium (Balanced)",
                        "Aggressive" to "Aggressive (Night Mode)",
                    ),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "Medium",
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_AUDIO_EQ_PRESET,
                label = "Equalizer Profile",
                subtitle = "Apply an acoustic EQ curve to shape sound frequencies",
                options = listOf(
                    "Flat" to "Flat (Neutral / Default)",
                    "Bass Boost" to "Bass Boost (Deep Lows)",
                    "Vocal Boost" to "Vocal Boost (Clear Dialogue)",
                    "Cinematic" to "Cinematic (V-Shape)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "Flat",
            )
        }

        SettingsGroupCard(title = "Spatial Immersion & Sync Latency") {
            MviSettingsToggle(
                key = PlayerConfig.PREF_AUDIO_SPATIAL,
                label = "3D Spatial Audio (Stereo Widener)",
                subtitle = "Expand the stereo soundstage for an immersive surround effect",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsSlider(
                key = PlayerConfig.PREF_AUDIO_DELAY,
                label = "Audio Sync (Delay Offset)",
                subtitle = "Fix Bluetooth latency by shifting audio track (Current: ${String.format("%.2f", audioDelay)}s)",
                valueRange = -1.0f..1.0f,
                steps = 40,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 0f,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_AUDIO_VOLUME_MAX,
                label = "Volume Overdrive (Boost to 200%)",
                subtitle = "Allows raising player volume beyond 100% for quiet recordings",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compatibility Aliases
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayer(viewModel: SettingsViewModel, onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) =
    SettingsPlayerPlaybackScreen(viewModel, onNavigateToSubScreen)

@Composable
fun SettingsPlayerRenderingScreen(viewModel: SettingsViewModel) =
    SettingsPlayerPlaybackScreen(viewModel)

@Composable
fun SettingsPlayerAutoPlayScreen(viewModel: SettingsViewModel) =
    SettingsPlayerPlaybackScreen(viewModel)

// ─────────────────────────────────────────────────────────────────────────────
// 5. SUB-SCREEN: DOWNLOADS & STORAGE ENGINE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerDownloadsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentPath by remember {
        mutableStateOf(DesktopDataStore.getKey<String>(DesktopDataStore.PREF_DOWNLOAD_PATH) ?: com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager.downloadsDir.absolutePath)
    }

    val downloadThreads = uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_THREADS] ?: (DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f)
    val maxConcurrent = uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT] ?: (DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Download Visibility & Storage Location") {
            MviSettingsToggle(
                key = DesktopDataStore.PREF_ENABLE_DOWNLOAD_BUTTONS,
                label = "Show Download Buttons",
                subtitle = "Display direct 1-click download buttons on movie details, episode cards, and right-click menus",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = true,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Download Storage Directory",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val selected = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.chooseDirectory(
                                title = "Select Download Directory",
                                category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.DOWNLOADS,
                                initialDirectory = currentPath,
                            )
                            if (selected != null) {
                                val newPath = selected.absolutePath
                                currentPath = newPath
                                scope.launch(Dispatchers.IO) {
                                    DesktopDataStore.setKey(DesktopDataStore.PREF_DOWNLOAD_PATH, newPath)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Folder...")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().open(File(currentPath))
                            } catch (e: Exception) {
                                AppLogger.e("Failed to open download folder in explorer", e)
                                AppToastManager.showError("Unable to open folder in system explorer")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Open in Explorer")
                    }
                }
            }
        }

        SettingsGroupCard(title = "Turbo Acceleration & Queue Limits") {
            MviSettingsSlider(
                key = DesktopDataStore.PREF_DOWNLOAD_THREADS,
                label = "Parallel Turbo Download Threads (Chunks)",
                subtitle = "${downloadThreads.toInt()} parallel chunk workers per file",
                valueRange = 1f..16f,
                steps = 14,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 8f,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsSlider(
                key = DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT,
                label = "Maximum Concurrent Active Downloads",
                subtitle = "${maxConcurrent.toInt()} simultaneous downloading tasks",
                valueRange = 1f..5f,
                steps = 3,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 2f,
            )
        }

        SettingsGroupCard(title = "Screenshots & Media Capture") {
            var currentScreenshotPath by remember {
                mutableStateOf(com.lagradost.common.platform.PlatformPaths.screenshotsDir.absolutePath)
            }
            val captureScope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Screenshot Output Directory",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentScreenshotPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val chooser = javax.swing.JFileChooser().apply {
                                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Select Screenshot Directory"
                                currentDirectory = File(currentScreenshotPath)
                            }
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                                val newPath = chooser.selectedFile.absolutePath
                                currentScreenshotPath = newPath
                                captureScope.launch(Dispatchers.IO) {
                                    DesktopDataStore.setKey(PlayerConfig.PREF_SCREENSHOT_DIR, newPath)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Folder...")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().open(File(currentScreenshotPath))
                            } catch (e: Exception) {
                                AppLogger.e("Failed to open screenshot folder in explorer", e)
                                AppToastManager.showError("Unable to open folder in system explorer")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Open in Explorer")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_SCREENSHOT_FORMAT,
                label = "Screenshot Format",
                subtitle = "Format used when saving video captures (Shortcuts: Shift+S or Ctrl+S)",
                options = listOf(
                    "png" to "PNG (Lossless Quality)",
                    "jpg" to "JPEG (High Quality, Compact)",
                    "webp" to "WebP (Modern Compact)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "png",
            )
        }

    }
}
