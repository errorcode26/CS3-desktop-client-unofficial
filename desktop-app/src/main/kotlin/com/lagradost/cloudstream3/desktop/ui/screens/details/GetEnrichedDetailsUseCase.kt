package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

sealed interface EnrichmentUpdate {
    data class RawData(val response: LoadResponse) : EnrichmentUpdate
    data class ExtractedColor(val color: Long) : EnrichmentUpdate
    data class LogoLoaded(val url: String) : EnrichmentUpdate
    data class BackdropLoaded(val url: String) : EnrichmentUpdate
    data class ScreenshotsLoaded(val urls: List<String>) : EnrichmentUpdate
    data class ActorsLoaded(val actors: List<com.lagradost.cloudstream3.ActorData>) : EnrichmentUpdate
    data class MetadataLoaded(
        val tagline: String?,
        val status: String?,
        val studios: List<String>,
        val collName: String?,
        val collBg: String?,
        val seasons: Int?,
        val episodes: Int?,
        val seasonsMetadata: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata>?,
        val lang: String?,
        val relDate: String?,
        val country: String?,
        val collItems: List<SearchResponse>,
        val budget: Long?,
        val revenue: Long?,
        val networks: List<String>?,
        val year: Int?,
        val duration: Int?,
        val tags: List<String>?,
        val actors: List<com.lagradost.cloudstream3.ActorData>?,
    ) : EnrichmentUpdate
    data object FullyEnriched : EnrichmentUpdate
    data class Error(val message: String) : EnrichmentUpdate
}

object GetEnrichedDetailsUseCase {
    operator fun invoke(
        provider: MainAPI,
        url: String,
        preloadedName: String? = null,
        preloadedPoster: String? = null,
        preloadedBg: String? = null,
    ): Flow<EnrichmentUpdate> = callbackFlow {
        val rawData = try {
            DetailsRepository.fetchRaw(provider, url, fallbackName = preloadedName)
        } catch (e: Exception) {
            trySend(EnrichmentUpdate.Error(e.message ?: "Failed to fetch raw details"))
            close()
            return@callbackFlow
        }

        if (rawData == null) {
            trySend(EnrichmentUpdate.Error("Failed to fetch raw details"))
            close()
            return@callbackFlow
        }

        trySend(EnrichmentUpdate.RawData(rawData))

        val imageUrl = rawData.backgroundPosterUrl ?: rawData.posterUrl ?: preloadedBg ?: preloadedPoster
        val targetEnrichUrl = if (rawData.url.isNotBlank() && !rawData.url.contains("themoviedb.org")) rawData.url else url

        val enrichJob = launch {
            HybridEnrichmentService.enrich(
                loaded = rawData,
                url = targetEnrichUrl,
                onEnrichmentComplete = {
                    if (rawData.backgroundPosterUrl != null) {
                        trySend(EnrichmentUpdate.BackdropLoaded(rawData.backgroundPosterUrl!!))
                    }
                    if (rawData.posterUrl != null) {
                        // We don't have a PosterLoaded state, but we can just use BackdropLoaded for now or maybe we don't need it for UI.
                    }
                    if (rawData.logoUrl != null) {
                        trySend(EnrichmentUpdate.LogoLoaded(rawData.logoUrl!!))
                    }
                    trySend(EnrichmentUpdate.FullyEnriched)

                    close()
                },
                onScreenshotsLoaded = { screenshots ->
                    trySend(EnrichmentUpdate.ScreenshotsLoaded(screenshots))
                },
                onActorsLoaded = { actors ->
                    trySend(EnrichmentUpdate.ActorsLoaded(actors))
                },
                onMetadataLoaded = { tagline, status, studios, collName, collBg, seasonsCount, episodesCount, seasonsMetadata, origLang, releaseDate, country, collItems, budget, revenue, networks, year, duration, tags, actors ->
                    trySend(
                        EnrichmentUpdate.MetadataLoaded(
                            tagline, status, studios, collName, collBg, seasonsCount, episodesCount, seasonsMetadata, origLang, releaseDate, country, collItems, budget, revenue, networks, year, duration, tags, actors,
                        ),
                    )
                },
            )
        }

        awaitClose {
            enrichJob.cancel()
        }
    }
}
