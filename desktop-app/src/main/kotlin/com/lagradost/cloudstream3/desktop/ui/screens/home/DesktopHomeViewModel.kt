package com.lagradost.cloudstream3.desktop.ui.screens.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.GetBookmarks
import com.lagradost.cloudstream3.desktop.domain.hero.repository.HeroRepository
import com.lagradost.cloudstream3.desktop.domain.hero.repository.HeroRepository.HeroUpdate
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetContinueWatching
import com.lagradost.cloudstream3.desktop.domain.history.interactor.RemoveWatchHistory
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.relink.ReLinkSearchCoordinator
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeCategoryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Job
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Returns true only for real, user-facing content providers:
 * - Excludes built-in MetaProviders (Trakt, TMDB, CrossTMDB)
 * - Excludes "NONE"
 */
fun MainAPI.isRealProvider(): Boolean = com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.isRealContentProvider(this)

class DesktopHomeViewModel(
    private val getContinueWatching: GetContinueWatching = AppContainerHolder.container.getContinueWatching,
    private val removeWatchHistory: RemoveWatchHistory = AppContainerHolder.container.removeWatchHistory,
    private val getBookmarks: GetBookmarks = AppContainerHolder.container.getBookmarks,
    private val activeProviderRepository: ActiveProviderRepository = AppContainerHolder.container.activeProviderRepository,
    private val heroRepository: HeroRepository = AppContainerHolder.container.heroRepository,
) : BaseMviViewModel<HomeUiState, HomeUiEvent, HomeUiEffect>(
    initialState = HomeUiState(),
) {
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, com.lagradost.cloudstream3.HomePageResponse>()
    private val categoryMutex = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private var reLinkSearchJob: Job? = null

    init {
        viewModelScope.launch {
            getBookmarks.subscribeAll().collect { bookmarks ->
                updateState { copy(bookmarks = bookmarks) }
            }
        }

        // Reactively observe providers from single source of truth
        viewModelScope.launch {
            activeProviderRepository.allRealProviders.collectLatest { realProviders ->
                updateState { copy(providers = realProviders) }
            }
        }

        viewModelScope.launch {
            activeProviderRepository.activeProviders.collectLatest { activeApis ->
                updateState {
                    copy(
                        activeProviderApis = activeApis,
                    )
                }
                // Eagerly preload top categories for primary active provider so home page renders immediately
                val primary = activeApis.firstOrNull()
                if (primary != null) {
                    val disabled = uiState.value.disabledCatalogs[primary.name] ?: emptySet()
                    val initialCatalogs = primary.mainPage.filter { it.name !in disabled }.take(3)
                    for (pageData in initialCatalogs) {
                        loadCategory(primary, pageData)
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            uiState.map { it.providers }.distinctUntilChanged().collect { apis ->
                val disabledMap = mutableMapOf<String, Set<String>>()
                val orderedMap = mutableMapOf<String, List<String>>()
                for (api in apis) {
                    val keyName = PreferenceKeys.disabledCatalogsKey(api.name)
                    val provKey = com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.getProviderKey(api)
                    val keyProv = PreferenceKeys.disabledCatalogsKey(provKey)
                    val disabled = DesktopDataStore.getKey<Set<String>>(keyName)
                        ?: DesktopDataStore.getKey<Set<String>>(keyProv)
                        ?: emptySet()
                    disabledMap[api.name] = disabled
                    disabledMap[provKey] = disabled

                    val orderKeyName = PreferenceKeys.orderedCatalogsKey(api.name)
                    val orderKeyProv = PreferenceKeys.orderedCatalogsKey(provKey)
                    val ordered = DesktopDataStore.getKey<List<String>>(orderKeyName)
                        ?: DesktopDataStore.getKey<List<String>>(orderKeyProv)
                        ?: emptyList()
                    if (ordered.isNotEmpty()) {
                        orderedMap[api.name] = ordered
                        orderedMap[provKey] = ordered
                    }
                }
                updateState { copy(disabledCatalogs = disabledMap, orderedCatalogs = orderedMap) }
            }
        }

        viewModelScope.launch {
            DesktopRepositoryManager.remotePluginIcons.collectLatest { icons ->
                updateState { copy(mergedPluginIcons = icons) }
            }
        }

        viewModelScope.launch {
            getContinueWatching.subscribe().collect { newHistory ->
                updateState { copy(historyList = newHistory) }
                prefetchTopHistory(newHistory.take(3))
            }
        }

        reloadIcons()
    }

    override fun handleEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnToggleProviderActive -> {
                val current = uiState.value.activeProviders.toMutableList()
                if (event.isActive) {
                    if (!current.contains(event.providerName)) current.add(event.providerName)
                } else {
                    current.remove(event.providerName)
                }
                activeProviderRepository.setActiveProviders(current)
            }
            is HomeUiEvent.OnSetSingleProvider -> {
                activeProviderRepository.setActiveProviders(listOf(event.providerName))
            }
            is HomeUiEvent.OnMoveProvider -> {
                val current = uiState.value.activeProviders.toMutableList()
                if (event.fromIndex in current.indices && event.toIndex in current.indices) {
                    val item = current.removeAt(event.fromIndex)
                    current.add(event.toIndex, item)
                }
                activeProviderRepository.setActiveProviders(current)
            }
            is HomeUiEvent.OnClearHistory -> clearHistory()
            is HomeUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.parentId)
            is HomeUiEvent.OnPrefetchHeroItem -> prefetchHeroItem(event.provider, event.item)
            is HomeUiEvent.OnProviderRefresh -> reloadProvider()
            is HomeUiEvent.OnShowHomeManagement -> {
                updateState { copy(showHomeManagement = event.show) }
            }
            is HomeUiEvent.OnToggleCatalog -> {
                val currentDisabled = uiState.value.disabledCatalogs[event.providerName] ?: emptySet()
                val newDisabled = if (event.isEnabled) {
                    currentDisabled - event.catalogName
                } else {
                    currentDisabled + event.catalogName
                }
                val matchingApi = uiState.value.activeProviderApis.firstOrNull { it.name == event.providerName }
                val provKey = matchingApi?.let { com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.getProviderKey(it) }

                val updatedMap = uiState.value.disabledCatalogs.toMutableMap()
                updatedMap[event.providerName] = newDisabled
                if (provKey != null) {
                    updatedMap[provKey] = newDisabled
                }

                updateState { copy(disabledCatalogs = updatedMap) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(PreferenceKeys.disabledCatalogsKey(event.providerName), newDisabled)
                    if (provKey != null) {
                        DesktopDataStore.setKey(PreferenceKeys.disabledCatalogsKey(provKey), newDisabled)
                    }
                }
            }
            is HomeUiEvent.OnMoveCatalog -> {
                val matchingApi = uiState.value.providers.firstOrNull { it.name == event.providerName }
                    ?: uiState.value.activeProviderApis.firstOrNull { it.name == event.providerName }
                val defaultList = matchingApi?.mainPage?.map { it.name } ?: emptyList()
                val currentOrdered = (uiState.value.orderedCatalogs[event.providerName] ?: defaultList).toMutableList()

                if (event.fromIndex in currentOrdered.indices && event.toIndex in currentOrdered.indices) {
                    val item = currentOrdered.removeAt(event.fromIndex)
                    currentOrdered.add(event.toIndex, item)

                    val provKey = matchingApi?.let { com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.getProviderKey(it) }
                    val updatedOrderedMap = uiState.value.orderedCatalogs.toMutableMap()
                    updatedOrderedMap[event.providerName] = currentOrdered
                    if (provKey != null) {
                        updatedOrderedMap[provKey] = currentOrdered
                    }
                    updateState { copy(orderedCatalogs = updatedOrderedMap) }

                    viewModelScope.launch(Dispatchers.IO) {
                        DesktopDataStore.setKey(PreferenceKeys.orderedCatalogsKey(event.providerName), currentOrdered)
                        if (provKey != null) {
                            DesktopDataStore.setKey(PreferenceKeys.orderedCatalogsKey(provKey), currentOrdered)
                        }
                    }
                }
            }
            is HomeUiEvent.OnResetCatalogOrder -> {
                val matchingApi = uiState.value.providers.firstOrNull { it.name == event.providerName }
                    ?: uiState.value.activeProviderApis.firstOrNull { it.name == event.providerName }
                val provKey = matchingApi?.let { com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.getProviderKey(it) }

                val updatedOrderedMap = uiState.value.orderedCatalogs.toMutableMap()
                updatedOrderedMap.remove(event.providerName)
                if (provKey != null) {
                    updatedOrderedMap.remove(provKey)
                }
                updateState { copy(orderedCatalogs = updatedOrderedMap) }

                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.removeKey(PreferenceKeys.orderedCatalogsKey(event.providerName))
                    if (provKey != null) {
                        DesktopDataStore.removeKey(PreferenceKeys.orderedCatalogsKey(provKey))
                    }
                }
            }
            is HomeUiEvent.OnStartReLink -> startReLink(event.history)
            is HomeUiEvent.OnSelectReLinkMatch -> selectReLinkMatch(event.history, event.match)
            is HomeUiEvent.OnSelectReLinkAddon -> selectReLinkAddon(event.history, event.item)
            is HomeUiEvent.OnDismissRecoveryModal -> dismissRecoveryModal()
            is HomeUiEvent.OnLoadCategory -> {
                loadCategory(event.provider, event.pageData)
            }
        }
    }

    private fun loadCategory(provider: MainAPI, pageData: MainPageData) {
        val cacheKey = "${provider.name}_${pageData.name}"
        val cachedResponse = categoryCache[cacheKey]
        val currentState = uiState.value.categories[cacheKey]

        if (cachedResponse != null && currentState?.response == cachedResponse) {
            return
        }

        if (cachedResponse != null) {
            updateState {
                copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = false, response = cachedResponse, error = null)))
            }
            return
        }

        if (currentState?.isLoading == true) {
            return
        }

        updateState {
            copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = true, response = null, error = null)))
        }

        viewModelScope.launch(Dispatchers.IO) {
            val mutex = categoryMutex.getOrPut(cacheKey) { kotlinx.coroutines.sync.Mutex() }
            mutex.withLock {
                val existing = categoryCache[cacheKey]
                if (existing != null) {
                    updateState {
                        copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = false, response = existing, error = null)))
                    }
                    return@withLock
                }

                val request = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                com.lagradost.common.logging.AppLogger.i("Plugin:${provider.name}", "Loading home category: '${pageData.name}'")
                val result = SafePluginInvoker.invoke(
                    tag = "HomeCategory:${provider.name}:${pageData.name.ifBlank { "Category" }}",
                    timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                ) {
                    provider.getMainPage(1, request)
                }

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    if (response != null && response.items.isNotEmpty()) {
                        com.lagradost.common.logging.AppLogger.i("Plugin:${provider.name}", "Loaded ${response.items.size} items for category '${pageData.name}'")
                        categoryCache[cacheKey] = response
                        updateState {
                            copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = false, response = response, error = null)))
                        }
                    } else {
                        updateState {
                            copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = false, response = null, error = "No items found.")))
                        }
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    if (ex is kotlinx.coroutines.CancellationException) {
                        throw ex
                    }
                    com.lagradost.common.logging.AppLogger.w("Plugin:${provider.name}", "Failed to load category '${pageData.name}': ${ex?.message}")
                    DesktopErrorReporter.report("getMainPage failed for ${provider.name} - ${pageData.name.ifBlank { "Unknown Category" }}", ex ?: Exception("Unknown error"))
                    val errorMsg = ex?.localizedMessage ?: "Connection error"
                    updateState {
                        copy(categories = categories + (cacheKey to HomeCategoryUiState(isLoading = false, response = null, error = errorMsg)))
                    }
                }
            }
        }
    }

    private fun prefetchTopHistory(topHistory: List<com.lagradost.common.storage.WatchHistory>) {
        if (topHistory.isEmpty()) return
        viewModelScope.launch {
            heroRepository.prefetchTopHistory(topHistory, uiState.value.providers)
        }
    }

    private fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse) {
        viewModelScope.launch {
            heroRepository.prefetchHeroItem(provider, item)
                .collect { update ->
                    if (update is HeroUpdate.Meta) {
                        updateState {
                            copy(heroMetaMap = heroMetaMap + (update.url to update.meta))
                        }
                    }
                }
        }
    }

    private fun reloadIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            val icons = DesktopRepositoryManager.remotePluginIcons.value
            updateState { copy(mergedPluginIcons = icons) }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            removeWatchHistory.clearAll()
        }
    }

    private fun removeHistoryItem(parentId: String) {
        updateState { copy(historyList = historyList.filterNot { it.parentId == parentId }) }
        viewModelScope.launch(Dispatchers.IO) {
            removeWatchHistory.awaitByParent(parentId)
        }
    }

    private fun startReLink(history: WatchHistory) {
        val isSeries = history.season != null || history.episode != null
        val initialExplore = ExploreItem(
            id = history.showName,
            type = if (isSeries) "series" else "movie",
            name = history.showName,
            posterUrl = history.posterUrl,
        )
        updateState {
            copy(
                orphanRecoveryHistory = history,
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
                        rawTitle = history.showName,
                        posterUrl = history.posterUrl,
                        isSeriesHint = isSeries,
                    )
                    updateState { copy(orphanExploreItem = enriched) }
                } catch (_: Exception) {}
            }

            try {
                ReLinkSearchCoordinator.searchRealProvidersProgressive(
                    searchTitle = history.showName,
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

    private fun selectReLinkMatch(history: WatchHistory, match: ProviderMatch) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DesktopDataStore.migrateWatchHistoryAndBookmark(
                    oldApiName = history.apiName,
                    oldUrl = history.showUrl,
                    newApiName = match.providerName,
                    newUrl = match.searchResponse.url,
                    newName = match.searchResponse.name,
                    newPosterUrl = match.searchResponse.posterUrl ?: history.posterUrl,
                )
                updateState {
                    copy(
                        orphanRecoveryHistory = null,
                        orphanExploreItem = null,
                        isSearchingMatches = false,
                        matchedResults = emptyList(),
                    )
                }
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("Transferred to ${match.providerName}")
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("DesktopHomeViewModel: Failed to migrate watch history: ${e.message}")
            }
        }
    }

    private fun selectReLinkAddon(history: WatchHistory, item: ExploreItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stremioUrl = "stremio://${item.type}/${item.id}"
                val newApiName = "Stremio"
                DesktopDataStore.migrateWatchHistoryAndBookmark(
                    oldApiName = history.apiName,
                    oldUrl = history.showUrl,
                    newApiName = newApiName,
                    newUrl = stremioUrl,
                    newName = item.name,
                    newPosterUrl = item.posterUrl ?: history.posterUrl,
                )
                updateState {
                    copy(
                        orphanRecoveryHistory = null,
                        orphanExploreItem = null,
                        isSearchingMatches = false,
                        matchedResults = emptyList(),
                    )
                }
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("Transferred to Addons")
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("DesktopHomeViewModel: Failed to transfer watch history to Addons: ${e.message}")
            }
        }
    }

    private fun dismissRecoveryModal() {
        reLinkSearchJob?.cancel()
        updateState {
            copy(
                orphanRecoveryHistory = null,
                orphanExploreItem = null,
                isSearchingMatches = false,
                matchedResults = emptyList(),
            )
        }
    }

    private fun reloadProvider() {
        categoryCache.clear()
        categoryMutex.clear()
        com.lagradost.runtime.executor.PluginCircuitBreaker.resetAll()
        updateState {
            copy(
                categories = emptyMap(),
                refreshEpoch = refreshEpoch + 1L,
            )
        }
    }
}
