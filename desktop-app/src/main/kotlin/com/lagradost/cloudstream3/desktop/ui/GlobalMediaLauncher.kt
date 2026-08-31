package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.utils.NativeFileDialog
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object GlobalMediaLauncher {
    var showNetworkStreamDialog by mutableStateOf(false)
    val globalPlayerLauncher = AtomicReference<((VideoLaunchData) -> Unit)?>(null)

    fun openLocalFileDialog(
        scope: CoroutineScope = com.lagradost.cloudstream3.desktop.utils.appScope,
        launcher: ((VideoLaunchData) -> Unit)? = globalPlayerLauncher.get(),
    ) {
        val selectedFile = NativeFileDialog.open(
            title = "Select Video File",
            allowedExtensions = listOf(".mp4", ".mkv", ".m3u8", ".webm", ".avi", ".mov", ".ts", ".flv", ".mp3", ".flac", ".m4a"),
            category = NativeFileDialog.Category.MEDIA,
        )
        if (selectedFile != null && selectedFile.exists()) {
            playLocalFile(selectedFile, scope, launcher)
        }
    }

    fun playLocalFile(
        file: File,
        scope: CoroutineScope = com.lagradost.cloudstream3.desktop.utils.appScope,
        launcher: ((VideoLaunchData) -> Unit)? = globalPlayerLauncher.get(),
    ) {
        val filePath = file.absolutePath
        val targetLauncher = launcher ?: globalPlayerLauncher.get() ?: return
        scope.launch(Dispatchers.IO) {
            targetLauncher(
                VideoLaunchData(
                    links = listOf(
                        newExtractorLink(
                            source = "Local File",
                            name = file.name,
                            url = filePath,
                            type = if (filePath.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.quality = Qualities.Unknown.value
                        },
                    ),
                    initialIndex = 0,
                    title = file.name,
                    subtitles = emptyList(),
                    startPositionMs = 0L,
                    history = WatchHistory(
                        parentId = "local",
                        showName = file.name,
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

    fun playStreamUrl(
        url: String,
        scope: CoroutineScope = com.lagradost.cloudstream3.desktop.utils.appScope,
        launcher: ((VideoLaunchData) -> Unit)? = globalPlayerLauncher.get(),
    ) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val targetLauncher = launcher ?: globalPlayerLauncher.get() ?: return
        val isM3u8 = trimmed.contains(".m3u8", ignoreCase = true)
        val isDash = trimmed.contains(".mpd", ignoreCase = true)
        val linkType = when {
            isM3u8 -> ExtractorLinkType.M3U8
            isDash -> ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
        val streamName = trimmed.substringAfterLast("/").substringBefore("?").ifEmpty { "Network Stream" }

        scope.launch(Dispatchers.IO) {
            targetLauncher(
                VideoLaunchData(
                    links = listOf(
                        newExtractorLink(
                            source = "Network Stream",
                            name = streamName,
                            url = trimmed,
                            type = linkType,
                        ) {
                            this.quality = Qualities.Unknown.value
                        },
                    ),
                    initialIndex = 0,
                    title = streamName,
                    subtitles = emptyList(),
                    startPositionMs = 0L,
                    history = WatchHistory(
                        parentId = "network",
                        showName = streamName,
                        showUrl = trimmed,
                        apiName = "Network",
                        posterUrl = null,
                        episodeThumbnailUrl = null,
                        screenshotUrl = null,
                        episode = null,
                        season = null,
                        episodeId = "network",
                        position = 0L,
                        duration = 0L,
                    ),
                ),
            )
        }
    }

    @androidx.compose.runtime.Composable
    fun GlobalNetworkStreamDialog() {
        if (!showNetworkStreamDialog) return
        var streamUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
            show = showNetworkStreamDialog,
            onDismissRequest = { showNetworkStreamDialog = false },
            title = { androidx.compose.material3.Text("Open Network Stream") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Paste a direct HTTP / HLS .m3u8 stream link below:")
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = streamUrl,
                        onValueChange = { streamUrl = it },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { androidx.compose.material3.Text("https://example.com/video.m3u8") },
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (streamUrl.isNotBlank()) {
                            playStreamUrl(streamUrl, scope)
                        }
                        showNetworkStreamDialog = false
                    },
                ) {
                    androidx.compose.material3.Text("Play")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNetworkStreamDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }
}
