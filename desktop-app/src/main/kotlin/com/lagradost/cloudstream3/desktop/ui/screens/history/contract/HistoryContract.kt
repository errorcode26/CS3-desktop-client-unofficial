package com.lagradost.cloudstream3.desktop.ui.screens.history.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.WatchHistory

@Immutable
data class HistoryUiState(
    val historyList: List<WatchHistory> = emptyList(),
    val isLoading: Boolean = true,
) : UiState

sealed interface HistoryUiEvent : UiEvent {
    object ClearAll : HistoryUiEvent
    data class RemoveItem(val parentId: String) : HistoryUiEvent
}

sealed interface HistoryUiEffect : UiEffect {
    data class ShowToast(val message: String) : HistoryUiEffect
}
