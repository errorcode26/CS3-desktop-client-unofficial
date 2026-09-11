package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.details.FullCastCategory

data class FullCastUiState(
    val selectedCategory: FullCastCategory = FullCastCategory.ALL,
    val searchQuery: String = "",
    val activeSeason: Int? = null,
    val availableSeasons: List<Int> = emptyList(),
    val seasonCreditsCache: Map<Int, List<ActorData>> = emptyMap(),
    val isLoadingSeasonCredits: Boolean = false,
    val allMembers: List<Pair<ActorData, FullCastCategory>> = emptyList(),
    val filteredMembers: List<ActorData> = emptyList(),
    val totalCount: Int = 0,
    val castCount: Int = 0,
    val directorsCount: Int = 0,
    val writersCount: Int = 0,
    val producersCount: Int = 0,
) : UiState

sealed interface FullCastUiEvent : UiEvent {
    data class OnSelectCategory(val category: FullCastCategory) : FullCastUiEvent
    data class OnUpdateSearchQuery(val query: String) : FullCastUiEvent
    data class OnSelectSeason(val season: Int?) : FullCastUiEvent
}

sealed interface FullCastUiEffect : UiEffect
