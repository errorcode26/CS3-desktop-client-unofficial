package com.lagradost.cloudstream3.desktop.ui.screens.history.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.WatchHistory

@Immutable
data class HistoryUiState(
    val historyList: List<WatchHistory> = emptyList(),
    val isLoading: Boolean = true,
    val orphanRecoveryHistory: WatchHistory? = null,
    val orphanExploreItem: ExploreItem? = null,
    val isSearchingMatches: Boolean = false,
    val matchedResults: List<ProviderMatch> = emptyList(),
) : UiState

sealed interface HistoryUiEvent : UiEvent {
    data object ClearAll : HistoryUiEvent
    data class RemoveItem(val parentId: String) : HistoryUiEvent
    data class OnStartReLink(val history: WatchHistory) : HistoryUiEvent
    data class OnSelectReLinkMatch(
        val history: WatchHistory,
        val match: ProviderMatch,
    ) : HistoryUiEvent
    data class OnSelectReLinkAddon(
        val history: WatchHistory,
        val item: ExploreItem,
    ) : HistoryUiEvent
    data object OnDismissRecoveryModal : HistoryUiEvent
}

sealed interface HistoryUiEffect : UiEffect {
    data class ShowToast(val message: String) : HistoryUiEffect
}
