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
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// 1. MAIN PLAYBACK ENGINE HUB SCREEN (Clean Dashboard - Zero Infinite Scrolling)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayer(
    viewModel: SettingsViewModel,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val hwdec = uiState.stringSettings[PlayerConfig.PREF_HWDEC] ?: "auto-safe"
    val audioNorm = uiState.booleanSettings[PlayerConfig.PREF_AUDIO_NORMALIZATION] ?: false
    val eqProfile = uiState.stringSettings[PlayerConfig.PREF_AUDIO_EQ_PRESET] ?: "Flat"
    val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: true
    val skipIntervals = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true
    val downloadThreads = uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_THREADS] ?: 8f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

        // Hub Card 1: Video & Hardware Acceleration Engine
        PlayerHubCard(
            icon = Icons.Default.Speed,
            title = "Video & Hardware Engine",
            subtitle = "Hardware acceleration (HWDEC), smooth video frame pacing, stream quality ranking, yt-dlp quality, and local/network file launcher.",
            badge = if (hwdec == "no") "Software Only" else "HWDEC ${hwdec.replace("-", " ").replaceFirstChar { it.uppercase() }}",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_RENDERING_ENGINE) },
        )

        // Hub Card 2: Audio Processing & Equalizer
        PlayerHubCard(
            icon = Icons.Default.GraphicEq,
            title = "Audio Processing & Equalizer",
            subtitle = "Volume normalization (Dynamic Range Compression), EQ profile curves (Vocal/Bass/Cinema), 3D spatial audio, audio sync latency offset, and volume overdrive.",
            badge = if (audioNorm) "DRC Normalization ON" else if (eqProfile != "Flat") "$eqProfile EQ" else "Audio Processing",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_AUDIO_EQ) },
        )

        // Hub Card 3: Auto-Play & Skip Automation
        PlayerHubCard(
            icon = Icons.Default.FastForward,
            title = "Auto-Play & Skip Automation",
            subtitle = "Auto-play highest scoring streams, timeout fallback, automatic anime opening & ending skipping (AniSkip + chapter detection).",
            badge = if (skipIntervals) "AniSkip Active" else if (autoPlay) "Auto-Play ON" else "Manual Control",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_AUTOPLAY_SKIP) },
        )

        // Hub Card 4: Subtitles & Languages
        PlayerHubCard(
            icon = Icons.Default.Subtitles,
            title = "Subtitles & Languages",
            subtitle = "Preferred subtitle language, multi-audio defaults, and 1-click access to the Subtitle Styling Studio (fonts, borders, shadows, and colors).",
            badge = "Subtitle Studio",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.SUBTITLES) },
        )

        // Hub Card 5: Downloads & Storage Engine
        PlayerHubCard(
            icon = Icons.Default.Download,
            title = "Downloads & Storage Engine",
            subtitle = "Download storage folder, parallel chunk threads (1–16), concurrent queue manager, and quick 1-click open in Windows Explorer.",
            badge = "${downloadThreads.toInt()} Turbo Chunks",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_DOWNLOADS) },
        )
    }
}

