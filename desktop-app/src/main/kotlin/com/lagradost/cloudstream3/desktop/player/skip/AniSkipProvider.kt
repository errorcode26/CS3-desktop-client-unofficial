package com.lagradost.cloudstream3.desktop.player.skip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object AniSkipProvider : ISkipProvider {
    override val id: String = "aniskip"
    override val name: String = "AniSkip (Anime)"

    private const val ANISKIP_BASE_URL = "https://api.aniskip.com/v2/skip-times"
    private const val JIKAN_SEARCH_URL = "https://api.jikan.moe/v4/anime"

    // In-memory cache: Normalized title -> MAL ID
    private val malIdCache = ConcurrentHashMap<String, Int>()

    private data class AniSkipResponse(
        @JsonProperty("found") val found: Boolean? = null,
        @JsonProperty("results") val results: List<AniSkipResult>? = null,
        @JsonProperty("message") val message: String? = null,
        @JsonProperty("statusCode") val statusCode: Int? = null
    )

    private data class AniSkipResult(
        @JsonProperty("interval") val interval: AniSkipInterval? = null,
        @JsonProperty("skipType") val skipType: String? = null,
        @JsonProperty("skipId") val skipId: String? = null,
        @JsonProperty("episodeLength") val episodeLength: Double? = null
    )

    private data class AniSkipInterval(
        @JsonProperty("startTime") val startTime: Double? = null,
        @JsonProperty("endTime") val endTime: Double? = null
    )

    private data class JikanSearchResponse(
        @JsonProperty("data") val data: List<JikanAnimeData>? = null
    )

    private data class JikanAnimeData(
        @JsonProperty("mal_id") val malId: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("title_english") val titleEnglish: String? = null
    )

    private data class AniListGraphQlRequest(
        @JsonProperty("query") val query: String,
        @JsonProperty("variables") val variables: Map<String, Any?>? = null
    )

    private data class AniListSearchResponse(
        @JsonProperty("data") val data: AniListSearchData? = null
    )

    private data class AniListSearchData(
        @JsonProperty("Page") val page: AniListSearchPage? = null
    )

    private data class AniListSearchPage(
        @JsonProperty("media") val media: List<AniListMediaItem>? = null
    )

    private data class AniListMediaItem(
        @JsonProperty("idMal") val idMal: Int? = null,
        @JsonProperty("id") val id: Int? = null
    )

    override suspend fun getSkipIntervals(query: SkipQuery): List<SkipInterval> {
        return withContext(Dispatchers.IO) {
            try {
                val malId = query.malId ?: resolveMalId(query.title) ?: return@withContext emptyList()
                val ep = if (query.episode > 0) query.episode else 1
                val durationParam = if (query.durationSeconds > 0) "&episodeLength=${query.durationSeconds.toInt()}" else "&episodeLength=0"
                val url = "$ANISKIP_BASE_URL/$malId/$ep?types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed$durationParam"

                AppLogger.i("AniSkipProvider", "Querying AniSkip: $url (show='${query.title}', MAL=$malId, ep=$ep)")

                val response = app.get(
                    url = url,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "User-Agent" to "CloudStream-Desktop/1.0"
                    ),
                    timeout = 7000L
                ).text

                val parsed = tryParseJson<AniSkipResponse>(response)
                AppLogger.i("AniSkipProvider", "AniSkip response for '$malId' EP $ep: found=${parsed?.found}, count=${parsed?.results?.size ?: 0}")

                if (parsed?.found == true && !parsed.results.isNullOrEmpty()) {
                    val intervals = parsed.results.mapNotNull { res ->
                        val startSec = res.interval?.startTime ?: return@mapNotNull null
                        val endSec = res.interval.endTime ?: return@mapNotNull null
                        val type = SkipType.fromString(res.skipType)
                        val label = when (type) {
                            SkipType.OPENING, SkipType.MIXED_OP -> "Skip Opening"
                            SkipType.ENDING, SkipType.MIXED_ED -> "Skip Ending"
                            SkipType.RECAP -> "Skip Recap"
                            SkipType.PREVIEW -> "Skip Preview"
                            else -> "Skip Intro"
                        }
                        SkipInterval(
                            startMs = (startSec * 1000).toLong(),
                            endMs = (endSec * 1000).toLong(),
                            type = type,
                            label = label,
                            providerId = id
                        )
                    }
                    AppLogger.i("AniSkipProvider", "AniSkip successfully parsed ${intervals.size} skip intervals for '${query.title}'")
                    intervals
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                AppLogger.i("AniSkipProvider", "Failed to fetch skip intervals: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun resolveMalId(title: String): Int? {
        val cleanTitle = cleanTitle(title)
        if (cleanTitle.isBlank()) return null

        malIdCache[cleanTitle]?.let { return it }
        AppLogger.i("AniSkipProvider", "Resolving MAL ID for '$cleanTitle'...")

        // 1. Try AniList GraphQL (Fast, reliable, returns idMal directly)
        try {
            val graphqlQuery = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 1) {
                    media(search: ${'$'}search, type: ANIME) {
                      idMal
                      id
                    }
                  }
                }
            """.trimIndent()
            val req = AniListGraphQlRequest(query = graphqlQuery, variables = mapOf("search" to cleanTitle))
            val resp = app.post(
                url = "https://graphql.anilist.co",
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                json = req,
                timeout = 5000L
            ).text
            val parsed = tryParseJson<AniListSearchResponse>(resp)
            val malId = parsed?.data?.page?.media?.firstOrNull()?.idMal
            if (malId != null && malId > 0) {
                malIdCache[cleanTitle] = malId
                AppLogger.i("AniSkipProvider", "Resolved MAL ID $malId for '$cleanTitle' via AniList")
                return malId
            }
        } catch (e: Exception) {
            AppLogger.d("AniSkipProvider", "AniList MAL resolution failed: ${e.message}")
        }

        // 2. Fallback: Jikan Search
        try {
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
            val url = "$JIKAN_SEARCH_URL?q=$encoded&limit=1"
            val resp = app.get(
                url = url,
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to "CloudStream-Desktop/1.0"
                ),
                timeout = 7000L
            ).text
            val parsed = tryParseJson<JikanSearchResponse>(resp)
            val id = parsed?.data?.firstOrNull()?.malId
            if (id != null && id > 0) {
                malIdCache[cleanTitle] = id
                AppLogger.i("AniSkipProvider", "Resolved MAL ID $id for '$cleanTitle' via Jikan")
                return id
            }
        } catch (e: Exception) {
            AppLogger.i("AniSkipProvider", "Jikan MAL resolution failed for '$title': ${e.message}")
        }

        AppLogger.i("AniSkipProvider", "Could not resolve MAL ID for '$title', skipping AniSkip.")
        return null
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\b(season\s*\d+|s\d+|part\s*\d+|cour\s*\d+|dub|sub|uncensored|tv)\b"""), "")
            .replace(Regex("""[\[(].*?[\])]"""), "")
            .replace(Regex("""[-_]"""), " ")
            .trim()
    }
}
