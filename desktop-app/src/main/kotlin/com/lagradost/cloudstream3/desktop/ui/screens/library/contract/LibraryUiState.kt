package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType

data class LibraryUiState(
    val bookmarks: List<DesktopBookmark> = emptyList(),
    val filteredBookmarks: List<DesktopBookmark> = emptyList(),
    val selectedTab: DesktopWatchType = DesktopWatchType.WATCHING,
    val gridScale: String = "Normal",
    val showError: String? = null,
) : UiState
