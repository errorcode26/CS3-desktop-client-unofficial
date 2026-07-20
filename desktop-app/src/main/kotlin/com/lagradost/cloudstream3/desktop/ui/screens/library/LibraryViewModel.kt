package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : BaseMviViewModel<LibraryUiState, LibraryUiEvent, LibraryUiEffect>(
    initialState = LibraryUiState()
) {
    val filteredBookmarks: StateFlow<List<DesktopBookmark>> = uiState.map { it.filteredBookmarks }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.filteredBookmarks)

    val selectedTab: StateFlow<DesktopWatchType> = uiState.map { it.selectedTab }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.selectedTab)

    val showError: StateFlow<String?> = uiState.map { it.showError }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value.showError)

    init {
        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarksMap ->
                val allList = bookmarksMap.values.toList()
                updateState {
                    val currentTab = selectedTab
                    copy(
                        bookmarks = allList,
                        filteredBookmarks = allList.filter { it.watchType == currentTab.id }
                    )
                }
            }
        }
        viewModelScope.launch {
            AppearanceConfig.gridScale.collect { scale ->
                updateState { copy(gridScale = scale) }
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

    fun selectTab(tab: DesktopWatchType) {
        updateState {
            copy(
                selectedTab = tab,
                filteredBookmarks = bookmarks.filter { it.watchType == tab.id }
            )
        }
    }

    fun handleBookmarkClick(apiName: String, url: String) {
        val provider = APIHolder.getApiFromNameNull(apiName)
        if (provider != null) {
            sendEffect(LibraryUiEffect.Navigate(Screen.Details(provider.name, url)))
        } else {
            updateState {
                copy(showError = "The provider '$apiName' is not loaded. Please install or enable it first.")
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        BookmarksRepository.removeBookmark(bookmarkId)
    }

    fun dismissError() {
        updateState { copy(showError = null) }
    }
}
