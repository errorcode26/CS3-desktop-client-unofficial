package com.lagradost.cloudstream3.desktop.domain.player.interactor

import com.lagradost.cloudstream3.desktop.data.history.WatchHistoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.history.interactor.UpsertWatchHistory
import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.common.storage.WatchHistory

class SavePlaybackProgress(
    private val watchHistoryRepo: WatchHistoryRepository = WatchHistoryRepositoryImpl(),
    private val upsertWatchHistory: UpsertWatchHistory = UpsertWatchHistory(watchHistoryRepo),
) {
    suspend fun await(history: WatchHistory, forceNotify: Boolean = false) {
        upsertWatchHistory.await(history, forceNotify = forceNotify)
    }
}
