package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor
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
    data class MetadataLoaded(
        val tagline: String?,
        val status: String?,
        val studios: List<String>,
        val collName: String?,
        val collBg: String?,
        val seasons: Int?,
        val episodes: Int?,
        val lang: String?,
        val relDate: String?,
        val country: String?,
        val collItems: List<SearchResponse>,
        val budget: Long?,
        val revenue: Long?,
        val networks: List<String>?,
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

        val colorJob = if (!imageUrl.isNullOrBlank()) {
            launch {
                val cachedColor = ImageColorExtractor.getCachedColor(imageUrl)
                if (cachedColor != null) {
                    trySend(EnrichmentUpdate.ExtractedColor(cachedColor.value.toLong()))
                } else {
                    val color = ImageColorExtractor.extractDominantColorFromUrl(imageUrl)
                    if (color != null) trySend(EnrichmentUpdate.ExtractedColor(color.value.toLong()))
                }
            }
        } else {
            null
        }

        val enrichJob = launch {
            TmdbEnrichmentService.enrich(
                loaded = rawData,
                url = targetEnrichUrl,
                onEnrichmentComplete = {
                    trySend(EnrichmentUpdate.FullyEnriched)

                    if (rawData.backgroundPosterUrl != null) {
                        launch {
                            val color = ImageColorExtractor.extractDominantColorFromUrl(rawData.backgroundPosterUrl!!)
                            if (color != null) trySend(EnrichmentUpdate.ExtractedColor(color.value.toLong()))
                            close()
                        }
                    } else {
                        close()
                    }
                },
                onScreenshotsLoaded = { screenshots ->
                    trySend(EnrichmentUpdate.ScreenshotsLoaded(screenshots))
                },
                onMetadataLoaded = { tagline, status, studios, collName, collBg, seasons, episodes, lang, relDate, country, collItems, budget, revenue, networks ->
                    trySend(EnrichmentUpdate.MetadataLoaded(tagline, status, studios, collName, collBg, seasons, episodes, lang, relDate, country, collItems, budget, revenue, networks))
                },
            )
        }

        awaitClose {
            colorJob?.cancel()
            enrichJob.cancel()
        }
    }
}
