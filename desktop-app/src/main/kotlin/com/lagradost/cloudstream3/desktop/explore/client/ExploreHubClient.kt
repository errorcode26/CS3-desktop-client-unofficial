package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbRateLimiter
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object ExploreHubClient {
    private const val TAG = "ExploreHubClient"
    private val mapper = jacksonObjectMapper()

    private val TMDB_GENRES = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10765 to "Sci-Fi & Fantasy",
    )

    fun getGenreName(id: Int): String? = TMDB_GENRES[id]

    fun getGenreId(name: String): Int? {
        val entry = TMDB_GENRES.entries.firstOrNull { it.value.equals(name.trim(), ignoreCase = true) }
        return entry?.key
    }

    /**
     * Fetches movies and series available on a specific streaming platform using TMDB Watch Providers.
     */
    suspend fun fetchPlatformItems(
        platform: StreamingPlatform,
        page: Int = 1,
        genreName: String? = null,
        targetType: String? = null, // "movie", "series", or null for blended
    ): List<ExploreItem> = withContext(Dispatchers.IO) {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""

        val items = mutableListOf<ExploreItem>()

        try {
            coroutineScope {
                val fetchMovies = targetType == null || targetType.equals("movie", ignoreCase = true)
                val fetchSeries = targetType == null || targetType.equals("series", ignoreCase = true) || targetType.equals("tv", ignoreCase = true)

                val movieDeferred = if (fetchMovies && platform != StreamingPlatform.CRUNCHYROLL) {
                    async {
                        fetchTmdbDiscover(
                            url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey" +
                                "&with_watch_providers=${platform.tmdbProviderId}&watch_region=US" +
                                "&with_watch_monetization_types=flatrate&sort_by=popularity.desc&page=$page$genreQuery",
                            mediaType = "movie",
                        )
                    }
                } else null

                val tvDeferred = if (fetchSeries) {
                    async {
                        fetchTmdbDiscover(
                            url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey" +
                                "&with_watch_providers=${platform.tmdbProviderId}&watch_region=US" +
                                "&with_watch_monetization_types=flatrate&sort_by=popularity.desc&page=$page$genreQuery",
                            mediaType = "series",
                        )
                    }
                } else null

                val movies = movieDeferred?.await() ?: emptyList()
                val series = tvDeferred?.await() ?: emptyList()

                // Interleave movies and TV shows naturally
                val maxLen = maxOf(movies.size, series.size)
                for (i in 0 until maxLen) {
                    if (i < series.size) items.add(series[i])
                    if (i < movies.size) items.add(movies[i])
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed fetching items for platform ${platform.displayName}: ${e.message}")
        }

        items.distinctBy { it.id }
    }

    private suspend fun fetchTmdbDiscover(url: String, mediaType: String): List<ExploreItem> {
        return try {
            TmdbRateLimiter.acquire()
            val response = app.get(url, timeout = 10_000L, cacheTime = 60 * 12)
            val root = mapper.readTree(response.text)
            val results = root["results"] ?: return emptyList()
            if (!results.isArray) return emptyList()

            val list = mutableListOf<ExploreItem>()
            for (node in results) {
                val tmdbId = node["id"]?.asInt() ?: continue
                val title = (node["title"]?.asText() ?: node["name"]?.asText())?.takeIf { it.isNotBlank() } ?: continue
                val posterPath = node["poster_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val backdropPath = node["backdrop_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val releaseDate = (node["release_date"]?.asText() ?: node["first_air_date"]?.asText())?.takeIf { it.isNotBlank() }
                val year = releaseDate?.take(4)
                val rating = node["vote_average"]?.asDouble()
                val overview = node["overview"]?.asText()?.takeIf { it.isNotBlank() }

                val genreIds = node["genre_ids"]?.mapNotNull { it.asInt() } ?: emptyList()
                val genreNames = genreIds.mapNotNull { TMDB_GENRES[it] }

                val posterUrl = TmdbEnrichmentService.tmdbImageUrl(posterPath, "w500")
                val backdropUrl = TmdbEnrichmentService.tmdbImageUrl(backdropPath, "original")

                list.add(
                    ExploreItem(
                        id = "tmdb:$tmdbId",
                        type = mediaType,
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = backdropUrl,
                        releaseYear = year,
                        description = overview,
                        rating = rating?.takeIf { it > 0.0 },
                        genres = genreNames,
                    )
                )
            }
            list
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchTmdbDiscover error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchLogoForTmdb(tmdbId: Int, isTv: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            TmdbRateLimiter.acquire()
            val type = if (isTv) "tv" else "movie"
            val apiKey = TmdbEnrichmentService.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/$type/$tmdbId/images?api_key=$apiKey&include_image_language=en,null"
            val response = app.get(url, timeout = 8_000L, cacheTime = 60 * 24 * 7)
            val root = mapper.readTree(response.text)
            val logosNode = root["logos"] ?: return@withContext null
            if (!logosNode.isArray || logosNode.size() == 0) return@withContext null

            val allLogos = logosNode.mapNotNull { node ->
                val path = node["file_path"]?.asText()
                val lang = node["iso_639_1"]?.asText()
                val votes = node["vote_average"]?.asDouble() ?: 0.0
                val count = node["vote_count"]?.asInt() ?: 0
                if (!path.isNullOrBlank() && path != "null") Triple(path, lang, Pair(votes, count)) else null
            }

            val bestLogoCandidate = allLogos.filter { it.first.endsWith(".png", ignoreCase = true) && (it.second == "en" || it.second == "en-US") }
                .maxByOrNull { it.third.first }
                ?: allLogos.filter { it.first.endsWith(".png", ignoreCase = true) && (it.second.isNullOrBlank() || it.second == "null") }
                    .maxByOrNull { it.third.first }
                ?: allLogos.filter { it.first.endsWith(".png", ignoreCase = true) }
                    .maxByOrNull { it.third.first }
                ?: allLogos.filter { (it.second == "en" || it.second == "en-US") }
                    .maxByOrNull { it.third.first }
                ?: allLogos.firstOrNull()

            bestLogoCandidate?.let {
                val path = it.first
                val sizeParam = if (path.endsWith(".svg", ignoreCase = true)) "original" else "w500"
                TmdbEnrichmentService.tmdbImageUrl(path, sizeParam)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "fetchLogoForTmdb failed for $tmdbId: ${e.message}")
            null
        }
    }

    /**
     * Fetches a curated shelf row for a streaming platform (e.g. Trending, Top Rated, New Releases, Genre).
     */
    suspend fun fetchPlatformShelfRow(
        platform: StreamingPlatform,
        sortBy: String = "popularity.desc",
        targetType: String = "both", // "movie", "series", or "both"
        movieGenreId: Int? = null,
        tvGenreId: Int? = null,
        minVoteCount: Int = 0,
        page: Int = 1,
    ): List<ExploreItem> = withContext(Dispatchers.IO) {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val movieGenreQuery = if (movieGenreId != null) "&with_genres=$movieGenreId" else ""
        val effectiveTvGenreId = tvGenreId ?: movieGenreId
        val tvGenreQuery = if (effectiveTvGenreId != null) "&with_genres=$effectiveTvGenreId" else ""
        val voteCountQuery = if (minVoteCount > 0) "&vote_count.gte=$minVoteCount" else ""

        val items = mutableListOf<ExploreItem>()

        try {
            coroutineScope {
                val fetchMovies = targetType.equals("both", ignoreCase = true) || targetType.equals("movie", ignoreCase = true)
                val fetchSeries = targetType.equals("both", ignoreCase = true) || targetType.equals("series", ignoreCase = true) || targetType.equals("tv", ignoreCase = true)

                val movieDeferred = if (fetchMovies && platform != StreamingPlatform.CRUNCHYROLL) {
                    async {
                        fetchTmdbDiscover(
                            url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey" +
                                "&with_watch_providers=${platform.tmdbProviderId}&watch_region=US" +
                                "&with_watch_monetization_types=flatrate&sort_by=$sortBy&page=$page$movieGenreQuery$voteCountQuery",
                            mediaType = "movie",
                        )
                    }
                } else null

                val tvDeferred = if (fetchSeries && platform != StreamingPlatform.CRUNCHYROLL) {
                    async {
                        fetchTmdbDiscover(
                            url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey" +
                                "&with_watch_providers=${platform.tmdbProviderId}&watch_region=US" +
                                "&with_watch_monetization_types=flatrate&sort_by=$sortBy&page=$page$tvGenreQuery$voteCountQuery",
                            mediaType = "series",
                        )
                    }
                } else null

                val movies = movieDeferred?.await() ?: emptyList()
                val series = tvDeferred?.await() ?: emptyList()

                if (targetType.equals("movie", ignoreCase = true)) {
                    items.addAll(movies)
                } else if (targetType.equals("series", ignoreCase = true) || targetType.equals("tv", ignoreCase = true)) {
                    items.addAll(series)
                } else {
                    val maxLen = maxOf(movies.size, series.size)
                    for (i in 0 until maxLen) {
                        if (i < series.size) items.add(series[i])
                        if (i < movies.size) items.add(movies[i])
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed fetching shelf row for ${platform.displayName}: ${e.message}")
        }

        items.distinctBy { it.id }
    }

    /**
     * Fetches trending anime from AniList GraphQL.
     */
    suspend fun fetchTrendingAnime(page: Int = 1, perPage: Int = 24): List<ExploreItem> =
        fetchAnilistShelfRow(sort = "TRENDING_DESC", page = page, perPage = perPage)

    /**
     * Fetches anime shelf rows from AniList GraphQL with custom sort and optional genre filter.
     */
    suspend fun fetchAnilistShelfRow(
        sort: String = "TRENDING_DESC",
        genre: String? = null,
        page: Int = 1,
        perPage: Int = 24,
    ): List<ExploreItem> = withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}genre: String) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, sort: ${'$'}sort, genre: ${'$'}genre) {
                        id
                        idMal
                        title {
                            userPreferred
                            english
                            romaji
                        }
                        coverImage {
                            extraLarge
                            large
                        }
                        bannerImage
                        averageScore
                        seasonYear
                        genres
                        description(asHtml: false)
                        format
                    }
                }
            }
        """.trimIndent()

        val jsonMap = mutableMapOf<String, Any?>(
            "query" to query,
            "variables" to mutableMapOf<String, Any?>(
                "page" to page,
                "perPage" to perPage,
                "sort" to listOf(sort),
            ).apply {
                if (!genre.isNullOrBlank()) {
                    put("genre", genre)
                }
            }
        )
        val payload = mapper.writeValueAsString(jsonMap).toRequestBody("application/json".toMediaTypeOrNull())

        try {
            val response = app.post(
                "https://graphql.anilist.co",
                requestBody = payload,
                timeout = 10_000L,
                cacheTime = 60 * 6,
            )
            val root = mapper.readTree(response.text)
            val mediaArray = root["data"]?.get("Page")?.get("media") ?: return@withContext emptyList()
            if (!mediaArray.isArray) return@withContext emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in mediaArray) {
                val anilistId = node["id"]?.asInt() ?: continue
                val idMal = node["idMal"]?.asInt()
                val titleNode = node["title"]
                val title = (titleNode?.get("userPreferred")?.asText()
                    ?: titleNode?.get("english")?.asText()
                    ?: titleNode?.get("romaji")?.asText())?.takeIf { it.isNotBlank() } ?: continue

                val coverNode = node["coverImage"]
                val posterUrl = (coverNode?.get("extraLarge")?.asText() ?: coverNode?.get("large")?.asText())?.takeIf { it.isNotBlank() }
                val bannerUrl = node["bannerImage"]?.asText()?.takeIf { it.isNotBlank() }
                val score = node["averageScore"]?.asDouble()?.let { it / 10.0 }
                val year = node["seasonYear"]?.asText()?.takeIf { it.isNotBlank() }
                val desc = node["description"]?.asText()?.takeIf { it.isNotBlank() }
                val genres = node["genres"]?.mapNotNull { it.asText() } ?: emptyList()
                val format = node["format"]?.asText()?.lowercase() ?: "anime"

                items.add(
                    ExploreItem(
                        id = if (idMal != null) "mal:$idMal" else "anilist:$anilistId",
                        type = if (format == "movie") "movie" else "series",
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = bannerUrl,
                        releaseYear = year,
                        description = desc,
                        rating = score,
                        genres = genres,
                    )
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchAnilistShelfRow error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchInTheaters(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val now = java.time.LocalDate.now()
        val gte = now.minusDays(75).toString()
        val lte = now.plusDays(7).toString()
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&region=US&with_release_type=3&release_date.gte=$gte&release_date.lte=$lte&with_runtime.gte=60&sort_by=popularity.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchCriticsPicks(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=2015-01-01&vote_average.gte=7.8&vote_count.gte=2500&sort_by=vote_average.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchHiddenGems(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&vote_average.gte=7.5&vote_count.gte=300&vote_count.lte=2500&sort_by=vote_average.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchUnderNinety(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_runtime.lte=90&with_runtime.gte=70&vote_average.gte=6.8&vote_count.gte=300&sort_by=vote_average.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchDecadeMovies(
        startYear: Int,
        endYear: Int,
        minVotes: Int,
        minRating: Double,
        page: Int = 1,
        genreName: String? = null,
    ): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&primary_release_date.gte=$startYear-01-01&primary_release_date.lte=$endYear-12-31&vote_average.gte=$minRating&vote_count.gte=$minVotes&sort_by=popularity.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchForeignCinema(language: String, minRating: Double, page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_original_language=$language&vote_average.gte=$minRating&vote_count.gte=200&sort_by=vote_average.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "movie")
    }

    suspend fun fetchTrendingTv(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/trending/tv/week?api_key=$apiKey&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "series")
    }

    suspend fun fetchAiringTodayTv(page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/tv/airing_today?api_key=$apiKey&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "series")
    }

    suspend fun fetchNetworkTv(
        networkId: Int,
        minVotes: Int = 100,
        minRating: Double = 7.0,
        page: Int = 1,
        genreName: String? = null,
    ): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=$networkId&vote_average.gte=$minRating&vote_count.gte=$minVotes&sort_by=popularity.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "series")
    }

    suspend fun fetchCuratedTvGenre(
        genreId: Int,
        minVotes: Int = 200,
        minRating: Double = 7.5,
        page: Int = 1,
        genreName: String? = null,
    ): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val effectiveGenreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) } ?: genreId
        val url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_genres=$effectiveGenreId&vote_average.gte=$minRating&vote_count.gte=$minVotes&sort_by=vote_average.desc&page=$page"
        return fetchTmdbDiscover(url, "series")
    }

    suspend fun fetchForeignTv(countryOrLang: String, isCountry: Boolean, minRating: Double = 7.5, minVotes: Int = 100, page: Int = 1, genreName: String? = null): List<ExploreItem> {
        val apiKey = TmdbEnrichmentService.TMDB_API_KEY
        val genreId = genreName?.takeIf { !it.equals("All", ignoreCase = true) }?.let { getGenreId(it) }
        val genreQuery = if (genreId != null) "&with_genres=$genreId" else ""
        val countryParam = if (isCountry) "with_origin_country" else "with_original_language"
        val url = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&$countryParam=$countryOrLang&vote_average.gte=$minRating&vote_count.gte=$minVotes&sort_by=popularity.desc&page=$page$genreQuery"
        return fetchTmdbDiscover(url, "series")
    }
}
