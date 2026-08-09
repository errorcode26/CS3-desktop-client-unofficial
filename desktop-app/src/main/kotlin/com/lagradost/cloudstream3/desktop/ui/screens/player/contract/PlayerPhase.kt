package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Single source of truth for where the player pipeline is at any moment.
 * Replaces the old 4-boolean cluster (isScrapingLinks, isLoadingNextEpisode,
 * isProbingOverlay, activeLink) which could drift into invalid combinations.
 *
 * Transitions:
 *   Idle --> Scraping --> Probing --> Playing
 *                 ^           |
 *                 +-----------+ (link failed, no next, but still scraping)
 */
sealed class PlayerPhase {

    /** No episode is loading and nothing is playing. Initial state after open. */
    data object Idle : PlayerPhase()

    /**
     * Scraping links for an episode - no playable link delivered yet.
     * The loading overlay is shown in full.
     */
    data object Scraping : PlayerPhase()

    /**
     * First link arrived from the scraper. MPV is trying it but has not yet
     * confirmed a decoded frame (onPlaybackReady not fired yet).
     * Scraping may still be delivering more links in the background.
     */
    data class Probing(
        val link: ExtractorLink,
        val stillScraping: Boolean,
    ) : PlayerPhase()

    /**
     * MPV confirmed at least one decoded frame (onPlaybackReady fired).
     * Controls are visible. Scraping may still be accumulating extra links.
     */
    data class Playing(
        val link: ExtractorLink,
        val stillScraping: Boolean,
    ) : PlayerPhase()
}
