package com.lagradost.cloudstream3.desktop.stremio

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class StremioApi : MainAPI() {
    override var name = "Stremio"
    override var mainUrl = "stremio://"
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AnimeMovie)
    override var hasMainPage = false

    private val mapper = jacksonObjectMapper()

    override suspend fun load(url: String): LoadResponse? = withContext(Dispatchers.IO) {
        try {
            val parsedType: String?
            val cleanId: String

            when {
                url.startsWith("stremio://", ignoreCase = true) -> {
                    val path = url.removePrefix("stremio://").removePrefix("/")
                    parsedType = path.substringBefore("/").lowercase()
                    cleanId = path.substringAfter("/")
                }
                url.contains(":") && !url.startsWith("tt", ignoreCase = true) &&
                    !url.startsWith("kitsu:", ignoreCase = true) &&
                    !url.startsWith("tmdb:", ignoreCase = true) &&
                    !url.startsWith("mal:", ignoreCase = true) &&
                    !url.startsWith("anilist:", ignoreCase = true) -> {
                    val prefix = url.substringBefore(":").lowercase()
                    if (prefix == "movie" || prefix == "series" || prefix == "anime") {
                        parsedType = prefix
                        cleanId = url.substringAfter(":")
                    } else {
                        parsedType = null
                        cleanId = url
                    }
                }
                else -> {
                    parsedType = null
                    cleanId = url
                }
            }

            if (cleanId.isBlank()) {
                AppLogger.w(TAG, "Empty media ID for load: $url")
                return@withContext null
            }

            // Direct external ID delegation
            if (cleanId.startsWith("tmdb:", ignoreCase = true)) {
                val rawTmdb = cleanId.removePrefix("tmdb:").trim()
                val inferredType = when {
                    rawTmdb.startsWith("movie:", ignoreCase = true) -> "movie"
                    rawTmdb.startsWith("tv:", ignoreCase = true) || rawTmdb.startsWith("series:", ignoreCase = true) -> "series"
                    else -> parsedType
                }
                val sanitizedTmdbId = rawTmdb
                    .removePrefix("movie:")
                    .removePrefix("tv:")
                    .removePrefix("series:")
                    .trim()
                return@withContext loadFromTmdb(sanitizedTmdbId, inferredType, url)
            }
            if (cleanId.startsWith("mal:", ignoreCase = true)) {
                return@withContext loadFromMal(cleanId.removePrefix("mal:").trim(), parsedType, url)
            }
            if (cleanId.startsWith("anilist:", ignoreCase = true)) {
                return@withContext loadFromAniList(cleanId.removePrefix("anilist:").trim(), parsedType, url)
            }
            if (cleanId.all { it.isDigit() }) {
                val tmdbRes = loadFromTmdb(cleanId.trim(), parsedType, url)
                if (tmdbRes != null) return@withContext tmdbRes
            }

            val targetType = parsedType ?: "series"
            var meta = StremioAddonClient.getMeta(cleanId, targetType)
            var resolvedType = targetType

            if (meta == null && parsedType == null) {
                meta = StremioAddonClient.getMeta(cleanId, "movie")
                if (meta != null) {
                    resolvedType = "movie"
                }
            }

            if (meta == null) {
                AppLogger.w(TAG, "Failed to resolve metadata from addons for ID: $cleanId")
                return@withContext null
            }

            buildResponseFromMeta(meta, cleanId, url, resolvedType)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error in StremioApi.load for $url: ${e.message}", e)
            null
        }
    }

    private suspend fun loadFromTmdb(
        tmdbId: String,
        requestedType: String?,
        originalUrl: String,
    ): LoadResponse? = withContext(Dispatchers.IO) {
        try {
            val sanitizedId = tmdbId
                .removePrefix("movie:")
                .removePrefix("tv:")
                .removePrefix("series:")
                .trim()
            val apiKey = TmdbEnrichmentService.TMDB_API_KEY
            val isMovie = requestedType == "movie"
            val typeStr = if (isMovie) "movie" else "tv"

            var tmdbUrl = "https://api.themoviedb.org/3/$typeStr/$sanitizedId?api_key=$apiKey&append_to_response=external_ids"
            var res = app.get(tmdbUrl, timeout = 8000L).parsedSafe<JsonNode>()
            var effectiveIsMovie = isMovie

            if (res == null && requestedType == null) {
                val altType = if (isMovie) "tv" else "movie"
                tmdbUrl = "https://api.themoviedb.org/3/$altType/$sanitizedId?api_key=$apiKey&append_to_response=external_ids"
                res = app.get(tmdbUrl, timeout = 8000L).parsedSafe<JsonNode>()
                if (res != null) {
                    effectiveIsMovie = altType == "movie"
                }
            }

            if (res == null || (res.has("status_code") && !res.has("id"))) {
                AppLogger.w(TAG, "TMDB fetch returned error or null for ID: $sanitizedId")
                return@withContext null
            }

            val title = res.get("name")?.asText() ?: res.get("title")?.asText() ?: "TMDB $sanitizedId"
            val posterPath = res.get("poster_path")?.asText()
            val backdropPath = res.get("backdrop_path")?.asText()
            val poster = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }
            val backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
            val overview = res.get("overview")?.asText()
            val year = (res.get("first_air_date")?.asText() ?: res.get("release_date")?.asText())?.take(4)?.toIntOrNull()
            val score = res.get("vote_average")?.asDouble()
            val genres = res.get("genres")?.mapNotNull { it.get("name")?.asText() } ?: emptyList()

            val imdbId = res.get("external_ids")?.get("imdb_id")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                ?: res.get("imdb_id")?.asText()?.takeIf { it.isNotBlank() && it != "null" }

            if (imdbId != null) {
                val stremioType = if (effectiveIsMovie) "movie" else "series"
                val addonMeta = StremioAddonClient.getMeta(imdbId, stremioType)
                if (addonMeta != null) {
                    AppLogger.i(TAG, "Resolved TMDB $sanitizedId via IMDb $imdbId to Stremio metadata '${addonMeta.name}'")
                    return@withContext buildResponseFromMeta(
                        meta = addonMeta,
                        cleanId = imdbId,
                        url = originalUrl,
                        resolvedType = stremioType,
                        extraSyncData = mapOf("tmdb" to sanitizedId),
                    )
                }
            }

            AppLogger.i(TAG, "Building load response directly from TMDB data for ID $sanitizedId ('$title')")
            if (effectiveIsMovie) {
                newMovieLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.Movie,
                    dataUrl = imdbId ?: "tmdb:$sanitizedId",
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.plot = overview
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("tmdb" to sanitizedId).apply {
                        if (imdbId != null) put("imdb", imdbId)
                    }
                }
            } else {
                val seasonsNode = res.get("seasons")
                val episodes = mutableListOf<Episode>()
                val mediaId = imdbId ?: "tmdb:$sanitizedId"

                if (seasonsNode != null && seasonsNode.isArray) {
                    for (seasonObj in seasonsNode) {
                        val sNum = seasonObj.get("season_number")?.asInt() ?: continue
                        if (sNum <= 0) continue
                        val epCount = seasonObj.get("episode_count")?.asInt() ?: 0
                        for (eNum in 1..epCount) {
                            @Suppress("DEPRECATION_ERROR")
                            episodes.add(
                                Episode(
                                    data = "$mediaId:$sNum:$eNum",
                                    name = "Episode $eNum",
                                    season = sNum,
                                    episode = eNum,
                                )
                            )
                        }
                    }
                }

                if (episodes.isEmpty()) {
                    @Suppress("DEPRECATION_ERROR")
                    episodes.add(
                        Episode(
                            data = "$mediaId:1:1",
                            name = "Episode 1",
                            season = 1,
                            episode = 1,
                        )
                    )
                }

                newTvSeriesLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.TvSeries,
                    episodes = episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.plot = overview
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("tmdb" to sanitizedId).apply {
                        if (imdbId != null) put("imdb", imdbId)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed resolving TMDB ID $tmdbId: ${e.message}", e)
            null
        }
    }

    private suspend fun loadFromMal(
        malId: String,
        requestedType: String?,
        originalUrl: String,
    ): LoadResponse? = withContext(Dispatchers.IO) {
        try {
            val jikanUrl = "https://api.jikan.moe/v4/anime/$malId"
            val res = app.get(jikanUrl, cacheTime = 60 * 12, timeout = 10000L).parsedSafe<JsonNode>()
            val data = res?.get("data")
            if (data == null) {
                AppLogger.w(TAG, "Jikan returned null for MAL ID: $malId")
                return@withContext null
            }

            val titleEng = data.get("title_english")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val titleDefault = data.get("title")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val titleJap = data.get("title_japanese")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val title = titleEng ?: titleDefault ?: titleJap ?: "MAL $malId"

            val typeStr = data.get("type")?.asText()?.lowercase()
            val isMovie = typeStr == "movie" || requestedType == "movie"
            val episodesCount = data.get("episodes")?.asInt()?.takeIf { it > 0 } ?: 1
            val synopsis = data.get("synopsis")?.asText()
            val score = data.get("score")?.asDouble()
            val year = data.get("year")?.asInt()?.takeIf { it > 0 }
                ?: data.get("aired")?.get("from")?.asText()?.take(4)?.toIntOrNull()

            val images = data.get("images")
            val webp = images?.get("webp")
            val jpg = images?.get("jpg")
            val poster = (webp?.get("large_image_url")?.asText()
                ?: jpg?.get("large_image_url")?.asText()
                ?: webp?.get("image_url")?.asText()
                ?: jpg?.get("image_url")?.asText())?.takeIf { it.isNotBlank() && it != "null" }

            val genres = data.get("genres")?.mapNotNull { it.get("name")?.asText() } ?: emptyList()

            val targetStremioType = if (isMovie) "movie" else "series"
            var resolvedImdbId: String? = null

            try {
                val searchResults = StremioAddonClient.search(title, targetStremioType)
                val match = searchResults?.firstOrNull {
                    it.name?.equals(title, ignoreCase = true) == true ||
                    (titleEng != null && it.name?.equals(titleEng, ignoreCase = true) == true)
                } ?: searchResults?.firstOrNull()

                if (match?.id != null && match.id.startsWith("tt")) {
                    val addonMeta = StremioAddonClient.getMeta(match.id, targetStremioType)
                    if (addonMeta != null) {
                        AppLogger.i(TAG, "Resolved MAL $malId ('$title') via Cinemeta IMDb ${match.id}")
                        return@withContext buildResponseFromMeta(
                            meta = addonMeta,
                            cleanId = match.id,
                            url = originalUrl,
                            resolvedType = targetStremioType,
                            extraSyncData = mapOf("mal" to malId),
                        )
                    } else {
                        resolvedImdbId = match.id
                    }
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Cinemeta search failed for MAL '$title': ${e.message}")
            }

            AppLogger.i(TAG, "Building load response directly from Jikan MAL data for ID $malId ('$title')")
            val mediaId = resolvedImdbId ?: "mal:$malId"

            if (isMovie) {
                newMovieLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.AnimeMovie,
                    dataUrl = mediaId,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = synopsis
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("mal" to malId).apply {
                        if (resolvedImdbId != null) put("imdb", resolvedImdbId)
                    }
                }
            } else {
                val episodes = (1..episodesCount).map { epNum ->
                    @Suppress("DEPRECATION_ERROR")
                    Episode(
                        data = "$mediaId:1:$epNum",
                        name = "Episode $epNum",
                        season = 1,
                        episode = epNum,
                        posterUrl = poster,
                    )
                }

                newTvSeriesLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.Anime,
                    episodes = episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = synopsis
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("mal" to malId).apply {
                        if (resolvedImdbId != null) put("imdb", resolvedImdbId)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed resolving MAL ID $malId: ${e.message}", e)
            null
        }
    }

    private suspend fun loadFromAniList(
        anilistId: String,
        requestedType: String?,
        originalUrl: String,
    ): LoadResponse? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query (${'$'}id: Int) {
                    Media(id: ${'$'}id) {
                        id
                        idMal
                        title { english romaji native }
                        bannerImage
                        coverImage { extraLarge large }
                        description(asHtml: false)
                        averageScore
                        episodes
                        format
                        seasonYear
                        genres
                    }
                }
            """.trimIndent()

            val body = mapOf(
                "query" to query,
                "variables" to mapOf("id" to anilistId.toIntOrNull()),
            )
            val jsonBody = mapper.writeValueAsString(body)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val response = app.post(
                "https://graphql.anilist.co",
                requestBody = requestBody,
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                timeout = 10000L,
            ).parsedSafe<JsonNode>()

            val media = response?.get("data")?.get("Media")
            if (media == null) {
                AppLogger.w(TAG, "AniList returned null for ID: $anilistId")
                return@withContext null
            }

            val malId = media.get("idMal")?.asInt()?.takeIf { it > 0 }
            if (malId != null) {
                val malResp = loadFromMal(malId.toString(), requestedType, originalUrl)
                if (malResp != null) {
                    malResp.syncData["anilist"] = anilistId
                    return@withContext malResp
                }
            }

            val titleEng = media.get("title")?.get("english")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val titleRom = media.get("title")?.get("romaji")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val title = titleEng ?: titleRom ?: "AniList $anilistId"

            val format = media.get("format")?.asText()?.lowercase()
            val isMovie = format == "movie" || requestedType == "movie"
            val epCount = media.get("episodes")?.asInt()?.takeIf { it > 0 } ?: 1
            val poster = media.get("coverImage")?.get("extraLarge")?.asText()
                ?: media.get("coverImage")?.get("large")?.asText()
            val backdrop = media.get("bannerImage")?.asText()
            val desc = media.get("description")?.asText()
            val year = media.get("seasonYear")?.asInt()
            val score = media.get("averageScore")?.asDouble()?.let { it / 10.0 }
            val genres = media.get("genres")?.mapNotNull { it.asText() } ?: emptyList()

            val mediaId = "anilist:$anilistId"

            if (isMovie) {
                newMovieLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.AnimeMovie,
                    dataUrl = mediaId,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.plot = desc
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("anilist" to anilistId)
                }
            } else {
                val episodes = (1..epCount).map { epNum ->
                    @Suppress("DEPRECATION_ERROR")
                    Episode(
                        data = "$mediaId:1:$epNum",
                        name = "Episode $epNum",
                        season = 1,
                        episode = epNum,
                        posterUrl = poster,
                    )
                }

                newTvSeriesLoadResponse(
                    name = title,
                    url = originalUrl,
                    type = TvType.Anime,
                    episodes = episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.plot = desc
                    this.year = year
                    this.tags = genres
                    this.score = Score.from10(score)
                    this.syncData = mutableMapOf("anilist" to anilistId)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed resolving AniList ID $anilistId: ${e.message}", e)
            null
        }
    }

    private suspend fun buildResponseFromMeta(
        meta: StremioAddonClient.StremioMetaItem,
        cleanId: String,
        url: String,
        resolvedType: String,
        extraSyncData: Map<String, String> = emptyMap(),
    ): LoadResponse {
        val title = meta.name ?: cleanId
        val poster = meta.poster
        val background = meta.background?.replace("t/p/original//", "t/p/original/")
        val logo = meta.logo
        val description = meta.description
        val year = meta.releaseInfo?.take(4)?.toIntOrNull()
        val genres = meta.genres ?: emptyList()
        val isSeries = resolvedType == "series" || !meta.videos.isNullOrEmpty()

        return if (isSeries) {
            val videos = meta.videos ?: emptyList()
            val episodes = videos.map { video ->
                val seasonNum = video.season ?: 1
                val epNum = video.episode ?: 1
                @Suppress("DEPRECATION_ERROR")
                Episode(
                    data = "$cleanId:$seasonNum:$epNum",
                    name = video.title?.takeIf { it.isNotBlank() } ?: "Episode $epNum",
                    season = seasonNum,
                    episode = epNum,
                    posterUrl = video.thumbnail,
                    description = video.description,
                )
            }

            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.logoUrl = logo
                this.plot = description
                this.year = year
                this.tags = genres
                this.score = Score.from10(meta.imdbRating?.toDoubleOrNull())
                this.syncData = mutableMapOf("imdb" to cleanId).apply {
                    putAll(extraSyncData)
                }
            }
        } else {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = cleanId,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.logoUrl = logo
                this.plot = description
                this.year = year
                this.tags = genres
                this.score = Score.from10(meta.imdbRating?.toDoubleOrNull())
                this.syncData = mutableMapOf("imdb" to cleanId).apply {
                    putAll(extraSyncData)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // Meta-provider: streams are populated directly by stream addons via StremioAddonManager
        return true
    }

    companion object {
        private const val TAG = "StremioApi"
    }
}
