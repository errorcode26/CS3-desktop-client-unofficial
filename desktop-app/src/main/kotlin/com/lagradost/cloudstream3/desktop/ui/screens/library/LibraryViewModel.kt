package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.GetBookmarks
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.RemoveBookmark
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.ToggleBookmark
import com.lagradost.cloudstream3.desktop.domain.category.interactor.SetItemCategory
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.relink.ReLinkSearchCoordinator
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.DesktopWatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getBookmarks: GetBookmarks = AppContainerHolder.container.getBookmarks,
    private val toggleBookmark: ToggleBookmark = AppContainerHolder.container.toggleBookmark,
    private val removeBookmark: RemoveBookmark = AppContainerHolder.container.removeBookmark,
    private val setItemCategory: SetItemCategory = AppContainerHolder.container.setItemCategory,
) : BaseMviViewModel<LibraryUiState, LibraryUiEvent, LibraryUiEffect>(
    initialState = LibraryUiState(),
) {
    private var reLinkSearchJob: Job? = null

    init {
        viewModelScope.launch {
            getBookmarks.subscribeAll().collect { bookmarksMap ->
                val allList = bookmarksMap.values.toList()
                val installed = APIHolder.allProviders.map { it.name }.toSet()
                val provMap = APIHolder.allProviders.associateBy { it.name }
                updateState {
                    val availableProvs = allList.map { it.apiName }.distinct().sorted()
                    val newSelectedProv = if (selectedProvider in availableProvs) selectedProvider else null
                    copy(
                        bookmarks = allList,
                        availableProviders = availableProvs,
                        selectedProvider = newSelectedProv,
                        installedProviderNames = installed,
                        providerMap = provMap,
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
            is LibraryUiEvent.OnSelectSection -> updateState { copy(selectedSection = event.section) }
            is LibraryUiEvent.OnSelectTab -> selectTab(event.tab)
            is LibraryUiEvent.OnBookmarkClick -> handleBookmarkClick(event.bookmark)
            is LibraryUiEvent.OnDeleteBookmark -> deleteBookmark(event.bookmarkId)
            is LibraryUiEvent.OnDismissError -> dismissError()
            is LibraryUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query).applyFilters() }
            is LibraryUiEvent.OnSortOptionChange -> updateState { copy(sortOption = event.sortOption).applyFilters() }
            is LibraryUiEvent.OnProviderFilterChange -> updateState { copy(selectedProvider = event.provider).applyFilters() }
            is LibraryUiEvent.OnStartReLink -> startReLink(event.bookmark)
            is LibraryUiEvent.OnSelectReLinkMatch -> selectReLinkMatch(event.bookmark, event.match)
            is LibraryUiEvent.OnSelectReLinkAddon -> selectReLinkAddon(event.bookmark, event.item)
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
        val initialExplore = ExploreItem(
            id = bookmark.name,
            type = "series",
            name = bookmark.name,
            posterUrl = bookmark.posterUrl,
        )
        updateState {
            copy(
                orphanRecoveryBookmark = bookmark,
                orphanExploreItem = initialExplore,
                isSearchingMatches = true,
                matchedResults = emptyList(),
            )
        }
        reLinkSearchJob?.cancel()
        reLinkSearchJob = viewModelScope.launch(Dispatchers.IO) {
            launch {
                try {
                    val enriched = ReLinkSearchCoordinator.resolveExploreItem(
                        rawTitle = bookmark.name,
                        posterUrl = bookmark.posterUrl,
                        isSeriesHint = null,
                    )
                    updateState { copy(orphanExploreItem = enriched) }
                } catch (_: Exception) {}
            }

            try {
                ReLinkSearchCoordinator.searchRealProvidersProgressive(
                    searchTitle = bookmark.name,
                    onUpdate = { matches ->
                        updateState { copy(matchedResults = matches) }
                    },
                )
            } catch (_: Exception) {
            } finally {
                updateState { copy(isSearchingMatches = false) }
            }
        }
    }

    private fun selectReLinkMatch(bookmark: DesktopBookmark, match: ProviderMatch) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DesktopDataStore.migrateWatchHistoryAndBookmark(
                    oldApiName = bookmark.apiName,
                    oldUrl = bookmark.url,
                    newApiName = match.providerName,
                    newUrl = match.searchResponse.url,
                    newName = match.searchResponse.name,
                    newPosterUrl = match.searchResponse.posterUrl ?: bookmark.posterUrl,
                )
                val newId = DesktopDataStore.bookmarkId(match.providerName, match.searchResponse.url)
                val updated = DesktopBookmark(
                    id = newId,
                    apiName = match.providerName,
                    url = match.searchResponse.url,
                    name = match.searchResponse.name,
                    posterUrl = match.searchResponse.posterUrl ?: bookmark.posterUrl,
                    watchType = bookmark.watchType,
                    dateAdded = bookmark.dateAdded,
                )
                toggleBookmark.saveBookmark(updated)
                if (bookmark.id != newId) {
                    removeBookmark.await(bookmark.id)
                }
                updateState {
                    copy(
                        orphanRecoveryBookmark = null,
                        orphanExploreItem = null,
                        isSearchingMatches = false,
                        matchedResults = emptyList(),
                    )
                }
                sendEffect(
                    LibraryUiEffect.Navigate(
                        Config.Details(
                            providerName = match.providerName,
                            url = match.searchResponse.url,
                            preloadedName = match.displayTitle,
                            preloadedPoster = match.searchResponse.posterUrl ?: bookmark.posterUrl,
                        ),
                    ),
                )
            } catch (e: Exception) {
                AppLogger.e("LibraryViewModel: Failed to save re-linked bookmark: ${e.message}")
            }
        }
    }

    private fun selectReLinkAddon(bookmark: DesktopBookmark, item: ExploreItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stremioUrl = "stremio://${item.type}/${item.id}"
                val newApiName = "Stremio"
                DesktopDataStore.migrateWatchHistoryAndBookmark(
                    oldApiName = bookmark.apiName,
                    oldUrl = bookmark.url,
                    newApiName = newApiName,
                    newUrl = stremioUrl,
                    newName = item.name,
                    newPosterUrl = item.posterUrl ?: bookmark.posterUrl,
                )
                val newId = DesktopDataStore.bookmarkId(newApiName, stremioUrl)
                val updated = DesktopBookmark(
                    id = newId,
                    apiName = newApiName,
                    url = stremioUrl,
                    name = item.name,
                    posterUrl = item.posterUrl ?: bookmark.posterUrl,
                    watchType = bookmark.watchType,
                    dateAdded = bookmark.dateAdded,
                )
                toggleBookmark.saveBookmark(updated)
                if (bookmark.id != newId) {
                    removeBookmark.await(bookmark.id)
                }
                updateState {
                    copy(
                        orphanRecoveryBookmark = null,
                        orphanExploreItem = null,
                        isSearchingMatches = false,
                        matchedResults = emptyList(),
                    )
                }
                sendEffect(
                    LibraryUiEffect.Navigate(
                        Config.Details(
                            providerName = newApiName,
                            url = stremioUrl,
                            preloadedName = item.name,
                            preloadedPoster = item.posterUrl ?: bookmark.posterUrl,
                        ),
                    ),
                )
            } catch (e: Exception) {
                AppLogger.e("LibraryViewModel: Failed to transfer bookmark to Addon: ${e.message}")
            }
        }
    }

    private fun changeWatchType(bookmarkId: String, newType: DesktopWatchType) {
        viewModelScope.launch(Dispatchers.IO) {
            setItemCategory.await(bookmarkId, newType.id)
        }
    }

    private fun searchGlobal(title: String) {
        updateState { copy(orphanRecoveryBookmark = null, orphanExploreItem = null) }
        sendEffect(LibraryUiEffect.Navigate(Config.Search))
    }

    private fun dismissRecoveryModal() {
        reLinkSearchJob?.cancel()
        updateState {
            copy(
                orphanRecoveryBookmark = null,
                orphanExploreItem = null,
                isSearchingMatches = false,
                matchedResults = emptyList(),
            )
        }
    }

    private fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            removeBookmark.await(bookmarkId)
        }
    }

    private fun dismissError() {
        updateState { copy(showError = null) }
    }
}
