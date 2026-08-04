package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.launch

@Composable
fun SettingsPlayer(
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    var hwdec by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_HWDEC) ?: "auto") }
    var subSize by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SIZE) ?: "45") }
    var subColor by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_COLOR) ?: "#FFFFFF") }
    var subBg by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000") }
    var subFont by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_FONT)) }
    var ytdlFormat by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_YTDL_FORMAT) ?: "bestvideo[height<=?1080]+bestaudio/best") }
    var preferredQuality by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto") }
    var autoPlay by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true) }
    var waitForLinks by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY_WAIT_FOR_LINKS) ?: true) }
    var autoPlayTimeout by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "15000") }
    var useInterpolation by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_INTERPOLATION) ?: false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroupCard(title = "Playback Engine") {
            SettingsDropdownItem(
                label = "Hardware Acceleration",
                subtitle = "Choose how video decoding is handled by your system",
                options = listOf("auto-safe" to "Auto Safe", "auto-copy" to "Auto Copy", "no" to "Software Decoding (Off)"),
                currentValue = hwdec,
                onSelectionChanged = {
                    hwdec = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_HWDEC, it)
                    }
                },
            )

            SettingsToggleItem(
                label = "Smooth Video",
                subtitle = "Uses display-resample to eliminate frame pacing judder on high-refresh-rate screens",
                checked = useInterpolation,
                onCheckedChange = {
                    useInterpolation = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_INTERPOLATION, it)
                    }
                }
            )

            SettingsDropdownItem(
                label = "Preferred Stream Quality",
                subtitle = "The preferred video quality when playing native streams",
                options = listOf(
                    "Auto" to "Auto / Highest",
                    "2160p (4K)" to "2160p (4K)",
                    "1080p" to "1080p",
                    "720p" to "720p",
                    "480p" to "480p / SD",
                ),
                currentValue = preferredQuality,
                onSelectionChanged = {
                    preferredQuality = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_PREFERRED_QUALITY, it)
                    }
                },
            )

            SettingsDropdownItem(
                label = "yt-dlp Default Quality (Advanced)",
                subtitle = "Preferred video resolution when streaming via yt-dlp",
                options = listOf(
                    "bestvideo[height<=?1080]+bestaudio/best" to "1080p",
                    "bestvideo[height<=?720]+bestaudio/best" to "720p",
                    "bestvideo[height<=?480]+bestaudio/best" to "480p",
                    "best" to "Highest Available",
                ),
                currentValue = ytdlFormat,
                onSelectionChanged = {
                    ytdlFormat = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_YTDL_FORMAT, it)
                    }
                },
            )
        }

        SettingsGroupCard(title = "Auto-Play Features") {
            SettingsToggleItem(
                label = "Wait for links before Auto-playing",
                subtitle = "If disabled, the player will instantly play the first link it finds that matches your preferred quality.",
                checked = waitForLinks,
                onCheckedChange = {
                    waitForLinks = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY_WAIT_FOR_LINKS, it)
                    }
                },
            )

            SettingsToggleItem(
                label = "Enable Download Buttons",
                subtitle = "Shows download buttons on episodes and movies that open the link loader.",
                checked = !autoPlay,
                onCheckedChange = {
                    autoPlay = !it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY, !it)
                    }
                },
            )

            if (autoPlay) {
                SettingsDropdownItem(
                    label = "Playback Timeout",
                    subtitle = "How long to wait for a stream to load before falling back",
                    options = listOf(
                        "10000" to "10 Seconds",
                        "15000" to "15 Seconds",
                        "20000" to "20 Seconds",
                        "30000" to "30 Seconds",
                        "60000" to "60 Seconds",
                    ),
                    currentValue = autoPlayTimeout,
                    onSelectionChanged = {
                        autoPlayTimeout = it
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            DesktopDataStore.setKey(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT, it)
                        }
                    },
                )
            }
        }

        SettingsGroupCard(title = "Subtitles") {
            SettingsNavigationItem(
                label = "Subtitle Appearance",
                subtitle = "Customize colors, fonts, borders, and shadows",
                onClick = { onNavigateToSubScreen(SettingsSubScreen.SUBTITLE_EDITOR) },
            )
        }
    }
}
