package com.lagradost.cloudstream3.desktop.metadata

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData

/**
 * Represents a resolved universal media match from a metadata resolver.
 */
data class MetadataMatch(
    val providerId: String,
    val matchedTitle: String,
    val matchedYear: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val anilistId: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val rating: Double? = null,
    val rawData: Any? = null,
)

/**
 * Context passed to metadata providers during enrichment.
 */
data class MetadataEnrichmentContext(
    val rawUrl: String,
    val fetchCast: Boolean = true,
    val overwrite: Boolean = false,
    val directImdbId: String? = null,
    val directTmdbId: Int? = null,
)

/**
 * Unified callbacks for streaming progressive metadata updates to the UI layer.
 */
data class MetadataEnrichmentCallbacks(
    val onScreenshotsLoaded: (List<String>) -> Unit = {},
    val onActorsLoaded: (List<ActorData>) -> Unit = {},
    val onTrailersLoaded: (List<TrailerData>) -> Unit = {},
    val onReviewsLoaded: (List<ReviewData>) -> Unit = {},
    val onEpisodeThumbnailsEnriched: () -> Unit = {},
    val onRatingsLoaded: (imdb: Double?, tmdb: Double?, anilist: Double?) -> Unit = { _, _, _ -> },
    val onMetadataLoaded: (
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
    val onEnrichmentComplete: () -> Unit = {},
)
