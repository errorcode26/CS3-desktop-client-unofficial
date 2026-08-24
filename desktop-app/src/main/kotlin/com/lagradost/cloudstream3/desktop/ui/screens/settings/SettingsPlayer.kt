package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
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
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun SettingsPlayer(
    viewModel: SettingsViewModel,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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

            SettingsGroupCard(title = "Local Media & Streams") {
                SettingsNavigationItem(
                    label = "Open Local File",
                    subtitle = "Browse your computer for a video file",
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

                SettingsNavigationItem(
                    label = "Open Network Stream",
                    subtitle = "Paste a direct video URL to play",
                    onClick = {
                        showNetworkStreamDialog = true
                    },
                )
            }

            SettingsGroupCard(title = "Audio Enhancements") {
                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUDIO_NORMALIZATION,
                    label = "Volume Normalization (Stable Audio)",
                    subtitle = "Boost quiet dialogue and compress loud explosions (Dynamic Range Compression)",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                val audioNorm = uiState.booleanSettings[PlayerConfig.PREF_AUDIO_NORMALIZATION] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false }
                if (audioNorm) {
                    MviSettingsDropdown(
                        key = PlayerConfig.PREF_AUDIO_NORM_STRENGTH,
                        label = "Normalization Strength",
                        subtitle = "How aggressive the volume leveling should be",
                        options = listOf("Low" to "Low", "Medium" to "Medium", "Aggressive" to "Aggressive"),
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = "Medium",
                    )
                }

                MviSettingsDropdown(
                    key = PlayerConfig.PREF_AUDIO_EQ_PRESET,
                    label = "Equalizer Profile",
                    subtitle = "Apply a custom EQ curve to shape the sound",
                    options = listOf(
                        "Flat" to "Flat (Default)",
                        "Bass Boost" to "Bass Boost",
                        "Vocal Boost" to "Vocal Boost (Clear Dialogue)",
                        "Cinematic" to "Cinematic (V-Shape)",
                    ),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "Flat",
                )

                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUDIO_SPATIAL,
                    label = "Spatial Audio (Stereo Widener)",
                    subtitle = "Expand the stereo field to create an immersive 3D surround effect",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                val audioDelay = uiState.floatSettings[PlayerConfig.PREF_AUDIO_DELAY] ?: remember { DesktopDataStore.getKey<Float>(PlayerConfig.PREF_AUDIO_DELAY) ?: 0f }
                MviSettingsSlider(
                    key = PlayerConfig.PREF_AUDIO_DELAY,
                    label = "Audio Sync (Delay Offset)",
                    subtitle = "Fix Bluetooth audio latency by shifting the audio track (Current: ${String.format("%.2f", audioDelay)}s)",
                    valueRange = -1.0f..1.0f,
                    steps = 40,
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = 0f,
                )

                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUDIO_VOLUME_MAX,
                    label = "Volume Overdrive (Boost to 200%)",
                    subtitle = "Increase the maximum volume limit to 200% for quiet videos",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )
            }

            SettingsGroupCard(title = "Playback Engine") {
                MviSettingsDropdown(
                    key = PlayerConfig.PREF_HWDEC,
                    label = "Hardware Acceleration",
                    subtitle = "Choose how video decoding is handled by your system",
                    options = listOf("auto-safe" to "Auto Safe", "auto-copy" to "Auto Copy", "no" to "Software Decoding (Off)"),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "auto-safe",
                )

                MviSettingsToggle(
                    key = PlayerConfig.PREF_INTERPOLATION,
                    label = "Smooth Video",
                    subtitle = "Uses display-resample to eliminate frame pacing judder on high-refresh-rate screens",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = false,
                )

                SettingsNavigationItem(
                    label = "Source & Quality Priorities",
                    subtitle = "Customize automatic stream ranking, resolution preferences, and server priorities",
                    onClick = { showSourcePriorityDialog = true },
                )

                MviSettingsDropdown(
                    key = PlayerConfig.PREF_PREFERRED_QUALITY,
                    label = "Preferred Stream Quality",
                    subtitle = "The preferred video quality when playing native streams",
                    options = listOf(
                        "Auto" to "Auto / Highest",
                        "2160p (4K)" to "2160p (4K)",
                        "1080p" to "1080p",
                        "720p" to "720p",
                        "480p" to "480p / SD",
                    ),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "Auto",
                )

                MviSettingsDropdown(
                    key = PlayerConfig.PREF_PREFERRED_AUDIO_LANG,
                    label = "Preferred Audio Language",
                    subtitle = "Language to auto-select for multi-audio streams",
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

                MviSettingsDropdown(
                    key = PlayerConfig.PREF_PREFERRED_SUB_LANG,
                    label = "Preferred Subtitle Language",
                    subtitle = "Language to auto-select for subtitles",
                    options = listOf(
                        "auto" to "Auto (Stream Default)",
                        "off" to "Off (Disabled by Default)",
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

                MviSettingsDropdown(
                    key = PlayerConfig.PREF_YTDL_FORMAT,
                    label = "yt-dlp Default Quality (Advanced)",
                    subtitle = "Preferred video resolution when streaming via yt-dlp",
                    options = listOf(
                        "bestvideo[height<=?1080]+bestaudio/best" to "1080p",
                        "bestvideo[height<=?720]+bestaudio/best" to "720p",
                        "bestvideo[height<=?480]+bestaudio/best" to "480p",
                        "best" to "Highest Available",
                    ),
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = "bestvideo[height<=?1080]+bestaudio/best",
                )
            }

            SettingsGroupCard(title = "Auto-Play Features") {
                MviSettingsToggle(
                    key = PlayerConfig.PREF_AUTO_PLAY_WAIT_FOR_LINKS,
                    label = "Wait for links before Auto-playing",
                    subtitle = "When enabled, waits for all providers to finish scraping to pick the best source. When disabled, plays immediately on the first available source.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = true,
                )

                // Note: The previous logic was "Enable Download Buttons" checked = !autoPlay
                // Because autoPlay is the internal key, but we want to present it as "Download Buttons"
                // To properly do this with MviSettingsToggle, we must invert the key representation.
                // But since MviSettingsToggle strictly binds the UI switch to the DB value,
                // if we want to invert it, we'd need a custom manual toggle.
                // Let's just rename it back to Auto Play for simplicity and accuracy,
                // or we use a manual SettingsToggleItem for this specific one.
                // Using a manual one is better to maintain exact UX.

                val autoPlay = uiState.booleanSettings[PlayerConfig.PREF_AUTO_PLAY] ?: remember { DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true }
                SettingsToggleItem(
                    label = "Enable Download Buttons",
                    subtitle = "Shows download buttons on episodes and movies that open the link loader.",
                    checked = !autoPlay,
                    onCheckedChange = {
                        viewModel.onEvent(com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent.OnUpdateBoolean(PlayerConfig.PREF_AUTO_PLAY, !it))
                    },
                )

                if (autoPlay) {
                    MviSettingsDropdown(
                        key = PlayerConfig.PREF_AUTO_PLAY_TIMEOUT,
                        label = "Playback Timeout",
                        subtitle = "How long to wait for a stream to load before falling back",
                        options = listOf(
                            "10000" to "10 Seconds",
                            "15000" to "15 Seconds",
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

            SettingsGroupCard(title = "Intro & Outro Skipping") {
                MviSettingsToggle(
                    key = PlayerConfig.PREF_ENABLE_SKIP_INTERVALS,
                    label = "Enable Intro & Outro Detection",
                    subtitle = "Automatically discovers openings, endings, and recaps using AniSkip and chapter metadata.",
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    defaultValue = true,
                )

                val skipEnabled = uiState.booleanSettings[PlayerConfig.PREF_ENABLE_SKIP_INTERVALS] ?: true
                if (skipEnabled) {
                    MviSettingsToggle(
                        key = PlayerConfig.PREF_AUTO_SKIP_INTRO,
                        label = "Auto-Skip Openings & Intros",
                        subtitle = "Automatically skips intros and anime openings without needing to click the on-screen button.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )

                    MviSettingsToggle(
                        key = PlayerConfig.PREF_AUTO_SKIP_OUTRO,
                        label = "Auto-Skip Endings & Outros",
                        subtitle = "Automatically skips outros and anime endings.",
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        defaultValue = false,
                    )
                }
            }

            SettingsGroupCard(title = "Subtitles") {
                SettingsNavigationItem(
                    label = "Subtitle Appearance",
                    subtitle = "Customize colors, fonts, borders, and shadows",
                    onClick = { onNavigateToSubScreen(SettingsSubScreen.SUBTITLES) },
                )
            }

            com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
                show = showSourcePriorityDialog,
                onDismissRequest = { showSourcePriorityDialog = false },
            )
        }
    }
}
