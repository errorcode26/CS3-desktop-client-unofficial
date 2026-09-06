package com.lagradost.cloudstream3.desktop.ui.screens.home.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent

sealed interface HomeUiEvent : UiEvent {

    data class OnToggleProviderActive(val providerName: String, val isActive: Boolean) : HomeUiEvent
    data class OnSetSingleProvider(val providerName: String) : HomeUiEvent
    data class OnMoveProvider(val fromIndex: Int, val toIndex: Int) : HomeUiEvent
    data object OnClearHistory : HomeUiEvent
    data class OnRemoveHistoryItem(val parentId: String) : HomeUiEvent
    data class OnPrefetchHeroItem(val provider: com.lagradost.cloudstream3.MainAPI?, val item: com.lagradost.cloudstream3.SearchResponse) : HomeUiEvent
    data object OnProviderRefresh : HomeUiEvent
    data class OnShowHomeManagement(val show: Boolean) : HomeUiEvent
    data class OnToggleCatalog(val providerName: String, val catalogName: String, val isEnabled: Boolean) : HomeUiEvent
    data class OnLoadCategory(val provider: MainAPI, val pageData: com.lagradost.cloudstream3.MainPageData) : HomeUiEvent
}
