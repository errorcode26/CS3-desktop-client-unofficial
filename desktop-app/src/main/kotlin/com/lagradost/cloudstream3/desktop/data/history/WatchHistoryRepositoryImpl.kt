package com.lagradost.cloudstream3.desktop.data.history

import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WatchHistoryRepositoryImpl : WatchHistoryRepository {

    override fun subscribeAll(): Flow<List<WatchHistory>> {
        return DesktopDataStore.historyUpdates.map {
            withContext(Dispatchers.IO) {
                DesktopDataStore.getAllWatchHistory()
            }
        }
    }

    override fun subscribeByParent(parentId: String): Flow<List<WatchHistory>> {
        return DesktopDataStore.historyUpdates.map {
            withContext(Dispatchers.IO) {
                DesktopDataStore.getWatchHistoryByParent(parentId)
            }
        }
    }

    override suspend fun getByParent(parentId: String): List<WatchHistory> = withContext(Dispatchers.IO) {
        DesktopDataStore.getWatchHistoryByParent(parentId)
    }

    override suspend fun getByEpisode(parentId: String, episodeId: String): WatchHistory? = withContext(Dispatchers.IO) {
        DesktopDataStore.getEpisodeWatched(parentId, episodeId)
    }

    override suspend fun upsert(history: WatchHistory, forceNotify: Boolean) = withContext(Dispatchers.IO) {
        DesktopDataStore.setLastWatched(history, forceNotify = forceNotify)
    }

    override suspend fun deleteByParent(parentId: String) = withContext(Dispatchers.IO) {
        DesktopDataStore.removeWatchHistory(parentId)
    }

    override suspend fun deleteByEpisode(parentId: String, episodeId: String) = withContext(Dispatchers.IO) {
        DesktopDataStore.removeEpisodeWatched(parentId, episodeId)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        DesktopDataStore.clearAllWatchHistory()
    }
}
