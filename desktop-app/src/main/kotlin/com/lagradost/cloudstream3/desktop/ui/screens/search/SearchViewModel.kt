package com.lagradost.cloudstream3.desktop.ui.screens.search


import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel

import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiState
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PREF_GLOBAL_SEARCH = "global_search_enabled"
const val PREF_SELECTED_PROVIDER = "preferred_provider_name"

class SearchViewModel : BaseMviViewModel<SearchUiState, SearchUiEvent, SearchUiEffect>(
    initialState = SearchUiState(),
) {

    private var searchJob: kotlinx.coroutines.Job? = null
    private var lastSearchedQuery: String = ""

    init {
        updateState { copy(isGlobalSearchEnabled = false) }
        viewModelScope.launch(Dispatchers.IO) {
            val selectedProviderName = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
            updateState { copy(selectedProviderName = selectedProviderName) }
        }

        viewModelScope.launch {
            uiState.map { it.selectedProviderName }.distinctUntilChanged().collect { providerName ->
                providerName?.let { DesktopDataStore.setKey(PREF_SELECTED_PROVIDER, it) }
            }
        }

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }
                .distinctUntilChanged() // prevent spurious re-searches on unrelated state changes
                .debounce(500)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        searchJob?.cancel()
                        updateState { copy(searchResultsGrouped = null, isLoadingSearch = false) }
                    } else {
                        search()
                    }
                }
        }

        viewModelScope.launch {
            DesktopRepositoryManager.remotePluginIcons.collectLatest { icons ->
                updateState { copy(pluginIcons = icons) }
            }
        }
    }

    override fun handleEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query) }
            is SearchUiEvent.OnSearch -> search()
            is SearchUiEvent.OnClearSearch -> {
                searchJob?.cancel()
                lastSearchedQuery = ""
                updateState { copy(searchQuery = "", searchResultsGrouped = null, isLoadingSearch = false) }
            }
            is SearchUiEvent.OnToggleGlobalSearch -> {
                updateState { copy(isGlobalSearchEnabled = event.enabled) }
                // Re-run search immediately with the new scope if there's an active query
                if (uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnProviderSelected -> {
                updateState { copy(selectedProviderName = event.providerName) }
                if (!uiState.value.isGlobalSearchEnabled && uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnCategorySelected -> {
                updateState { copy(selectedCategory = event.category) }
            }
        }
    }

    private fun search(force: Boolean = false) {
        val query = uiState.value.searchQuery
        if (query.isBlank() || (!force && query == lastSearchedQuery)) return

        lastSearchedQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            updateState { copy(isLoadingSearch = true, searchResultsGrouped = null) }
            try {
                val providers = APIHolder.allProviders.filter { it.isRealProvider() }
                
                val activeProviders = if (uiState.value.isGlobalSearchEnabled) {
                    providers.filter { it.hasMainPage || it.supportedTypes.isNotEmpty() }
                } else {
                    val active = providers.find { it.name == uiState.value.selectedProviderName } ?: providers.firstOrNull()
                    active?.let { listOf(it) } ?: emptyList()
                }

                // Temporary concurrent map to hold results as they arrive
                val tempResults = java.util.concurrent.ConcurrentHashMap<String, List<SearchResponse>>()

                withContext(Dispatchers.IO) {
                    activeProviders.map { p ->
                        launch {
                            try {
                                val res = p.search(query, 1)
                                if (res != null && res.items.isNotEmpty()) {
                                    tempResults[p.name] = res.items
                                    // Update state incrementally so results appear as they arrive,
                                    // but use a snapshot copy to avoid ConcurrentModificationException
                                    updateState { copy(searchResultsGrouped = tempResults.toMap()) }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                DesktopErrorReporter.report("Search provider ${p.name} failed", e)
                            }
                        }
                    }.forEach { it.join() }
                }
            } catch (e: Throwable) {
                DesktopErrorReporter.report("Search failed", e)
            } finally {
                updateState { copy(isLoadingSearch = false) }
            }
        }
    }
}
