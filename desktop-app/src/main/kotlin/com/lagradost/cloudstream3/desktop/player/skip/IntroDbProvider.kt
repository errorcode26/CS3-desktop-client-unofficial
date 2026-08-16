package com.lagradost.cloudstream3.desktop.player.skip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object IntroDbProvider : ISkipProvider {
    override val id: String = "introdb"
    override val name: String = "IntroDB (TV Shows)"

    private const val INTRODB_BASE_URL = "https://api.introdb.app"
    private const val DEFAULT_TMDB_API_KEY = "3828864585df9d4f006c09403eb9a888"

    private val imdbIdCache = ConcurrentHashMap<String, String>()

    private data class IntroDbTimeRange(
        @JsonProperty("start") val start: Any? = null,
        @JsonProperty("end") val end: Any? = null,
        @JsonProperty("start_sec") val startSec: Any? = null,
        @JsonProperty("end_sec") val endSec: Any? = null,
        @JsonProperty("startTime") val startTime: Any? = null,
        @JsonProperty("endTime") val endTime: Any? = null
    )

    private data class IntroDbSegment(
        @JsonProperty("segment_type") val segmentType: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("start_sec") val startSec: Any? = null,
        @JsonProperty("end_sec") val endSec: Any? = null,
        @JsonProperty("start") val start: Any? = null,
        @JsonProperty("end") val end: Any? = null,
        @JsonProperty("startTime") val startTime: Any? = null,
        @JsonProperty("endTime") val endTime: Any? = null
    )

    private data class IntroDbResponse(
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("intro") val intro: IntroDbTimeRange? = null,
        @JsonProperty("recap") val recap: IntroDbTimeRange? = null,
        @JsonProperty("outro") val outro: IntroDbTimeRange? = null,
        @JsonProperty("segments") val segments: List<IntroDbSegment>? = null,
        @JsonProperty("data") val data: List<IntroDbSegment>? = null,
        @JsonProperty("start") val start: Any? = null,
        @JsonProperty("end") val end: Any? = null
    )

    private data class CinemetaResponse(
        @JsonProperty("metas") val metas: List<CinemetaMeta>? = null
    )

    private data class CinemetaMeta(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("type") val type: String? = null
    )

    private data class TmdbSearchResponse(
        @JsonProperty("results") val results: List<TmdbSearchResult>? = null
    )

    private data class TmdbSearchResult(
        @JsonProperty("id") val id: Int? = null
    )

    private data class TmdbExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null
    )

    override suspend fun getSkipIntervals(query: SkipQuery): List<SkipInterval> {
        return withContext(Dispatchers.IO) {
            try {
                val imdbId = query.imdbId ?: resolveImdbId(query.title)
                if (imdbId.isNullOrBlank()) {
                    AppLogger.i("IntroDbProvider", "Could not resolve IMDb ID for title '${query.title}', skipping IntroDB.")
                    return@withContext emptyList()
                }

                val season = if (query.season > 0) query.season else 1
                val episode = if (query.episode > 0) query.episode else 1
                val url = "$INTRODB_BASE_URL/segments?imdb_id=$imdbId&season=$season&episode=$episode"

                AppLogger.i("IntroDbProvider", "Querying IntroDB: $url (show='${query.title}', imdb=$imdbId, S${season}E${episode})")

                val response = app.get(
                    url = url,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "User-Agent" to "CloudStream-Desktop/1.0"
                    ),
                    timeout = 6000L
                )

                val responseText = response.text
                AppLogger.i("IntroDbProvider", "IntroDB HTTP ${response.code} response for '$imdbId': $responseText")

                if (response.code != 200 || responseText.isBlank()) {
                    return@withContext emptyList()
                }

                val intervals = mutableListOf<SkipInterval>()

                // 1. Try parsing as standard IntroDbResponse object with intro/recap/outro
                val objResponse = tryParseJson<IntroDbResponse>(responseText)
                if (objResponse != null) {
                    objResponse.intro?.let { range ->
                        val startSec = parseSeconds(range.start ?: range.startSec ?: range.startTime)
                        val endSec = parseSeconds(range.end ?: range.endSec ?: range.endTime)
                        if (startSec != null && endSec != null && endSec > startSec) {
                            intervals.add(
                                SkipInterval(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    type = SkipType.INTRO,
                                    label = "Skip Intro",
                                    providerId = id
                                )
                            )
                        }
                    }

                    objResponse.recap?.let { range ->
                        val startSec = parseSeconds(range.start ?: range.startSec ?: range.startTime)
                        val endSec = parseSeconds(range.end ?: range.endSec ?: range.endTime)
                        if (startSec != null && endSec != null && endSec > startSec) {
                            intervals.add(
                                SkipInterval(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    type = SkipType.RECAP,
                                    label = "Skip Recap",
                                    providerId = id
                                )
                            )
                        }
                    }

                    objResponse.outro?.let { range ->
                        val startSec = parseSeconds(range.start ?: range.startSec ?: range.startTime)
                        val endSec = parseSeconds(range.end ?: range.endSec ?: range.endTime)
                        if (startSec != null && endSec != null && endSec > startSec) {
                            intervals.add(
                                SkipInterval(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    type = SkipType.OUTRO,
                                    label = "Skip Outro",
                                    providerId = id
                                )
                            )
                        }
                    }

                    // Top-level start/end fallback
                    if (intervals.isEmpty() && objResponse.start != null && objResponse.end != null) {
                        val startSec = parseSeconds(objResponse.start)
                        val endSec = parseSeconds(objResponse.end)
                        if (startSec != null && endSec != null && endSec > startSec) {
                            intervals.add(
                                SkipInterval(
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    type = SkipType.INTRO,
                                    label = "Skip Intro",
                                    providerId = id
                                )
                            )
                        }
                    }

                    val segList = objResponse.segments ?: objResponse.data
                    if (!segList.isNullOrEmpty()) {
                        for (item in segList) {
                            val typeStr = item.segmentType ?: item.type ?: "intro"
                            val startSec = parseSeconds(item.startSec ?: item.start ?: item.startTime)
                            val endSec = parseSeconds(item.endSec ?: item.end ?: item.endTime)

                            if (startSec != null && endSec != null && endSec > startSec) {
                                val skipType = SkipType.fromString(typeStr)
                                val label = when (skipType) {
                                    SkipType.RECAP -> "Skip Recap"
                                    SkipType.OUTRO, SkipType.ENDING -> "Skip Outro"
                                    else -> "Skip Intro"
                                }
                                intervals.add(
                                    SkipInterval(
                                        startMs = (startSec * 1000).toLong(),
                                        endMs = (endSec * 1000).toLong(),
                                        type = skipType,
                                        label = label,
                                        providerId = id
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. Try parsing as direct List of segments if object parsing didn't find any
                if (intervals.isEmpty()) {
                    val directList = tryParseJson<List<IntroDbSegment>>(responseText)
                    if (!directList.isNullOrEmpty()) {
                        for (item in directList) {
                            val typeStr = item.segmentType ?: item.type ?: "intro"
                            val startSec = parseSeconds(item.startSec ?: item.start ?: item.startTime)
                            val endSec = parseSeconds(item.endSec ?: item.end ?: item.endTime)

                            if (startSec != null && endSec != null && endSec > startSec) {
                                val skipType = SkipType.fromString(typeStr)
                                val label = when (skipType) {
                                    SkipType.RECAP -> "Skip Recap"
                                    SkipType.OUTRO, SkipType.ENDING -> "Skip Outro"
                                    else -> "Skip Intro"
                                }
                                intervals.add(
                                    SkipInterval(
                                        startMs = (startSec * 1000).toLong(),
                                        endMs = (endSec * 1000).toLong(),
                                        type = skipType,
                                        label = label,
                                        providerId = id
                                    )
                                )
                            }
                        }
                    }
                }

                AppLogger.i("IntroDbProvider", "IntroDB successfully parsed ${intervals.size} skip intervals for '${query.title}' (IMDb: $imdbId)")
                intervals
            } catch (e: Exception) {
                AppLogger.i("IntroDbProvider", "IntroDB lookup failed for '${query.title}': ${e.message}")
                emptyList()
            }
        }
    }

    private fun parseSeconds(obj: Any?): Double? {
        if (obj == null) return null
        if (obj is Number) return obj.toDouble()
        val text = obj.toString().trim()
        if (text.isBlank()) return null
        text.toDoubleOrNull()?.let { return it }

        // Clock string format (HH:MM:SS or MM:SS)
        val parts = text.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                1 -> parts[0].toDouble()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun resolveImdbId(title: String): String? {
        val clean = cleanTitle(title)
        if (clean.isBlank()) return null

        imdbIdCache[clean]?.let { return it }

        // 1. If user configured a custom Stremio metadata addon, query it first
        try {
            val stremioResults = com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient.search(clean, "series")
                ?: com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient.search(clean, "movie")
            val imdbId = stremioResults?.firstOrNull { it.id?.startsWith("tt") == true }?.id
            if (!imdbId.isNullOrBlank()) {
                imdbIdCache[clean] = imdbId
                AppLogger.i("IntroDbProvider", "Resolved IMDb ID $imdbId for show '$clean' via custom Stremio addon")
                return imdbId
            }
        } catch (e: Exception) {
            AppLogger.d("IntroDbProvider", "Custom Stremio addon IMDb lookup: ${e.message}")
        }

        // 2. Primary Official Resolver: TMDB search -> external_ids
        return try {
            val key = DesktopDataStore.getKey<String>("tmdb_api_key")?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=$key&query=$encoded&page=1"
            val searchResp = app.get(
                url = searchUrl,
                headers = mapOf("Accept" to "application/json", "User-Agent" to "CloudStream-Desktop/1.0"),
                timeout = 4000L
            ).text

            val searchResult = tryParseJson<TmdbSearchResponse>(searchResp)
            val tmdbId = searchResult?.results?.firstOrNull()?.id
            if (tmdbId != null && tmdbId > 0) {
                val extUrl = "https://api.themoviedb.org/3/tv/$tmdbId/external_ids?api_key=$key"
                val extResp = app.get(url = extUrl, timeout = 4000L).text
                val extResult = tryParseJson<TmdbExternalIds>(extResp)
                val imdbId = extResult?.imdbId?.takeIf { it.startsWith("tt") }
                if (!imdbId.isNullOrBlank()) {
                    imdbIdCache[clean] = imdbId
                    AppLogger.i("IntroDbProvider", "Resolved IMDb ID $imdbId (TMDB: $tmdbId) for show '$clean'")
                    return imdbId
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.i("IntroDbProvider", "Failed to resolve IMDb ID for '$title': ${e.message}")
            null
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\b(season\s*\d+|s\d+|part\s*\d+|cour\s*\d+|dub|sub|uncensored|tv)\b"""), "")
            .replace(Regex("""[\[(].*?[\])]"""), "")
            .replace(Regex("""[-_]"""), " ")
            .trim()
    }
}
