package com.lagradost.cloudstream3.desktop.domain.history.interactor

import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoveWatchHistory(
    private val repository: WatchHistoryRepository,
) {
    suspend fun awaitByParent(parentId: String) = withContext(Dispatchers.IO) {
        repository.deleteByParent(parentId)
    }

    suspend fun awaitByEpisode(
        parentId: String,
        episodeId: String,
        season: Int? = null,
        episode: Int? = null,
        extraEpisodeIds: List<String> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        repository.deleteByEpisode(parentId, episodeId, season, episode, extraEpisodeIds)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        repository.deleteAll()
    }
}
