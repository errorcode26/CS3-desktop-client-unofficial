package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent

sealed interface PlayerUiEvent : UiEvent {
    data class OnInit(val launchData: VideoLaunchData) : PlayerUiEvent
    data class OnLoadEpisode(val episode: Episode) : PlayerUiEvent
    data object OnLoadNextEpisode : PlayerUiEvent
    data object OnLoadPrevEpisode : PlayerUiEvent
    data object OnPlayLoadedEpisode : PlayerUiEvent
    data object OnCancelLoading : PlayerUiEvent
    data object OnCancelScraping : PlayerUiEvent
    data class OnSavePosition(val history: com.lagradost.common.storage.WatchHistory) : PlayerUiEvent
    data class OnSelectShader(val shaderName: String) : PlayerUiEvent
    data class OnPlaybackError(val failedUrl: String) : PlayerUiEvent
    data class OnLinkChange(val url: String) : PlayerUiEvent
}
