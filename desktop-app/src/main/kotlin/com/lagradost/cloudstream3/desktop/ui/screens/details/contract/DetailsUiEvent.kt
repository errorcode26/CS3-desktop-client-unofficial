package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.common.storage.WatchHistory

sealed interface DetailsUiEvent : UiEvent {
    data object OnLoad : DetailsUiEvent
    data object OnRetry : DetailsUiEvent
    data class OnOpenLinksPanel(val data: Triple<MainAPI, String, WatchHistory>) : DetailsUiEvent
    data object OnCloseLinksPanel : DetailsUiEvent
}
