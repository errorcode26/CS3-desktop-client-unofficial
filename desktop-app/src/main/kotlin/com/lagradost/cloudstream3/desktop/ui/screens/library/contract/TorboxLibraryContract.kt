package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxStorageType
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.WatchHistory

enum class TorrentFilter(val label: String) {
    All("All"),
    Completed("Completed"),
    Downloading("Downloading"),
}

@Immutable
data class TorboxLibraryUiState(
    val apiKey: String = "",
    val selectedStorageType: TorboxStorageType = TorboxStorageType.TORRENTS,
    val itemsList: List<TorboxTorrentData> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedFilter: TorrentFilter = TorrentFilter.All,
    val isAddingItem: Boolean = false,
    val addItemError: String? = null,
    val isDeleting: Boolean = false,
    val resolvingItemId: Int? = null,
    val resolvingFileId: Int? = null,
    val recentHistory: List<WatchHistory> = emptyList(),
) : UiState {
    val filteredItems: List<TorboxTorrentData>
        get() = itemsList.filter { item ->
            val matchesQuery = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                TorrentFilter.All -> true
                TorrentFilter.Completed -> item.isCompleted
                TorrentFilter.Downloading -> !item.isCompleted
            }
            matchesQuery && matchesFilter
        }
}

sealed interface TorboxLibraryUiEvent : UiEvent {
    data object OnLoadApiKey : TorboxLibraryUiEvent
    data class OnRefresh(val force: Boolean = false) : TorboxLibraryUiEvent
    data class OnSelectStorageType(val type: TorboxStorageType) : TorboxLibraryUiEvent
    data class OnSearchQueryChange(val query: String) : TorboxLibraryUiEvent
    data class OnFilterChange(val filter: TorrentFilter) : TorboxLibraryUiEvent
    data class OnAddItem(val link: String, val onSuccess: () -> Unit) : TorboxLibraryUiEvent
    data class OnDeleteItem(val item: TorboxTorrentData, val onSuccess: () -> Unit) : TorboxLibraryUiEvent
    data class OnPlayItem(
        val item: TorboxTorrentData,
        val file: TorboxFile?,
        val onLaunchPlayer: (VideoLaunchData) -> Unit,
    ) : TorboxLibraryUiEvent
    data class OnResumeFromHistory(
        val history: WatchHistory,
        val onLaunchPlayer: (VideoLaunchData) -> Unit,
    ) : TorboxLibraryUiEvent
    data class OnRemoveHistory(val history: WatchHistory) : TorboxLibraryUiEvent
    data object OnDismissError : TorboxLibraryUiEvent
    data object OnDismissAddItemError : TorboxLibraryUiEvent
}

sealed interface TorboxLibraryUiEffect : UiEffect {
    data class ShowToast(val message: String) : TorboxLibraryUiEffect
}
