package com.lagradost.cloudstream3.desktop.ui.screens.search.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.base.UiState

@Immutable
data class SearchUiState(
    val searchQuery: String = "",
    val searchResultsGrouped: Map<String, Pair<com.lagradost.cloudstream3.MainAPI, List<SearchResponse>>>? = null,
    val isLoadingSearch: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canPaginate: Boolean = true,
    val isGlobalSearchEnabled: Boolean = false,
    val selectedProviderName: String? = null,
    val selectedProviderSource: String? = null,
    val selectedCategories: Set<TvType> = emptySet(),
    val pluginIcons: Map<String, String> = emptyMap(),
    val providers: List<com.lagradost.cloudstream3.MainAPI> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val searchSuggestions: List<com.lagradost.cloudstream3.desktop.ui.screens.search.SearchSuggestionItem> = emptyList(),
    val showSuggestions: Boolean = false,
) : UiState
