package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object TmdbStudioFetcher {

    suspend fun fetchStudioDetail(
        companyId: Int? = null,
        name: String,
        isNetwork: Boolean = false,
    ): StudioDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = TmdbEnrichmentService.TMDB_API_KEY
                var resolvedId = if (companyId != null && companyId > 0) companyId else null
                var entityName = name
                var description: String? = null
                var headquarters: String? = null
                var originCountry: String? = null
                var homepage: String? = null
                var logoUrl: String? = null

                if (isNetwork) {
                    // TMDB Network path (Broadcaster / Streaming Channel)
                    if (resolvedId == null && name.isNotBlank()) {
                        TmdbRateLimiter.acquire()
                        val searchUrl = "https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=${URLEncoder.encode(name, "UTF-8")}&page=1"
                        val searchData = app.get(searchUrl).parsedSafe<JsonNode>()
                        val results = searchData?.get("results")
                        if (results != null && results.isArray && results.size() > 0) {
                            val first = results.get(0)
                            resolvedId = first.get("id")?.asInt()
                            val resName = first.get("name")?.asText()
                            if (!resName.isNullOrBlank()) entityName = resName
                            val logoPath = first.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            if (logoPath != null) logoUrl = TmdbEnrichmentService.tmdbImageUrl(logoPath, "w500")
                            originCountry = first.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                        }
                    }

                    if (resolvedId != null && resolvedId > 0) {
                        try {
                            TmdbRateLimiter.acquire()
                            val detailsUrl = "https://api.themoviedb.org/3/network/$resolvedId?api_key=$apiKey"
                            val detailsData = app.get(detailsUrl).parsedSafe<JsonNode>()
                            if (detailsData != null) {
                                val nName = detailsData.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                if (nName != null) entityName = nName
                                headquarters = detailsData.get("headquarters")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                originCountry = detailsData.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" } ?: originCountry
                                homepage = detailsData.get("homepage")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                val logoPath = detailsData.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                if (logoPath != null) logoUrl = TmdbEnrichmentService.tmdbImageUrl(logoPath, "w500")
                            }
                        } catch (_: Exception) {
                            // ignore network details fetch failure, continue with discover
                        }
                    }

                    if (resolvedId == null || resolvedId <= 0) return@withContext null

                    val movieTitles = mutableListOf<StudioMediaItem>()
                    val tvTitles = mutableListOf<StudioMediaItem>()

                    // Discover TV shows broadcast by this network
                    try {
                        TmdbRateLimiter.acquire()
                        val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=$resolvedId&sort_by=popularity.desc&page=1"
                        val tvData = app.get(tvUrl).parsedSafe<JsonNode>()
                        parseTvResults(tvData, tvTitles)
                    } catch (_: Exception) {
                        // ignore tv discover errors
                    }

                    if (tvTitles.size >= 20) {
                        try {
                            TmdbRateLimiter.acquire()
                            val tvUrl2 = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=$resolvedId&sort_by=popularity.desc&page=2"
                            val tvData2 = app.get(tvUrl2).parsedSafe<JsonNode>()
                            parseTvResults(tvData2, tvTitles)
                        } catch (_: Exception) {
                            // ignore page 2 errors
                        }
                    }

                    if (tvTitles.isEmpty()) return@withContext null

                    return@withContext StudioDetail(
                        id = resolvedId,
                        name = entityName,
                        description = description,
                        headquarters = headquarters,
                        originCountry = originCountry,
                        homepage = homepage,
                        logoUrl = logoUrl,
                        movieTitles = movieTitles,
                        tvTitles = tvTitles,
                        isNetwork = true,
                    )
                } else {
                    // TMDB Production Company path
                    if (resolvedId == null && name.isNotBlank()) {
                        TmdbRateLimiter.acquire()
                        val searchUrl = "https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=${URLEncoder.encode(name, "UTF-8")}&page=1"
                        val searchData = app.get(searchUrl).parsedSafe<JsonNode>()
                        val results = searchData?.get("results")
                        if (results != null && results.isArray && results.size() > 0) {
                            val first = results.get(0)
                            resolvedId = first.get("id")?.asInt()
                            val resName = first.get("name")?.asText()
                            if (!resName.isNullOrBlank()) entityName = resName
                            val logoPath = first.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            if (logoPath != null) logoUrl = TmdbEnrichmentService.tmdbImageUrl(logoPath, "w500")
                            originCountry = first.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                        }
                    }

                    if (resolvedId != null && resolvedId > 0) {
                        try {
                            TmdbRateLimiter.acquire()
                            val detailsUrl = "https://api.themoviedb.org/3/company/$resolvedId?api_key=$apiKey"
                            val detailsData = app.get(detailsUrl).parsedSafe<JsonNode>()
                            if (detailsData != null) {
                                val cName = detailsData.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                if (cName != null) entityName = cName
                                description = detailsData.get("description")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                headquarters = detailsData.get("headquarters")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                originCountry = detailsData.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" } ?: originCountry
                                homepage = detailsData.get("homepage")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                val logoPath = detailsData.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                if (logoPath != null) logoUrl = TmdbEnrichmentService.tmdbImageUrl(logoPath, "w500")
                            }
                        } catch (_: Exception) {
                            // ignore company details fetch failure, continue with discover
                        }
                    }

                    if (resolvedId == null || resolvedId <= 0) return@withContext null

                    val movieTitles = mutableListOf<StudioMediaItem>()
                    val tvTitles = mutableListOf<StudioMediaItem>()

                    // Discover movies by company
                    try {
                        TmdbRateLimiter.acquire()
                        val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_companies=$resolvedId&sort_by=popularity.desc&page=1"
                        val movieData = app.get(movieUrl).parsedSafe<JsonNode>()
                        parseMovieResults(movieData, movieTitles)
                    } catch (_: Exception) {
                        // ignore movie discover errors
                    }

                    // Discover TV shows by company
                    try {
                        TmdbRateLimiter.acquire()
                        val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_companies=$resolvedId&sort_by=popularity.desc&page=1"
                        val tvData = app.get(tvUrl).parsedSafe<JsonNode>()
                        parseTvResults(tvData, tvTitles)
                    } catch (_: Exception) {
                        // ignore tv discover errors
                    }

                    if (movieTitles.isEmpty() && tvTitles.isEmpty()) return@withContext null

                    return@withContext StudioDetail(
                        id = resolvedId,
                        name = entityName,
                        description = description,
                        headquarters = headquarters,
                        originCountry = originCountry,
                        homepage = homepage,
                        logoUrl = logoUrl,
                        movieTitles = movieTitles,
                        tvTitles = tvTitles,
                        isNetwork = false,
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("TmdbStudioFetcher: Failed to fetch studio detail", e)
                null
            }
        }
    }

    private fun parseMovieResults(data: JsonNode?, target: MutableList<StudioMediaItem>) {
        val results = data?.get("results")
        if (results != null && results.isArray) {
            results.forEach { item ->
                val id = item.get("id")?.asInt() ?: return@forEach
                if (target.any { it.tmdbId == id }) return@forEach
                val title = item.get("title")?.asText() ?: item.get("name")?.asText() ?: return@forEach
                val posterPath = item.get("poster_path")?.asText()
                val backdropPath = item.get("backdrop_path")?.asText()
                val releaseDate = item.get("release_date")?.asText()
                val releaseYear = releaseDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                val voteAverage = item.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                val popularity = item.get("popularity")?.asDouble() ?: 0.0
                val overview = item.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                target.add(
                    StudioMediaItem(
                        tmdbId = id,
                        title = title,
                        posterUrl = TmdbEnrichmentService.tmdbImageUrl(posterPath, "w500"),
                        backdropUrl = TmdbEnrichmentService.tmdbImageUrl(backdropPath, "original"),
                        releaseYear = releaseYear,
                        mediaType = TvType.Movie,
                        voteAverage = voteAverage,
                        popularity = popularity,
                        overview = overview,
                    )
                )
            }
        }
    }

    private fun parseTvResults(data: JsonNode?, target: MutableList<StudioMediaItem>) {
        val results = data?.get("results")
        if (results != null && results.isArray) {
            results.forEach { item ->
                val id = item.get("id")?.asInt() ?: return@forEach
                if (target.any { it.tmdbId == id }) return@forEach
                val title = item.get("name")?.asText() ?: item.get("title")?.asText() ?: return@forEach
                val posterPath = item.get("poster_path")?.asText()
                val backdropPath = item.get("backdrop_path")?.asText()
                val firstAirDate = item.get("first_air_date")?.asText()
                val releaseYear = firstAirDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                val voteAverage = item.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                val popularity = item.get("popularity")?.asDouble() ?: 0.0
                val overview = item.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                target.add(
                    StudioMediaItem(
                        tmdbId = id,
                        title = title,
                        posterUrl = TmdbEnrichmentService.tmdbImageUrl(posterPath, "w500"),
                        backdropUrl = TmdbEnrichmentService.tmdbImageUrl(backdropPath, "original"),
                        releaseYear = releaseYear,
                        mediaType = TvType.TvSeries,
                        voteAverage = voteAverage,
                        popularity = popularity,
                        overview = overview,
                    )
                )
            }
        }
    }
}
