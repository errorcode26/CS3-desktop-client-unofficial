package com.lagradost.cloudstream3.desktop.ui.screens.history

import com.lagradost.cloudstream3.desktop.data.history.WatchHistoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetContinueWatching
import com.lagradost.cloudstream3.desktop.domain.history.interactor.RemoveWatchHistory
import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.relink.ReLinkSearchCoordinator
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.history.contract.HistoryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.history.contract.HistoryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.history.contract.HistoryUiState
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HistoryViewModel(
    watchHistoryRepo: WatchHistoryRepository = WatchHistoryRepositoryImpl(),
    private val getContinueWatching: GetContinueWatching = GetContinueWatching(watchHistoryRepo),
    private val removeWatchHistory: RemoveWatchHistory = RemoveWatchHistory(watchHistoryRepo),
) : BaseMviViewModel<HistoryUiState, HistoryUiEvent, HistoryUiEffect>(
    initialState = HistoryUiState(),
) {
    private var reLinkSearchJob: Job? = null

    init {
        viewModelScope.launch {
            getContinueWatching.subscribe().collect { list ->
                updateState { copy(historyList = list, isLoading = false) }
            }
        }
    }

    override fun handleEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.ClearAll -> {
                viewModelScope.launch(Dispatchers.IO) {
                    removeWatchHistory.clearAll()
                    sendEffect(HistoryUiEffect.ShowToast("Watch history cleared"))
                }
            }
            is HistoryUiEvent.RemoveItem -> {
                viewModelScope.launch(Dispatchers.IO) {
                    removeWatchHistory.awaitByParent(event.parentId)
                }
            }
            is HistoryUiEvent.OnStartReLink -> startReLink(event.history)
            is HistoryUiEvent.OnSelectReLinkMatch -> selectReLinkMatch(event.history, event.match)
            is HistoryUiEvent.OnSelectReLinkAddon -> selectReLinkAddon(event.history, event.item)
            is HistoryUiEvent.OnDismissRecoveryModal -> dismissRecoveryModal()
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
                sendEffect(HistoryUiEffect.ShowToast("Transferred to ${match.providerName}"))
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("HistoryViewModel: Failed to migrate watch history: ${e.message}")
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
                sendEffect(HistoryUiEffect.ShowToast("Transferred to Addons"))
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("HistoryViewModel: Failed to transfer watch history to Addons: ${e.message}")
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
}
