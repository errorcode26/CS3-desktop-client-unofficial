package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient

/**
 * Backward-compatible bridge delegating to [StremioAddonClient].
 */
@Deprecated("Use StremioAddonClient instead", ReplaceWith("StremioAddonClient"))
object CinemetaAPI {
    suspend fun search(query: String, type: String = "movie") = StremioAddonClient.search(query, type)
    suspend fun getMeta(id: String, type: String = "movie") = StremioAddonClient.getMeta(id, type)
}
