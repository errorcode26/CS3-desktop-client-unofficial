package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu

@Composable
fun SettingsPlayer() {
    var hwdec by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_HWDEC) ?: "auto-copy") }
    var subSize by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SIZE) ?: "45") }
    var subColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_COLOR) ?: "#FFFFFF") }
    var subBg by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000") }
    var ytdlFormat by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080]+bestaudio/best") }
    var autoPlay by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true) }
    var autoPlayTimeout by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "15000") }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("Global Playback Engine Options", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(24.dp))

            // Hardware Acceleration
            PlayerDropdownSetting(
                label = "Hardware Acceleration",
                options = listOf("auto-safe" to "Auto Safe", "auto-copy" to "Auto Copy", "no" to "Software Decoding (Off)"),
                currentValue = hwdec,
                onSelectionChanged = {
                    hwdec = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_HWDEC, it)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Default Quality
            PlayerDropdownSetting(
                label = "Default Quality",
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

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Auto Play", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Auto Play Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Auto Play", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Skip the links panel and start playing immediately", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoPlay,
                    onCheckedChange = {
                        autoPlay = it
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY, it)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto Play Timeout
            PlayerDropdownSetting(
                label = "Playback Timeout",
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

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Subtitles", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Size
            PlayerDropdownSetting(
                label = "Font Size",
                options = listOf("30" to "Small", "45" to "Medium", "60" to "Large", "75" to "Extra Large"),
                currentValue = subSize,
                onSelectionChanged = {
                    subSize = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_SIZE, it)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle Color
            PlayerDropdownSetting(
                label = "Text Color",
                options = listOf("#FFFFFF" to "White", "#FFFF00" to "Yellow", "#00FFFF" to "Cyan"),
                currentValue = subColor,
                onSelectionChanged = {
                    subColor = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_COLOR, it)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle Background
            PlayerDropdownSetting(
                label = "Background Style",
                options = listOf("#00000000" to "Transparent", "#80000000" to "Semi-transparent Black"),
                currentValue = subBg,
                onSelectionChanged = {
                    subBg = it
                    DesktopDataStore.setKey(PlayerConfig.PREF_SUB_BG, it)
                },
            )
        }
}

@Composable
fun PlayerDropdownSetting(
    label: String,
    options: List<Pair<String, String>>,
    currentValue: String,
    onSelectionChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))

        Box {
            FilledTonalButton(onClick = { expanded = true }) {
                Text(options.find { it.first == currentValue }?.second ?: currentValue)
            }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelectionChanged(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
