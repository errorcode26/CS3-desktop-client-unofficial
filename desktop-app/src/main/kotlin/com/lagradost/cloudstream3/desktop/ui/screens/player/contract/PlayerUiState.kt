package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.utils.ExtractorLink

@Immutable
data class PlayerUiState(
    val launchData: VideoLaunchData? = null,
    val nextEpisodeError: PlayerError? = null,
    val nextEpisodeLinks: List<ExtractorLink> = emptyList(),
    val nextEpisodeSubtitles: List<SubtitleFile> = emptyList(),
    val targetEpisodeData: Episode? = null,
    val failedLinks: Map<String, String> = emptyMap(),
    val countdownToNextEpisode: Int? = null,
    val autoPlayEnabled: Boolean = true,
    /** Single source of truth for the scrape/playback pipeline state. */
    val phase: PlayerPhase = PlayerPhase.Idle,
) : UiState {

    // ---------------------------------------------------------------------------
    // Backward-compat derived properties - the UI reads these; no UI changes needed
    // ---------------------------------------------------------------------------

    val activeLink: ExtractorLink? get() = when (phase) {
        is PlayerPhase.Probing -> phase.link
        is PlayerPhase.Playing -> phase.link
        else -> null
    }

    /** True while the probing spinner should be shown over the video canvas. */
    val isProbingOverlay: Boolean get() =
        phase is PlayerPhase.Scraping || phase is PlayerPhase.Probing || phase is PlayerPhase.Exhausted

    /** True while a scrape job is running in the background. */
    val isScrapingLinks: Boolean get() = when (phase) {
        is PlayerPhase.Scraping -> true
        is PlayerPhase.Probing -> phase.stillScraping
        is PlayerPhase.Playing -> phase.stillScraping
        else -> false
    }

    /** True while waiting for the first link to arrive (full loading overlay). */
    val isLoadingNextEpisode: Boolean get() = phase is PlayerPhase.Scraping

    // ---------------------------------------------------------------------------
    // Episode list helpers
    // ---------------------------------------------------------------------------

    val episodes: List<Episode> get() {
        val currentData = launchData ?: return emptyList()
        if (currentData.episodes.isNotEmpty()) {
            return currentData.episodes
        }
        return when (val resp = currentData.loadResponse) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> resp.episodes
            is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                val exactDub = resp.episodes.entries.firstOrNull { entry ->
                    entry.value.any { it.data == currentData.history.episodeId }
                }?.key
                val fallbackDub = exactDub ?: resp.episodes.entries.firstOrNull { entry ->
                    entry.value.any { currentData.history.episode != null && it.episode == currentData.history.episode }
                }?.key
                resp.episodes[fallbackDub] ?: resp.episodes.values.firstOrNull() ?: emptyList()
            }
            else -> emptyList()
        }
    }

    val hasNextEpisode: Boolean get() {
        val eps = episodes
        val currentData = launchData ?: return false
        val currentEpId = currentData.history.episodeId
        var currentIndex = eps.indexOfFirst { it.data == currentEpId }
        if (currentIndex == -1 && currentData.history.episode != null) {
            currentIndex = eps.indexOfFirst {
                it.episode == currentData.history.episode &&
                    (currentData.history.season == null || it.season == currentData.history.season)
            }
        }
        if (currentIndex == -1 || currentIndex + 1 >= eps.size) return false
        val nextEp = eps[currentIndex + 1]
        val lockUnreleased = com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.value
        if (lockUnreleased) {
            val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(nextEp)
            if (status.isUnreleased) return false
        }
        return true
    }

    val nextEpisodeData: Episode? get() {
        val eps = episodes
        val currentData = launchData ?: return null
        val currentEpId = currentData.history.episodeId
        var currentIndex = eps.indexOfFirst { it.data == currentEpId }
        if (currentIndex == -1 && currentData.history.episode != null) {
            currentIndex = eps.indexOfFirst {
                it.episode == currentData.history.episode &&
                    (currentData.history.season == null || it.season == currentData.history.season)
            }
        }
        if (currentIndex == -1 || currentIndex + 1 >= eps.size) return null
        val nextEp = eps[currentIndex + 1]
        val lockUnreleased = com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.value
        if (lockUnreleased) {
            val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(nextEp)
            if (status.isUnreleased) return null
        }
        return nextEp
    }

    val hasPrevEpisode: Boolean get() {
        val eps = episodes
        val currentData = launchData ?: return false
        val currentEpId = currentData.history.episodeId
        var currentIndex = eps.indexOfFirst { it.data == currentEpId }
        if (currentIndex == -1 && currentData.history.episode != null) {
            currentIndex = eps.indexOfFirst {
                it.episode == currentData.history.episode &&
                    (currentData.history.season == null || it.season == currentData.history.season)
            }
        }
        return currentIndex > 0
    }
}
