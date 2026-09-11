package com.lagradost.cloudstream3.desktop.domain.history.interactor

import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GetContinueWatching(
    private val repository: WatchHistoryRepository,
) {
    fun subscribe(): Flow<List<WatchHistory>> {
        return repository.subscribeAll().map { allHistory ->
            filterContinueWatching(allHistory)
        }.flowOn(Dispatchers.IO)
    }

    suspend fun await(): List<WatchHistory> = withContext(Dispatchers.IO) {
        val all = repository.subscribeAll()
        // Compute from current state
        val historyList = repository.getByParent("") // fallback or fetch all
        filterContinueWatching(historyList)
    }

    companion object {
        fun filterContinueWatching(allHistory: List<WatchHistory>): List<WatchHistory> {
            val valid = allHistory.filter {
                it.apiName != "Offline" && !it.parentId.startsWith("offline") && it.parentId != "local"
            }
            val grouped = valid.groupBy { it.parentId }
            return grouped.mapNotNull { (_, histories) ->
                val hasAnyCompleted = histories.any {
                    it.duration > 0L && PlayerLinkHandler.isCompleted(it.position, it.duration)
                }
                val hasRealProgress = histories.any { it.position > 0L }

                // Discard shows that have no completed episodes and no active playback progress
                if (!hasAnyCompleted && !hasRealProgress) {
                    return@mapNotNull null
                }

                val inProgressOrQueued = histories.filter {
                    val isCompleted = it.duration > 0L && PlayerLinkHandler.isCompleted(it.position, it.duration)
                    !isCompleted && (it.position > 0L || hasAnyCompleted)
                }.maxByOrNull { it.updateTime }

                if (inProgressOrQueued != null) {
                    inProgressOrQueued
                } else {
                    val latestCompleted = histories.maxByOrNull { it.updateTime }
                    if (latestCompleted != null && (latestCompleted.episode != null || latestCompleted.season != null)) {
                        latestCompleted.copy(
                            episode = (latestCompleted.episode ?: 0) + 1,
                            position = 0L,
                            duration = 0L,
                            screenshotUrl = null,
                            episodeThumbnailUrl = null,
                        )
                    } else {
                        null
                    }
                }
            }.sortedByDescending { it.updateTime }
        }
    }
}
