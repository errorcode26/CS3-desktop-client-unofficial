package com.lagradost.cloudstream3.desktop.ui.screens.links.contract

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.utils.ExtractorLink

data class LinksUiState(
    val links: List<ExtractorLink> = emptyList(),
    val subtitles: List<SubtitleFile> = emptyList(),
    val statusText: String = "Finding streams for you...",
    val isScraping: Boolean = true,
) : UiState
