package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

sealed interface LibraryUiEvent : UiEvent {
    data class OnSelectSection(val section: LibrarySection) : LibraryUiEvent
    data class OnSelectTab(val tab: DesktopWatchType) : LibraryUiEvent
    data class OnBookmarkClick(val bookmark: DesktopBookmark) : LibraryUiEvent
    data class OnDeleteBookmark(val bookmarkId: String) : LibraryUiEvent
    data object OnDismissError : LibraryUiEvent
    data class OnSearchQueryChange(val query: String) : LibraryUiEvent
    data class OnSortOptionChange(val sortOption: SortOption) : LibraryUiEvent
    data class OnProviderFilterChange(val provider: String?) : LibraryUiEvent
    data class OnStartReLink(val bookmark: DesktopBookmark) : LibraryUiEvent
    data class OnSelectReLinkMatch(val bookmark: DesktopBookmark, val match: com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch) : LibraryUiEvent
    data class OnSelectReLinkAddon(val bookmark: DesktopBookmark, val item: com.lagradost.cloudstream3.desktop.explore.models.ExploreItem) : LibraryUiEvent
    data class OnChangeWatchType(val bookmarkId: String, val newType: DesktopWatchType) : LibraryUiEvent
    data class OnSearchGlobal(val title: String) : LibraryUiEvent
    data object OnDismissRecoveryModal : LibraryUiEvent
}
