package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopWatchType
import kotlinx.coroutines.launch

class LibraryViewModel : BaseMviViewModel<LibraryUiState, LibraryUiEvent, LibraryUiEffect>(
    initialState = LibraryUiState(),
) {
    init {
        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarksMap ->
                val allList = bookmarksMap.values.toList()
                updateState {
                    val currentTab = selectedTab
                    copy(
                        bookmarks = allList,
                        filteredBookmarks = allList.filter { it.watchType == currentTab.id },
                    )
                }
            }
        }
        viewModelScope.launch {
            AppearanceConfig.posterWidthDp.collect { width ->
                updateState { copy(posterWidthDp = width) }
            }
        }
    }

    override fun handleEvent(event: LibraryUiEvent) {
        when (event) {
            is LibraryUiEvent.OnSelectTab -> selectTab(event.tab)
            is LibraryUiEvent.OnBookmarkClick -> handleBookmarkClick(event.apiName, event.url)
            is LibraryUiEvent.OnDeleteBookmark -> deleteBookmark(event.bookmarkId)
            is LibraryUiEvent.OnDismissError -> dismissError()
        }
    }

    private fun selectTab(tab: DesktopWatchType) {
        updateState {
            copy(
                selectedTab = tab,
                filteredBookmarks = bookmarks.filter { it.watchType == tab.id },
            )
        }
    }

    private fun handleBookmarkClick(apiName: String, url: String) {
        val provider = APIHolder.getApiFromNameNull(apiName)
        if (provider != null) {
            sendEffect(LibraryUiEffect.Navigate(Screen.Details(provider.name, url)))
        } else {
            updateState {
                copy(showError = "The provider '$apiName' is not loaded. Please install or enable it first.")
            }
        }
    }

    private fun deleteBookmark(bookmarkId: String) {
        BookmarksRepository.removeBookmark(bookmarkId)
    }

    private fun dismissError() {
        updateState { copy(showError = null) }
    }
}
