package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

object TmdbRateLimiter {
    private val mutex = Mutex()
    private var lastRequestTime = 0L
    private val minInterval = 1000L / 35L

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < minInterval) {
                kotlinx.coroutines.delay(minInterval - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }
}

object GlobalDetailsCache {
    // Size-limited LRU Cache for the last 50 visited pages to prevent OutOfMemory errors
    val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )

    suspend fun fetchRaw(provider: MainAPI, url: String): LoadResponse? {
        val existing = cache[url]
        if (existing != null) return existing

        val loaded = withContext(Dispatchers.IO) {
            try {
                provider.load(url)
            } catch (e: Exception) {
                null
            }
        }

        if (loaded != null) {
            cache[url] = loaded
        }
        return loaded
    }

    suspend fun enrich(loaded: LoadResponse, url: String, onScreenshotsLoaded: (List<String>) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cleanName = loaded.name
                    .replace(Regex("""\s*\(\d{4}\).*"""), "")
                    .replace(Regex("""\s*(?i)(dual audio|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                    .replace(Regex("""\s*[\[\{].*"""), "")
                    .trim()

                TmdbRateLimiter.acquire()
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")
                val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=e6333b32409e02a4a6eba6fb7ff866bb&query=${java.net.URLEncoder.encode(cleanName, "UTF-8")}&page=1&language=en-US"
                val searchData = com.lagradost.cloudstream3.app.get(searchUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                val results = searchData?.get("results")

                var matchNode: com.fasterxml.jackson.databind.JsonNode? = null
                if (results != null && results.isArray) {
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
                                matchNode = result
                                break
                            }
                        }
                    }
                }

                if (matchNode != null) {
                    val isMovie = matchNode.get("media_type")?.asText() == "movie"
                    val matchId = matchNode.get("id")?.asInt()
                    val typeStr = if (isMovie) "movie" else "tv"

                    try {
                        TmdbRateLimiter.acquire()

                        // Optimize TV show fetching by appending seasons 1-15 to the single request!
                        // This prevents making N requests for N seasons, keeping us well under rate limits.
                        val seasonsAppend = if (!isMovie) ",${(1..15).joinToString(",") { "season/$it" }}" else ""
                        val tmdbUrl = "https://api.themoviedb.org/3/$typeStr/$matchId?api_key=e6333b32409e02a4a6eba6fb7ff866bb&append_to_response=images,credits,recommendations$seasonsAppend&language=en-US"

                        val tmdbData = com.lagradost.cloudstream3.app.get(tmdbUrl).parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                        if (tmdbData != null) {
                            val tmdbTitle = tmdbData.get("name")?.asText() ?: tmdbData.get("title")?.asText()
                            if (!tmdbTitle.isNullOrBlank() && tmdbTitle != "null") {
                                loaded.name = tmdbTitle
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

                            val genres = tmdbData.get("genres")
                            if (genres != null && genres.isArray && loaded.tags.isNullOrEmpty()) {
                                val tags = mutableListOf<String>()
                                genres.forEach { tag ->
                                    val name = tag.get("name")?.asText()
                                    if (!name.isNullOrBlank() && name != "null") tags.add(name)
                                }
                                if (tags.isNotEmpty()) loaded.tags = tags
                            }

                            val castList = tmdbData.get("credits")?.get("cast")
                            if (castList != null && castList.isArray && (loaded.actors.isNullOrEmpty() || loaded.actors!!.all { it.actor.image.isNullOrBlank() })) {
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
                            if (!isMovie && loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                                loaded.episodes.forEach { ep ->
                                    val seasonNode = tmdbData.get("season/${ep.season}")
                                    if (seasonNode != null && seasonNode.isObject) {
                                        val episodesNode = seasonNode.get("episodes")
                                        if (episodesNode != null && episodesNode.isArray) {
                                            val epNode = episodesNode.find { it.get("episode_number")?.asInt() == ep.episode }
                                            if (epNode != null) {
                                                val epPosterPath = epNode.get("still_path")?.asText()
                                                if (ep.posterUrl.isNullOrBlank() && epPosterPath != null && epPosterPath != "null") {
                                                    ep.posterUrl = "https://image.tmdb.org/t/p/w500$epPosterPath"
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
            } catch (t: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation (composable disposed)
            } catch (t: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error enriching TMDB data", t)
            }
        }
        cache[url] = loaded
    }
}

class DetailsViewModel(
    private val viewModelScope: CoroutineScope,
    private val provider: MainAPI,
    private val url: String,
    private val preloadedName: String? = null,
    private val preloadedPoster: String? = null,
    private val preloadedBg: String? = null,
) {

    private val _response = MutableStateFlow<LoadResponse?>(GlobalDetailsCache.cache[url])
    val response: StateFlow<LoadResponse?> = _response.asStateFlow()

    private val _enrichmentTrigger = MutableStateFlow(0)
    val enrichmentTrigger: StateFlow<Int> = _enrichmentTrigger.asStateFlow()

    private val _fakeData = MutableStateFlow<LoadResponse?>(null)
    val fakeData: StateFlow<LoadResponse?> = _fakeData.asStateFlow()

    private val _isLoading = MutableStateFlow(_response.value == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeLinkData = MutableStateFlow<Triple<MainAPI, String, WatchHistory>?>(null)
    val activeLinkData: StateFlow<Triple<MainAPI, String, WatchHistory>?> = _activeLinkData.asStateFlow()

    private val _isPanelOpen = MutableStateFlow(false)
    val isPanelOpen: StateFlow<Boolean> = _isPanelOpen.asStateFlow()

    private val _screenshots = MutableStateFlow<List<String>?>(null)
    val screenshots: StateFlow<List<String>?> = _screenshots.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        if (_response.value != null) return

        viewModelScope.launch {
            if (preloadedName != null) {
                _fakeData.value = provider.newMovieLoadResponse(
                    name = preloadedName,
                    url = url,
                    type = TvType.Movie,
                    dataUrl = url,
                ) {
                    this.posterUrl = preloadedPoster
                    this.backgroundPosterUrl = preloadedBg
                }
            }

            try {
                val rawData = GlobalDetailsCache.fetchRaw(provider, url)
                _response.value = rawData
                _isLoading.value = false

                if (rawData != null) {
                    if (!preloadedName.isNullOrBlank()) {
                        rawData.name = preloadedName
                    }
                    viewModelScope.launch {
                        GlobalDetailsCache.enrich(rawData, url) { images ->
                            _screenshots.value = images
                        }
                        // Trigger a recomposition since the rawData object is mutated in-place
                        _enrichmentTrigger.value += 1
                    }
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading details", e)
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun openLinksPanel(data: Triple<MainAPI, String, WatchHistory>) {
        _activeLinkData.value = data
        _isPanelOpen.value = true
    }

    fun closeLinksPanel() {
        _isPanelOpen.value = false
    }
}
