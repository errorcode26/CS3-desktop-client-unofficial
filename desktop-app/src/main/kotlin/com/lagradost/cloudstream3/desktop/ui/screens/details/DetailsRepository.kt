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

/**
 * Rate-limiter for TMDB API requests to stay under the 40 req/sec free-tier limit.
 * Moved out of DetailsViewModel to keep the ViewModel focused on UI state only.
 */
object TmdbRateLimiter {
    // Cancellation-safe: no Mutex, so a CancellationException mid-acquire
    // can never permanently lock the limiter for the rest of the session.
    @Volatile private var lastRequestTime = 0L
    private val minInterval = 1000L / 35L // ~28ms between calls = 35 req/sec

    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val wait = minInterval - (now - lastRequestTime)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestTime = System.currentTimeMillis()
    }
}

/**
 * Repository responsible for fetching and caching plugin load responses and TMDB enrichment.
 * Extracted from DetailsViewModel to give the ViewModel a single responsibility: owning UI state.
 */
object GlobalDetailsCache {
    private const val TMDB_API_KEY = "3828864585df9d4f006c09403eb9a888"

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
        val knownFor: List<com.lagradost.cloudstream3.SearchResponse>
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

                        if (mediaType == "movie") {
                            knownFor.add(
                                dummyApi.newMovieSearchResponse(title, url = "", com.lagradost.cloudstream3.TvType.Movie, false) {
                                    this.posterUrl = posterUrl
                                }
                            )
                        } else if (mediaType == "tv") {
                            knownFor.add(
                                dummyApi.newTvSeriesSearchResponse(title, url = "", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                                    this.posterUrl = posterUrl
                                }
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
                    knownFor = knownFor
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // Size-limited LRU Cache for the last 50 visited pages to prevent OutOfMemory errors
    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )

    suspend fun fetchRaw(provider: MainAPI, url: String): LoadResponse? {
        cache[url]?.let { return it }

        // 3-attempt exponential backoff — never cache a null/failed result
        repeat(3) { attempt ->
            try {
                val loaded = withContext(Dispatchers.IO) { provider.load(url) }
                if (loaded != null) {
                    cache[url] = loaded
                    return loaded
                }
            } catch (e: CancellationException) {
                throw e // Always re-throw cancellation immediately
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("[DetailsRepo] fetchRaw attempt ${attempt + 1}/3 failed for $url", e)
                if (attempt < 2) delay(500L * (attempt + 1)) // 0.5s then 1s backoff
            }
        }
        return null
    }

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onEnrichmentComplete: () -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            try {
                val cleanName = loaded.name
                    .replace(Regex("""\s*\(\d{4}\).*"""), "")
                    .replace(Regex("""\s*(?i)(dual audio|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                    .replace(Regex("""\s*[\[\{].*"""), "")
                    .replace(Regex("""\s*(?i)\(?\b(season|episodes|episode|s\d+e\d+|vol|volume|added)\b.*"""), "")
                    .trim()

                TmdbRateLimiter.acquire()
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")
                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=${java.net.URLEncoder.encode(cleanName, "UTF-8")}&page=1&language=en-US"
                val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                val results = searchData?.get("results")

                var matchNode: com.fasterxml.jackson.databind.JsonNode? = null
                if (results != null && results.isArray) {
                    val possibleMatches = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
                    for (result in results) {
                        val mediaType = result.get("media_type")?.asText()
                        if (mediaType == "person") continue

                        val resultName = result.get("name")?.asText() ?: result.get("title")?.asText() ?: result.get("original_name")?.asText() ?: ""
                        val strippedResultName = resultName.replace(Regex("[^a-zA-Z0-9]"), "")

                        val releaseDate = result.get("release_date")?.asText() ?: result.get("first_air_date")?.asText()
                        val resultYear = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

                        if (strippedResultName.equals(strippedCleanName, ignoreCase = true) && strippedCleanName.isNotEmpty()) {
                            if (mediaType == "movie" && resultYear != null && loaded.year != null && resultYear != loaded.year) {
                                continue // Year conflicts for Movie
                            } else {
                                possibleMatches.add(result)
                            }
                        }
                    }
                    
                    if (possibleMatches.isNotEmpty()) {
                        if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                            matchNode = possibleMatches.find { res ->
                                val genreArray = res.get("genre_ids")
                                val isAnimation = genreArray?.isArray == true && genreArray.any { it.asInt() == 16 }
                                val originArray = res.get("origin_country")
                                val isJP = originArray?.isArray == true && originArray.any { it.asText() == "JP" }
                                isAnimation || isJP
                            } ?: possibleMatches.first()
                        } else {
                            matchNode = possibleMatches.first()
                        }
                    }
                }

                var tmdbIsAnime = false
                if (matchNode != null) {
                    val isMovie = matchNode.get("media_type")?.asText() == "movie"
                    val matchId = matchNode.get("id")?.asInt()
                    val typeStr = if (isMovie) "movie" else "tv"

                    try {
                        TmdbRateLimiter.acquire()

                        // Optimize TV show fetching by appending seasons 1-15 to the single request!
                        // This prevents making N requests for N seasons, keeping us well under rate limits.
                        val seasonsAppend = if (!isMovie) ",${(1..15).joinToString(",") { "season/$it" }}" else ""
                        val tmdbUrl = "https://api.themoviedb.org/3/$typeStr/$matchId?api_key=$TMDB_API_KEY&append_to_response=images,credits,recommendations$seasonsAppend&language=en-US"

                        val tmdbData = com.lagradost.cloudstream3.app.get(tmdbUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        if (tmdbData != null) {
                            val tmdbTitle = tmdbData.get("name")?.asText() ?: tmdbData.get("title")?.asText()
                            if (!tmdbTitle.isNullOrBlank() && tmdbTitle != "null") {
                                // loaded.name = tmdbTitle (Keep original provider name for display and split layout)
                            }

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

                            val originalLanguage = tmdbData.get("original_language")?.asText()
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
                            val hasPluginVoiceActors = loaded.actors?.any { it.voiceActor != null } == true
                            if (castList != null && castList.isArray && !hasPluginVoiceActors && (loaded.actors.isNullOrEmpty() || loaded.actors!!.all { it.actor.image.isNullOrBlank() })) {
                                val actors = mutableListOf<com.lagradost.cloudstream3.ActorData>()
                                castList.take(20).forEach { cast ->
                                    val name = cast.get("name")?.asText()
                                    val profilePath = cast.get("profile_path")?.asText()
                                    val character = cast.get("character")?.asText()
                                    if (!name.isNullOrBlank() && name != "null") {
                                        val profileUrl = if (profilePath != null && profilePath != "null") "https://image.tmdb.org/t/p/w500$profilePath" else null
                                        actors.add(com.lagradost.cloudstream3.ActorData(com.lagradost.cloudstream3.Actor(name, profileUrl), roleString = character))
                                    }
                                }
                                if (actors.isNotEmpty()) loaded.actors = actors
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
                                } else emptyList()

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
                                val enLogo = logosNode.find { it.get("iso_639_1")?.asText() == "en" }
                                val logoToUse = enLogo ?: logosNode.get(0)
                                val logoPath = logoToUse?.get("file_path")?.asText()
                                if (logoPath != null && logoPath != "null") {
                                    val logoUrl = "https://image.tmdb.org/t/p/w500$logoPath"
                                    if (loaded is com.lagradost.cloudstream3.MovieLoadResponse) loaded.logoUrl = logoUrl
                                    else if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) loaded.logoUrl = logoUrl
                                    else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) loaded.logoUrl = logoUrl
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
                val isAnime = tmdbIsAnime
                    || loaded is com.lagradost.cloudstream3.AnimeLoadResponse
                    || loaded.type == com.lagradost.cloudstream3.TvType.Anime
                    || loaded.type == com.lagradost.cloudstream3.TvType.OVA
                    || loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie
                    || loaded.tags?.any { it.equals("animation", ignoreCase = true) || it.equals("anime", ignoreCase = true) } == true
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
        // Persist enriched data back to cache and notify UI — fires ONCE, fully complete
        cache[url] = loaded
        onEnrichmentComplete()
    }
    /**
     * Fetches anime character art + voice actor data from the public AniList GraphQL API.
     * No authentication required — uses the free public endpoint https://graphql.anilist.co
     *
     * Returns a list of [ActorData] where:
     *   - actor = Anime character (character name + character art image)
     *   - voiceActor = Human voice actor (VA name + real photo, Japanese VA preferred)
     *   - role = Main / Supporting / Background
     */
    private suspend fun fetchAniListCast(title: String, year: Int?): List<com.lagradost.cloudstream3.ActorData>? {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Search for the show by title to get its AniList media ID
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
                    "variables" to mapOf("search" to title)
                )

                val searchResult = com.lagradost.cloudstream3.app.post(
                    "https://graphql.anilist.co",
                    json = searchPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json")
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
                        titles?.get("userPreferred")?.asText()
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
                    "variables" to mapOf("id" to mediaId)
                )

                val castResult = com.lagradost.cloudstream3.app.post(
                    "https://graphql.anilist.co",
                    json = castPayload,
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json")
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
                    } else null

                    actors.add(
                        com.lagradost.cloudstream3.ActorData(
                            actor = com.lagradost.cloudstream3.Actor(charName, charImage),
                            role = roleStr,
                            voiceActor = voiceActor
                        )
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
