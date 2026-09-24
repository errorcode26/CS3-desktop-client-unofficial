package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object JikanClient {
    private const val TAG = "JikanClient"
    private const val BASE_URL = "https://api.jikan.moe/v4"
    private val mapper = jacksonObjectMapper()

    private val rateMutex = Mutex()
    private var lastRequestTime = 0L

    private suspend fun throttle() {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < 340L) {
                delay(340L - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    suspend fun fetchAiringNow(page: Int = 1): List<ExploreItem> =
        fetchJikanEndpoint("$BASE_URL/seasons/now?page=$page&limit=24&sfw=true")

    suspend fun fetchUpcoming(page: Int = 1): List<ExploreItem> =
        fetchJikanEndpoint("$BASE_URL/seasons/upcoming?page=$page&limit=24&sfw=true")

    suspend fun fetchTopAnime(
        page: Int = 1,
        filter: String? = null,
        type: String? = null,
    ): List<ExploreItem> {
        val filterQuery = if (filter != null) "&filter=$filter" else ""
        val typeQuery = if (type != null) "&type=$type" else ""
        return fetchJikanEndpoint("$BASE_URL/top/anime?page=$page&limit=24$filterQuery$typeQuery&sfw=true")
    }

    suspend fun fetchByEra(
        startDate: String,
        endDate: String,
        page: Int = 1,
    ): List<ExploreItem> =
        fetchJikanEndpoint("$BASE_URL/anime?start_date=$startDate&end_date=$endDate&order_by=score&sort=desc&min_score=7.5&page=$page&limit=24&sfw=true")

    suspend fun fetchByGenre(
        genreId: Int,
        page: Int = 1,
    ): List<ExploreItem> =
        fetchJikanEndpoint("$BASE_URL/anime?genres=$genreId&order_by=score&sort=desc&min_score=7.0&page=$page&limit=24&sfw=true")

    private suspend fun fetchJikanEndpoint(url: String): List<ExploreItem> = withContext(Dispatchers.IO) {
        try {
            throttle()
            val response = app.get(url, timeout = 12_000L, cacheTime = 60 * 12)
            val root = mapper.readTree(response.text)
            val dataArray = root["data"] ?: return@withContext emptyList()
            if (!dataArray.isArray) return@withContext emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in dataArray) {
                val malId = node["mal_id"]?.asInt() ?: continue
                val titleEng = node["title_english"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val titleDefault = node["title"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val titleJap = node["title_japanese"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val title = titleEng ?: titleDefault ?: titleJap ?: continue

                val typeStr = node["type"]?.asText()?.lowercase() ?: "tv"
                val mediaType = if (typeStr == "movie") "movie" else "series"

                val images = node["images"]
                val webp = images?.get("webp")
                val jpg = images?.get("jpg")
                val posterUrl = (webp?.get("large_image_url")?.asText()
                    ?: jpg?.get("large_image_url")?.asText()
                    ?: webp?.get("image_url")?.asText()
                    ?: jpg?.get("image_url")?.asText())?.takeIf { it.isNotBlank() && it != "null" }

                val year = node["year"]?.asInt()?.takeIf { it > 0 }?.toString()
                    ?: node["aired"]?.get("from")?.asText()?.take(4)?.takeIf { it.isNotBlank() && it != "null" }

                val synopsis = node["synopsis"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val score = node["score"]?.asDouble()?.takeIf { it > 0.0 }

                val genres = mutableListOf<String>()
                node["genres"]?.forEach { g ->
                    g["name"]?.asText()?.takeIf { it.isNotBlank() }?.let { genres.add(it) }
                }

                items.add(
                    ExploreItem(
                        id = "mal:$malId",
                        type = mediaType,
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = posterUrl,
                        releaseYear = year,
                        description = synopsis,
                        rating = score,
                        genres = genres,
                    )
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed fetching Jikan endpoint $url: ${e.message}")
            emptyList()
        }
    }
}
