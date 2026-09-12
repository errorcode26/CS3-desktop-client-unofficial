package com.lagradost.cloudstream3.desktop.ui.screens.studio

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioCategory
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CopyOnWriteArrayList

@androidx.compose.runtime.Immutable
data class StudioUiState(
    val isLoading: Boolean = true,
    val studioDetail: StudioDetail? = null,
    val selectedCategory: StudioCategory = StudioCategory.ALL,
    val selectedItemForMatch: StudioMediaItem? = null,
    val providerMatches: List<ProviderMatch> = emptyList(),
    val isSearchingProviders: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface StudioUiEvent : UiEvent {
    data class Load(val name: String, val companyId: Int? = null) : StudioUiEvent
    data class SelectCategory(val category: StudioCategory) : StudioUiEvent
    data class SelectItemForMatch(val item: StudioMediaItem) : StudioUiEvent
    data object CloseProviderPicker : StudioUiEvent
    data object Retry : StudioUiEvent
}

sealed interface StudioUiEffect : UiEffect {
    data class NavigateToDetails(
        val providerName: String,
        val url: String,
        val title: String,
        val poster: String?,
    ) : StudioUiEffect
}

class StudioViewModel : BaseMviViewModel<StudioUiState, StudioUiEvent, StudioUiEffect>(StudioUiState()) {

    private val searchSemaphore = Semaphore(8)
    private var currentName: String = ""
    private var currentCompanyId: Int? = null
    private var providerSearchJob: Job? = null

    override fun handleEvent(event: StudioUiEvent) {
        when (event) {
            is StudioUiEvent.Load -> loadStudio(event.name, event.companyId)
            is StudioUiEvent.SelectCategory -> selectCategory(event.category)
            is StudioUiEvent.SelectItemForMatch -> selectItemForMatch(event.item)
            is StudioUiEvent.CloseProviderPicker -> closeProviderPicker()
            is StudioUiEvent.Retry -> loadStudio(currentName, currentCompanyId)
        }
    }

    fun loadStudio(name: String, companyId: Int? = null) {
        if (name.isBlank() && (companyId == null || companyId <= 0)) return
        currentName = name
        currentCompanyId = companyId

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            try {
                val detail = TmdbEnrichmentService.fetchStudioDetail(companyId, name)
                if (detail != null) {
                    updateState {
                        copy(
                            isLoading = false,
                            studioDetail = detail,
                            error = null,
                        )
                    }
                } else {
                    updateState {
                        copy(
                            isLoading = false,
                            studioDetail = null,
                            error = "No titles found for studio $name",
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("StudioViewModel", "Failed to load studio details", e)
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load studio details",
                    )
                }
            }
        }
    }

    private fun selectCategory(category: StudioCategory) {
        updateState { copy(selectedCategory = category) }
    }

    private fun selectItemForMatch(item: StudioMediaItem) {
        providerSearchJob?.cancel()
        updateState {
            copy(
                selectedItemForMatch = item,
                providerMatches = emptyList(),
                isSearchingProviders = true,
            )
        }

        providerSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeApis: List<MainAPI> = APIHolder.apis
                val activeProviders = activeApis.filter { it.isRealProvider() }

                if (activeProviders.isEmpty()) {
                    updateState { copy(isSearchingProviders = false) }
                    return@launch
                }

                val searchTitle = item.title.trim()
                val aggregatedMatches = CopyOnWriteArrayList<ProviderMatch>()

                val jobs = activeProviders.map { provider ->
                    launch {
                        searchSemaphore.withPermit {
                            try {
                                val res = SafePluginInvoker.invokeOrNull(
                                    tag = "Studio:Search:${provider.name}",
                                    timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                                ) {
                                    provider.search(searchTitle, 1)
                                }

                                val searchItems = res?.items
                                if (!searchItems.isNullOrEmpty()) {
                                    for (searchRes in searchItems) {
                                        if (isTitleRelevant(searchTitle, searchRes.name)) {
                                            val meta = CardTitleSanitizer.sanitize(searchRes.name)
                                            aggregatedMatches.add(
                                                ProviderMatch(
                                                    providerName = provider.name,
                                                    searchResponse = searchRes,
                                                    displayTitle = meta.displayTitle,
                                                    qualityText = meta.qualityText,
                                                    hasSub = meta.hasSub,
                                                    hasDub = meta.hasDub,
                                                )
                                            )
                                        }
                                    }
                                    updateState { copy(providerMatches = aggregatedMatches.toList()) }
                                }
                            } catch (_: Exception) {
                                // Ignored per provider failure
                            }
                        }
                    }
                }

                jobs.joinAll()
                updateState {
                    copy(
                        providerMatches = aggregatedMatches.toList(),
                        isSearchingProviders = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("StudioViewModel", "Provider resolution failed: ${e.message}")
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

    private fun isTitleRelevant(query: String, resultName: String): Boolean {
        val qClean = normalizeForMatching(query)
        val rClean = normalizeForMatching(resultName)
        if (qClean.isEmpty() || rClean.isEmpty()) return false
        if (rClean.contains(qClean) || qClean.contains(rClean)) return true

        val qTokens = qClean.split(" ").filter { it.length > 2 }
        if (qTokens.isEmpty()) return true
        val matchCount = qTokens.count { rClean.contains(it) }
        return matchCount.toDouble() / qTokens.size >= 0.5
    }

    private fun normalizeForMatching(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
