package com.lagradost.cloudstream3.desktop.metadata.providers

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentContext
import com.lagradost.cloudstream3.desktop.metadata.MetadataMatch
import com.lagradost.cloudstream3.desktop.metadata.MetadataProvider
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CancellationException

object TmdbMetadataProvider : MetadataProvider {
    private const val TAG = "TmdbProvider"

    override val id: String = "tmdb"
    override val displayName: String = "The Movie Database (TMDB)"
    override val priority: Int = 2
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Cartoon,
        TvType.Documentary,
        TvType.AsianDrama,
    )

    override suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch? {
        // TMDB resolution is typically driven by IMDb/TMDB ID or direct search fallback.
        return null
    }

    override suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean {
        val directTmdbId = match?.tmdbId ?: context.directTmdbId
        val directImdbId = match?.imdbId ?: context.directImdbId

        return try {
            AppLogger.i(TAG, "Enriching via TMDB | directTmdbId=$directTmdbId | directImdbId=$directImdbId")
            TmdbEnrichmentService.enrich(
                loaded = loaded,
                url = context.rawUrl,
                fetchCast = context.fetchCast,
                onLogoLoaded = callbacks.onLogoLoaded,
                onBackdropLoaded = callbacks.onBackdropLoaded,
                onScreenshotsLoaded = callbacks.onScreenshotsLoaded,
                onActorsLoaded = callbacks.onActorsLoaded,
                onTrailersLoaded = callbacks.onTrailersLoaded,
                onReviewsLoaded = callbacks.onReviewsLoaded,
                onRatingsLoaded = callbacks.onRatingsLoaded,
                onMetadataLoaded = callbacks.onMetadataLoaded,
                onEnrichmentComplete = {},
                directTmdbId = directTmdbId,
                directImdbId = directImdbId,
                overwrite = context.overwrite,
                onEpisodeThumbnailsEnriched = callbacks.onEpisodeThumbnailsEnriched,
            )
            AppLogger.i(TAG, "✓ TMDB enrichment completed successfully")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "✗ TMDB enrichment failed — ${e::class.simpleName}: ${e.message}")
            false
        }
    }
}
