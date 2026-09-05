package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.desktop.domain.player.interactor.SavePlaybackProgress
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory

internal object WatchHistoryCoordinator {

    /**
     * Saves the current episode watch progress.
     * If the current episode is >= 90% completed and [hasNextEpisode] is true,
     * it also creates or updates a queued placeholder for [nextEpisode] so the
     * user can smoothly continue watching from the home/continue-watching row.
     */
    suspend fun saveWithNextEpisodeQueue(
        history: WatchHistory,
        hasNextEpisode: Boolean,
        nextEpisode: Episode?,
        saveProgress: SavePlaybackProgress,
        forceNotify: Boolean = false,
    ) {
        val currentDurSec = history.duration
        val currentPosSec = history.position
        val percentage = if (currentDurSec > 0) currentPosSec.toFloat() / currentDurSec else 0f

        saveProgress.await(history, forceNotify = forceNotify)

        if (percentage >= 0.90f && hasNextEpisode && nextEpisode != null) {
            val existingNext = DesktopDataStore.getEpisodeWatched(
                parentId = history.parentId,
                episodeId = nextEpisode.data,
            )
            if (existingNext == null) {
                val nextEpHistory = WatchHistory(
                    parentId = history.parentId,
                    showName = history.showName,
                    showUrl = history.showUrl,
                    apiName = history.apiName,
                    posterUrl = history.posterUrl,
                    episodeThumbnailUrl = nextEpisode.posterUrl ?: history.posterUrl,
                    screenshotUrl = null,
                    episode = nextEpisode.episode,
                    season = nextEpisode.season,
                    episodeId = nextEpisode.data,
                    position = 0,
                    duration = 0,
                    updateTime = System.currentTimeMillis() + 1000,
                    episodeName = nextEpisode.name,
                    episodeDescription = nextEpisode.description,
                )
                saveProgress.await(nextEpHistory, forceNotify = forceNotify)
            } else {
                saveProgress.await(
                    existingNext.copy(
                        updateTime = System.currentTimeMillis() + 1000,
                        episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEpisode.posterUrl ?: history.posterUrl,
                    ),
                    forceNotify = forceNotify,
                )
            }
        }
    }
}
