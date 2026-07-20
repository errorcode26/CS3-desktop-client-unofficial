package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.utils.ExtractorLink

data class PlayerUiState(
    val launchData: VideoLaunchData? = null,
    val isLoadingNextEpisode: Boolean = false,
    val nextEpisodeError: String? = null,
    val nextEpisodeLinks: List<ExtractorLink> = emptyList(),
    val nextEpisodeSubtitles: List<SubtitleFile> = emptyList(),
    val isScrapingLinks: Boolean = false,
    val targetEpisodeData: Episode? = null,
    val autoPlayEnabled: Boolean = true,
) : UiState
