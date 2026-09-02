package com.lagradost.cloudstream3.desktop.domain.history.interactor

import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class GetWatchHistory(
    private val repository: WatchHistoryRepository,
) {
    fun subscribeAll(): Flow<List<WatchHistory>> {
        return repository.subscribeAll().flowOn(Dispatchers.IO)
    }

    fun subscribeByParent(parentId: String): Flow<List<WatchHistory>> {
        return repository.subscribeByParent(parentId).flowOn(Dispatchers.IO)
    }

    suspend fun awaitByParent(parentId: String): List<WatchHistory> = withContext(Dispatchers.IO) {
        repository.getByParent(parentId)
    }

    suspend fun awaitByEpisode(parentId: String, episodeId: String): WatchHistory? = withContext(Dispatchers.IO) {
        repository.getByEpisode(parentId, episodeId)
    }
}
