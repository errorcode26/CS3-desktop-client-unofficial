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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
// 1. PLAYBACK & MEDIA CATEGORY HUB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerHubScreen(
    viewModel: SettingsViewModel,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Live state computations for dynamic badges
    val hwdec = uiState.stringSettings[PlayerConfig.PREF_HWDEC] ?: "auto-safe"
    val hwdecBadge = when (hwdec) {
        "auto-safe" -> "Auto-Safe GPU"
        "auto-copy" -> "Auto-Copy GPU"
        "no" -> "Software CPU"
        else -> "Hardware Accel"
    }

    val audioNorm = uiState.booleanSettings[PlayerConfig.PREF_AUDIO_NORMALIZATION] ?: false
    val audioBadge = if (audioNorm) "Normalization Active" else "Direct Audio"

    val subFont = uiState.stringSettings[PlayerConfig.PREF_SUB_FONT] ?: "Inter"
    val subSize = uiState.stringSettings[PlayerConfig.PREF_SUB_SIZE] ?: "45"
    val subtitleBadge = "$subFont • ${subSize}px"

    val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: true
    val skipEnabled = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true
    val autoSkipIntro = uiState.booleanSettings[PlayerConfig.PREF_AUTO_SKIP_INTRO] ?: false
    val autoPlayBadge = when {
        autoPlay && autoSkipIntro -> "Auto-Play & Skip"
        autoPlay -> "Auto-Play Active"
        skipEnabled -> "AniSkip Ready"
        else -> "Manual Selection"
    }

    val downloadThreads = (uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_THREADS] ?: 8f).toInt()
    val downloadBadge = "$downloadThreads Threads"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Video & Hardware Engine
        SettingsHubCard(
            icon = Icons.Outlined.PlayCircle,
            title = "Video & Hardware Engine",
            subtitle = "GPU hardware decoding acceleration, smooth display resample interpolation, pause metadata overlays, and yt-dlp format streams.",
            badge = hwdecBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_RENDERING_ENGINE) },
        )

        // Card 2: Audio Processing & Equalizer
        SettingsHubCard(
            icon = Icons.Outlined.GraphicEq,
            title = "Audio Processing & Equalizer",
            subtitle = "Volume normalization (dynamic range compression), dialogue boost, multi-channel downmixing, audio sync offset delay, and EQ sound presets.",
            badge = audioBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_AUDIO_EQ) },
        )

        // Card 3: Subtitle Styling Studio
        SettingsHubCard(
            icon = Icons.Outlined.Subtitles,
            title = "Subtitle Styling Studio",
            subtitle = "Interactive preview studio for font typography, font size, text colors, border outlines, drop shadow blur, and background opacity.",
            badge = subtitleBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.SUBTITLES) },
        )

        // Card 4: Stream Scraping & Priorities
        SettingsHubCard(
            icon = Icons.Outlined.Tune,
            title = "Stream Scraping & Priorities",
            subtitle = "Video quality resolution priority order (4K/1080p/720p), preferred audio language stack, and fallback subtitle language stack.",
            badge = "Priority Stack",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.STREAM_PRIORITIES) },
        )

        // Card 5: Auto-Play & Skip Automation
        SettingsHubCard(
            icon = Icons.Default.FastForward,
            title = "Auto-Play & Skip Automation",
            subtitle = "Automatic next episode stream connection, provider connection timeout, AniSkip opening/ending intro and outro skipping.",
            badge = autoPlayBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_AUTOPLAY_SKIP) },
        )

        // Card 6: Downloads & Offline Storage
        SettingsHubCard(
            icon = Icons.Outlined.Download,
            title = "Downloads & Storage Engine",
            subtitle = "Offline downloads directory, multi-threaded parallel download chunks, concurrent task limits, and screenshot export path.",
            badge = downloadBadge,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.PLAYER_DOWNLOADS) },
        )

        // Card 7: Keyboard Shortcuts & Hotkeys
        SettingsHubCard(
            icon = Icons.Outlined.Keyboard,
            title = "Keyboard Shortcuts & Hotkeys",
            subtitle = "Complete desktop keyboard reference for cinematic clean mode, borderless fullscreen, volume leveling, seek intervals, and subtitle sync hotkeys.",
            badge = "30+ Shortcuts",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.KEYBOARD_SHORTCUTS) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. VIDEO & HARDWARE RENDERING SUB-SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerRenderingScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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

        SettingsGroupCard(title = "Buffer Engine & Seeking") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_VIDEO_BUFFER_SIZE,
                label = "Video Cache Buffer RAM",
                subtitle = "Maximum memory allocated to demuxer stream cache (enforced 100 MB safe minimum)",
                options = listOf(
                    "100000000" to "100 MB (Default / Safe Minimum)",
                    "250000000" to "250 MB (Recommended for 1080p)",
                    "500000000" to "500 MB (Recommended for 4K)",
                    "1000000000" to "1000 MB (1 GB - High Bandwidth)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "100000000",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_VIDEO_BUFFER_LENGTH,
                label = "Forward Buffer Duration",
                subtitle = "Seconds of video stream demuxer reads ahead into RAM (enforced 30s minimum)",
                options = listOf(
                    "30" to "30 Seconds (Default / Safe Minimum)",
                    "60" to "60 Seconds (1 Minute)",
                    "120" to "120 Seconds (2 Minutes)",
                    "300" to "300 Seconds (5 Minutes)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "30",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_SEEK_DURATION,
                label = "Seek Step Duration",
                subtitle = "Time jumped when pressing Left/Right arrow keys, J/L keys, or mouse side buttons",
                options = listOf(
                    "5" to "5 Seconds",
                    "10" to "10 Seconds (Default)",
                    "15" to "15 Seconds",
                    "30" to "30 Seconds",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "10",
            )
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsToggle(
                key = PlayerConfig.PREF_SHOW_SERVER_QUALITY,
                label = "Show Server & Quality",
                subtitle = "Displays the active streaming server and video quality in the player top bar",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = false,
            )
        }

        SettingsGroupCard(title = "Advanced Engine") {
            MviSettingsDropdown(
                key = PlayerConfig.PREF_YTDL_FORMAT,
                label = "yt-dlp Format / Quality",
                subtitle = "Applies only to external YouTube and yt-dlp extracted stream links",
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. AUTO-PLAY & SKIP AUTOMATION SUB-SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayerAutoPlayScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: true
    val skipEnabled = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. COMPATIBILITY ALIASES
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPlayer(viewModel: SettingsViewModel, onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) =
    SettingsPlayerHubScreen(viewModel, onNavigateToSubScreen)

@Composable
fun SettingsPlayerPlaybackScreen(
    viewModel: SettingsViewModel,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) = SettingsPlayerHubScreen(viewModel, onNavigateToSubScreen)

