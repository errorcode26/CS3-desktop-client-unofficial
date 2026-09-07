package com.lagradost.cloudstream3.desktop.ui.screens.links.contract

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.StreamFormatFilter
import com.lagradost.cloudstream3.utils.ExtractorLink

data class LinksUiState(
    val links: List<ExtractorLink> = emptyList(),
    val subtitles: List<SubtitleFile> = emptyList(),
    val statusText: String = "",
    val isScraping: Boolean = false,
    val preferredPlayer: String = "mpv",
    val autoPlayEnabled: Boolean = true,
    val isLaunchingPlayer: Boolean = false,
    val playerLaunchError: String? = null,
    val currentPlayingUrl: String? = null,
    val selectedQuality: Int? = null,
    val selectedFormat: StreamFormatFilter = StreamFormatFilter.ALL,
    val isP2pEnabled: Boolean = false,
) : UiState
