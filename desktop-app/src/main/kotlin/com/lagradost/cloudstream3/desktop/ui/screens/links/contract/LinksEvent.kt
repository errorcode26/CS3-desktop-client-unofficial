package com.lagradost.cloudstream3.desktop.ui.screens.links.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.common.storage.WatchHistory

sealed interface LinksUiEvent : UiEvent {
    data class OnScrape(val provider: MainAPI, val dataUrl: String) : LinksUiEvent
    data object OnCancelScrape : LinksUiEvent
    data class OnStatusTextChanged(val text: String) : LinksUiEvent
    data class OnSaveWatchPosition(val history: WatchHistory, val positionMs: Long, val durationMs: Long) : LinksUiEvent
    data class OnPreferredPlayerChanged(val player: String) : LinksUiEvent
}
