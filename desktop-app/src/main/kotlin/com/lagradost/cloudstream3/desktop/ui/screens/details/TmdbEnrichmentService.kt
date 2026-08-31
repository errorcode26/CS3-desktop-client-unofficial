package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object TmdbRateLimiter {
    private var lastRequestTime = 0L
    private val mutex = Mutex()

    // Rate limit set to 35 to play safe. TMDB docs say 40 but we don't trust them.
    private val minInterval = 1000L / 35L

    suspend fun acquire() = mutex.withLock {
        val now = System.currentTimeMillis()
        val wait = minInterval - (now - lastRequestTime)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestTime = System.currentTimeMillis()
    }
}

// ARCHITECTURE NOTE: This is a self-contained, swappable enrichment source.
// It is called exclusively by HybridEnrichmentService and must remain decoupled
// from all UI and ViewModel layers. If this service needs to be replaced (e.g.
// the upstream API goes down), create a new service with the same enrich()
// signature and swap the single call site in HybridEnrichmentService.
object TmdbEnrichmentService {
    private val TMDB_API_KEY: String
        get() = com.lagradost.common.storage.DesktopDataStore.getKey<String>("tmdb_api_key")?.takeIf { it.isNotBlank() } ?: "3828864585df9d4f006c09403eb9a888"

    private val dummyApi = object : com.lagradost.cloudstream3.MainAPI() {
        override var name = "TMDB"
        override var mainUrl = "https://www.themoviedb.org"
    }

    fun tmdbImageUrl(path: String?, size: String = "original"): String? {
        if (path.isNullOrBlank() || path == "null") return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "https://image.tmdb.org/t/p/$size$cleanPath"
    }

    data class DesktopActorDetails(
        val id: Int,
        val name: String,
        val profilePath: String?,
        val biography: String?,
        val birthday: String?,
        val placeOfBirth: String?,
        val deathday: String?,
        val knownFor: List<com.lagradost.cloudstream3.SearchResponse>,
    )

