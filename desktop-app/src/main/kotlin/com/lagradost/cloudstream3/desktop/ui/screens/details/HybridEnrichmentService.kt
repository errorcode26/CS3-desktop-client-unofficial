package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks
import com.lagradost.cloudstream3.desktop.metadata.MetadataPipeline
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData

/**
 * Backward-compatible bridge delegating directly to [MetadataPipeline].
 */
object HybridEnrichmentService {

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onActorsLoaded: (List<ActorData>) -> Unit = {},
        onTrailersLoaded: (List<TrailerData>) -> Unit = {},
        onReviewsLoaded: (List<ReviewData>) -> Unit = {},
        onMetadataLoaded: (
            tagline: String?,
            status: String?,
            studios: List<String>,
            collectionName: String?,
            collectionBg: String?,
            seasonsCount: Int?,
            episodesCount: Int?,
            seasons: List<SeasonMetadata>?,
            originalLang: String?,
            releaseDate: String?,
            country: String?,
            collectionItems: List<SearchResponse>,
            budget: Long?,
            revenue: Long?,
            networks: List<String>?,
            year: Int?,
            duration: Int?,
            tags: List<String>?,
            actors: List<ActorData>?,
        ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onEnrichmentComplete: () -> Unit = {},
        onEpisodeThumbnailsEnriched: () -> Unit = {},
    ) {
        val callbacks = MetadataEnrichmentCallbacks(
            onScreenshotsLoaded = onScreenshotsLoaded,
            onActorsLoaded = onActorsLoaded,
            onTrailersLoaded = onTrailersLoaded,
            onReviewsLoaded = onReviewsLoaded,
            onMetadataLoaded = onMetadataLoaded,
            onEnrichmentComplete = onEnrichmentComplete,
            onEpisodeThumbnailsEnriched = onEpisodeThumbnailsEnriched,
        )

        MetadataPipeline.enrich(
            loaded = loaded,
            url = url,
            fetchCast = fetchCast,
            callbacks = callbacks,
        )
    }
}
