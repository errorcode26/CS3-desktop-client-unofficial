package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.common.storage.DesktopWatchType

sealed interface LibraryUiEvent : UiEvent {
    data class OnSelectTab(val tab: DesktopWatchType) : LibraryUiEvent
    data class OnBookmarkClick(val apiName: String, val url: String) : LibraryUiEvent
    data class OnDeleteBookmark(val bookmarkId: String) : LibraryUiEvent
    data object OnDismissError : LibraryUiEvent
}
