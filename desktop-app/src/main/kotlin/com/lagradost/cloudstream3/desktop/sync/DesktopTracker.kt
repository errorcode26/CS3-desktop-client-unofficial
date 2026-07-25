package com.lagradost.cloudstream3.desktop.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * TODO: External Tracking (AniList/Simkl) is currently PAUSED.
 * This interface is boilerplate for the future native desktop tracking implementation.
 * Do not wire this up to the UI until it's ready.
 *
 * A clean, minimalist interface for Tracking services (AniList, Simkl, etc.)
 * specifically designed for the Desktop JVM environment, replacing the legacy Android AuthAPI.
 */
interface DesktopTracker {
    /** The display name of the tracker (e.g. "AniList") */
    val name: String

    /** Whether the user is currently authenticated with this tracker */
    val isLoggedIn: StateFlow<Boolean>

    /**
     * Initiates the OAuth flow.
     * Opens the user's browser, spins up a localhost server to catch the redirect token,
     * saves the token, and returns true if successful.
     */
    suspend fun authenticate(): Boolean

    /** Logs the user out and clears tokens */
    fun logout()

    /**
     * Marks a specific episode as watched on the tracking service.
     * @param providerId The ID of the show on the external tracker
     * @param episodeNumber The episode number
     * @return true if successfully marked
     */
    suspend fun markEpisodeWatched(providerId: String, episodeNumber: Int): Boolean
}