@Composable
private fun PlayerHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. SUB-SCREEN: VIDEO & HARDWARE ENGINE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerRenderingScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val videoPlayerLauncher = LocalVideoPlayer.current

    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showSourcePriorityDialog by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf("") }

    CloudstreamAlertDialog(
        show = showNetworkStreamDialog,
        onDismissRequest = { showNetworkStreamDialog = false },
        title = { Text("Open Network Stream") },
        text = {
            Column {
                Text("Paste an HTTP/HTTPS stream link below:")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://example.com/video.mp4") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (streamUrl.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            videoPlayerLauncher(
                                VideoLaunchData(
                                    links = listOf(
                                        newExtractorLink(
                                            source = "Network Stream",
                                            name = "Network Stream",
                                            url = streamUrl,
                                            type = if (streamUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                        ) {
                                            this.quality = Qualities.Unknown.value
                                        },
                                    ),
                                    initialIndex = 0,
                                    title = "Network Stream",
                                    subtitles = emptyList(),
                                    startPositionMs = 0L,
                                    history = WatchHistory(
                                        parentId = "local",
                                        showName = "Network Stream",
                                        showUrl = streamUrl,
                                        apiName = "Local",
                                        posterUrl = null,
                                        episodeThumbnailUrl = null,
                                        screenshotUrl = null,
                                        episode = null,
                                        season = null,
                                        episodeId = "local",
                                        position = 0L,
                                        duration = 0L,
                                    ),
                                ),
                            )
                        }
                    }
                    showNetworkStreamDialog = false
                },
            ) {
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(onClick = { showNetworkStreamDialog = false }) {
                Text("Cancel")
            }
        },
    )

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

        SettingsGroupCard(title = "Local Media & Network Streams") {
            SettingsNavigationItem(
                label = "Open Local Video File",
                subtitle = "Browse your computer for an MP4, MKV, WebM, or AVI video file",
                onClick = {
                    val selectedFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                        title = "Select Video File",
                        allowedExtensions = listOf(".mp4", ".mkv", ".m3u8", ".webm", ".avi", ".mov", ".ts", ".flv"),
                    )
                    if (selectedFile != null && selectedFile.exists()) {
                        val filePath = selectedFile.absolutePath
                        scope.launch(Dispatchers.IO) {
                            videoPlayerLauncher(
                                VideoLaunchData(
                                    links = listOf(
                                        newExtractorLink(
                                            source = "Local File",
                                            name = selectedFile.name,
                                            url = filePath,
                                            type = if (filePath.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                        ) {
                                            this.quality = Qualities.Unknown.value
                                        },
                                    ),
                                    initialIndex = 0,
                                    title = selectedFile.name,
                                    subtitles = emptyList(),
                                    startPositionMs = 0L,
                                    history = WatchHistory(
                                        parentId = "local",
                                        showName = selectedFile.name,
                                        showUrl = filePath,
                                        apiName = "Local",
                                        posterUrl = null,
                                        episodeThumbnailUrl = null,
                                        screenshotUrl = null,
                                        episode = null,
                                        season = null,
                                        episodeId = "local",
                                        position = 0L,
                                        duration = 0L,
                                    ),
                                ),
                            )
                        }
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsNavigationItem(
                label = "Open Network Stream URL",
                subtitle = "Paste a direct HTTP / HLS .m3u8 video URL to play immediately",
                onClick = { showNetworkStreamDialog = true },
            )
        }
    }

    com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
        show = showSourcePriorityDialog,
        onDismissRequest = { showSourcePriorityDialog = false },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. SUB-SCREEN: AUDIO PROCESSING & EQUALIZER
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

        SettingsGroupCard(title = "Preferred Audio Track Language") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_PREFERRED_AUDIO_LANG,
                label = "Preferred Audio Language",
                subtitle = "Language to auto-select for multi-audio video streams",
                options = listOf(
                    "auto" to "Auto (Stream Default)",
                    "eng,en" to "English",
                    "jpn,ja" to "Japanese",
                    "hin,hi" to "Hindi",
                    "tel,te" to "Telugu",
                    "tam,ta" to "Tamil",
                    "spa,es" to "Spanish",
                    "fre,fra,fr" to "French",
                    "ger,deu,de" to "German",
                    "ita,it" to "Italian",
                    "por,pt" to "Portuguese",
                    "rus,ru" to "Russian",
                    "kor,ko" to "Korean",
                    "chi,zho,zh" to "Chinese",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "auto",
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. SUB-SCREEN: AUTO-PLAY & SKIP AUTOMATION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerAutoPlayScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true }
    val skipEnabled = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Stream Auto-Play") {
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
    }
}

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
                            } catch (_: Exception) {}
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
                            } catch (_: Exception) {}
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
