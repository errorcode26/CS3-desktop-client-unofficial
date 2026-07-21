package com.lagradost.cloudstream3.desktop.ui.screens.search.contract

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiState

data class SearchUiState(
    val searchQuery: String = "",
    val searchResultsGrouped: Map<String, List<SearchResponse>>? = null,
    val isLoadingSearch: Boolean = false,
    val isGlobalSearchEnabled: Boolean = false,
    val selectedProviderName: String? = null,
    val selectedCategory: com.lagradost.cloudstream3.TvType? = null,
    val pluginIcons: Map<String, String> = emptyMap(),
    val providers: List<com.lagradost.cloudstream3.MainAPI> = emptyList(),
) : UiState
