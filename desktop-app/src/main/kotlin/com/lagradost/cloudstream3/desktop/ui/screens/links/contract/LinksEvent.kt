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
    data class OnAddLinks(val links: List<com.lagradost.cloudstream3.utils.ExtractorLink>) : LinksUiEvent
    data class OnPlayLink(
        val link: com.lagradost.cloudstream3.utils.ExtractorLink,
        val displayTitle: String,
        val history: WatchHistory,
        val loadResponse: com.lagradost.cloudstream3.LoadResponse?,
        val currentPlayingUrl: String?,
    ) : LinksUiEvent
    data class OnFilterQuality(val quality: Int?) : LinksUiEvent
    data class OnFilterFormat(val format: com.lagradost.cloudstream3.desktop.ui.screens.StreamFormatFilter) : LinksUiEvent
    data class OnPlayerLaunchFinished(val error: String? = null) : LinksUiEvent
    data class OnP2pEnabledChanged(val enabled: Boolean) : LinksUiEvent
}
