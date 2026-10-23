package com.lagradost.cloudstream3.desktop.explore.client

import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem

/**
 * Legacy compatibility wrapper that redirects calls to [AniListCatalogClient].
 * Jikan (api.jikan.moe) is replaced by AniList GraphQL for improved reliability and performance.
 */
object JikanClient {
    suspend fun fetchAiringNow(page: Int = 1): List<ExploreItem> =
        AniListCatalogClient.fetchAiringNow(page)

    suspend fun fetchUpcoming(page: Int = 1): List<ExploreItem> =
        AniListCatalogClient.fetchUpcoming(page)

    suspend fun fetchTopAnime(
        page: Int = 1,
        filter: String? = null,
        type: String? = null,
    ): List<ExploreItem> {
        return when {
            type.equals("movie", ignoreCase = true) -> AniListCatalogClient.fetchTopMovies(page)
            type.equals("tv", ignoreCase = true) -> AniListCatalogClient.fetchTopSeries(page)
            filter.equals("bypopularity", ignoreCase = true) -> AniListCatalogClient.fetchPopular(page)
            else -> AniListCatalogClient.fetchAllTime(page)
        }
    }

    suspend fun fetchByEra(
        startDate: String,
        endDate: String,
        page: Int = 1,
    ): List<ExploreItem> {
        val startYear = startDate.take(4).toIntOrNull() ?: 2020
        val endYear = endDate.take(4).toIntOrNull() ?: 2029
        return AniListCatalogClient.fetchByEra(startYear, endYear, page)
    }

    suspend fun fetchByGenre(
        genreId: Int,
        page: Int = 1,
    ): List<ExploreItem> {
        val genre = when (genreId) {
            1 -> "Action"
            10 -> "Fantasy"
            else -> "Action"
        }
        return AniListCatalogClient.fetchByGenre(genre, page)
    }
}
