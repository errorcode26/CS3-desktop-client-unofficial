package com.lagradost.cloudstream3.desktop.ui.screens.search.contract

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent

sealed class SearchUiEvent : UiEvent {
    data class OnSearchQueryChange(val query: String) : SearchUiEvent()
    object OnSearch : SearchUiEvent()
    object OnClearSearch : SearchUiEvent()
    data class OnToggleGlobalSearch(val enabled: Boolean) : SearchUiEvent()
    data class OnProviderSelected(val providerName: String, val sourcePlugin: String? = null) : SearchUiEvent()
    data class OnToggleCategory(val category: TvType) : SearchUiEvent()
    data object OnClearCategories : SearchUiEvent()
    data class OnRemoveSearchHistoryItem(val query: String) : SearchUiEvent()
    object OnClearSearchHistory : SearchUiEvent()
    data object OnLoadMore : SearchUiEvent()
    data class OnSelectSuggestion(val query: String, val submitSearch: Boolean = true) : SearchUiEvent()
    data object OnDismissSuggestions : SearchUiEvent()
    data class OnSetProviderTypeFilter(val types: Set<TvType>) : SearchUiEvent()
}
