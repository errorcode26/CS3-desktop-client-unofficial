package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.utils.ExtractorLink

data class PlayerUiState(
    val launchData: VideoLaunchData? = null,
    val isLoadingNextEpisode: Boolean = false,
    val nextEpisodeError: PlayerError? = null,
    val nextEpisodeLinks: List<ExtractorLink> = emptyList(),
    val nextEpisodeSubtitles: List<SubtitleFile> = emptyList(),
    val isScrapingLinks: Boolean = false,
    val targetEpisodeData: Episode? = null,
    val autoPlayEnabled: Boolean = true,
    // Single source of truth: the link MPV should be playing right now.
    // Owned entirely by the ViewModel — the UI never mutates this directly.
    val activeLink: ExtractorLink? = null,
    // URLs that have failed playback in the current episode session.
    // Owned by the ViewModel so link-picking can account for failures across recompositions.
    val failedLinks: Set<String> = emptySet(),
) : UiState {
    val episodes: List<Episode> get() {
        val currentData = launchData ?: return emptyList()
        return when (val resp = currentData.loadResponse) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> resp.episodes
            is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                val dub = resp.episodes.entries.firstOrNull { entry -> entry.value.any { it.data == currentData.history.episodeId } }?.key
                resp.episodes[dub] ?: emptyList()
            }
            else -> emptyList()
        }
    }

    val hasNextEpisode: Boolean get() {
        val eps = episodes
        val currentData = launchData ?: return false
        val currentIndex = eps.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex != -1 && currentIndex + 1 < eps.size
    }

    val nextEpisodeData: Episode? get() {
        val eps = episodes
        val currentData = launchData ?: return null
        val currentIndex = eps.indexOfFirst { it.data == currentData.history.episodeId }
        return if (currentIndex != -1 && currentIndex + 1 < eps.size) eps[currentIndex + 1] else null
    }

    val hasPrevEpisode: Boolean get() {
        val eps = episodes
        val currentData = launchData ?: return false
        val currentIndex = eps.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex > 0
    }
}
