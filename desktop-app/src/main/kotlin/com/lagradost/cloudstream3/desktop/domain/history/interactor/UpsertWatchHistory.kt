package com.lagradost.cloudstream3.desktop.domain.history.interactor

import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpsertWatchHistory(
    private val repository: WatchHistoryRepository,
) {
    suspend fun await(history: WatchHistory, forceNotify: Boolean = false) = withContext(Dispatchers.IO) {
        repository.upsert(history, forceNotify = forceNotify)
    }

    suspend fun markEpisodeWatched(
        parentId: String,
        episodeId: String,
        showName: String,
        showUrl: String,
        apiName: String,
        posterUrl: String? = null,
        episode: Int? = null,
        season: Int? = null,
        episodeName: String? = null,
        episodeDescription: String? = null,
        episodeThumbnailUrl: String? = null,
        isWatched: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val existing = repository.getByEpisode(parentId, episodeId)
        val duration = existing?.duration?.takeIf { it > 0 } ?: 1000L
        val position = if (isWatched) duration else 0L

        val updated = WatchHistory(
            parentId = parentId,
            episodeId = episodeId,
            showName = showName,
            showUrl = showUrl,
            apiName = apiName,
            posterUrl = posterUrl ?: existing?.posterUrl,
            episodeThumbnailUrl = episodeThumbnailUrl ?: existing?.episodeThumbnailUrl,
            screenshotUrl = existing?.screenshotUrl,
            episode = episode ?: existing?.episode,
            season = season ?: existing?.season,
            position = position,
            duration = duration,
            updateTime = System.currentTimeMillis(),
            episodeName = episodeName ?: existing?.episodeName,
            episodeDescription = episodeDescription ?: existing?.episodeDescription,
        )
        repository.upsert(updated)
    }

    companion object {
        const val WATCHED_THRESHOLD = 0.85f

        fun isWatched(position: Long, duration: Long): Boolean {
            if (duration <= 0) return false
            return (position.toFloat() / duration.toFloat()) >= WATCHED_THRESHOLD
        }
    }
}
