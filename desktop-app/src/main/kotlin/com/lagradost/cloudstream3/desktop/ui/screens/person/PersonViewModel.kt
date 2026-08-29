package com.lagradost.cloudstream3.desktop.ui.screens.person

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
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.FilmographyCategory
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

@androidx.compose.runtime.Immutable
data class PersonUiState(
    val isLoading: Boolean = true,
    val personDetail: PersonDetail? = null,
    val selectedCategory: FilmographyCategory = FilmographyCategory.ALL,
    val selectedCreditForMatch: PersonMediaCredit? = null,
    val providerMatches: List<ProviderMatch> = emptyList(),
    val isSearchingProviders: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface PersonUiEvent : UiEvent {
    data class Load(val name: String, val tmdbId: Int? = null) : PersonUiEvent
    data class SelectCategory(val category: FilmographyCategory) : PersonUiEvent
    data class SelectCreditForMatch(val credit: PersonMediaCredit) : PersonUiEvent
    data object CloseProviderPicker : PersonUiEvent
    data object Retry : PersonUiEvent
}

sealed interface PersonUiEffect : UiEffect {
    data class NavigateToDetails(
        val providerName: String,
        val url: String,
        val title: String,
        val poster: String?,
    ) : PersonUiEffect
}

class PersonViewModel : BaseMviViewModel<PersonUiState, PersonUiEvent, PersonUiEffect>(PersonUiState()) {

    private var currentName: String = ""
    private var currentTmdbId: Int? = null
    private var providerSearchJob: Job? = null

    override fun handleEvent(event: PersonUiEvent) {
        when (event) {
            is PersonUiEvent.Load -> loadPerson(event.name, event.tmdbId)
            is PersonUiEvent.SelectCategory -> selectCategory(event.category)
            is PersonUiEvent.SelectCreditForMatch -> selectCreditForMatch(event.credit)
            is PersonUiEvent.CloseProviderPicker -> closeProviderPicker()
            is PersonUiEvent.Retry -> loadPerson(currentName, currentTmdbId)
        }
    }

    fun loadPerson(name: String, tmdbId: Int? = null) {
        if (name.isBlank() && (tmdbId == null || tmdbId <= 0)) return
        currentName = name
        currentTmdbId = tmdbId

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            try {
                val detail = TmdbEnrichmentService.fetchPersonDetail(name, tmdbId)
                if (detail != null) {
                    updateState {
                        copy(
                            isLoading = false,
                            personDetail = detail,
                            error = null,
                        )
                    }
                } else {
                    updateState {
                        copy(
                            isLoading = false,
                            personDetail = null,
                            error = "No details found for $name",
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("PersonViewModel", "Failed to load person details", e)
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load person details",
                    )
                }
            }
        }
    }

    private fun selectCategory(category: FilmographyCategory) {
        updateState { copy(selectedCategory = category) }
    }

    private fun selectCreditForMatch(credit: PersonMediaCredit) {
        providerSearchJob?.cancel()
        updateState {
            copy(
                selectedCreditForMatch = credit,
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

                val searchTitle = credit.title.trim()
                val aggregatedMatches = CopyOnWriteArrayList<ProviderMatch>()

                val jobs = activeProviders.map { provider ->
                    launch {
                        try {
                            val res = SafePluginInvoker.invokeOrNull(
                                tag = "Person:Search:${provider.name}",
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
                AppLogger.e("PersonViewModel", "Provider resolution failed: ${e.message}")
                updateState { copy(isSearchingProviders = false) }
            }
        }
    }

    private fun closeProviderPicker() {
        providerSearchJob?.cancel()
        updateState {
            copy(
                selectedCreditForMatch = null,
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
