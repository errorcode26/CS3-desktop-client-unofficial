package com.lagradost.cloudstream3.desktop.ui.screens.search

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiState
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_SEARCH_HISTORY = "search_history"
private const val MAX_HISTORY_SIZE = 20

class SearchViewModel : BaseMviViewModel<SearchUiState, SearchUiEvent, SearchUiEffect>(
    initialState = SearchUiState(),
) {

    private var searchJob: kotlinx.coroutines.Job? = null
    private var lastSearchedQuery: String = ""

    init {
        // Collect real providers reactively
        viewModelScope.launch {
            ActiveProviderRepository.allRealProviders.collectLatest { providers ->
                updateState { copy(providers = providers) }
            }
        }

        // Collect current selected provider reactively from the shared domain repository
        viewModelScope.launch {
            ActiveProviderRepository.currentSelectedProvider.collectLatest { provider ->
                updateState {
                    copy(
                        selectedProviderName = provider?.name,
                        selectedProviderSource = provider?.sourcePlugin,
                    )
                }
            }
        }

        // Load search history
        viewModelScope.launch(Dispatchers.IO) {
            val history = DesktopDataStore.getKey<List<String>>(PREF_SEARCH_HISTORY) ?: emptyList()
            updateState { copy(searchHistory = history) }
        }

        // Debounced search query
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(350)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        searchJob?.cancel()
                        updateState { copy(searchResultsGrouped = null, isLoadingSearch = false, searchSuggestions = emptyList(), showSuggestions = false) }
                    } else {
                        search()
                    }
                }
        }

        // Debounced search suggestions
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.length >= 2) {
                        val suggestions = SearchSuggestionApi.getSuggestions(trimmed, uiState.value.searchHistory)
                        updateState { copy(searchSuggestions = suggestions, showSuggestions = suggestions.isNotEmpty()) }
                    } else {
                        updateState { copy(searchSuggestions = emptyList(), showSuggestions = false) }
                    }
                }
        }

        // Remote plugin icons
        viewModelScope.launch {
            DesktopRepositoryManager.remotePluginIcons.collectLatest { icons ->
                updateState { copy(pluginIcons = icons) }
            }
        }
    }

    override fun handleEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query) }
            is SearchUiEvent.OnSearch -> {
                updateState { copy(showSuggestions = false) }
                addToHistory(uiState.value.searchQuery)
                search(force = true)
            }
            is SearchUiEvent.OnClearSearch -> {
                searchJob?.cancel()
                lastSearchedQuery = ""
                updateState { copy(searchQuery = "", searchResultsGrouped = null, isLoadingSearch = false, searchSuggestions = emptyList(), showSuggestions = false) }
            }
            is SearchUiEvent.OnSelectSuggestion -> {
                updateState { copy(searchQuery = event.query, showSuggestions = false) }
                if (event.submitSearch) {
                    addToHistory(event.query)
                    search(force = true)
                }
            }
            is SearchUiEvent.OnDismissSuggestions -> updateState { copy(showSuggestions = false) }
            is SearchUiEvent.OnToggleGlobalSearch -> {
                updateState { copy(isGlobalSearchEnabled = event.enabled, showSuggestions = false) }
                if (uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnProviderSelected -> {
                ActiveProviderRepository.setSelectedProviderByName(event.providerName, event.sourcePlugin)
                if (!uiState.value.isGlobalSearchEnabled && uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnToggleCategory -> {
                val current = uiState.value.selectedCategories
                val updated = if (event.category in current) {
                    current - event.category
                } else {
                    current + event.category
                }
                updateState { copy(selectedCategories = updated) }
            }
            is SearchUiEvent.OnClearCategories -> updateState { copy(selectedCategories = emptySet()) }
            is SearchUiEvent.OnRemoveSearchHistoryItem -> {
                val updated = uiState.value.searchHistory.filter { it != event.query }
                updateState { copy(searchHistory = updated) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(PREF_SEARCH_HISTORY, updated)
                }
            }
            is SearchUiEvent.OnClearSearchHistory -> {
                updateState { copy(searchHistory = emptyList()) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(PREF_SEARCH_HISTORY, emptyList<String>())
                }
            }
            is SearchUiEvent.OnLoadMore -> loadMore()
        }
    }

    private var currentPage = 1

    private fun addToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = uiState.value.searchHistory.toMutableList()
        current.remove(trimmed) // Deduplicate
        current.add(0, trimmed) // Prepend
        val capped = current.take(MAX_HISTORY_SIZE)
        updateState { copy(searchHistory = capped) }
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_SEARCH_HISTORY, capped)
        }
    }

    private fun search(force: Boolean = false) {
        val query = uiState.value.searchQuery
        if (query.isBlank() || (!force && query == lastSearchedQuery)) return

        lastSearchedQuery = query
        currentPage = 1

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            updateState { copy(isLoadingSearch = true, isLoadingMore = false, canPaginate = true, searchResultsGrouped = null) }
            try {
                val providers = uiState.value.providers

                val activeProviders = if (uiState.value.isGlobalSearchEnabled) {
                    providers.filter { it.hasMainPage || it.supportedTypes.isNotEmpty() }
                } else {
                    val selName = uiState.value.selectedProviderName
                    val selSource = uiState.value.selectedProviderSource
                    val active = providers.find {
                        (selName != null && (it.name == selName || it.name == selName.substringAfter("::"))) &&
                            (selSource == null || it.sourcePlugin == selSource)
                    } ?: providers.firstOrNull()
                    active?.let { listOf(it) } ?: emptyList()
                }

                val tempResults = java.util.concurrent.ConcurrentHashMap<String, Pair<com.lagradost.cloudstream3.MainAPI, List<SearchResponse>>>()

                activeProviders.map { p ->
                    launch {
                        com.lagradost.common.logging.AppLogger.i("Plugin:${p.name}", "Searching query: '$query'")
                        val res = SafePluginInvoker.invokeOrNull(
                            tag = "Search:${p.name}",
                            timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                        ) {
                            p.search(query, 1)
                        }
                        if (res != null && res.items.isNotEmpty()) {
                            com.lagradost.common.logging.AppLogger.i("Plugin:${p.name}", "Found ${res.items.size} results for '$query'")
                            val uniqueKey = "${p.name}::${p.sourcePlugin ?: ""}"
                            tempResults[uniqueKey] = Pair(p, res.items)
                            updateState { copy(searchResultsGrouped = tempResults.toMap()) }
                        } else {
                            com.lagradost.common.logging.AppLogger.i("Plugin:${p.name}", "No results found for '$query'")
                        }
                    }
                }.forEach { it.join() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                DesktopErrorReporter.report("Search failed", e)
            } finally {
                updateState { copy(isLoadingSearch = false) }
            }
        }
    }

    private fun loadMore() {
        val state = uiState.value
        val query = state.searchQuery
        if (state.isGlobalSearchEnabled || state.isLoadingSearch || state.isLoadingMore || !state.canPaginate || query.isBlank()) return

        val activeProvider = state.providers.find {
            it.name == state.selectedProviderName &&
                (state.selectedProviderSource == null || it.sourcePlugin == state.selectedProviderSource)
        } ?: state.providers.firstOrNull() ?: return
        val uniqueKey = "${activeProvider.name}::${activeProvider.sourcePlugin ?: ""}"
        val currentGrouped = state.searchResultsGrouped ?: return
        val currentPair = currentGrouped[uniqueKey] ?: return
        val currentItems = currentPair.second

        val nextPage = currentPage + 1
        viewModelScope.launch {
            updateState { copy(isLoadingMore = true) }
            try {
                com.lagradost.common.logging.AppLogger.i("Plugin:${activeProvider.name}", "Loading more search results (page $nextPage) for '$query'")
                val res = SafePluginInvoker.invokeOrNull(
                    tag = "SearchMore:${activeProvider.name}",
                    timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                ) {
                    activeProvider.search(query, nextPage)
                }

                if (res != null && res.items.isNotEmpty()) {
                    val existingUrls = currentItems.map { it.url }.toSet()
                    val newUniqueItems = res.items.filter { it.url !in existingUrls }
                    if (newUniqueItems.isNotEmpty()) {
                        currentPage = nextPage
                        val updatedItems = currentItems + newUniqueItems
                        val updatedMap = currentGrouped.toMutableMap().apply { put(uniqueKey, Pair(activeProvider, updatedItems)) }
                        updateState { copy(searchResultsGrouped = updatedMap, canPaginate = true) }
                        com.lagradost.common.logging.AppLogger.i("Plugin:${activeProvider.name}", "Appended ${newUniqueItems.size} new items (total: ${updatedItems.size})")
                    } else {
                        updateState { copy(canPaginate = false) }
                    }
                } else {
                    updateState { copy(canPaginate = false) }
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Failed loading more search results: ${e.message}")
                updateState { copy(canPaginate = false) }
            } finally {
                updateState { copy(isLoadingMore = false) }
            }
        }
    }
}
