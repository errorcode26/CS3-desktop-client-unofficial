package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal object DiscordRpcCoordinator {

    fun updatePlaying(
        launchData: VideoLaunchData?,
        positionSeconds: Long,
        durationSeconds: Long,
        isPaused: Boolean,
    ) {
        if (launchData == null) return
        val showName = launchData.history.showName.takeIf { it.isNotBlank() }
        val title = showName ?: launchData.title ?: "Playing Media"
        val season = launchData.history.season
        val episode = launchData.history.episode
        val epName = launchData.history.episodeName?.takeIf { it.isNotBlank() && !it.startsWith("Episode ", ignoreCase = true) }
        val episodeInfo = when {
            season != null && episode != null -> if (epName != null) "S$season • E$episode: $epName" else "S$season • E$episode"
            episode != null -> if (epName != null) "Episode $episode: $epName" else "Episode $episode"
            showName != null && launchData.title != null && launchData.title != showName -> launchData.title
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

    fun attachObservers(
        scope: CoroutineScope,
        playerState: PlayerState,
        launchDataFlow: Flow<VideoLaunchData?>,
        getLaunchData: () -> VideoLaunchData?,
    ) {
        // 1. Episode transition reactivity: immediately updates when next episode launchData is assigned
        scope.launch(Dispatchers.IO) {
            var lastTrackedEpisodeKey: String? = null
            launchDataFlow.collect { launch ->
                val epId = launch?.history?.episodeId
                val trackKey = if (launch != null && epId != null) {
                    "${epId}_${launch.history.season}_${launch.history.episode}_${launch.history.episodeName}"
                } else null
                if (launch != null && trackKey != null && trackKey != lastTrackedEpisodeKey) {
                    lastTrackedEpisodeKey = trackKey
                    val currentPosSec = (launch.startPositionMs / 1000L).coerceAtLeast(playerState.positionMs.value / 1000L)
                    val durSec = launch.history.duration.takeIf { it > 0 } ?: (playerState.durationMs.value / 1000L)
                    val isPaused = playerState.isPaused.value
                    updatePlaying(
                        launchData = launch,
                        positionSeconds = currentPosSec,
                        durationSeconds = durSec,
                        isPaused = isPaused,
                    )
                }
            }
        }

        // 2. Immediate Pause/Resume reactivity
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

        // 3. Instant Duration Discovery (fires the moment MPV demuxes stream headers)
        scope.launch(Dispatchers.IO) {
            playerState.durationMs.collect { durMs ->
                if (durMs <= 0L) return@collect
                val currentData = getLaunchData() ?: return@collect
                val currentPosSec = playerState.positionMs.value / 1000L
                val durSec = durMs / 1000L
                val isPaused = playerState.isPaused.value
                updatePlaying(
                    launchData = currentData,
                    positionSeconds = currentPosSec,
                    durationSeconds = durSec,
                    isPaused = isPaused,
                )
            }
        }

        // 4. Instant Seek Synchronization (sampled once per second)
        scope.launch(Dispatchers.IO) {
            var lastEmittedSec = -1L
            playerState.positionMs.collect { posMs ->
                val currentPosSec = posMs / 1000L
                if (currentPosSec == lastEmittedSec) return@collect
                lastEmittedSec = currentPosSec
                val currentData = getLaunchData() ?: return@collect
                val durSec = playerState.durationMs.value / 1000L
                val isPaused = playerState.isPaused.value
                updatePlaying(
                    launchData = currentData,
                    positionSeconds = currentPosSec,
                    durationSeconds = durSec,
                    isPaused = isPaused,
                )
            }
        }
    }

    fun attachPauseObserver(
        scope: CoroutineScope,
        playerState: PlayerState,
        getLaunchData: () -> VideoLaunchData?,
    ) {
        attachObservers(
            scope = scope,
            playerState = playerState,
            launchDataFlow = emptyFlow(),
            getLaunchData = getLaunchData,
        )
    }
}
