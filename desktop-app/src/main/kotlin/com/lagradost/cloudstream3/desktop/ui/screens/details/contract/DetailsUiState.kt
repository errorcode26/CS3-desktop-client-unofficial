package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory

sealed interface EnrichmentPhase {
    data object Idle : EnrichmentPhase
    data object InProgress : EnrichmentPhase
    data object Complete : EnrichmentPhase
}

data class SeasonMetadata(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int?,
    val posterUrl: String?,
)

data class ReviewData(
    val author: String,
    val content: String,
    val rating: Double?,
    val avatarUrl: String?,
    val createdAt: String?,
    val url: String?,
)

data class DetailsUiState(
    val preloadedName: String? = null,
    val response: LoadResponse? = null,
    val fakeData: LoadResponse? = null,
    val isLoading: Boolean = true,
    val fetchFailed: Boolean = false,
    val watchHistory: Map<String, WatchHistory> = emptyMap(),
    val activeLinkData: Triple<MainAPI, String, WatchHistory>? = null,
    val isPanelOpen: Boolean = false,
    val screenshots: List<String>? = null,
    val enrichmentPhase: EnrichmentPhase = EnrichmentPhase.Idle,
    val enrichedLogoUrl: String? = null,
    val enrichedBackdropUrl: String? = null,
    val enrichedTagline: String? = null,
    val enrichedStatus: String? = null,
    val enrichedStudios: List<String> = emptyList(),
    val enrichedCollectionName: String? = null,
    val enrichedCollectionBackdrop: String? = null,
    val enrichedSeasonsCount: Int? = null,
    val enrichedEpisodesCount: Int? = null,
    val enrichedSeasonsMetadata: List<SeasonMetadata> = emptyList(),
    val enrichedOriginalLanguage: String? = null,
    val enrichedReleaseDate: String? = null,
    val enrichedCountry: String? = null,
    val enrichedCollectionItems: List<SearchResponse> = emptyList(),
    val isEnriching: Boolean = false,
    val error: String? = null,
    val enrichedBudget: Long? = null,
    val enrichedRevenue: Long? = null,
    val enrichedNetworks: List<String> = emptyList(),
    val enrichedYear: Int? = null,
    val enrichedDuration: Int? = null,
    val enrichedTags: List<String>? = null,
    val enrichedActors: List<ActorData>? = null,
    val enrichedImdbRating: Double? = null,
    val enrichedTmdbRating: Double? = null,
    val enrichedAniListRating: Double? = null,
    val enrichedReviews: List<ReviewData> = emptyList(),
    val enrichedTrailers: List<TrailerData> = emptyList(),
    val enrichedTrailerUrl: String? = null,
    val bookmarks: Map<String, DesktopBookmark> = emptyMap(),
    val autoPlayEnabled: Boolean = true,
    val hasAutoPlayed: Boolean = false,
    val isInitialized: Boolean = false,
    val backupSeasonHistory: Map<String, WatchHistory> = emptyMap(),
    val isEpisodesStackedView: Boolean = false,
    // Bumped each time episode thumbnail URLs are mutated in-place by enrichment.
    // Compose observes this to trigger recomposition of episode cards.
    val episodeThumbnailVersion: Int = 0,
) : UiState
