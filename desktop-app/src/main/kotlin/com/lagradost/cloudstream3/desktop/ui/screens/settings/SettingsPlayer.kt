package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore

@Composable
fun SettingsPlayer() {
    var hwdec by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_HWDEC) ?: "auto-copy") }
    var subSize by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SIZE) ?: "45") }
    var subColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_COLOR) ?: "#FFFFFF") }
    var subBg by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000") }
    var ytdlFormat by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080]+bestaudio/best") }
    var autoPlay by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true) }
    var autoPlayTimeout by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "15000") }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // --- Group 1: Playback Engine ---
        SettingsGroupCard(title = "Playback Engine") {
            SettingsDropdownItem(
                label = "Hardware Acceleration",
                subtitle = "Choose how video decoding is handled by your system",
                options = listOf("auto-safe" to "Auto Safe", "auto-copy" to "Auto Copy", "no" to "Software Decoding (Off)"),
                currentValue = hwdec,
                onSelectionChanged = {
                    hwdec = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_HWDEC, it)
                },
            )

            SettingsDropdownItem(
                label = "Default Quality",
                subtitle = "Preferred video resolution when streaming",
                options = listOf(
                    "bestvideo[height<=?1080]+bestaudio/best" to "1080p",
                    "bestvideo[height<=?720]+bestaudio/best" to "720p",
                    "bestvideo[height<=?480]+bestaudio/best" to "480p",
                    "best" to "Highest Available",
                ),
                currentValue = ytdlFormat,
                onSelectionChanged = {
                    ytdlFormat = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_YTDL_FORMAT, it)
                },
            )
        }

        // --- Group 2: Automation ---
        SettingsGroupCard(title = "Automation") {
            SettingsToggleItem(
                label = "Auto Play",
                subtitle = "Skip the links panel and start playing the best link immediately",
                checked = autoPlay,
                onCheckedChange = {
                    autoPlay = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY, it)
                },
            )

            if (autoPlay) {
                SettingsDropdownItem(
                    label = "Playback Timeout",
                    subtitle = "How long to wait for a stream to load before falling back",
                    options = listOf(
                        "10000" to "10 Seconds",
                        "15000" to "15 Seconds",
                        "30000" to "30 Seconds",
                        "60000" to "60 Seconds",
                    ),
                    currentValue = autoPlayTimeout,
                    onSelectionChanged = {
                        autoPlayTimeout = it
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT, it)
                    },
                )
            }
        }

        // --- Group 3: Subtitles ---
        SettingsGroupCard(title = "Subtitles") {
            SettingsDropdownItem(
                label = "Font Size",
                subtitle = "Adjust the size of the subtitle text",
                options = listOf("30" to "Small", "45" to "Medium", "60" to "Large", "75" to "Extra Large"),
                currentValue = subSize,
                onSelectionChanged = {
                    subSize = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_SIZE, it)
                },
            )

            SettingsDropdownItem(
                label = "Text Color",
                options = listOf("#FFFFFF" to "White", "#FFFF00" to "Yellow", "#00FFFF" to "Cyan"),
                currentValue = subColor,
                onSelectionChanged = {
                    subColor = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_COLOR, it)
                },
            )

            SettingsDropdownItem(
                label = "Background Style",
                subtitle = "Add a dark background box to subtitles for better readability",
                options = listOf("#00000000" to "Transparent", "#80000000" to "Semi-transparent Black"),
                currentValue = subBg,
                onSelectionChanged = {
                    subBg = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BG, it)
                },
            )
        }
    }
}
