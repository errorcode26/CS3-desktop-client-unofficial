package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.BookmarksRepository
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class LibraryViewModel : BaseMviViewModel<LibraryUiState, LibraryUiEvent, LibraryUiEffect>(
    initialState = LibraryUiState(),
) {
    private var reLinkSearchJob: Job? = null

    init {
        viewModelScope.launch {
            BookmarksRepository.bookmarksFlow.collect { bookmarksMap ->
                val allList = bookmarksMap.values.toList()
                val installed = APIHolder.allProviders.map { it.name }.toSet()
                updateState {
                    val availableProvs = allList.map { it.apiName }.distinct().sorted()
                    val newSelectedProv = if (selectedProvider in availableProvs) selectedProvider else null
                    copy(
                        bookmarks = allList,
                        availableProviders = availableProvs,
                        selectedProvider = newSelectedProv,
                        installedProviderNames = installed,
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
            is LibraryUiEvent.OnBookmarkClick -> handleBookmarkClick(event.bookmark)
            is LibraryUiEvent.OnDeleteBookmark -> deleteBookmark(event.bookmarkId)
            is LibraryUiEvent.OnDismissError -> dismissError()
            is LibraryUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query).applyFilters() }
            is LibraryUiEvent.OnSortOptionChange -> updateState { copy(sortOption = event.sortOption).applyFilters() }
            is LibraryUiEvent.OnProviderFilterChange -> updateState { copy(selectedProvider = event.provider).applyFilters() }
            is LibraryUiEvent.OnStartReLink -> startReLink(event.bookmark)
            is LibraryUiEvent.OnSelectReLinkMatch -> selectReLinkMatch(event.bookmark, event.provider, event.match)
            is LibraryUiEvent.OnChangeWatchType -> changeWatchType(event.bookmarkId, event.newType)
            is LibraryUiEvent.OnSearchGlobal -> searchGlobal(event.title)
            is LibraryUiEvent.OnDismissRecoveryModal -> dismissRecoveryModal()
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

    private fun handleBookmarkClick(bookmark: DesktopBookmark) {
        val provider = APIHolder.allProviders.firstOrNull {
            it.name == bookmark.apiName && it.mainUrl.isNotBlank() && bookmark.url.startsWith(it.mainUrl)
        } ?: APIHolder.getApiFromNameNull(bookmark.apiName)
        if (provider != null) {
            sendEffect(LibraryUiEffect.Navigate(Config.Details(provider.name, bookmark.url, null, null, null, false)))
        } else {
            startReLink(bookmark)
        }
    }

    private fun startReLink(bookmark: DesktopBookmark) {
        updateState {
            copy(
                orphanRecoveryBookmark = bookmark,
                isSearchingMatches = true,
                matchedResults = emptyList(),
            )
        }
        reLinkSearchJob?.cancel()
        reLinkSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val activeProviders = APIHolder.allProviders.filter { it.hasMainPage || it.supportedTypes.isNotEmpty() }
            val resultsList = CopyOnWriteArrayList<Pair<MainAPI, SearchResponse>>()

            val jobs = activeProviders.map { p ->
                launch {
                    try {
                        val searchRes = SafePluginInvoker.invokeOrNull(p.name, "search") {
                            p.search(bookmark.name)
                        }
                        val matches = searchRes?.filterIsInstance<SearchResponse>() ?: emptyList()
                        matches.take(3).forEach { resp ->
                            resultsList.add(p to resp)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            jobs.forEach { it.join() }

            updateState {
                copy(
                    isSearchingMatches = false,
                    matchedResults = resultsList.toList(),
                )
            }
        }
    }

    private fun selectReLinkMatch(bookmark: DesktopBookmark, provider: MainAPI, match: SearchResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = bookmark.copy(
                url = match.url,
                apiName = provider.name,
                posterUrl = match.posterUrl ?: bookmark.posterUrl,
            )
            BookmarksRepository.addBookmark(updated)
            updateState {
                copy(
                    orphanRecoveryBookmark = null,
                    isSearchingMatches = false,
                    matchedResults = emptyList(),
                )
            }
        }
    }

    private fun changeWatchType(bookmarkId: String, newType: DesktopWatchType) {
        val bookmark = uiState.value.bookmarks.find { it.id == bookmarkId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = bookmark.copy(watchType = newType.id)
            BookmarksRepository.addBookmark(updated)
        }
    }

    private fun searchGlobal(title: String) {
        updateState { copy(orphanRecoveryBookmark = null) }
        sendEffect(LibraryUiEffect.Navigate(Config.Search))
    }

    private fun dismissRecoveryModal() {
        reLinkSearchJob?.cancel()
        updateState {
            copy(
                orphanRecoveryBookmark = null,
                isSearchingMatches = false,
                matchedResults = emptyList(),
            )
        }
    }

    private fun deleteBookmark(bookmarkId: String) {
        BookmarksRepository.removeBookmark(bookmarkId)
    }

    private fun dismissError() {
        updateState { copy(showError = null) }
    }
}
