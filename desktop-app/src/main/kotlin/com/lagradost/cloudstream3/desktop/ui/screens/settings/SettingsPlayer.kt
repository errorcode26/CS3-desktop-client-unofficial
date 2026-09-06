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

