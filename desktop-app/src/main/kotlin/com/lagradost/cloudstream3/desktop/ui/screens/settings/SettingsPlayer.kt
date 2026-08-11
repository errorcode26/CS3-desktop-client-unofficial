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
    
    var audioNorm by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false) }
    var audioNormStrength by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUDIO_NORM_STRENGTH) ?: "Medium") }
    var audioMax by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_VOLUME_MAX) ?: false) }
    var audioSpatial by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_SPATIAL) ?: false) }
    var audioEqPreset by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUDIO_EQ_PRESET) ?: "Flat") }
    var audioDelay by remember { mutableStateOf(DesktopDataStore.getKey<Float>(PlayerConfig.PREF_AUDIO_DELAY) ?: 0f) }
    var autoPlayTimeout by remember { mutableStateOf(DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "15000") }
    var useInterpolation by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_INTERPOLATION) ?: false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val videoPlayerLauncher = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
        var showNetworkStreamDialog by remember { mutableStateOf(false) }

        var streamUrl by remember { mutableStateOf("") }
        com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
            show = showNetworkStreamDialog,
            onDismissRequest = { showNetworkStreamDialog = false },
            title = { androidx.compose.material3.Text("Open Network Stream") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Paste an HTTP/HTTPS stream link below:")
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = streamUrl,
                        onValueChange = { streamUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { androidx.compose.material3.Text("https://example.com/video.mp4") }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (streamUrl.isNotBlank()) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                videoPlayerLauncher(
                                    com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                                        links = listOf(
                                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                                source = "Network Stream",
                                                name = "Network Stream",
                                                url = streamUrl,
                                                type = if (streamUrl.contains(".m3u8", ignoreCase = true)) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                                            ) {
                                                this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                                            }
                                        ),
                                        initialIndex = 0,
                                        title = "Network Stream",
                                        subtitles = emptyList(),
                                        startPositionMs = 0L,
                                        history = com.lagradost.common.storage.WatchHistory(
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
                                            duration = 0L
                                        )
                                    )
                                )
                            }
                        }
                        showNetworkStreamDialog = false
                    }
                ) {
                    androidx.compose.material3.Text("Play")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNetworkStreamDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )

        SettingsGroupCard(title = "Local Media & Streams") {
            SettingsNavigationItem(
                label = "Open Local File",
                subtitle = "Browse your computer for a video file",
                onClick = {
                    val dialog = java.awt.FileDialog(null as? java.awt.Frame, "Select Video File", java.awt.FileDialog.LOAD)
                    dialog.isVisible = true
                    if (dialog.directory != null && dialog.file != null) {
                        val filePath = java.io.File(dialog.directory, dialog.file).absolutePath
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            videoPlayerLauncher(
                                com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                                    links = listOf(
                                        com.lagradost.cloudstream3.utils.newExtractorLink(
                                            source = "Local File",
                                            name = dialog.file,
                                            url = filePath,
                                            type = if (filePath.contains(".m3u8", ignoreCase = true)) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                                        ) {
                                            this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                                        }
                                    ),
                                    initialIndex = 0,
                                    title = dialog.file,
                                    subtitles = emptyList(),
                                    startPositionMs = 0L,
                                    history = com.lagradost.common.storage.WatchHistory(
                                        parentId = "local",
                                        showName = dialog.file,
                                        showUrl = filePath,
                                        apiName = "Local",
                                        posterUrl = null,
                                        episodeThumbnailUrl = null,
                                        screenshotUrl = null,
                                        episode = null,
                                        season = null,
                                        episodeId = "local",
                                        position = 0L,
                                        duration = 0L
                                    )
                                )
                            )
                        }
                    }
                },
            )

            SettingsNavigationItem(
                label = "Open Network Stream",
                subtitle = "Paste a direct video URL to play",
                onClick = {
                    showNetworkStreamDialog = true
                },
            )
        }

        SettingsGroupCard(title = "Audio Enhancements") {
            SettingsToggleItem(
                label = "Volume Normalization (Stable Audio)",
                subtitle = "Boost quiet dialogue and compress loud explosions (Dynamic Range Compression)",
                checked = audioNorm,
                onCheckedChange = {
                    audioNorm = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_NORMALIZATION, it)
                    }
                }
            )

            if (audioNorm) {
                SettingsDropdownItem(
                    label = "Normalization Strength",
                    subtitle = "How aggressive the volume leveling should be",
                    options = listOf(
                        "Low" to "Low",
                        "Medium" to "Medium",
                        "Aggressive" to "Aggressive",
                    ),
                    currentValue = audioNormStrength,
                    onSelectionChanged = {
                        audioNormStrength = it
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_NORM_STRENGTH, it)
                        }
                    }
                )
            }

            SettingsDropdownItem(
                label = "Equalizer Profile",
                subtitle = "Apply a custom EQ curve to shape the sound",
                options = listOf(
                    "Flat" to "Flat (Default)",
                    "Bass Boost" to "Bass Boost",
                    "Vocal Boost" to "Vocal Boost (Clear Dialogue)",
                    "Cinematic" to "Cinematic (V-Shape)",
                ),
                currentValue = audioEqPreset,
                onSelectionChanged = {
                    audioEqPreset = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_EQ_PRESET, it)
                    }
                }
            )

            SettingsToggleItem(
                label = "Spatial Audio (Stereo Widener)",
                subtitle = "Expand the stereo field to create an immersive 3D surround effect",
                checked = audioSpatial,
                onCheckedChange = {
                    audioSpatial = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_SPATIAL, it)
                    }
                }
            )

            SettingsSliderItem(
                label = "Audio Sync (Delay Offset)",
                subtitle = "Fix Bluetooth audio latency by shifting the audio track (Current: ${String.format("%.2f", audioDelay)}s)",
                value = audioDelay,
                valueRange = -1.0f..1.0f,
                steps = 40,
                onValueChange = {
                    audioDelay = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_DELAY, it)
                    }
                }
            )

            SettingsToggleItem(
                label = "Volume Overdrive (Boost to 200%)",
                subtitle = "Increase the maximum volume limit to 200% for quiet videos",
                checked = audioMax,
                onCheckedChange = {
                    audioMax = it
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        DesktopDataStore.setKey(PlayerConfig.PREF_AUDIO_VOLUME_MAX, it)
                    }
                }
            )
        }

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
                },
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
