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
    internal val TMDB_API_KEY: String
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

    typealias DesktopActorDetails = TmdbPersonFetcher.DesktopActorDetails

    suspend fun getActorDetails(name: String): DesktopActorDetails? =
        TmdbPersonFetcher.getActorDetails(name)

    suspend fun fetchPersonDetail(
        name: String,
        tmdbId: Int? = null,
    ): com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail? =
        TmdbPersonFetcher.fetchPersonDetail(name, tmdbId)

    suspend fun fetchStudioDetail(
        companyId: Int? = null,
        name: String,
    ): com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail? =
        TmdbStudioFetcher.fetchStudioDetail(companyId, name)

    fun parseCreditsJson(
        castList: com.fasterxml.jackson.databind.JsonNode?,
        crewList: com.fasterxml.jackson.databind.JsonNode?,
        createdBy: com.fasterxml.jackson.databind.JsonNode? = null,
        limit: Int = 150,
    ): List<com.lagradost.cloudstream3.ActorData> {
        val parsedCast = mutableListOf<com.lagradost.cloudstream3.ActorData>()
        val parsedCrew = mutableListOf<com.lagradost.cloudstream3.ActorData>()

        // 1. Map crew roles by person name (so actors who are also producers/directors get dual attribution)
        val personCrewRoles = mutableMapOf<String, String>()
        if (crewList != null && crewList.isArray) {
            crewList.forEach { crew ->
                val job = crew.get("job")?.asText()
                val dept = crew.get("department")?.asText()
                val name = crew.get("name")?.asText()
                val profilePath = crew.get("profile_path")?.asText()
                if (!name.isNullOrBlank() && name != "null") {
                    val profileUrl = tmdbImageUrl(profilePath, "original")
                    val role = when {
                        job?.equals("Director", ignoreCase = true) == true -> "Director"
                        job?.equals("Screenplay", ignoreCase = true) == true ||
                        job?.equals("Writer", ignoreCase = true) == true ||
                        dept?.equals("Writing", ignoreCase = true) == true -> "Writer"
                        job?.equals("Producer", ignoreCase = true) == true ||
                        job?.equals("Executive Producer", ignoreCase = true) == true -> "Producer"
                        else -> null
                    }
                    if (role != null) {
                        val existingRole = personCrewRoles[name.lowercase()]
                        if (existingRole == null || (role == "Director" && existingRole != "Director")) {
                            personCrewRoles[name.lowercase()] = role
                        }
                        if (parsedCrew.none { it.actor.name.equals(name, ignoreCase = true) && it.roleString.equals(role, ignoreCase = true) }) {
                            parsedCrew.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = role, role = null))
                        }
                    }
                }
            }
        }

        // 2. Creators from TV details
        if (createdBy != null && createdBy.isArray) {
            createdBy.forEach { creator ->
                val name = creator.get("name")?.asText()
                val profilePath = creator.get("profile_path")?.asText()
                if (!name.isNullOrBlank() && name != "null") {
                    val profileUrl = tmdbImageUrl(profilePath, "original")
                    personCrewRoles[name.lowercase()] = "Creator"
                    if (parsedCrew.none { it.actor.name.equals(name, ignoreCase = true) && it.roleString == "Creator" }) {
                        parsedCrew.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = "Creator", role = null))
                    }
                }
            }
        }

        // 3. Cast in strict billing order - never overwrite an actor with Producer!
        if (castList != null && castList.isArray) {
            castList.take(limit).forEach { cast ->
                val name = cast.get("name")?.asText()
                val profilePath = cast.get("profile_path")?.asText()
                val character = cast.get("character")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                if (!name.isNullOrBlank() && name != "null") {
                    if (parsedCast.none { it.actor.name.equals(name, ignoreCase = true) }) {
                        val profileUrl = tmdbImageUrl(profilePath, "original")
                        val crewRole = personCrewRoles[name.lowercase()]
                        val combinedRole = when {
                            !character.isNullOrBlank() && !crewRole.isNullOrBlank() -> "$character • $crewRole"
                            !character.isNullOrBlank() -> character
                            !crewRole.isNullOrBlank() -> crewRole
                            else -> null
                        }
                        parsedCast.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = combinedRole, role = null))
                    }
                }
            }
        }

        return (parsedCast + parsedCrew).distinctBy { it.actor.name + (it.roleString ?: "") }
    }

    suspend fun fetchSeasonCredits(
        tmdbId: Int,
        seasonNumber: Int,
    ): List<com.lagradost.cloudstream3.ActorData> = withContext(Dispatchers.IO) {
        try {
            TmdbRateLimiter.acquire()
            val tmdbLang = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.tmdbLanguage.value.ifBlank { "en-US" }
            val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber/credits?api_key=$TMDB_API_KEY&language=$tmdbLang"
            val resp = com.lagradost.cloudstream3.app.get(url).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
            if (resp != null) {
                parseCreditsJson(
                    castList = resp.get("cast"),
                    crewList = resp.get("crew"),
                    createdBy = null,
                    limit = 150,
                )
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("TmdbEnrichment", "Failed to fetch season $seasonNumber credits for series $tmdbId: ${e.message}")
            emptyList()
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

                val isAnime = loaded is com.lagradost.cloudstream3.AnimeLoadResponse ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                    loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                    loaded.type == com.lagradost.cloudstream3.TvType.OVA ||
                    loaded.tags?.any { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) } == true

                val isTv = loaded.type == com.lagradost.cloudstream3.TvType.TvSeries ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                    loaded.type == com.lagradost.cloudstream3.TvType.AsianDrama ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Cartoon

                val resolvedMatch = TmdbMatchResolver.resolve(
                    loaded = loaded,
                    cleanName = cleanName,
                    isAnime = isAnime,
                    isTv = isTv,
                    tempYear = tempYear,
                    directTmdbId = directTmdbId,
                    directImdbId = directImdbId,
                    apiKey = TMDB_API_KEY,
                )

                var tmdbIsAnime = false
                if (resolvedMatch != null) {
                    val resolvedMatchId = resolvedMatch.id
                    val resolvedIsMovie = resolvedMatch.isMovie
                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: fetching details for ${if (resolvedIsMovie) "movie" else "tv"}/$resolvedMatchId")
                    val isMovie = resolvedIsMovie
                    val matchId = resolvedMatchId
                    val typeStr = if (isMovie) "movie" else "tv"
                    loaded.syncData["tmdb"] = matchId.toString()

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
                                    loaded.plot = com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanHtml(overview)
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
                                        if (!fallbackPlot.isNullOrBlank()) loaded.plot = com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanHtml(fallbackPlot)
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
                                val limit = if (tempActors.isNullOrEmpty()) 30 else 150
                                val actors = parseCreditsJson(
                                    castList = castList,
                                    crewList = crewList,
                                    createdBy = tmdbData.get("created_by"),
                                    limit = limit,
                                )

                                if (actors.isNotEmpty()) {
                                    if (tempActors.isNullOrEmpty()) {
                                        tempActors = actors
                                    } else {
                                        // TMDB cast has authoritative billing order (order 0 is lead actor).
                                        // Start with TMDB cast so lead actors are always front and center,
                                        // then append any unique non-duplicate provider-scraped actors.
                                        val merged = actors.toMutableList()
                                        tempActors.orEmpty().forEach { providerActor ->
                                            if (merged.none { it.actor.name.equals(providerActor.actor.name, ignoreCase = true) }) {
                                                merged.add(providerActor)
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

                            // Enrich episode thumbnails, descriptions, and synthesize future episodes
                            TmdbEpisodeEnricher.enrich(
                                loaded = loaded,
                                tmdbData = tmdbData,
                                isMovie = isMovie,
                                overwrite = overwrite,
                                onEpisodeThumbnailsEnriched = onEpisodeThumbnailsEnriched,
                            )

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

    private suspend fun fetchAniListCast(title: String, year: Int?): List<com.lagradost.cloudstream3.ActorData>? =
        AniListCastFetcher.fetch(title, year)
}

