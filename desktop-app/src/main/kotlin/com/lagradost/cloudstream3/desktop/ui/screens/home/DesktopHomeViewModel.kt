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
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeCategoryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState
import com.lagradost.common.storage.DesktopDataStore
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

    // Redundant StateFlow mappings have been permanently deleted in accordance with MVI best practices.
    // UI should collect `uiState` and read properties directly from the immutable snapshot.

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
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            uiState.map { it.activeProviders }.distinctUntilChanged().collect { names ->
                val disabledMap = names.associateWith { name ->
                    DesktopDataStore.getKey<Set<String>>(PreferenceKeys.disabledCatalogsKey(name)) ?: emptySet()
                }
                updateState { copy(disabledCatalogs = disabledMap) }
            }
        }

        viewModelScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { syncGen ->
                if (syncGen > 0) {
                    reloadIcons()
                }
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
                updateState {
                    copy(disabledCatalogs = disabledCatalogs + (event.providerName to newDisabled))
                }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(PreferenceKeys.disabledCatalogsKey(event.providerName), newDisabled)
                }
            }
            is HomeUiEvent.OnLoadCategory -> {
                loadCategory(event.provider, event.pageData)
            }
        }
    }

    private fun loadCategory(provider: MainAPI, pageData: MainPageData) {
        val cacheKey = "${provider.name}_${pageData.name}"
        val cachedResponse = HomeCategorySectionCache.categoryCache[cacheKey]
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
            val mutex = HomeCategorySectionCache.categoryMutex.getOrPut(cacheKey) { kotlinx.coroutines.sync.Mutex() }
            mutex.withLock {
                val existing = HomeCategorySectionCache.categoryCache[cacheKey]
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
                        HomeCategorySectionCache.categoryCache[cacheKey] = response
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

    private fun reloadProvider() {
        HomeCategorySectionCache.clear()
        updateState { copy(categories = emptyMap()) }
        viewModelScope.launch {
            val currentApis = activeProviderRepository.activeProviders.value
            if (currentApis.isNotEmpty()) {
                updateState { copy(activeProviderApis = emptyList()) }
                kotlinx.coroutines.delay(50)
                updateState {
                    copy(
                        activeProviderApis = currentApis,
                    )
                }
            }
        }
    }
}
