package com.lagradost.cloudstream3.desktop.domain.history.repository

import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.flow.Flow

interface WatchHistoryRepository {
    fun subscribeAll(): Flow<List<WatchHistory>>
    fun subscribeByParent(parentId: String): Flow<List<WatchHistory>>
    suspend fun getByParent(parentId: String): List<WatchHistory>
    suspend fun getByEpisode(parentId: String, episodeId: String): WatchHistory?
    suspend fun upsert(history: WatchHistory, forceNotify: Boolean = false)
    suspend fun deleteByParent(parentId: String)
    suspend fun deleteByEpisode(
        parentId: String,
        episodeId: String,
        season: Int? = null,
        episode: Int? = null,
        extraEpisodeIds: List<String> = emptyList(),
    )
    suspend fun deleteAll()
}
