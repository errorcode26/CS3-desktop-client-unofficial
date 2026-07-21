package com.lagradost.cloudstream3.desktop.ui.screens.search.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEvent

sealed class SearchUiEvent : UiEvent {
    data class OnSearchQueryChange(val query: String) : SearchUiEvent()
    object OnSearch : SearchUiEvent()
    object OnClearSearch : SearchUiEvent()
    data class OnToggleGlobalSearch(val enabled: Boolean) : SearchUiEvent()
    data class OnProviderSelected(val providerName: String) : SearchUiEvent()
    data class OnCategorySelected(val category: com.lagradost.cloudstream3.TvType?) : SearchUiEvent()
}