    suspend fun getActorDetails(name: String): DesktopActorDetails? {
        return withContext(Dispatchers.IO) {
            try {
                TmdbRateLimiter.acquire()
                val searchUrl = "https://api.themoviedb.org/3/search/person?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(name, "UTF-8")}&page=1"
                val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                val results = searchData?.get("results")

                val firstResult = if (results != null && results.isArray && results.size() > 0) results.get(0) else null
                val id = firstResult?.get("id")?.asInt() ?: return@withContext null

                TmdbRateLimiter.acquire()
                val detailsUrl = "https://api.themoviedb.org/3/person/$id?api_key=$TMDB_API_KEY&append_to_response=combined_credits"
                val detailsData = com.lagradost.cloudstream3.app.get(detailsUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>() ?: return@withContext null

                val bio = detailsData.get("biography")?.asText()?.takeIf { it.isNotBlank() }
                val bday = detailsData.get("birthday")?.asText()?.takeIf { it.isNotBlank() }
                val pob = detailsData.get("place_of_birth")?.asText()?.takeIf { it.isNotBlank() }
                val dday = detailsData.get("deathday")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val profilePath = detailsData.get("profile_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val profileUrl = tmdbImageUrl(profilePath, "original")

                val castList = detailsData.get("combined_credits")?.get("cast")
                val knownFor = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                if (castList != null && castList.isArray) {
                    val sortedCast = castList.toList().sortedByDescending { it.get("popularity")?.asDouble() ?: 0.0 }
                    sortedCast.take(15).forEach { credit ->
                        val mediaType = credit.get("media_type")?.asText()
                        val title = credit.get("title")?.asText() ?: credit.get("name")?.asText() ?: return@forEach
                        val posterPath = credit.get("poster_path")?.asText()
                        val posterUrl = tmdbImageUrl(posterPath, "original")

                        val recId = credit.get("id")?.asInt()
                        val recUrl = if (recId != null) "https://www.themoviedb.org/$mediaType/$recId" else ""
                        if (mediaType == "movie") {
                            knownFor.add(
                                dummyApi.newMovieSearchResponse(title, url = recUrl, com.lagradost.cloudstream3.TvType.Movie, false) {
                                    this.posterUrl = posterUrl
                                    if (recId != null) this.id = recId
                                },
                            )
                        } else if (mediaType == "tv") {
                            knownFor.add(
                                dummyApi.newTvSeriesSearchResponse(title, url = recUrl, com.lagradost.cloudstream3.TvType.TvSeries, false) {
                                    this.posterUrl = posterUrl
                                    if (recId != null) this.id = recId
                                },
                            )
                        }
                    }
                }

                DesktopActorDetails(
                    id = id,
                    name = name,
                    profilePath = profileUrl,
                    biography = bio,
                    birthday = bday,
                    placeOfBirth = pob,
                    deathday = dday,
                    knownFor = knownFor,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchPersonDetail(
        name: String,
        tmdbId: Int? = null,
    ): com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedId = if (tmdbId != null && tmdbId > 0) {
                    tmdbId
                } else {
                    TmdbRateLimiter.acquire()
                    val searchUrl = "https://api.themoviedb.org/3/search/person?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(name, "UTF-8")}&page=1"
                    val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    val results = searchData?.get("results")
                    val firstResult = if (results != null && results.isArray && results.size() > 0) results.get(0) else null
                    firstResult?.get("id")?.asInt() ?: return@withContext null
                }

                TmdbRateLimiter.acquire()
                val detailsUrl = "https://api.themoviedb.org/3/person/$resolvedId?api_key=$TMDB_API_KEY&append_to_response=combined_credits"
                val detailsData = com.lagradost.cloudstream3.app.get(detailsUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>() ?: return@withContext null

                val personName = detailsData.get("name")?.asText()?.takeIf { it.isNotBlank() } ?: name
                val bio = detailsData.get("biography")?.asText()?.takeIf { it.isNotBlank() }
                val bday = detailsData.get("birthday")?.asText()?.takeIf { it.isNotBlank() }
                val pob = detailsData.get("place_of_birth")?.asText()?.takeIf { it.isNotBlank() }
                val dday = detailsData.get("deathday")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val profilePath = detailsData.get("profile_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val profileUrl = tmdbImageUrl(profilePath, "original")
                val department = detailsData.get("known_for_department")?.asText()?.takeIf { it.isNotBlank() }

                val castNode = detailsData.get("combined_credits")?.get("cast")
                val movieCredits = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit>()
                val tvCredits = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit>()

                if (castNode != null && castNode.isArray) {
                    val sortedList = castNode.toList().sortedByDescending { it.get("popularity")?.asDouble() ?: 0.0 }
                    val seenIds = mutableSetOf<String>()

                    sortedList.forEach { credit ->
                        val creditId = credit.get("id")?.asInt() ?: return@forEach
                        val mediaTypeStr = credit.get("media_type")?.asText() ?: "movie"
                        val key = "$mediaTypeStr-$creditId"
                        if (!seenIds.add(key)) return@forEach

                        val title = credit.get("title")?.asText() ?: credit.get("name")?.asText() ?: return@forEach
                        val posterPath = credit.get("poster_path")?.asText()
                        val backdropPath = credit.get("backdrop_path")?.asText()
                        val releaseDate = credit.get("release_date")?.asText() ?: credit.get("first_air_date")?.asText()
                        val releaseYear = releaseDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                        val character = credit.get("character")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                        val voteAverage = credit.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                        val popularity = credit.get("popularity")?.asDouble() ?: 0.0
                        val overview = credit.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                        val item = com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit(
                            tmdbId = creditId,
                            title = title,
                            posterUrl = tmdbImageUrl(posterPath, "w500"),
                            backdropUrl = tmdbImageUrl(backdropPath, "original"),
                            releaseYear = releaseYear,
                            characterOrJob = character,
                            mediaType = if (mediaTypeStr == "tv") com.lagradost.cloudstream3.TvType.TvSeries else com.lagradost.cloudstream3.TvType.Movie,
                            voteAverage = voteAverage,
                            popularity = popularity,
                            overview = overview,
                        )

                        if (mediaTypeStr == "tv") {
                            tvCredits.add(item)
                        } else {
                            movieCredits.add(item)
                        }
                    }
                }

                com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail(
                    tmdbId = resolvedId,
                    name = personName,
                    biography = bio,
                    birthday = bday,
                    deathday = dday,
                    placeOfBirth = pob,
                    profileUrl = profileUrl,
                    knownForDepartment = department,
                    movieCredits = movieCredits,
                    tvCredits = tvCredits,
                )
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("TmdbEnrichmentService: Failed to fetch person detail", e)
                null
            }
        }
    }

    suspend fun fetchStudioDetail(
        companyId: Int? = null,
        name: String,
    ): com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail? {
        return withContext(Dispatchers.IO) {
            try {
                var resolvedId = if (companyId != null && companyId > 0) companyId else null
                var studioName = name
                var description: String? = null
                var headquarters: String? = null
                var originCountry: String? = null
                var homepage: String? = null
                var logoUrl: String? = null

                if (resolvedId == null && name.isNotBlank()) {
                    TmdbRateLimiter.acquire()
                    val searchUrl = "https://api.themoviedb.org/3/search/company?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(name, "UTF-8")}&page=1"
                    val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    val results = searchData?.get("results")
                    if (results != null && results.isArray && results.size() > 0) {
                        val first = results.get(0)
                        resolvedId = first.get("id")?.asInt()
                        val resName = first.get("name")?.asText()
                        if (!resName.isNullOrBlank()) studioName = resName
                        val logoPath = first.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                        if (logoPath != null) logoUrl = tmdbImageUrl(logoPath, "w500")
                        originCountry = first.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                    }
                }

                if (resolvedId != null && resolvedId > 0) {
                    try {
                        TmdbRateLimiter.acquire()
                        val detailsUrl = "https://api.themoviedb.org/3/company/$resolvedId?api_key=$TMDB_API_KEY"
                        val detailsData = com.lagradost.cloudstream3.app.get(detailsUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        if (detailsData != null) {
                            val cName = detailsData.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            if (cName != null) studioName = cName
                            description = detailsData.get("description")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            headquarters = detailsData.get("headquarters")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            originCountry = detailsData.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" } ?: originCountry
                            homepage = detailsData.get("homepage")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val logoPath = detailsData.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            if (logoPath != null) logoUrl = tmdbImageUrl(logoPath, "w500")
                        }
                    } catch (_: Exception) {
                        // ignore company details fetch failure, continue with discover
                    }
                }

                if (resolvedId == null || resolvedId <= 0) return@withContext null

                val movieTitles = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem>()
                val tvTitles = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem>()

                // Discover movies by company
                try {
                    TmdbRateLimiter.acquire()
                    val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$TMDB_API_KEY&with_companies=$resolvedId&sort_by=popularity.desc&page=1"
                    val movieData = com.lagradost.cloudstream3.app.get(movieUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    val results = movieData?.get("results")
                    if (results != null && results.isArray) {
                        results.forEach { item ->
                            val id = item.get("id")?.asInt() ?: return@forEach
                            val title = item.get("title")?.asText() ?: item.get("name")?.asText() ?: return@forEach
                            val posterPath = item.get("poster_path")?.asText()
                            val backdropPath = item.get("backdrop_path")?.asText()
                            val releaseDate = item.get("release_date")?.asText()
                            val releaseYear = releaseDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                            val voteAverage = item.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                            val popularity = item.get("popularity")?.asDouble() ?: 0.0
                            val overview = item.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                            movieTitles.add(
                                com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem(
                                    tmdbId = id,
                                    title = title,
                                    posterUrl = tmdbImageUrl(posterPath, "w500"),
                                    backdropUrl = tmdbImageUrl(backdropPath, "original"),
                                    releaseYear = releaseYear,
                                    mediaType = com.lagradost.cloudstream3.TvType.Movie,
                                    voteAverage = voteAverage,
                                    popularity = popularity,
                                    overview = overview,
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // ignore movie discover errors
                }

                // Discover TV shows by company
                try {
                    TmdbRateLimiter.acquire()
                    val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$TMDB_API_KEY&with_companies=$resolvedId&sort_by=popularity.desc&page=1"
                    val tvData = com.lagradost.cloudstream3.app.get(tvUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    val results = tvData?.get("results")
                    if (results != null && results.isArray) {
                        results.forEach { item ->
                            val id = item.get("id")?.asInt() ?: return@forEach
                            val title = item.get("name")?.asText() ?: item.get("title")?.asText() ?: return@forEach
                            val posterPath = item.get("poster_path")?.asText()
                            val backdropPath = item.get("backdrop_path")?.asText()
                            val firstAirDate = item.get("first_air_date")?.asText()
                            val releaseYear = firstAirDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                            val voteAverage = item.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                            val popularity = item.get("popularity")?.asDouble() ?: 0.0
                            val overview = item.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                            tvTitles.add(
                                com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem(
                                    tmdbId = id,
                                    title = title,
                                    posterUrl = tmdbImageUrl(posterPath, "w500"),
                                    backdropUrl = tmdbImageUrl(backdropPath, "original"),
                                    releaseYear = releaseYear,
                                    mediaType = com.lagradost.cloudstream3.TvType.TvSeries,
                                    voteAverage = voteAverage,
                                    popularity = popularity,
                                    overview = overview,
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // ignore tv discover errors
                }

                // If tvTitles is empty, also try with_networks in case resolvedId is a TV network
                if (tvTitles.isEmpty()) {
                    try {
                        TmdbRateLimiter.acquire()
                        val tvNetUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$TMDB_API_KEY&with_networks=$resolvedId&sort_by=popularity.desc&page=1"
                        val tvNetData = com.lagradost.cloudstream3.app.get(tvNetUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        val results = tvNetData?.get("results")
                        if (results != null && results.isArray) {
                            results.forEach { item ->
                                val id = item.get("id")?.asInt() ?: return@forEach
                                val title = item.get("name")?.asText() ?: item.get("title")?.asText() ?: return@forEach
                                val posterPath = item.get("poster_path")?.asText()
                                val backdropPath = item.get("backdrop_path")?.asText()
                                val firstAirDate = item.get("first_air_date")?.asText()
                                val releaseYear = firstAirDate?.take(4)?.takeIf { it.isNotBlank() && it != "null" }
                                val voteAverage = item.get("vote_average")?.asDouble()?.takeIf { it > 0.0 }
                                val popularity = item.get("popularity")?.asDouble() ?: 0.0
                                val overview = item.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

                                tvTitles.add(
                                    com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem(
                                        tmdbId = id,
                                        title = title,
                                        posterUrl = tmdbImageUrl(posterPath, "w500"),
                                        backdropUrl = tmdbImageUrl(backdropPath, "original"),
                                        releaseYear = releaseYear,
                                        mediaType = com.lagradost.cloudstream3.TvType.TvSeries,
                                        voteAverage = voteAverage,
                                        popularity = popularity,
                                        overview = overview,
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }

                if (movieTitles.isEmpty() && tvTitles.isEmpty()) return@withContext null

                com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail(
                    id = resolvedId,
                    name = studioName,
                    description = description,
                    headquarters = headquarters,
                    originCountry = originCountry,
                    homepage = homepage,
                    logoUrl = logoUrl,
                    movieTitles = movieTitles,
                    tvTitles = tvTitles,
                )
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("TmdbEnrichmentService: Failed to fetch studio detail", e)
                null
            }
        }
    }

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onActorsLoaded: (List<com.lagradost.cloudstream3.ActorData>) -> Unit = {},
        onTrailersLoaded: (List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData>) -> Unit = {},
        onReviewsLoaded: (List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData>) -> Unit = {},
        onMetadataLoaded: (
            tagline: String?,
            status: String?,
            studios: List<String>,
            collectionName: String?,
            collectionBg: String?,
            seasonsCount: Int?,
            episodesCount: Int?,
            seasons: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata>?,
            originalLang: String?,
            releaseDate: String?,
            country: String?,
            collectionItems: List<com.lagradost.cloudstream3.SearchResponse>,
            budget: Long?,
            revenue: Long?,
            networks: List<String>?,
            year: Int?,
            duration: Int?,
            tags: List<String>?,
            actors: List<com.lagradost.cloudstream3.ActorData>?,
            productionCompanies: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>?,
            networkCompanies: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>?,
        ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onRatingsLoaded: (imdb: Double?, tmdb: Double?, anilist: Double?) -> Unit = { _, _, _ -> },
        onEnrichmentComplete: () -> Unit = {},
        onEpisodeThumbnailsEnriched: () -> Unit = {},
        // When Cinemeta already resolved the TMDB ID, we can skip text search entirely.
        directTmdbId: Int? = null,
        directImdbId: String? = null,
        overwrite: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val isDummy = url.startsWith("dummy_")
                val urlClean = url.removePrefix("dummy_")
                // Use shared TitleUtils for consistent title cleaning across all enrichment stages.
                val (cleanName, titleParsedYear) = TitleUtils.cleanProviderTitle(loaded.name)
                var tempYear: Int? = loaded.year ?: titleParsedYear

                var tempDuration: Int? = loaded.duration
                var tempTags: List<String>? = loaded.tags
                var tempActors: List<com.lagradost.cloudstream3.ActorData>? = loaded.actors
                TmdbRateLimiter.acquire()
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

                val isAnime = loaded is com.lagradost.cloudstream3.AnimeLoadResponse ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                    loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                    loaded.type == com.lagradost.cloudstream3.TvType.OVA ||
                    loaded.tags?.any { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) } == true

                val isTv = loaded.type == com.lagradost.cloudstream3.TvType.TvSeries ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                    loaded.type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Cartoon

                val findMatch: (com.fasterxml.jackson.databind.JsonNode?, String) -> com.fasterxml.jackson.databind.JsonNode? = { resultsNode, queryName ->
                    if (resultsNode == null || !resultsNode.isArray) {
                        null
                    } else {
                        val possible = mutableListOf<Pair<com.fasterxml.jackson.databind.JsonNode, Double>>()
                        val isExplicitMovie = loaded.type == com.lagradost.cloudstream3.TvType.Movie || loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie
                        val isExplicitTv = loaded.type == com.lagradost.cloudstream3.TvType.TvSeries || loaded.type == com.lagradost.cloudstream3.TvType.AsianDrama || loaded.type == com.lagradost.cloudstream3.TvType.Cartoon

                        for (result in resultsNode) {
                            val mediaType = result.get("media_type")?.asText()
                            if (mediaType != null && mediaType != "movie" && mediaType != "tv") continue
                            if (isExplicitMovie && mediaType == "tv") continue
                            if (isExplicitTv && mediaType == "movie") continue

                            val resultName = result.get("name")?.asText() ?: result.get("title")?.asText() ?: result.get("original_name")?.asText() ?: ""
                            val cleanCompare = queryName.lowercase().removePrefix("the ").trim()
                            val resultCompare = resultName.lowercase().removePrefix("the ").trim()

                            val strippedResultName = resultCompare.replace(Regex("[^a-zA-Z0-9]"), "")
                            val strippedCleanName = cleanCompare.replace(Regex("[^a-zA-Z0-9]"), "")

                            // Check strict digit/roman number match to prevent Iron Man -> Iron Man 2 mismatches
                            val numbers1 = Regex("""\b\d+\b""").findAll(cleanCompare).map { it.value }.toSet()
                            val numbers2 = Regex("""\b\d+\b""").findAll(resultCompare).map { it.value }.toSet()
                            val romanRegex = Regex("""\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b""")
                            val romans1 = romanRegex.findAll(cleanCompare).map { it.value }.toSet()
                            val romans2 = romanRegex.findAll(resultCompare).map { it.value }.toSet()
                            val hasNumberMismatch = numbers1 != numbers2 || romans1 != romans2
                            if (hasNumberMismatch) continue

                            // Content Word Match: Rejects unrelated titles with overlapping stop words
                            if (!com.lagradost.cloudstream3.desktop.utils.StringUtils.hasContentWordMatch(queryName, resultName, minOverlapRatio = 0.75)) {
                                continue
                            }

                            val isStrictMatch = strippedResultName.equals(strippedCleanName, ignoreCase = true)

                            val genreArray = result.get("genre_ids")
                            val isAnimation = genreArray?.isArray == true && genreArray.any { it.asInt() == 16 }
                            val originArray = result.get("origin_country")
                            val isEastAsian = originArray?.isArray == true && originArray.any { it.asText() in setOf("JP", "CN", "KR") }

                            // 1. Hard Domain Check for Anime: Never let live action hijack anime!
                            if (isAnime && !isAnimation && !isEastAsian) {
                                continue
                            }

                            val releaseDate = result.get("release_date")?.asText() ?: result.get("first_air_date")?.asText()
                            val resultYear = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

                            // 2. Year Validation:
                            val loadedYear = tempYear
                            if (resultYear != null && loadedYear != null) {
                                if (isTv) {
                                    // For TV Series / Anime, parent show's first_air_date can start on or before season year
                                    if (resultYear > loadedYear + 1) continue
                                } else {
                                    // For Movies, must be within 1 year
                                    if (Math.abs(resultYear - loadedYear) > 1) continue
                                }
                            }

                            var similarity = com.lagradost.cloudstream3.desktop.utils.StringUtils.similarity(strippedCleanName, strippedResultName)
                            if (isStrictMatch) similarity = 1.0

                            if (similarity < 0.80) continue

                            var score = similarity * 10.0
                            if (isAnime && isAnimation) score += 20.0
                            if (resultYear != null && loadedYear != null) {
                                if (resultYear == loadedYear) score += 5.0
                                else if (isTv && resultYear <= loadedYear) score += 2.0
                            }

                            val popularity = result.get("popularity")?.asDouble() ?: 0.0
                            score += Math.min(5.0, popularity / 20.0)

                            possible.add(Pair(result, score))
                        }

                        if (possible.isEmpty()) {
                            null
                        } else {
                            possible.sortByDescending { it.second }
                            possible.first().first
                        }
                    }
                }

                var resolvedMatchId: Int? = null
                var resolvedIsMovie: Boolean = loaded.type == com.lagradost.cloudstream3.TvType.Movie || loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie
                val isExplicitTv = loaded.type == com.lagradost.cloudstream3.TvType.TvSeries || loaded.type == com.lagradost.cloudstream3.TvType.AsianDrama || loaded.type == com.lagradost.cloudstream3.TvType.Cartoon
                val isExplicitMovie = loaded.type == com.lagradost.cloudstream3.TvType.Movie || loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie

                // Fast path 1: We have an IMDb ID — use /find/ with strict type alignment.
                if (directImdbId != null) {
                    TmdbRateLimiter.acquire()
                    val findUrl = "https://api.themoviedb.org/3/find/$directImdbId?api_key=$TMDB_API_KEY&external_source=imdb_id"
                    val findData = com.lagradost.cloudstream3.app.get(findUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    val movieRes = findData?.get("movie_results")
                    val tvRes = findData?.get("tv_results")

                    if (isExplicitTv) {
                        if (tvRes?.isArray == true && tvRes.size() > 0) {
                            resolvedMatchId = tvRes[0].get("id")?.asInt()
                            resolvedIsMovie = false
                            com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → tv id=$resolvedMatchId")
                        }
                    } else if (isExplicitMovie) {
                        if (movieRes?.isArray == true && movieRes.size() > 0) {
                            resolvedMatchId = movieRes[0].get("id")?.asInt()
                            resolvedIsMovie = true
                            com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → movie id=$resolvedMatchId")
                        }
                    } else {
                        if (tvRes?.isArray == true && tvRes.size() > 0) {
                            resolvedMatchId = tvRes[0].get("id")?.asInt()
                            resolvedIsMovie = false
                            com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → tv id=$resolvedMatchId")
                        } else if (movieRes?.isArray == true && movieRes.size() > 0) {
                            resolvedMatchId = movieRes[0].get("id")?.asInt()
                            resolvedIsMovie = true
                            com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → movie id=$resolvedMatchId")
                        }
                    }
                }

                // Fast path 2: Direct TMDB ID — verify type alignment.
                if (resolvedMatchId == null && directTmdbId != null) {
                    resolvedMatchId = directTmdbId
                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: direct TMDB ID → ${if (resolvedIsMovie) "movie" else "tv"} id=$resolvedMatchId")
                }

                if (resolvedMatchId == null) {
                    // Type-safe text search across root title candidates
                    val searchCandidates = com.lagradost.cloudstream3.desktop.utils.TitleUtils.extractRootTitleCandidates(loaded.name)
                    val endpoint = if (isExplicitTv) "tv" else if (isExplicitMovie) "movie" else "multi"

                    for (cand in searchCandidates) {
                        val q = cand.first
                        val searchUrl = "https://api.themoviedb.org/3/search/$endpoint?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(q, "UTF-8")}&page=1&language=en-US"
                        val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        var matchNode = findMatch(searchData?.get("results"), q)

                        // Pass 2: no language filter for non-English titles
                        if (matchNode == null) {
                            TmdbRateLimiter.acquire()
                            val fallbackUrl = "https://api.themoviedb.org/3/search/$endpoint?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(q, "UTF-8")}&page=1"
                            val fallbackData = com.lagradost.cloudstream3.app.get(fallbackUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                            matchNode = findMatch(fallbackData?.get("results"), q)
                        }

                        if (matchNode != null) {
                            resolvedMatchId = matchNode.get("id")?.asInt()
                            val mType = matchNode.get("media_type")?.asText()
                            resolvedIsMovie = if (mType != null) mType == "movie" else !isExplicitTv
                            com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: text search ('$q') → ${if (resolvedIsMovie) "movie" else "tv"} id=$resolvedMatchId")
                            break
                        }
                    }

                    if (resolvedMatchId == null) {
                        com.lagradost.common.logging.AppLogger.w("Enrichment", "  TMDB: text search for '${loaded.name}' found no high-confidence match")
                    }
                }

                var tmdbIsAnime = false
                if (resolvedMatchId != null) {
                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: fetching details for ${if (resolvedIsMovie) "movie" else "tv"}/$resolvedMatchId")
                    val isMovie = resolvedIsMovie
                    val matchId = resolvedMatchId
                    val typeStr = if (isMovie) "movie" else "tv"

                    try {
                        TmdbRateLimiter.acquire()

                        val allLoadedEpisodes = if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                            loaded.episodes
                        } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                            loaded.episodes.values.flatten()
                        } else {
                            emptyList()
                        }
                        val neededSeasons = allLoadedEpisodes.mapNotNull { it.season }.distinct().ifEmpty { listOf(1) }
                        val targetSeasons = neededSeasons.filter { it in 1..25 }.take(6)
                        val seasonsAppend = if (!isMovie && targetSeasons.isNotEmpty()) ",${targetSeasons.joinToString(",") { "season/$it" }}" else ""
                        val ratingsAppend = if (isMovie) ",release_dates" else ",content_ratings"
                        val tmdbLang = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.tmdbLanguage.value.ifBlank { "en-US" }
                        val tmdbImgLang = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.tmdbImageLanguage.value.ifBlank { "en,en-US,null" }
                        val tmdbUrl = "https://api.themoviedb.org/3/$typeStr/$matchId?api_key=$TMDB_API_KEY&append_to_response=images,credits,recommendations,translations,videos,reviews$ratingsAppend$seasonsAppend&language=$tmdbLang&include_image_language=$tmdbImgLang"

                        val tmdbData = com.lagradost.cloudstream3.app.get(tmdbUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        if (tmdbData != null) {
                            val tmdbTitle = tmdbData.get("name")?.asText() ?: tmdbData.get("title")?.asText()
                            if (!tmdbTitle.isNullOrBlank() && tmdbTitle != "null") {
                                withContext(Dispatchers.Main.immediate) {
                                    loaded.name = tmdbTitle
                                }
                            }

                            val tagline = tmdbData.get("tagline")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val status = tmdbData.get("status")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val studios = mutableListOf<String>()
                            val prodCompanies = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>()
                            val prodList = tmdbData.get("production_companies")
                            if (prodList != null && prodList.isArray) {
                                prodList.forEach { s ->
                                    val sId = s.get("id")?.asInt() ?: 0
                                    val sName = s.get("name")?.asText()
                                    val sLogoPath = s.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                    val sLogoUrl = tmdbImageUrl(sLogoPath, "w300")
                                    val sCountry = s.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                    if (!sName.isNullOrBlank() && sName != "null") {
                                        studios.add(sName)
                                        prodCompanies.add(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(sId, sName, sLogoUrl, sCountry))
                                    }
                                }
                            }
                            val collectionNode = tmdbData.get("belongs_to_collection")
                            val collName = collectionNode?.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val collBgPath = collectionNode?.get("backdrop_path")?.asText() ?: collectionNode?.get("poster_path")?.asText()
                            val collBgUrl = tmdbImageUrl(collBgPath, "w1280")

                            val seasonsCount = tmdbData.get("number_of_seasons")?.asInt()?.takeIf { it > 0 }
                            val episodesCount = tmdbData.get("number_of_episodes")?.asInt()?.takeIf { it > 0 }
                            val seasonsArray = tmdbData.get("seasons")
                            val parsedSeasons = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata>()
                            if (seasonsArray != null && seasonsArray.isArray) {
                                seasonsArray.forEach { s ->
                                    val seasonNumber = s.get("season_number")?.asInt()
                                    if (seasonNumber != null) {
                                        val sName = s.get("name")?.asText() ?: "Season $seasonNumber"
                                        val sEpisodeCount = s.get("episode_count")?.asInt()
                                        val sPosterPath = s.get("poster_path")?.asText()
                                        val sPosterUrl = tmdbImageUrl(sPosterPath, "w500")
                                        parsedSeasons.add(
                                            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata(
                                                seasonNumber = seasonNumber,
                                                name = sName,
                                                episodeCount = sEpisodeCount,
                                                posterUrl = sPosterUrl,
                                            ),
                                        )
                                    }
                                }
                            }
                            val originalLangCode = tmdbData.get("original_language")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val formattedLang = when (originalLangCode?.lowercase()) {
                                "ja" -> "Japanese"
                                "ko" -> "Korean"
                                "zh" -> "Chinese"
                                "en" -> "English"
                                "fr" -> "French"
                                "es" -> "Spanish"
                                "de" -> "German"
                                "it" -> "Italian"
                                "ru" -> "Russian"
                                "pt" -> "Portuguese"
                                "hi" -> "Hindi"
                                "th" -> "Thai"
                                else -> originalLangCode?.uppercase()
                            }

                            val rawRelDate = tmdbData.get("release_date")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val rawFirstAir = tmdbData.get("first_air_date")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val rawLastAir = tmdbData.get("last_air_date")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val releaseDateStr = if (isMovie) {
                                rawRelDate ?: rawFirstAir
                            } else {
                                val firstYr = rawFirstAir?.take(4)
                                val lastYr = rawLastAir?.take(4)
                                if (firstYr != null && lastYr != null && firstYr != lastYr) {
                                    "$firstYr – $lastYr"
                                } else {
                                    firstYr ?: rawRelDate
                                }
                            }

                            val countryList = tmdbData.get("origin_country")
                            val countryStr = if (countryList != null && countryList.isArray && countryList.size() > 0) {
                                countryList.mapNotNull { it.asText()?.takeIf { c -> c.isNotBlank() && c != "null" } }.take(2).joinToString(", ")
                            } else {
                                null
                            }

                            val collId = collectionNode?.get("id")?.asInt()
                            val collItems = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                            if (collId != null && collId > 0) {
                                try {
                                    TmdbRateLimiter.acquire()
                                    val collUrl = "https://api.themoviedb.org/3/collection/$collId?api_key=$TMDB_API_KEY&language=en-US"
                                    val collData = com.lagradost.cloudstream3.app.get(collUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                                    val partsNode = collData?.get("parts")
                                    if (partsNode != null && partsNode.isArray) {
                                        partsNode.forEach { p ->
                                            val pTitle = p.get("title")?.asText() ?: p.get("name")?.asText() ?: return@forEach
                                            val pId = p.get("id")?.asInt() ?: return@forEach
                                            val pPosterPath = p.get("poster_path")?.asText()
                                            val pPosterUrl = tmdbImageUrl(pPosterPath, "original")
                                            val pUrl = "https://www.themoviedb.org/movie/$pId"
                                            collItems.add(
                                                dummyApi.newMovieSearchResponse(pTitle, url = pUrl, com.lagradost.cloudstream3.TvType.Movie, false) {
                                                    this.posterUrl = pPosterUrl
                                                    this.id = pId
                                                },
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    // ignore collection fetch errors
                                }
                            }

                            val budget = tmdbData.get("budget")?.asLong()?.takeIf { it > 0 }
                            val revenue = tmdbData.get("revenue")?.asLong()?.takeIf { it > 0 }

                            val networksList = mutableListOf<String>()
                            val netCompanies = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany>()
                            val tmdbNetworks = tmdbData.get("networks")
                            if (tmdbNetworks != null && tmdbNetworks.isArray) {
                                tmdbNetworks.forEach { net ->
                                    val nId = net.get("id")?.asInt() ?: 0
                                    val netName = net.get("name")?.asText()
                                    val nLogoPath = net.get("logo_path")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                    val nLogoUrl = tmdbImageUrl(nLogoPath, "w300")
                                    val nCountry = net.get("origin_country")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                    if (!netName.isNullOrBlank() && netName != "null") {
                                        networksList.add(netName)
                                        netCompanies.add(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(nId, netName, nLogoUrl, nCountry))
                                    }
                                }
                            }

                            // onMetadataLoaded moved down to after tags and actors are parsed

                            val originalLanguage = tmdbData.get("original_language")?.asText()

                            val bgPath = tmdbData.get("backdrop_path")?.asText()
                            val posterPath = tmdbData.get("poster_path")?.asText()

                            withContext(Dispatchers.Main.immediate) {
                                if (bgPath != null && bgPath != "null" && (overwrite || loaded.backgroundPosterUrl.isNullOrBlank())) {
                                    loaded.backgroundPosterUrl = tmdbImageUrl(bgPath, "original")
                                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: set backdrop from backdrop_path")
                                } else if (posterPath != null && posterPath != "null" && loaded.backgroundPosterUrl.isNullOrBlank()) {
                                    loaded.backgroundPosterUrl = tmdbImageUrl(posterPath, "original")
                                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: set backdrop from poster_path (fallback)")
                                }

                                if (posterPath != null && posterPath != "null") {
                                    loaded.posterUrl = tmdbImageUrl(posterPath, "original")
                                }

                                val overview = tmdbData.get("overview")?.asText()
                                if (!overview.isNullOrBlank() && overview != "null" && (overwrite || loaded.plot.isNullOrBlank())) {
                                    loaded.plot = overview
                                }
                                if (loaded.plot.isNullOrBlank() || (overwrite && (overview.isNullOrBlank() || overview == "null"))) {
                                    val translationsList = tmdbData.get("translations")?.get("translations")
                                    if (translationsList != null && translationsList.isArray) {
                                        val enOverview = translationsList.firstOrNull { it.get("iso_639_1")?.asText() == "en" }
                                            ?.get("data")?.get("overview")?.asText()
                                        val nativeOverview = if (!originalLanguage.isNullOrBlank()) {
                                            translationsList.firstOrNull { it.get("iso_639_1")?.asText() == originalLanguage }
                                                ?.get("data")?.get("overview")?.asText()
                                        } else {
                                            null
                                        }
                                        val fallbackPlot = enOverview?.takeIf { it.isNotBlank() && it != "null" }
                                            ?: nativeOverview?.takeIf { it.isNotBlank() && it != "null" }
                                        if (!fallbackPlot.isNullOrBlank()) loaded.plot = fallbackPlot
                                    }
                                }

                                val voteAverage = tmdbData.get("vote_average")?.asDouble()
                                if (voteAverage != null) {
                                    if (loaded.score == null) {
                                        loaded.score = com.lagradost.cloudstream3.Score.from10(voteAverage)
                                    }
                                    onRatingsLoaded(null, voteAverage, null)
                                }

                                val runtime = tmdbData.get("runtime")?.asInt()
                                if (runtime != null && runtime > 0 && (loaded.duration == null || loaded.duration == 0)) {
                                    loaded.duration = runtime
                                } else {
                                    val episodeRunTime = tmdbData.get("episode_run_time")?.get(0)?.asInt()
                                    if (episodeRunTime != null && episodeRunTime > 0 && (tempDuration == null || tempDuration == 0)) {
                                        tempDuration = episodeRunTime
                                    }
                                }

                                val cert = if (isMovie) {
                                    val releaseDatesNode = tmdbData.get("release_dates")?.get("results")
                                    if (releaseDatesNode != null && releaseDatesNode.isArray) {
                                        val usEntry = releaseDatesNode.firstOrNull { it.get("iso_3166_1")?.asText() == "US" } ?: releaseDatesNode.firstOrNull()
                                        val relDates = usEntry?.get("release_dates")
                                        if (relDates != null && relDates.isArray) {
                                            relDates.mapNotNull { it.get("certification")?.asText()?.takeIf { c -> c.isNotBlank() && c != "null" } }.firstOrNull()
                                        } else null
                                    } else null
                                } else {
                                    val contentRatingsNode = tmdbData.get("content_ratings")?.get("results")
                                    if (contentRatingsNode != null && contentRatingsNode.isArray) {
                                        val usEntry = contentRatingsNode.firstOrNull { it.get("iso_3166_1")?.asText() == "US" } ?: contentRatingsNode.firstOrNull()
                                        usEntry?.get("rating")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                    } else null
                                }
                                if (!cert.isNullOrBlank() && loaded.contentRating.isNullOrBlank()) {
                                    loaded.contentRating = cert
                                }

                                val genres = tmdbData.get("genres")
                                if (genres != null && genres.isArray) {
                                    val tmdbTags = mutableListOf<String>()
                                    genres.forEach { tag ->
                                        val name = tag.get("name")?.asText()
                                        if (!name.isNullOrBlank() && name != "null") tmdbTags.add(name)
                                    }
                                    if (tmdbTags.isNotEmpty()) {
                                        if (tmdbTags.any { it.equals("Animation", ignoreCase = true) } && originalLanguage == "ja") {
                                            tmdbIsAnime = true
                                        }
                                        tempTags = tmdbTags
                                    }
                                }
                            }

                            val castList = tmdbData.get("credits")?.get("cast")
                            val crewList = tmdbData.get("credits")?.get("crew")
                            val hasPluginVoiceActors = tempActors?.any { it.voiceActor != null } == true
                            if (!hasPluginVoiceActors) {
                                val actors = mutableListOf<com.lagradost.cloudstream3.ActorData>()

                                // First, add directors from crew list
                                if (crewList != null && crewList.isArray) {
                                    crewList.forEach { crew ->
                                        val job = crew.get("job")?.asText()
                                        if (job?.equals("Director", ignoreCase = true) == true) {
                                            val name = crew.get("name")?.asText()
                                            val profilePath = crew.get("profile_path")?.asText()
                                            if (!name.isNullOrBlank() && name != "null") {
                                                val profileUrl = tmdbImageUrl(profilePath, "original")
                                                actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = "Director"))
                                            }
                                        }
                                    }
                                }
                                // Then, add creators (from TV details)
                                val createdBy = tmdbData.get("created_by")
                                if (createdBy != null && createdBy.isArray) {
                                    createdBy.forEach { creator ->
                                        val name = creator.get("name")?.asText()
                                        val profilePath = creator.get("profile_path")?.asText()
                                        if (!name.isNullOrBlank() && name != "null") {
                                            val profileUrl = tmdbImageUrl(profilePath, "original")
                                            if (actors.none { it.actor.name.equals(name, ignoreCase = true) }) {
                                                actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = "Creator"))
                                            }
                                        }
                                    }
                                }

                                // Then, add regular cast (up to 150 to match all provider actors)
                                if (castList != null && castList.isArray) {
                                    val limit = if (tempActors.isNullOrEmpty()) 30 else 150
                                    castList.take(limit).forEach { cast ->
                                        val name = cast.get("name")?.asText()
                                        val profilePath = cast.get("profile_path")?.asText()
                                        val character = cast.get("character")?.asText()
                                        if (!name.isNullOrBlank() && name != "null") {
                                            if (actors.none { it.actor.name.equals(name, ignoreCase = true) }) {
                                                val profileUrl = tmdbImageUrl(profilePath, "original")
                                                actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = character))
                                            }
                                        }
                                    }
                                }

                                if (actors.isNotEmpty()) {
                                    if (tempActors.isNullOrEmpty()) {
                                        tempActors = actors
                                    } else {
                                        val merged = tempActors.orEmpty().toMutableList()
                                        actors.forEach { tmdbActor ->
                                            val existingIdx = merged.indexOfFirst { it.actor.name.equals(tmdbActor.actor.name, ignoreCase = true) }
                                            if (existingIdx == -1) {
                                                merged.add(tmdbActor)
                                            } else {
                                                val existing = merged[existingIdx]
                                                // Overwrite provider's metadata with accurate TMDB name, photo, and character/role
                                                merged[existingIdx] = existing.copy(
                                                    actor = tmdbActor.actor,
                                                    roleString = tmdbActor.roleString,
                                                )
                                            }
                                        }
                                        tempActors = merged
                                    }
                                }
                            }

                            withContext(Dispatchers.Main.immediate) {
                                loaded.tags = tempTags
                                loaded.actors = tempActors
                            }

                            onMetadataLoaded(
                                tagline,
                                status,
                                studios,
                                collName,
                                collBgUrl,
                                seasonsCount,
                                episodesCount,
                                parsedSeasons,
                                formattedLang,
                                releaseDateStr,
                                countryStr,
                                collItems,
                                budget,
                                revenue,
                                networksList,
                                tempYear,
                                tempDuration,
                                tempTags,
                                tempActors,
                                prodCompanies,
                                netCompanies,
                            )

                            val recList = tmdbData.get("recommendations")?.get("results")
                            if (recList != null && recList.isArray && loaded.recommendations.isNullOrEmpty()) {
                                val recs = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                                recList.forEach { rec ->
                                    val recId = rec.get("id")?.asInt()
                                    val title = rec.get("title")?.asText() ?: rec.get("name")?.asText()
                                    val pPath = rec.get("poster_path")?.asText()
                                    val mType = rec.get("media_type")?.asText() ?: typeStr
                                    if (recId != null && !title.isNullOrBlank() && title != "null") {
                                        val pUrl = tmdbImageUrl(pPath, "original")
                                        val recUrl = "https://www.themoviedb.org/$mType/$recId"
                                        val dummyApi = object : com.lagradost.cloudstream3.MainAPI() {
                                            override var mainUrl = "https://www.themoviedb.org"
                                            override var name = "TMDB"
                                            override val hasMainPage = false
                                        }
                                        val searchResp = if (mType == "tv") {
                                            dummyApi.newTvSeriesSearchResponse(title, recUrl, com.lagradost.cloudstream3.TvType.TvSeries, false) {
                                                this.posterUrl = pUrl
                                                this.id = recId
                                            }
                                        } else {
                                            dummyApi.newMovieSearchResponse(title, recUrl, com.lagradost.cloudstream3.TvType.Movie, false) {
                                                this.posterUrl = pUrl
                                                this.id = recId
                                            }
                                        }
                                        recs.add(searchResp)
                                    }
                                }
                                if (recs.isNotEmpty()) {
                                    withContext(Dispatchers.Main.immediate) {
                                        loaded.recommendations = recs
                                    }
                                }
                            }

                            // Extract episode thumbnails for seasons 1-15
                            if (!isMovie) {
                                val allEpisodes = if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                    loaded.episodes
                                } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                                    loaded.episodes.values.flatten()
                                } else {
                                    emptyList()
                                }

                                val realEpisodes = allEpisodes.filter { !it.data.startsWith("unreleased_") }
                                val newEpisodesToAdd = mutableListOf<com.lagradost.cloudstream3.Episode>()
                                val allSeasonNumbers = (realEpisodes.mapNotNull { it.season } + listOf(1)).distinct()

                                allSeasonNumbers.forEach { seasonNum ->
                                    val seasonNode = tmdbData.get("season/$seasonNum")
                                    if (seasonNode != null && seasonNode.isObject) {
                                        val episodesNode = seasonNode.get("episodes")
                                        if (episodesNode != null && episodesNode.isArray) {
                                            val existingEpNumbersInSeason = realEpisodes.filter { (it.season ?: 1) == seasonNum }.mapNotNull { it.episode }.toSet()

                                            episodesNode.forEach { epNode ->
                                                val epNum = epNode.get("episode_number")?.asInt()
                                                if (epNum != null) {
                                                    val epPosterPath = epNode.get("still_path")?.asText()
                                                    val epPoster = tmdbImageUrl(epPosterPath, "original")
                                                    val epOverview = epNode.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                                    val epReleaseDate = epNode.get("air_date")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                                    val epName = epNode.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                                                    val epRuntime = epNode.get("runtime")?.asInt()?.takeIf { it > 0 }
                                                    val epVote = epNode.get("vote_average")?.asDouble()?.takeIf { it > 0 }

                                                    if (existingEpNumbersInSeason.contains(epNum)) {
                                                        // Enrich existing episode
                                                        val existingEp = realEpisodes.find { (it.season ?: 1) == seasonNum && it.episode == epNum }
                                                        if (existingEp != null) {
                                                            val epIsMissingOrBad = existingEp.posterUrl.isNullOrBlank() || existingEp.posterUrl?.contains("imgbb") == true
                                                            if (epPoster != null && epIsMissingOrBad) {
                                                                existingEp.posterUrl = epPoster
                                                            }
                                                            if ((existingEp.description.isNullOrBlank() || overwrite) && epOverview != null) {
                                                                existingEp.description = epOverview
                                                            }
                                                            if (epReleaseDate != null) {
                                                                val cleanDesc = (existingEp.description ?: "").replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")
                                                                existingEp.description = "||DATE:$epReleaseDate||" + cleanDesc
                                                            }
                                                            if (epName != null) {
                                                                existingEp.name = epName
                                                            }
                                                            if (epRuntime != null) {
                                                                existingEp.runTime = epRuntime
                                                            }
                                                            if (epVote != null && existingEp.score == null) {
                                                                existingEp.score = com.lagradost.cloudstream3.Score.from10(epVote)
                                                            }
                                                        }
                                                    } else {
                                                        // Strictly synthesize ONLY if the episode air date is in the FUTURE
                                                        val isFuture = epReleaseDate?.let { dateStr ->
                                                            try {
                                                                val parsed = java.time.LocalDate.parse(dateStr.take(10))
                                                                val now = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                                                                parsed.isAfter(now)
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        } ?: false

                                                        if (isFuture) {
                                                            val descWithDate = "||DATE:$epReleaseDate||${epOverview ?: ""}"
                                                            val synthetic = dummyApi.newEpisode("unreleased_s${seasonNum}_e${epNum}") {
                                                                this.name = epName ?: "Episode $epNum"
                                                                this.season = seasonNum
                                                                this.episode = epNum
                                                                this.posterUrl = epPoster
                                                                this.description = descWithDate
                                                                this.runTime = epRuntime
                                                                this.score = epVote?.let { com.lagradost.cloudstream3.Score.from10(it) }
                                                            }
                                                            newEpisodesToAdd.add(synthetic)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                val distinctNewEpisodes = newEpisodesToAdd.distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }

                                if (distinctNewEpisodes.isNotEmpty()) {
                                    withContext(Dispatchers.Main.immediate) {
                                        if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                            val baseEpisodes = loaded.episodes.filter { !it.data.startsWith("unreleased_") }
                                            val updated = (baseEpisodes + distinctNewEpisodes)
                                                .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                                                .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
                                            loaded.episodes = updated.toMutableList()
                                        } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                                            val mutableMap = loaded.episodes.toMutableMap()
                                            if (mutableMap.isEmpty()) {
                                                mutableMap[com.lagradost.cloudstream3.DubStatus.Subbed] = distinctNewEpisodes
                                            } else {
                                                mutableMap.keys.forEach { dubKey ->
                                                    val current = mutableMap[dubKey].orEmpty().filter { !it.data.startsWith("unreleased_") }
                                                    val updated = (current + distinctNewEpisodes)
                                                        .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                                                        .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
                                                    mutableMap[dubKey] = updated
                                                }
                                            }
                                            loaded.episodes = mutableMap
                                        }
                                    }
                                }
                            }
                            // Signal episode thumbnails were mutated
                            onEpisodeThumbnailsEnriched()

                            var resolvedLogoUrl: String? = null
                            val logosNode = tmdbData.get("images")?.get("logos")
                            if (logosNode != null && logosNode.isArray && logosNode.size() > 0) {
                                val allLogos = logosNode.mapNotNull { node ->
                                    val path = node.get("file_path")?.asText()
                                    val lang = node.get("iso_639_1")?.asText()
                                    val votes = node.get("vote_average")?.asDouble() ?: 0.0
                                    if (path != null && path != "null") Triple(path, lang, votes) else null
                                }

                                val bestLogoPath = allLogos.filter { it.first.endsWith(".png", ignoreCase = true) && (it.second == "en" || it.second == "en-US") }
                                    .maxByOrNull { it.third }?.first
                                    ?: allLogos.filter { it.first.endsWith(".png", ignoreCase = true) && (it.second.isNullOrBlank() || it.second == "null") }
                                        .maxByOrNull { it.third }?.first
                                    ?: allLogos.filter { it.first.endsWith(".png", ignoreCase = true) }
                                        .maxByOrNull { it.third }?.first
                                    ?: allLogos.filter { (it.second == "en" || it.second == "en-US") }
                                        .maxByOrNull { it.third }?.first
                                    ?: allLogos.firstOrNull()?.first

                                if (bestLogoPath != null && bestLogoPath != "null") {
                                    val sizeParam = if (bestLogoPath.endsWith(".svg", ignoreCase = true)) "original" else "w500"
                                    resolvedLogoUrl = tmdbImageUrl(bestLogoPath, sizeParam)
                                }
                            }

                            // Fallback for anime / multi-season TV shows: inherit root show logo if sub-entry has no logo
                            if (resolvedLogoUrl.isNullOrBlank()) {
                                val rootCandidates = com.lagradost.cloudstream3.desktop.utils.TitleUtils.extractRootTitleCandidates(loaded.name)
                                if (rootCandidates.size > 1) {
                                    for (i in 1 until rootCandidates.size) {
                                        val rootName = rootCandidates[i].first
                                        try {
                                            TmdbRateLimiter.acquire()
                                            val rootSearchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(rootName, "UTF-8")}&page=1"
                                            val rootSearchData = com.lagradost.cloudstream3.app.get(rootSearchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                                            val rootTvId = rootSearchData?.get("results")?.firstOrNull()?.get("id")?.asInt()
                                            if (rootTvId != null) {
                                                TmdbRateLimiter.acquire()
                                                val rootImagesUrl = "https://api.themoviedb.org/3/tv/$rootTvId/images?api_key=$TMDB_API_KEY&include_image_language=en,en-US,null"
                                                val rootImagesData = com.lagradost.cloudstream3.app.get(rootImagesUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                                                val rootLogos = rootImagesData?.get("logos")
                                                if (rootLogos != null && rootLogos.isArray && rootLogos.size() > 0) {
                                                    val rootPath = rootLogos.mapNotNull { node ->
                                                        val path = node.get("file_path")?.asText()
                                                        val lang = node.get("iso_639_1")?.asText()
                                                        val votes = node.get("vote_average")?.asDouble() ?: 0.0
                                                        if (path != null && path != "null") Triple(path, lang, votes) else null
                                                    }.filter { it.first.endsWith(".png", ignoreCase = true) }
                                                        .maxByOrNull { it.third }?.first

                                                    if (rootPath != null) {
                                                        resolvedLogoUrl = tmdbImageUrl(rootPath, "w500")
                                                        com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ Inherited root TV logo from '$rootName' (id=$rootTvId)")
                                                        break
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }

                            if (!resolvedLogoUrl.isNullOrBlank()) {
                                withContext(Dispatchers.Main.immediate) {
                                    if (loaded is com.lagradost.cloudstream3.MovieLoadResponse) {
                                        if (overwrite || loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = resolvedLogoUrl
                                    } else if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                        if (overwrite || loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = resolvedLogoUrl
                                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                                        if (overwrite || loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = resolvedLogoUrl
                                    }
                                }
                            }

                            val backdropsNode = tmdbData.get("images")?.get("backdrops")
                            if (backdropsNode != null && backdropsNode.isArray) {
                                val images = mutableListOf<String>()
                                backdropsNode.filter { it.get("iso_639_1")?.isNull ?: true }
                                    .take(15).forEach { img ->
                                        val path = img.get("file_path")?.asText()
                                        val url = tmdbImageUrl(path, "w1280")
                                        if (url != null) {
                                            images.add(url)
                                        }
                                    }
                                if (images.isNotEmpty()) {
                                    onScreenshotsLoaded(images)
                                }
                            }

                            val videosNode = tmdbData.get("videos")?.get("results")
                            if (videosNode != null && videosNode.isArray && videosNode.size() > 0) {
                                val parsedTrailers = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData>()
                                videosNode.forEach { v ->
                                    val vId = v.get("id")?.asText() ?: return@forEach
                                    val vKey = v.get("key")?.asText()?.takeIf { it.isNotBlank() && it != "null" } ?: return@forEach
                                    val vSite = v.get("site")?.asText() ?: "YouTube"
                                    val vName = v.get("name")?.asText() ?: "Official Trailer"
                                    val vOfficial = v.get("official")?.asBoolean() ?: false
                                    val vPublished = v.get("published_at")?.asText()
                                    val vType = v.get("type")?.asText()?.takeIf { it.isNotBlank() } ?: "Trailer"

                                    val vUrl = if (vSite.equals("YouTube", ignoreCase = true)) {
                                        "https://www.youtube.com/watch?v=$vKey"
                                    } else {
                                        null
                                    }
                                    val vThumbnail = if (vSite.equals("YouTube", ignoreCase = true)) {
                                        "https://img.youtube.com/vi/$vKey/hqdefault.jpg"
                                    } else {
                                        null
                                    }
                                    if (vUrl != null) {
                                        parsedTrailers.add(
                                            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData(
                                                id = vId,
                                                name = vName,
                                                url = vUrl,
                                                rawKey = vKey,
                                                thumbnailUrl = vThumbnail,
                                                site = vSite,
                                                isOfficial = vOfficial,
                                                publishedAt = vPublished,
                                                type = vType,
                                            ),
                                        )
                                    }
                                }
                                if (parsedTrailers.isNotEmpty()) {
                                    val maxLimit = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.maxTrailers.value.coerceIn(3, 50)
                                    val sortedTrailers = parsedTrailers
                                        .distinctBy { it.rawKey }
                                        .distinctBy { it.name.lowercase().trim() }
                                        .sortedWith(
                                            compareByDescending<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData> { it.isOfficial }
                                                .thenByDescending { it.type.equals("Trailer", ignoreCase = true) }
                                                .thenByDescending { it.type.equals("Teaser", ignoreCase = true) },
                                        )
                                        .take(maxLimit)
                                    onTrailersLoaded(sortedTrailers)
                                }
                            }

                            val reviewsNode = tmdbData.get("reviews")?.get("results")
                            if (reviewsNode != null && reviewsNode.isArray && reviewsNode.size() > 0) {
                                val parsedReviews = mutableListOf<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData>()
                                for (r in reviewsNode) {
                                    val author = r.get("author")?.asText() ?: continue
                                    val content = r.get("content")?.asText() ?: continue
                                    val url = r.get("url")?.asText()
                                    val createdAt = r.get("created_at")?.asText()

                                    val authorDetails = r.get("author_details")
                                    val rating = authorDetails?.get("rating")?.asDouble()
                                    var avatarPath = authorDetails?.get("avatar_path")?.asText()

                                    val avatarUrl = if (!avatarPath.isNullOrBlank()) {
                                        if (avatarPath.startsWith("/https")) {
                                            avatarPath.removePrefix("/")
                                        } else {
                                            tmdbImageUrl(avatarPath, "w200")
                                        }
                                    } else {
                                        null
                                    }

                                    parsedReviews.add(
                                        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData(
                                            author = author,
                                            content = content,
                                            rating = rating,
                                            avatarUrl = avatarUrl,
                                            createdAt = createdAt,
                                            url = url,
                                        ),
                                    )
                                }
                                if (parsedReviews.isNotEmpty()) {
                                    onReviewsLoaded(parsedReviews.sortedByDescending { it.rating ?: 0.0 })
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("Error fetching optimized TMDB data", e)
                    }
                }

                // Fetch AniList anime character art and voice actors
                val shouldFetchAniList = tmdbIsAnime || isAnime
                if (shouldFetchAniList && fetchCast) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val aniListCast = fetchAniListCast(cleanName, loaded.year)
                            if (!aniListCast.isNullOrEmpty()) {
                                withContext(Dispatchers.Main.immediate) {
                                    loaded.actors = aniListCast
                                }
                                onActorsLoaded(aniListCast)
                                com.lagradost.common.logging.AppLogger.i("[AniList] Enriched cast with ${aniListCast.size} character+VA entries for '${loaded.name}'")
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("[AniList] Failed to fetch cast for '${loaded.name}'", e)
                        }
                    }
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation (composable disposed)
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error enriching TMDB data", t)
            }
        }
        if (!url.startsWith("dummy_")) {
            DetailsCache.put(url, loaded)
        }
        onEnrichmentComplete()
    }

    private suspend fun fetchAniListCast(title: String, year: Int?): List<com.lagradost.cloudstream3.ActorData>? {
        return withContext(Dispatchers.IO) {
            try {
                val searchQuery = """
                    query (${'$'}search: String) {
                        Page(page: 1, perPage: 5) {
                            media(search: ${'$'}search, type: ANIME) {
                                id
                                title { romaji english native userPreferred }
                                startDate { year }
                            }
                        }
                    }
                """.trimIndent()

                val searchPayload = mapOf(
                    "query" to searchQuery,
                    "variables" to mapOf("search" to title),
                )

                val searchResult = com.lagradost.cloudstream3.app.post(
                    "https://graphql.anilist.co",
                    json = searchPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                ).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()

                val mediaList = searchResult?.get("data")?.get("Page")?.get("media")
                if (mediaList == null || !mediaList.isArray || mediaList.size() == 0) return@withContext null

                // Pick best match: exact title + year if possible, else first result
                val mediaNode = mediaList.firstOrNull { media ->
                    val mediaYear = media.get("startDate")?.get("year")?.asInt()
                    val titles = media.get("title")
                    val allTitles = listOfNotNull(
                        titles?.get("romaji")?.asText(),
                        titles?.get("english")?.asText(),
                        titles?.get("native")?.asText(),
                        titles?.get("userPreferred")?.asText(),
                    )
                    val titleMatch = allTitles.any { it.equals(title, ignoreCase = true) }
                    val yearMatch = year == null || mediaYear == null || mediaYear == year
                    titleMatch && yearMatch
                } ?: mediaList.get(0) // fallback to first result

                val mediaId = mediaNode.get("id")?.asInt() ?: return@withContext null

                // Step 2: Fetch characters + voice actors for the matched show
                val castQuery = """
                    query (${'$'}id: Int) {
                        Media(id: ${'$'}id, type: ANIME) {
                            characters(sort: ROLE, page: 1, perPage: 20) {
                                edges {
                                    role
                                    node {
                                        name { userPreferred full native }
                                        image { large medium }
                                    }
                                    voiceActors(language: JAPANESE) {
                                        name { userPreferred full native }
                                        image { large medium }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()

                val castPayload = mapOf(
                    "query" to castQuery,
                    "variables" to mapOf("id" to mediaId),
                )

                val castResult = com.lagradost.cloudstream3.app.post(
                    "https://graphql.anilist.co",
                    json = castPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                ).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()

                val edges = castResult?.get("data")?.get("Media")?.get("characters")?.get("edges")
                if (edges == null || !edges.isArray) return@withContext null

                val actors = mutableListOf<com.lagradost.cloudstream3.ActorData>()
                edges.forEach { edge ->
                    val charNode = edge.get("node") ?: return@forEach
                    val charName = charNode.get("name")?.let {
                        it.get("userPreferred")?.asText()
                            ?: it.get("full")?.asText()
                            ?: it.get("native")?.asText()
                    } ?: return@forEach
                    val charImage = charNode.get("image")?.let {
                        it.get("large")?.asText() ?: it.get("medium")?.asText()
                    }?.takeIf { it != "null" }

                    val roleStr = when (edge.get("role")?.asText()) {
                        "MAIN" -> com.lagradost.cloudstream3.ActorRole.Main
                        "SUPPORTING" -> com.lagradost.cloudstream3.ActorRole.Supporting
                        "BACKGROUND" -> com.lagradost.cloudstream3.ActorRole.Background
                        else -> null
                    }

                    val vaNodes = edge.get("voiceActors")
                    val voiceActor = if (vaNodes != null && vaNodes.isArray && vaNodes.size() > 0) {
                        val va = vaNodes.get(0)
                        val vaName = va.get("name")?.let {
                            it.get("userPreferred")?.asText()
                                ?: it.get("full")?.asText()
                                ?: it.get("native")?.asText()
                        }
                        val vaImage = va.get("image")?.let {
                            it.get("large")?.asText() ?: it.get("medium")?.asText()
                        }?.takeIf { it != "null" }
                        if (vaName != null) com.lagradost.cloudstream3.Actor(vaName, vaImage) else null
                    } else {
                        null
                    }

                    actors.add(
                        com.lagradost.cloudstream3.ActorData(
                            actor = com.lagradost.cloudstream3.Actor(charName, charImage),
                            role = roleStr,
                            voiceActor = voiceActor,
                        ),
                    )
                }

                if (actors.isEmpty()) null else actors
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("[AniList] fetchAniListCast exception", e)
                null
            }
        }
    }
}
