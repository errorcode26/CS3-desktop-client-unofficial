package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

object TmdbRateLimiter {
    @Volatile private var lastRequestTime = 0L

    // Rate limit set to 35 to play safe. TMDB docs say 40 but we don't trust them.
    private val minInterval = 1000L / 35L

    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val wait = minInterval - (now - lastRequestTime)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestTime = System.currentTimeMillis()
    }
}

object GlobalDetailsCache {
    private val TMDB_API_KEY: String
        get() = com.lagradost.common.storage.DesktopDataStore.getKey<String>("tmdb_api_key")?.takeIf { it.isNotBlank() } ?: "3828864585df9d4f006c09403eb9a888"

    private val dummyApi = object : com.lagradost.cloudstream3.MainAPI() {
        override var name = "TMDB"
        override var mainUrl = "https://www.themoviedb.org"
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
                val profileUrl = if (profilePath != null) "https://image.tmdb.org/t/p/w500$profilePath" else null

                val castList = detailsData.get("combined_credits")?.get("cast")
                val knownFor = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                if (castList != null && castList.isArray) {
                    val sortedCast = castList.toList().sortedByDescending { it.get("popularity")?.asDouble() ?: 0.0 }
                    sortedCast.take(15).forEach { credit ->
                        val mediaType = credit.get("media_type")?.asText()
                        val title = credit.get("title")?.asText() ?: credit.get("name")?.asText() ?: return@forEach
                        val posterPath = credit.get("poster_path")?.asText()
                        val posterUrl = if (posterPath != null && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else null

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

    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )

    suspend fun fetchRaw(provider: MainAPI, url: String, fallbackName: String? = null): LoadResponse? {
        cache[url]?.let { return it }

        var targetProvider = provider
        var targetUrl = url

        if (targetUrl.contains("themoviedb.org") && !fallbackName.isNullOrBlank()) {
            try {
                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] TMDB link detected ($targetUrl). Searching active provider (${provider.name}) for: '$fallbackName'...")
                val searchResults = withContext(Dispatchers.IO) { provider.search(fallbackName, 1)?.items }
                val bestMatch = searchResults?.find { it.name.equals(fallbackName, ignoreCase = true) } ?: searchResults?.firstOrNull()
                if (bestMatch != null && bestMatch.url.isNotBlank() && !bestMatch.url.contains("themoviedb.org")) {
                    com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found exact match on provider (${provider.name}): ${bestMatch.name} -> ${bestMatch.url}")
                    targetUrl = bestMatch.url
                } else {
                    val allApis = com.lagradost.cloudstream3.APIHolder.allProviders
                    for (api in allApis) {
                        if (api.name == provider.name || api.name == "TMDB") continue
                        try {
                            val altResults = withContext(Dispatchers.IO) { api.search(fallbackName, 1)?.items }
                            val altMatch = altResults?.find { it.name.equals(fallbackName, ignoreCase = true) } ?: altResults?.firstOrNull()
                            if (altMatch != null && altMatch.url.isNotBlank() && !altMatch.url.contains("themoviedb.org")) {
                                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found exact match on alternate provider (${api.name}): ${altMatch.name} -> ${altMatch.url}")
                                targetProvider = api
                                targetUrl = altMatch.url
                                break
                            }
                        } catch (e: Exception) {
                            // continue searching next provider
                        }
                    }
                }
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.w("[DetailsRepo] Provider search bridge failed for '$fallbackName': ${e.message}")
            }
        }

        repeat(3) { attempt ->
            try {
                val loaded = withContext(Dispatchers.IO) { targetProvider.load(targetUrl) }
                if (loaded != null) {
                    loaded.posterUrl = targetProvider.fixUrlNull(loaded.posterUrl)
                    loaded.backgroundPosterUrl = targetProvider.fixUrlNull(loaded.backgroundPosterUrl)
                    loaded.logoUrl = targetProvider.fixUrlNull(loaded.logoUrl)
                    if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                        loaded.episodes.forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                        loaded.episodes.values.flatten().forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                    }
                    if (loaded.url.isBlank()) loaded.url = targetUrl
                    cache[url] = loaded
                    if (targetUrl != url) cache[targetUrl] = loaded
                    return loaded
                }
            } catch (e: CancellationException) {
                throw e // Always re-throw cancellation immediately
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("[DetailsRepo] fetchRaw attempt ${attempt + 1}/3 failed for $targetUrl", e)
                if (attempt < 2) delay(500L * (attempt + 1)) // 0.5s then 1s backoff
            }
        }
        return null
    }

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onMetadataLoaded: (
            tagline: String?,
            status: String?,
            studios: List<String>,
            collectionName: String?,
            collectionBg: String?,
            seasonsCount: Int?,
            episodesCount: Int?,
            originalLang: String?,
            releaseDate: String?,
            country: String?,
            collectionItems: List<com.lagradost.cloudstream3.SearchResponse>,
            budget: Long?,
            revenue: Long?,
            networks: List<String>?
        ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onEnrichmentComplete: () -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Many providers fail to set loaded.year, but include the year in parentheses in the title (e.g., "Movie Name (2021)").
                // If loaded.year is null, attempt to extract it here before we strip the title.
                if (loaded.year == null) {
                    val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(loaded.name)
                    if (yearMatch != null) {
                        loaded.year = yearMatch.groupValues[1].toInt()
                    }
                }

                val cleanName = loaded.name
                    // Keep stripping year at the end, as it helps with matching base titles
                    .replace(Regex("""\s*\(\d{4}\).*"""), "")
                    // Remove standard quality tags and anything after them (including leading parentheses)
                    .replace(Regex("""\s*[\(\[\{]?(?i)(dual audio|multi audio|hindi dubbed|full movie|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                    // Remove brackets and anything after
                    .replace(Regex("""\s*[\[\{].*"""), "")
                    // Remove pipe and anything after (common delimiter for junk on provider sites)
                    .replace(Regex("""\s*\|.*"""), "")
                    // Safely remove Season/Episode ONLY when followed by digits (e.g. Season 1, S01E01).
                    // This prevents breaking titles like "Season of the Witch" or "Star Wars: Episode IV".
                    // Removed 'vol', 'volume', and 'added' which broke legitimate movie titles like "Guardians of the Galaxy Vol. 2".
                    .replace(Regex("""(?i)(-\s*)?\b(season|episodes|episode|s\d+e\d+)\b\s*\d+.*"""), "")
                    .trim()

                TmdbRateLimiter.acquire()
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

                val findMatch = { results: com.fasterxml.jackson.databind.JsonNode? ->
                    val possible = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
                    if (results != null && results.isArray) {
                        for (result in results) {
                            val mediaType = result.get("media_type")?.asText()
                            if (mediaType == "person") continue
                            val resultName = result.get("name")?.asText() ?: result.get("title")?.asText() ?: result.get("original_name")?.asText() ?: ""
                            val strippedResultName = resultName.replace(Regex("[^a-zA-Z0-9]"), "")
                            val releaseDate = result.get("release_date")?.asText() ?: result.get("first_air_date")?.asText()
                            val resultYear = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
                            if (strippedResultName.equals(strippedCleanName, ignoreCase = true) && strippedCleanName.isNotEmpty()) {
                                // Reject if the provider says it's a Movie but TMDB says TV show (and vice versa)
                                if (loaded.type == com.lagradost.cloudstream3.TvType.Movie && mediaType == "tv") continue
                                if (loaded.type == com.lagradost.cloudstream3.TvType.TvSeries && mediaType == "movie") continue
                                
                                // Strictly reject if years don't match (allowing a 1-year tolerance for release date weirdness)
                                if (resultYear != null && loaded.year != null && Math.abs(resultYear - loaded.year!!) > 1) continue
                                
                                possible.add(result)
                            }
                        }
                    }
                    if (possible.isEmpty()) {
                        null
                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                        possible.find { res ->
                            val genreArray = res.get("genre_ids")
                            val isAnimation = genreArray?.isArray == true && genreArray.any { it.asInt() == 16 }
                            val originArray = res.get("origin_country")
                            val isJP = originArray?.isArray == true && originArray.any { it.asText() == "JP" }
                            isAnimation || isJP
                        } ?: possible.first()
                    } else {
                        possible.first()
                    }
                }

                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(cleanName, "UTF-8")}&page=1&language=en-US"
                val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                var matchNode = findMatch(searchData?.get("results"))

                // Pass 2: no language filter — catches non-English titles (Korean, Japanese, Thai, etc.)
                if (matchNode == null) {
                    TmdbRateLimiter.acquire()
                    val fallbackUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(cleanName, "UTF-8")}&page=1"
                    val fallbackData = com.lagradost.cloudstream3.app.get(fallbackUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                    matchNode = findMatch(fallbackData?.get("results"))
                }

                var tmdbIsAnime = false
                if (matchNode != null) {
                    val isMovie = matchNode.get("media_type")?.asText() == "movie"
                    val matchId = matchNode.get("id")?.asInt()
                    val typeStr = if (isMovie) "movie" else "tv"

                    try {
                        TmdbRateLimiter.acquire()

                        val seasonsAppend = if (!isMovie) ",${(1..15).joinToString(",") { "season/$it" }}" else ""
                        val originalLang = matchNode.get("original_language")?.asText()?.takeIf { it.isNotBlank() && it != "en" }
                        val imageLangParam = if (originalLang != null) "en,en-US,$originalLang,null" else "en,en-US,null"
                        val tmdbUrl = "https://api.themoviedb.org/3/$typeStr/$matchId?api_key=$TMDB_API_KEY&append_to_response=images,credits,recommendations,translations$seasonsAppend&language=en-US&include_image_language=$imageLangParam"

                        val tmdbData = com.lagradost.cloudstream3.app.get(tmdbUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        if (tmdbData != null) {
                            val tmdbTitle = tmdbData.get("name")?.asText() ?: tmdbData.get("title")?.asText()
                            if (!tmdbTitle.isNullOrBlank() && tmdbTitle != "null") {
                                loaded.name = tmdbTitle
                            }

                            val tagline = tmdbData.get("tagline")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val status = tmdbData.get("status")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val studios = mutableListOf<String>()
                            val prodList = tmdbData.get("production_companies")
                            if (prodList != null && prodList.isArray) {
                                prodList.take(2).forEach { s ->
                                    val sName = s.get("name")?.asText()
                                    if (!sName.isNullOrBlank() && sName != "null") studios.add(sName)
                                }
                            }
                            if (studios.isEmpty() && !isMovie) {
                                val netList = tmdbData.get("networks")
                                if (netList != null && netList.isArray) {
                                    netList.take(2).forEach { n ->
                                        val nName = n.get("name")?.asText()
                                        if (!nName.isNullOrBlank() && nName != "null") studios.add(nName)
                                    }
                                }
                            }
                            val collectionNode = tmdbData.get("belongs_to_collection")
                            val collName = collectionNode?.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                            val collBgPath = collectionNode?.get("backdrop_path")?.asText() ?: collectionNode?.get("poster_path")?.asText()
                            val collBgUrl = if (collBgPath != null && collBgPath != "null") "https://image.tmdb.org/t/p/w1280$collBgPath" else null

                            val seasonsCount = tmdbData.get("number_of_seasons")?.asInt()?.takeIf { it > 0 }
                            val episodesCount = tmdbData.get("number_of_episodes")?.asInt()?.takeIf { it > 0 }
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
                                if (firstYr != null && lastYr != null && firstYr != lastYr) "$firstYr – $lastYr"
                                else firstYr ?: rawRelDate
                            }

                            val countryList = tmdbData.get("origin_country")
                            val countryStr = if (countryList != null && countryList.isArray && countryList.size() > 0) {
                                countryList.mapNotNull { it.asText()?.takeIf { c -> c.isNotBlank() && c != "null" } }.take(2).joinToString(", ")
                            } else null

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
                                            val pPosterUrl = if (pPosterPath != null && pPosterPath != "null") "https://image.tmdb.org/t/p/w500$pPosterPath" else null
                                            val pUrl = "https://www.themoviedb.org/movie/$pId"
                                            collItems.add(
                                                dummyApi.newMovieSearchResponse(pTitle, url = pUrl, com.lagradost.cloudstream3.TvType.Movie, false) {
                                                    this.posterUrl = pPosterUrl
                                                    if (pId != null) this.id = pId
                                                }
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
                            val tmdbNetworks = tmdbData.get("networks")
                            if (tmdbNetworks != null && tmdbNetworks.isArray) {
                                tmdbNetworks.forEach { net ->
                                    val netName = net.get("name")?.asText()
                                    if (!netName.isNullOrBlank() && netName != "null") {
                                        networksList.add(netName)
                                    }
                                }
                            }

                            onMetadataLoaded(
                                tagline,
                                status,
                                studios,
                                collName,
                                collBgUrl,
                                seasonsCount,
                                episodesCount,
                                formattedLang,
                                releaseDateStr,
                                countryStr,
                                collItems,
                                budget,
                                revenue,
                                networksList
                            )

                            val originalLanguage = tmdbData.get("original_language")?.asText()

                            val bgPath = tmdbData.get("backdrop_path")?.asText()
                            val posterPath = tmdbData.get("poster_path")?.asText()

                            if (bgPath != null && bgPath != "null") {
                                loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$bgPath"
                            } else if (posterPath != null && posterPath != "null" && loaded.backgroundPosterUrl.isNullOrBlank()) {
                                loaded.backgroundPosterUrl = "https://image.tmdb.org/t/p/original$posterPath"
                            }

                            if (loaded.posterUrl.isNullOrBlank() && posterPath != null && posterPath != "null") {
                                loaded.posterUrl = "https://image.tmdb.org/t/p/w500$posterPath"
                            }

                            val overview = tmdbData.get("overview")?.asText()
                            if (!overview.isNullOrBlank() && overview != "null" && loaded.plot.isNullOrBlank()) {
                                loaded.plot = overview
                            }
                            if (loaded.plot.isNullOrBlank()) {
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
                            if (voteAverage != null && loaded.score == null) {
                                loaded.score = com.lagradost.cloudstream3.Score.from10(voteAverage)
                            }

                            val runtime = tmdbData.get("runtime")?.asInt()
                            if (runtime != null && runtime > 0 && (loaded.duration == null || loaded.duration == 0)) {
                                loaded.duration = runtime
                            } else {
                                val episodeRunTime = tmdbData.get("episode_run_time")?.get(0)?.asInt()
                                if (episodeRunTime != null && episodeRunTime > 0 && (loaded.duration == null || loaded.duration == 0)) {
                                    loaded.duration = episodeRunTime
                                }
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
                                    if (loaded.tags.isNullOrEmpty()) {
                                        loaded.tags = tmdbTags
                                    } else {
                                        loaded.tags = (loaded.tags!! + tmdbTags).distinct()
                                    }
                                }
                            }

                            val castList = tmdbData.get("credits")?.get("cast")
                            val crewList = tmdbData.get("credits")?.get("crew")
                            val hasPluginVoiceActors = loaded.actors?.any { it.voiceActor != null } == true
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
                                                val profileUrl = if (profilePath != null && profilePath != "null") "https://image.tmdb.org/t/p/w500$profilePath" else null
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
                                            val profileUrl = if (profilePath != null && profilePath != "null") "https://image.tmdb.org/t/p/w500$profilePath" else null
                                            if (actors.none { it.actor.name.equals(name, ignoreCase = true) }) {
                                                actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = "Creator"))
                                            }
                                        }
                                    }
                                }

                                // Then, add regular cast (up to 150 to match all provider actors)
                                if (castList != null && castList.isArray) {
                                    val limit = if (loaded.actors.isNullOrEmpty()) 30 else 150
                                    castList.take(limit).forEach { cast ->
                                        val name = cast.get("name")?.asText()
                                        val profilePath = cast.get("profile_path")?.asText()
                                        val character = cast.get("character")?.asText()
                                        if (!name.isNullOrBlank() && name != "null") {
                                            if (actors.none { it.actor.name.equals(name, ignoreCase = true) }) {
                                                val profileUrl = if (profilePath != null && profilePath != "null") "https://image.tmdb.org/t/p/w500$profilePath" else null
                                                actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = character))
                                            }
                                        }
                                    }
                                }

                                if (actors.isNotEmpty()) {
                                    if (loaded.actors.isNullOrEmpty()) {
                                        loaded.actors = actors
                                    } else {
                                        val merged = loaded.actors!!.toMutableList()
                                        actors.forEach { tmdbActor ->
                                            val existingIdx = merged.indexOfFirst { it.actor.name.equals(tmdbActor.actor.name, ignoreCase = true) }
                                            if (existingIdx == -1) {
                                                merged.add(tmdbActor)
                                            } else {
                                                val existing = merged[existingIdx]
                                                // Overwrite provider's metadata with accurate TMDB name, photo, and character/role
                                                merged[existingIdx] = existing.copy(
                                                    actor = tmdbActor.actor,
                                                    roleString = tmdbActor.roleString
                                                )
                                            }
                                        }
                                        loaded.actors = merged
                                    }
                                }
                            }

                            val recList = tmdbData.get("recommendations")?.get("results")
                            if (recList != null && recList.isArray && loaded.recommendations.isNullOrEmpty()) {
                                val recs = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                                recList.forEach { rec ->
                                    val recId = rec.get("id")?.asInt()
                                    val title = rec.get("title")?.asText() ?: rec.get("name")?.asText()
                                    val pPath = rec.get("poster_path")?.asText()
                                    val mType = rec.get("media_type")?.asText() ?: typeStr
                                    if (recId != null && !title.isNullOrBlank() && title != "null") {
                                        val pUrl = if (pPath != null && pPath != "null") "https://image.tmdb.org/t/p/w500$pPath" else null
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
                                if (recs.isNotEmpty()) loaded.recommendations = recs
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

                                allEpisodes.forEach { ep ->
                                    val seasonToUse = ep.season ?: 1
                                    val seasonNode = tmdbData.get("season/$seasonToUse")
                                    if (seasonNode != null && seasonNode.isObject) {
                                        val episodesNode = seasonNode.get("episodes")
                                        if (episodesNode != null && episodesNode.isArray) {
                                            val epNode = episodesNode.find { it.get("episode_number")?.asInt() == ep.episode }
                                            if (epNode != null) {
                                                val epPosterPath = epNode.get("still_path")?.asText()
                                                if (epPosterPath != null && epPosterPath != "null") {
                                                    ep.posterUrl = "https://image.tmdb.org/t/p/w780$epPosterPath"
                                                }
                                                val epOverview = epNode.get("overview")?.asText()
                                                if (ep.description.isNullOrBlank() && !epOverview.isNullOrBlank() && epOverview != "null") {
                                                    ep.description = epOverview
                                                }
                                                val epName = epNode.get("name")?.asText()
                                                if (!epName.isNullOrBlank() && epName != "null") {
                                                    ep.name = epName
                                                }
                                            }
                                        }
                                    }
                                }
                            }

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
                                    val logoUrl = "https://image.tmdb.org/t/p/$sizeParam$bestLogoPath"
                                    if (loaded is com.lagradost.cloudstream3.MovieLoadResponse) {
                                        loaded.logoUrl = logoUrl
                                    } else if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                        loaded.logoUrl = logoUrl
                                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                                        loaded.logoUrl = logoUrl
                                    }
                                }
                            }

                            val backdropsNode = tmdbData.get("images")?.get("backdrops")
                            if (backdropsNode != null && backdropsNode.isArray) {
                                val images = mutableListOf<String>()
                                backdropsNode.take(15).forEach { img ->
                                    val path = img.get("file_path")?.asText()
                                    if (path != null && path != "null") {
                                        images.add("https://image.tmdb.org/t/p/w1280$path")
                                    }
                                }
                                if (images.isNotEmpty()) {
                                    onScreenshotsLoaded(images)
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("Error fetching optimized TMDB data", e)
                    }
                }

                // ── AniList: Fetch anime character art + voice actors ──────────────────
                val isAnime = tmdbIsAnime ||
                    loaded is com.lagradost.cloudstream3.AnimeLoadResponse ||
                    loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                    loaded.type == com.lagradost.cloudstream3.TvType.OVA ||
                    loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                    loaded.tags?.any { it.equals("animation", ignoreCase = true) || it.equals("anime", ignoreCase = true) } == true
                if (isAnime) {
                    try {
                        val aniListCast = fetchAniListCast(cleanName, loaded.year)
                        if (!aniListCast.isNullOrEmpty()) {
                            loaded.actors = aniListCast
                            com.lagradost.common.logging.AppLogger.i("[AniList] Enriched cast with ${aniListCast.size} character+VA entries for '${loaded.name}'")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("[AniList] Failed to fetch cast for '${loaded.name}'", e)
                    }
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation (composable disposed)
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error enriching TMDB data", t)
            }
        }
        cache[url] = loaded
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
