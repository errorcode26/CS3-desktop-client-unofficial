package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.utils.ExtractorLink

@Composable
fun ComposeMpvPlayer(
    link: ExtractorLink,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    BaseMpvPlayer(
        link = link,
        title = title,
        subtitles = subtitles,
        startPositionMs = startPositionMs,
        onPlaybackReady = onPlaybackReady,
        onPlaybackError = onPlaybackError,
        onFinished = onFinished,
        onPositionChange = onPositionChange,
        onCloseRequest = onCloseRequest,
        isExiting = isExiting,
        onFullscreenToggle = onFullscreenToggle,
        playerState = playerState,
        modifier = modifier,
    ) { videoCanvas, _ ->
        SwingPanel(
            background = androidx.compose.ui.graphics.Color.Black,
            factory = { videoCanvas },
            modifier = modifier,
        )
    }
}
