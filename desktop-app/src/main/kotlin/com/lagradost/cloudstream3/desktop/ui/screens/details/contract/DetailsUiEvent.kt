package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.common.storage.WatchHistory

sealed interface DetailsUiEvent : UiEvent {
    data object OnLoad : DetailsUiEvent
    data object OnRetry : DetailsUiEvent
    data class OnOpenLinksPanel(val data: Triple<MainAPI, String, WatchHistory>) : DetailsUiEvent
    data object OnCloseLinksPanel : DetailsUiEvent
    data object OnRequestAutoPlay : DetailsUiEvent
    data object OnMarkAutoPlayHandled : DetailsUiEvent
    data class OnPlayEpisode(val ep: com.lagradost.cloudstream3.Episode) : DetailsUiEvent
    data class OnDownloadEpisode(val ep: com.lagradost.cloudstream3.Episode) : DetailsUiEvent
    data class OnToggleEpisodeWatched(val ep: com.lagradost.cloudstream3.Episode, val isWatched: Boolean) : DetailsUiEvent
    data class OnRemoveEpisodeWatched(val ep: com.lagradost.cloudstream3.Episode) : DetailsUiEvent
    data class OnToggleSeasonWatched(val episodes: List<com.lagradost.cloudstream3.Episode>, val isWatched: Boolean) : DetailsUiEvent
    data class OnToggleEpisodesStackedView(val isStacked: Boolean) : DetailsUiEvent
    data class OnSetEpisodeViewMode(val viewMode: Int) : DetailsUiEvent
}
