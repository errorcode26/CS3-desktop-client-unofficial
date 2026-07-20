package com.lagradost.cloudstream3.desktop.ui.screens.home.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent

sealed interface HomeUiEvent : UiEvent {
    data class OnSearchQueryChange(val query: String) : HomeUiEvent
    data object OnSearch : HomeUiEvent
    data object OnClearSearch : HomeUiEvent
    data class OnSelectProvider(val providerName: String?) : HomeUiEvent
    data object OnClearHistory : HomeUiEvent
    data class OnRemoveHistoryItem(val parentId: String) : HomeUiEvent
    data class OnPrefetchHeroItem(val provider: MainAPI?, val item: SearchResponse) : HomeUiEvent
    data class OnSetCurrentHeroColor(val itemUrl: String?) : HomeUiEvent
    data class OnUpdateHeroColor(val imageUrl: String?, val itemUrl: String? = null) : HomeUiEvent
}
