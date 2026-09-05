package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore

/**
 * Audio processing, language preference, dynamic range compression, and audio sync offset screen.
 */
@Composable
fun SettingsPlayerAudioScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showPriorityDialog by remember { mutableStateOf(false) }

    val audioNorm = uiState.booleanSettings[PlayerConfig.PREF_AUDIO_NORMALIZATION] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false }
    val audioDelay = uiState.floatSettings[PlayerConfig.PREF_AUDIO_DELAY] ?: remember { DesktopDataStore.getKey<Float>(PlayerConfig.PREF_AUDIO_DELAY) ?: 0f }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Audio Track & Language Preferences") {
            SettingsNavigationItem(
                label = "Audio Language Priorities",
                subtitle = "Configure ranked level of preference for automatic audio track matching & dual-audio scoring",
                onClick = { showPriorityDialog = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_PREFERRED_AUDIO_LANG,
                label = "Default Audio Language",
                subtitle = "Fallback audio language when priority list is not configured or matches no stream tracks",
                options = PlayerConfig.GLOBAL_LANGUAGE_OPTIONS,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "auto",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsNavigationItem(
                label = "Subtitle & Styling Preferences",
                subtitle = "Configure companion subtitle languages, font styles, and custom typography",
                onClick = { SettingsSession.selectedLeaf = LeafTab.SUBTITLES },
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

    com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
        show = showPriorityDialog,
        initialTab = 1,
        onDismissRequest = { showPriorityDialog = false },
    )
}
