package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
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
                    val availableProvs = allList.map { it.apiName }.distinct().sorted()
                    val newSelectedProv = if (selectedProvider in availableProvs) selectedProvider else null
                    copy(
                        bookmarks = allList,
                        availableProviders = availableProvs,
                        selectedProvider = newSelectedProv,
                    ).applyFilters()
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
            is LibraryUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query).applyFilters() }
            is LibraryUiEvent.OnSortOptionChange -> updateState { copy(sortOption = event.sortOption).applyFilters() }
            is LibraryUiEvent.OnProviderFilterChange -> updateState { copy(selectedProvider = event.provider).applyFilters() }
        }
    }

    private fun LibraryUiState.applyFilters(): LibraryUiState {
        var result = bookmarks.filter { it.watchType == selectedTab.id }

        if (selectedProvider != null) {
            result = result.filter { it.apiName == selectedProvider }
        }

        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        result = when (sortOption) {
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.dateAdded }
            SortOption.DATE_ADDED_ASC -> result.sortedBy { it.dateAdded }
            SortOption.ALPHA_ASC -> result.sortedBy { it.name.lowercase() }
            SortOption.ALPHA_DESC -> result.sortedByDescending { it.name.lowercase() }
        }

        return copy(filteredBookmarks = result)
    }

    private fun selectTab(tab: DesktopWatchType) {
        updateState {
            copy(selectedTab = tab).applyFilters()
        }
    }

    private fun handleBookmarkClick(apiName: String, url: String) {
        val provider = APIHolder.getApiFromNameNull(apiName)
        if (provider != null) {
            sendEffect(LibraryUiEffect.Navigate(Config.Details(provider.name, url, null, null, null, false)))
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
