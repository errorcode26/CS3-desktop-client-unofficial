package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object DiscordRpcCoordinator {

    fun updatePlaying(
        launchData: VideoLaunchData?,
        positionSeconds: Long,
        durationSeconds: Long,
        isPaused: Boolean,
    ) {
        if (launchData == null) return
        val title = launchData.title ?: launchData.history.showName
        val season = launchData.history.season
        val episode = launchData.history.episode
        val episodeInfo = when {
            season != null && episode != null -> "S$season • E$episode"
            episode != null -> "Episode $episode"
            else -> null
        }
        DiscordRpcManager.updatePlaying(
            title = title,
            episodeInfo = episodeInfo,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            isPaused = isPaused,
            posterUrl = launchData.history.posterUrl,
        )
    }

    fun attachPauseObserver(
        scope: CoroutineScope,
        playerState: PlayerState,
        getLaunchData: () -> VideoLaunchData?,
    ) {
        scope.launch(Dispatchers.IO) {
            playerState.isPaused.collect { isPaused ->
                val currentData = getLaunchData() ?: return@collect
                val currentPosSec = playerState.positionMs.value / 1000L
                val durSec = playerState.durationMs.value / 1000L
                updatePlaying(
                    launchData = currentData,
                    positionSeconds = currentPosSec,
                    durationSeconds = durSec,
                    isPaused = isPaused,
                )
            }
        }
    }
}
