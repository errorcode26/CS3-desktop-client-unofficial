package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.FullCastUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.FullCastUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.FullCastUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullCastViewModel(
    private val config: Config.FullCast,
) : BaseMviViewModel<FullCastUiState, FullCastUiEvent, FullCastUiEffect>(
    initialState = computeInitialState(config)
) {
    init {
        val s = config.initialSeason
        if (s != null && s > 0 && config.tmdbId != null) {
            fetchSeasonCredits(s)
        }
    }

    override fun handleEvent(event: FullCastUiEvent) {
        when (event) {
            is FullCastUiEvent.OnSelectCategory -> {
                updateState {
                    computeDerivedState(
                        currentState = copy(selectedCategory = event.category),
                        config = config
                    )
                }
            }
            is FullCastUiEvent.OnUpdateSearchQuery -> {
                updateState {
                    computeDerivedState(
                        currentState = copy(searchQuery = event.query),
                        config = config
                    )
                }
            }
            is FullCastUiEvent.OnSelectSeason -> {
                val targetSeason = event.season
                if (targetSeason != null && targetSeason > 0 && config.tmdbId != null) {
                    if (uiState.value.seasonCreditsCache.containsKey(targetSeason)) {
                        updateState {
                            computeDerivedState(
                                currentState = copy(activeSeason = targetSeason),
                                config = config
                            )
                        }
                    } else {
                        fetchSeasonCredits(targetSeason)
                    }
                } else {
                    updateState {
                        computeDerivedState(
                            currentState = copy(activeSeason = null),
                            config = config
                        )
                    }
                }
            }
        }
    }

    private fun fetchSeasonCredits(season: Int) {
        val tmdbId = config.tmdbId ?: return
        updateState { copy(activeSeason = season, isLoadingSeasonCredits = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = try {
                TmdbEnrichmentService.fetchSeasonCredits(tmdbId, season)
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Default) {
                updateState {
                    val updatedCache = if (fetched.isNotEmpty()) {
                        seasonCreditsCache + (season to fetched)
                    } else {
                        seasonCreditsCache
                    }
                    computeDerivedState(
                        currentState = copy(
                            seasonCreditsCache = updatedCache,
                            isLoadingSeasonCredits = false,
                            activeSeason = season
                        ),
                        config = config
                    )
                }
            }
        }
    }

    companion object {
        private fun computeInitialState(config: Config.FullCast): FullCastUiState {
            return computeDerivedState(
                currentState = FullCastUiState(
                    activeSeason = config.initialSeason,
                    availableSeasons = config.availableSeasons,
                ),
                config = config
            )
        }

        private fun computeDerivedState(
            currentState: FullCastUiState,
            config: Config.FullCast,
        ): FullCastUiState {
            val activeSeason = currentState.activeSeason
            val currentSeasonActors = if (activeSeason != null) {
                currentState.seasonCreditsCache[activeSeason]
            } else null

            val hasDualCast = config.cast.any { it.voiceActor != null }
            val activeCast = if (currentSeasonActors != null && !hasDualCast) {
                currentSeasonActors.filter {
                    val r = it.roleString?.trim() ?: ""
                    !r.equals("Director", ignoreCase = true) &&
                        !r.equals("Creator", ignoreCase = true) &&
                        !r.equals("Writer", ignoreCase = true) &&
                        !r.equals("Screenplay", ignoreCase = true) &&
                        !r.equals("Producer", ignoreCase = true) &&
                        !r.equals("Executive Producer", ignoreCase = true)
                }.distinctBy { it.actor.name }
            } else config.cast

            val activeDirectors = if (currentSeasonActors != null) {
                currentSeasonActors.filter { it.roleString?.contains("Director", ignoreCase = true) == true }
            } else config.directors

            val activeWriters = if (currentSeasonActors != null) {
                currentSeasonActors.filter {
                    it.roleString?.contains("Creator", ignoreCase = true) == true ||
                        it.roleString?.contains("Writer", ignoreCase = true) == true ||
                        it.roleString?.contains("Screenplay", ignoreCase = true) == true
                }
            } else config.writers

            val activeProducers = if (currentSeasonActors != null) {
                currentSeasonActors.filter {
                    it.roleString?.contains("Producer", ignoreCase = true) == true
                }
            } else config.producers

            val allMembers = mutableListOf<Pair<ActorData, FullCastCategory>>().apply {
                activeDirectors.forEach { add(it to FullCastCategory.DIRECTORS) }
                activeWriters.forEach { add(it to FullCastCategory.WRITERS) }
                activeCast.forEach { add(it to FullCastCategory.CAST) }
                activeProducers.forEach { add(it to FullCastCategory.PRODUCERS) }
            }

            val query = currentState.searchQuery.trim().lowercase()
            val filteredMembers = allMembers.filter { (actor, category) ->
                val matchesCategory = when (currentState.selectedCategory) {
                    FullCastCategory.ALL -> true
                    else -> category == currentState.selectedCategory
                }
                if (!matchesCategory) return@filter false

                if (query.isBlank()) true else {
                    actor.actor.name.lowercase().contains(query) ||
                        actor.voiceActor?.name?.lowercase()?.contains(query) == true ||
                        actor.roleString?.lowercase()?.contains(query) == true
                }
            }.map { it.first }.distinctBy { it.actor.name + (it.roleString ?: "") }

            val totalCount = allMembers.distinctBy { it.first.actor.name + (it.first.roleString ?: "") }.size

            return currentState.copy(
                allMembers = allMembers,
                filteredMembers = filteredMembers,
                totalCount = totalCount,
                castCount = activeCast.size,
                directorsCount = activeDirectors.size,
                writersCount = activeWriters.size,
                producersCount = activeProducers.size,
            )
        }
    }
}
