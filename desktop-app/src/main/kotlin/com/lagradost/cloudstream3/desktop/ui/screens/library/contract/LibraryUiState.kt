package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

enum class SortOption(val title: String) {
    DATE_ADDED_DESC("Date Added (Newest)"),
    DATE_ADDED_ASC("Date Added (Oldest)"),
    ALPHA_ASC("Alphabetical (A-Z)"),
    ALPHA_DESC("Alphabetical (Z-A)"),
}

data class LibraryUiState(
    val bookmarks: List<DesktopBookmark> = emptyList(),
    val filteredBookmarks: List<DesktopBookmark> = emptyList(),
    val selectedTab: DesktopWatchType = DesktopWatchType.WATCHING,
    val posterWidthDp: Int = 190,
    val showError: String? = null,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_ADDED_DESC,
    val selectedProvider: String? = null,
    val availableProviders: List<String> = emptyList(),
) : UiState
