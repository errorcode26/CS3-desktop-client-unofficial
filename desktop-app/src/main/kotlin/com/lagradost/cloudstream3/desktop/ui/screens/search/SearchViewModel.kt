package com.lagradost.cloudstream3.desktop.ui.screens.search

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.data.providers.LazyPluginProvider
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchEngine
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiState
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.executor.SafePluginInvoker
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

private const val PREF_SEARCH_HISTORY = PreferenceKeys.PREF_SEARCH_HISTORY
private const val PREF_SEARCH_PROVIDERS = PreferenceKeys.PREF_SEARCH_PROVIDERS
private const val PREF_SEARCH_ADDON_MODE = "pref_search_addon_mode"
private const val MAX_HISTORY_SIZE = 20

class SearchViewModel(
    private val activeProviderRepository: ActiveProviderRepository = AppContainerHolder.container.activeProviderRepository,
    private val pluginRepository: PluginRepository = AppContainerHolder.container.pluginRepository,
) : BaseMviViewModel<SearchUiState, SearchUiEvent, SearchUiEffect>(
    initialState = SearchUiState(),
) {

    private var searchJob: kotlinx.coroutines.Job? = null
    private var lastSearchedQuery: String = ""
    private val searchSemaphore = kotlinx.coroutines.sync.Semaphore(8)
    private var suppressSuggestions: Boolean = false

    init {
        // Collect real providers reactively and purge any stale uninstalled plugins
        viewModelScope.launch {
            activeProviderRepository.allRealProviders.collectLatest { providers ->
                val validNames = providers.map { it.name }.toSet()
                val currentSelected = uiState.value.selectedProviderNames
                val hasStale = currentSelected.any { it !in validNames }
                val homeActive = activeProviderRepository.currentSelectedProvider.value
                    ?: activeProviderRepository.activeProviders.value.firstOrNull()
                    ?: providers.firstOrNull()

                val newSelected = if (hasStale || currentSelected.isEmpty()) {
                    setOfNotNull(homeActive?.name)
                } else {
                    currentSelected
                }

                updateState {
                    copy(
                        providers = providers,
                        selectedProviderNames = newSelected,
                        selectedProviderName = homeActive?.name ?: selectedProviderName,
                        selectedProviderSource = homeActive?.sourcePlugin ?: selectedProviderSource,
                    )
                }
            }
        }

        // Collect current selected provider reactively from the shared domain repository
        viewModelScope.launch {
            activeProviderRepository.currentSelectedProvider.collectLatest { provider ->
                if (provider != null) {
                    updateState {
                        copy(
                            selectedProviderName = provider.name,
                            selectedProviderSource = provider.sourcePlugin,
                            selectedProviderNames = setOf(provider.name),
                            isGlobalSearchEnabled = false,
                        )
                    }
                }
            }
        }

        // Load search history and addon mode preferences (clean up any legacy stale providers)
        viewModelScope.launch(Dispatchers.IO) {
            val history = DesktopDataStore.getKey<List<String>>(PREF_SEARCH_HISTORY) ?: emptyList()
            val savedAddonMode = DesktopDataStore.getKey<Boolean>(PREF_SEARCH_ADDON_MODE) ?: true
            // Purge legacy search providers key to prevent obsolete removed plugins from resurfacing
            DesktopDataStore.setKey(PREF_SEARCH_PROVIDERS, emptyList<String>())
            updateState {
                copy(
                    searchHistory = history,
                    isAddonMode = savedAddonMode,
                )
            }
        }

        // Collect installed addon categories reactively
        viewModelScope.launch {
            StremioAddonManager.addons.collectLatest { addons ->
                val types = addons.filter { it.enabled }.flatMap { it.types }.distinct()
                val mapped = types.mapNotNull { type ->
                    when (type.lowercase()) {
                        "movie" -> "Movies"
                        "series", "tv" -> "TV Series"
                        "anime" -> "Anime"
                        "channel" -> "Live Channels"
                        else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }.distinct()
                val defaultOrder = listOf("Movies", "TV Series", "Anime")

                val categories = listOf("All") + (defaultOrder + mapped).distinct()
                updateState { copy(availableAddonCategories = categories) }
            }
        }

        // Debounced search query (500ms to prevent rate limits on typing)
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(500)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        searchJob?.cancel()
                        lastSearchedQuery = ""
                        updateState { copy(searchResultsGrouped = null, addonSearchResults = null, isLoadingSearch = false, searchSuggestions = emptyList(), showSuggestions = false) }
                    } else if (query != lastSearchedQuery) {
                        search()
                    }
                }
        }

        // Debounced search suggestions
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(250)
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (suppressSuggestions) {
                        updateState { copy(showSuggestions = false) }
                        return@collectLatest
                    }
                    if (trimmed.length >= 2) {
                        val suggestions = SearchSuggestionApi.getSuggestions(trimmed, uiState.value.searchHistory)
                        if (!suppressSuggestions && uiState.value.searchQuery.trim() == trimmed) {
                            updateState { copy(searchSuggestions = suggestions, showSuggestions = suggestions.isNotEmpty()) }
                        }
                    } else {
                        updateState { copy(searchSuggestions = emptyList(), showSuggestions = false) }
                    }
                }
        }

        // Remote plugin icons
        viewModelScope.launch {
            pluginRepository.remotePluginIcons.collectLatest { icons ->
                updateState { copy(pluginIcons = icons) }
            }
        }
    }

    override fun handleEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnSearchQueryChange -> {
                suppressSuggestions = false
                updateState { copy(searchQuery = event.query) }
            }
            is SearchUiEvent.OnSearch -> {
                suppressSuggestions = true
                updateState { copy(showSuggestions = false) }
                addToHistory(uiState.value.searchQuery)
                search(force = true)
            }
            is SearchUiEvent.OnClearSearch -> {
                searchJob?.cancel()
                providerSearchJob?.cancel()
                lastSearchedQuery = ""
                suppressSuggestions = true
                updateState {
                    copy(
                        searchQuery = "",
                        searchResultsGrouped = null,
                        addonSearchResults = null,
                        isLoadingSearch = false,
                        searchSuggestions = emptyList(),
                        showSuggestions = false,
                        drilledSearchCategory = null,
                        selectedItemForMatch = null,
                        providerMatches = emptyList(),
                        isSearchingProviders = false,
                    )
                }
            }
            is SearchUiEvent.OnSelectSuggestion -> {
                suppressSuggestions = true
                updateState { copy(searchQuery = event.query, showSuggestions = false) }
                if (event.submitSearch) {
                    addToHistory(event.query)
                    search(force = true)
                }
            }
            is SearchUiEvent.OnDismissSuggestions -> {
                suppressSuggestions = true
                updateState { copy(showSuggestions = false) }
            }
            is SearchUiEvent.OnToggleSearchMode -> {
                updateState { copy(isAddonMode = event.isAddonMode) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(PREF_SEARCH_ADDON_MODE, event.isAddonMode)
                }
                if (!event.isAddonMode) {
                    syncWithHomeActiveProvider()
                }
                if (uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnEnterSearchScreen -> syncWithHomeActiveProvider()
            is SearchUiEvent.OnOpenProviderPicker -> openProviderPicker(event.item)
            is SearchUiEvent.OnCloseProviderPicker -> closeProviderPicker()
            is SearchUiEvent.OnDrillSearchCategory -> updateState { copy(drilledSearchCategory = event.category) }
            is SearchUiEvent.OnToggleGlobalSearch -> {
                updateState { copy(isGlobalSearchEnabled = event.enabled, showSuggestions = false) }
                if (uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnApplySelectedProviders -> {
                val validNames = uiState.value.providers.map { it.name }.toSet()
                val filteredProviders = event.providers.filter { it in validNames }.toSet()
                val finalProviders = if (filteredProviders.isEmpty()) {
                    val homeActive = activeProviderRepository.currentSelectedProvider.value
                        ?: activeProviderRepository.activeProviders.value.firstOrNull()
                        ?: uiState.value.providers.firstOrNull()
                    setOfNotNull(homeActive?.name)
                } else {
                    filteredProviders
                }
                val isGlobal = finalProviders.isEmpty() || finalProviders.size > 1
                updateState {
                    copy(
                        selectedProviderNames = finalProviders,
                        isGlobalSearchEnabled = isGlobal,
                        showSuggestions = false,
                    )
                }
                if (uiState.value.searchQuery.isNotBlank()) {
                    search(force = true)
                }
            }
            is SearchUiEvent.OnProviderSelected -> {
                activeProviderRepository.setSelectedProviderByName(event.providerName, event.sourcePlugin)
                updateState {
                    copy(
                        selectedProviderName = event.providerName,
                        selectedProviderSource = event.sourcePlugin,
                        selectedProviderNames = setOf(event.providerName),
                        isGlobalSearchEnabled = false,
                    )
                }
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
            is SearchUiEvent.OnSetProviderTypeFilter -> updateState { copy(providerTypeFilter = event.types) }
        }
    }

    private fun syncWithHomeActiveProvider() {
        val homeActive = activeProviderRepository.currentSelectedProvider.value
            ?: activeProviderRepository.activeProviders.value.firstOrNull()
            ?: uiState.value.providers.firstOrNull()
        if (homeActive != null) {
            updateState {
                copy(
                    selectedProviderName = homeActive.name,
                    selectedProviderSource = homeActive.sourcePlugin,
                    selectedProviderNames = setOf(homeActive.name),
                    isGlobalSearchEnabled = false,
                )
            }
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

    private var providerSearchJob: kotlinx.coroutines.Job? = null

    private fun search(force: Boolean = false) {
        val query = uiState.value.searchQuery
        if (query.isBlank() || (!force && query == lastSearchedQuery)) return

        lastSearchedQuery = query
        currentPage = 1

        searchJob?.cancel()
        if (uiState.value.isAddonMode) {
            searchAddons(query)
        } else {
            searchPlugins(query)
        }
    }

    private fun searchAddons(query: String) {
        val trimmed = query.trim()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoadingSearch = true, addonSearchResults = null) }
            try {
                val enabledAddons = StremioAddonManager.addons.value.filter { it.enabled }
                ExploreSearchEngine.searchFlow(
                    query = trimmed,
                    enabledAddons = enabledAddons,
                    limitPerCategory = 24,
                ).collect { results ->
                    updateState {
                        copy(
                            addonSearchResults = results,
                            isLoadingSearch = false,
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                DesktopErrorReporter.report("Addon search failed", e)
            } finally {
                updateState { copy(isLoadingSearch = false) }
            }
        }
    }

    private fun searchPlugins(query: String) {
        searchJob = viewModelScope.launch {
            updateState { copy(isLoadingSearch = true, isLoadingMore = false, canPaginate = true, searchResultsGrouped = null) }
            try {
                val selectedNames = uiState.value.selectedProviderNames
                val isGlobal = selectedNames.isEmpty() || selectedNames.size > 1
                updateState { copy(isGlobalSearchEnabled = isGlobal) }

                val providers = uiState.value.providers
                val p2pReady = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isP2pReady

                val validProviders = providers.filter {
                    (it.hasMainPage || it.supportedTypes.isNotEmpty()) &&
                        (p2pReady || !com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isPureTorrentProvider(it))
                }

                val targetProviders = if (selectedNames.isNotEmpty()) {
                    validProviders.filter { it.name in selectedNames }
                } else {
                    validProviders
                }

                // Check for unmounted LazyPluginProvider stubs and mount them on-demand
                val lazyProviders = targetProviders.filterIsInstance<LazyPluginProvider>()
                if (lazyProviders.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val jarsToLoad = lazyProviders.mapNotNull { it.sourcePlugin?.let { p -> File(p) } }.distinct()
                        var anyMounted = false
                        for (jar in jarsToLoad) {
                            val canonical = try {
                                jar.canonicalPath
                            } catch (_: Throwable) {
                                jar.absolutePath
                            }
                            val isAlreadyLoaded = ExtensionLoader.isPluginLoaded(jar.absolutePath) ||
                                ExtensionLoader.isPluginLoaded(canonical)
                            if (jar.exists() && !isAlreadyLoaded) {
                                try {
                                    ExtensionLoader.loadAndInit(jar)
                                    anyMounted = true
                                    AppLogger.i("Search: Mounted lazy plugin on-demand: ${jar.name}")
                                } catch (e: Throwable) {
                                    AppLogger.e("Search: Failed to mount lazy plugin ${jar.name}", e)
                                }
                            }
                        }
                        if (anyMounted) {
                            activeProviderRepository.refreshProviders()
                        }
                    }
                }

                // Resolve real providers after mounting, filtering out any remaining unmounted stubs
                val currentRealProviders = activeProviderRepository.allRealProviders.value.filter {
                    (it.hasMainPage || it.supportedTypes.isNotEmpty()) &&
                        (p2pReady || !com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isPureTorrentProvider(it)) &&
                        it !is LazyPluginProvider
                }

                val activeProviders = if (selectedNames.isNotEmpty()) {
                    currentRealProviders.filter { it.name in selectedNames }
                } else {
                    currentRealProviders
                }

                if (activeProviders.isEmpty()) {
                    return@launch
                }

                val tempResults = java.util.concurrent.ConcurrentHashMap<String, Pair<com.lagradost.cloudstream3.MainAPI, List<SearchResponse>>>()

                activeProviders.map { p ->
                    launch {
                        searchSemaphore.withPermit {
                            AppLogger.i("Plugin:${p.name}", "Searching query: '$query'")
                            val res = SafePluginInvoker.invokeOrNull(
                                tag = "Search:${p.name}",
                                providerName = p.name,
                                timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                            ) {
                                p.search(query, 1)
                            }
                            if (res != null && res.items.isNotEmpty()) {
                                AppLogger.i("Plugin:${p.name}", "Found ${res.items.size} results for '$query'")
                                val uniqueKey = "${p.name}::${p.sourcePlugin ?: ""}"
                                val sanitizedItems = res.items.distinctBy { "${it.url}::${it.name}" }
                                tempResults[uniqueKey] = Pair(p, sanitizedItems)
                                updateState { copy(searchResultsGrouped = tempResults.toMap()) }
                            } else {
                                AppLogger.i("Plugin:${p.name}", "No results found for '$query'")
                            }
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

    private fun openProviderPicker(item: ExploreItem) {
        updateState {
            copy(
                selectedItemForMatch = item,
                providerMatches = emptyList(),
                isSearchingProviders = true,
            )
        }

        providerSearchJob?.cancel()
        providerSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Enrich metadata in background
                launch {
                    try {
                        val meta = com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient.getMeta(item.id, item.type)
                        if (meta != null) {
                            val current = uiState.value.selectedItemForMatch ?: item
                            val enriched = current.copy(
                                logoUrl = meta.logo?.takeIf { it.isNotBlank() } ?: current.logoUrl,
                                backgroundUrl = (meta.background?.replace("t/p/original//", "t/p/original/"))?.takeIf { it.isNotBlank() } ?: current.backgroundUrl,
                                posterUrl = meta.poster?.takeIf { it.isNotBlank() } ?: current.posterUrl,
                                description = meta.description?.takeIf { it.isNotBlank() } ?: current.description,
                                rating = meta.imdbRating?.toDoubleOrNull() ?: current.rating,
                                releaseYear = meta.releaseInfo?.takeIf { it.isNotBlank() } ?: current.releaseYear,
                                genres = if (meta.genres.isNullOrEmpty()) current.genres else meta.genres,
                            )
                            updateState { copy(selectedItemForMatch = enriched) }
                        }
                    } catch (_: Throwable) { }
                }

                val p2pReady = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isP2pReady
                var activeProviders = activeProviderRepository.allRealProviders.value.filter {
                    (it.hasMainPage || it.supportedTypes.isNotEmpty()) &&
                        (p2pReady || !com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isPureTorrentProvider(it))
                }

                // Check for unmounted stubs and mount them
                val lazyProviders = activeProviders.filterIsInstance<LazyPluginProvider>()
                if (lazyProviders.isNotEmpty()) {
                    val jarsToLoad = lazyProviders.mapNotNull { it.sourcePlugin?.let { p -> File(p) } }.distinct()
                    var anyMounted = false
                    for (jar in jarsToLoad) {
                        val canonical = try {
                            jar.canonicalPath
                        } catch (_: Throwable) {
                            jar.absolutePath
                        }
                        val isAlreadyLoaded = ExtensionLoader.isPluginLoaded(jar.absolutePath) || ExtensionLoader.isPluginLoaded(canonical)
                        if (jar.exists() && !isAlreadyLoaded) {
                            try {
                                ExtensionLoader.loadAndInit(jar)
                                anyMounted = true
                            } catch (_: Throwable) {}
                        }
                    }
                    if (anyMounted) {
                        activeProviderRepository.refreshProviders()
                        activeProviders = activeProviderRepository.allRealProviders.value.filter {
                            (it.hasMainPage || it.supportedTypes.isNotEmpty()) &&
                                (p2pReady || !com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isPureTorrentProvider(it)) &&
                                it !is LazyPluginProvider
                        }
                    }
                }

                val realProviders = activeProviders.filter { it !is LazyPluginProvider }
                if (realProviders.isEmpty()) {
                    updateState { copy(isSearchingProviders = false) }
                    return@launch
                }

                val cleanTitle = CardTitleSanitizer.sanitize(item.name).displayTitle
                val searchTitle = cleanTitle.ifBlank { item.name }
                val aggregatedMatches = CopyOnWriteArrayList<ProviderMatch>()

                val jobs = realProviders.map { provider ->
                    launch {
                        searchSemaphore.withPermit {
                            try {
                                val res = SafePluginInvoker.invokeOrNull(
                                    tag = "SearchMatch:${provider.name}",
                                    providerName = provider.name,
                                    timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                                ) {
                                    provider.search(searchTitle, 1)
                                }

                                val searchItems = res?.items
                                if (!searchItems.isNullOrEmpty()) {
                                    val validMatches = mutableListOf<ProviderMatch>()
                                    for (searchRes in searchItems) {
                                        if (isTitleMatch(searchTitle, searchRes.name)) {
                                            val meta = CardTitleSanitizer.sanitize(searchRes.name)
                                            validMatches.add(
                                                ProviderMatch(
                                                    providerName = provider.name,
                                                    searchResponse = searchRes,
                                                    displayTitle = meta.displayTitle,
                                                    qualityText = meta.qualityText,
                                                    hasSub = meta.hasSub,
                                                    hasDub = meta.hasDub,
                                                ),
                                            )
                                        }
                                    }
                                    if (validMatches.isNotEmpty()) {
                                        aggregatedMatches.addAll(validMatches)
                                        updateState { copy(providerMatches = aggregatedMatches.toList()) }
                                    }
                                }
                            } catch (_: Throwable) {
                                // Ignored per provider failure
                            }
                        }
                    }
                }

                jobs.forEach { it.join() }
                updateState {
                    copy(
                        providerMatches = aggregatedMatches.toList(),
                        isSearchingProviders = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                DesktopErrorReporter.report("Provider matching failed", e)
                updateState { copy(isSearchingProviders = false) }
            }
        }
    }

    private fun closeProviderPicker() {
        providerSearchJob?.cancel()
        updateState {
            copy(
                selectedItemForMatch = null,
                providerMatches = emptyList(),
                isSearchingProviders = false,
            )
        }
    }

    private fun isTitleMatch(targetTitle: String, candidateTitle: String): Boolean {
        val nonAlpha = Regex("[^a-z0-9]")
        val targetNorm = nonAlpha.replace(
            CardTitleSanitizer.sanitize(targetTitle).displayTitle.lowercase(java.util.Locale.US),
            " ",
        ).trim()
        val candidateNorm = nonAlpha.replace(
            CardTitleSanitizer.sanitize(candidateTitle).displayTitle.lowercase(java.util.Locale.US),
            " ",
        ).trim()

        if (targetNorm.isBlank() || candidateNorm.isBlank()) return false
        if (targetNorm == candidateNorm) return true
        if (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm)) return true

        val stopWords = setOf("the", "a", "an", "and", "of", "in", "to", "for", "with", "on", "at", "by", "from", "season", "episode")
        val targetWords = targetNorm.split(Regex("\\s+")).filter { it.length > 1 && it !in stopWords }

        return targetWords.isNotEmpty() && targetWords.all { candidateNorm.contains(it) }
    }

    private fun loadMore() {
        val state = uiState.value
        val query = state.searchQuery
        if (state.isGlobalSearchEnabled || state.isLoadingSearch || state.isLoadingMore || !state.canPaginate || query.isBlank()) return

        val activeProvider = if (state.selectedProviderNames.size == 1) {
            state.providers.find { it.name == state.selectedProviderNames.first() }
        } else {
            state.providers.find {
                it.name == state.selectedProviderName &&
                    (state.selectedProviderSource == null || it.sourcePlugin == state.selectedProviderSource)
            }
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
                    val newUniqueItems = res.items.filter { it.url !in existingUrls }.distinctBy { "${it.url}::${it.name}" }
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
