package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

object AniListCatalogClient {
    private const val TAG = "AniListCatalogClient"
    private const val GRAPHQL_URL = "https://graphql.anilist.co"
    private val mapper = jacksonObjectMapper()

    suspend fun fetchAiringNow(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("POPULARITY_DESC"),
            status = "RELEASING",
            page = page,
        )

    suspend fun fetchUpcoming(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("POPULARITY_DESC"),
            status = "NOT_YET_RELEASED",
            page = page,
        )

    suspend fun fetchTopSeries(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("SCORE_DESC"),
            format = "TV",
            page = page,
        )

    suspend fun fetchTopMovies(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("SCORE_DESC"),
            format = "MOVIE",
            page = page,
        )

    suspend fun fetchPopular(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("POPULARITY_DESC"),
            page = page,
        )

    suspend fun fetchAllTime(page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("SCORE_DESC"),
            page = page,
        )

    suspend fun fetchByEra(startYear: Int, endYear: Int, page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("SCORE_DESC"),
            startDateGreater = startYear * 10000,
            startDateLesser = (endYear + 1) * 10000,
            page = page,
        )

    suspend fun fetchByGenre(genre: String, page: Int = 1): List<ExploreItem> =
        fetchAniList(
            sort = listOf("SCORE_DESC"),
            genre = genre,
            page = page,
        )

    private suspend fun fetchAniList(
        sort: List<String> = listOf("POPULARITY_DESC"),
        status: String? = null,
        format: String? = null,
        genre: String? = null,
        startDateGreater: Int? = null,
        startDateLesser: Int? = null,
        page: Int = 1,
        perPage: Int = 24,
    ): List<ExploreItem> = withContext(Dispatchers.IO) {
        val query = """
            query (
                ${'$'}page: Int,
                ${'$'}perPage: Int,
                ${'$'}sort: [MediaSort],
                ${'$'}status: MediaStatus,
                ${'$'}format: MediaFormat,
                ${'$'}genre: String,
                ${'$'}startDate_greater: FuzzyDateInt,
                ${'$'}startDate_lesser: FuzzyDateInt
            ) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(
                        type: ANIME,
                        sort: ${'$'}sort,
                        status: ${'$'}status,
                        format: ${'$'}format,
                        genre: ${'$'}genre,
                        startDate_greater: ${'$'}startDate_greater,
                        startDate_lesser: ${'$'}startDate_lesser
                    ) {
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

        val variables = mutableMapOf<String, Any?>(
            "page" to page,
            "perPage" to perPage,
            "sort" to sort,
        ).apply {
            if (status != null) put("status", status)
            if (format != null) put("format", format)
            if (!genre.isNullOrBlank()) put("genre", genre)
            if (startDateGreater != null) put("startDate_greater", startDateGreater)
            if (startDateLesser != null) put("startDate_lesser", startDateLesser)
        }

        val jsonMap = mapOf(
            "query" to query,
            "variables" to variables,
        )
        val payload = mapper.writeValueAsString(jsonMap).toRequestBody("application/json".toMediaTypeOrNull())

        try {
            val response = app.post(
                GRAPHQL_URL,
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
                val title = (
                    titleNode?.get("userPreferred")?.asText()
                        ?: titleNode?.get("english")?.asText()
                        ?: titleNode?.get("romaji")?.asText()
                    )?.takeIf { it.isNotBlank() } ?: continue

                val coverNode = node["coverImage"]
                val posterUrl = (coverNode?.get("extraLarge")?.asText() ?: coverNode?.get("large")?.asText())?.takeIf { it.isNotBlank() }
                val bannerUrl = node["bannerImage"]?.asText()?.takeIf { it.isNotBlank() }
                val score = node["averageScore"]?.asDouble()?.let { it / 10.0 }
                val year = node["seasonYear"]?.asText()?.takeIf { it.isNotBlank() }
                val desc = node["description"]?.asText()?.takeIf { it.isNotBlank() }
                val genres = node["genres"]?.mapNotNull { it.asText() } ?: emptyList()
                val itemFormat = node["format"]?.asText()?.lowercase() ?: "anime"

                items.add(
                    ExploreItem(
                        id = if (idMal != null) "mal:$idMal" else "anilist:$anilistId",
                        type = if (itemFormat == "movie") "movie" else "series",
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = bannerUrl,
                        releaseYear = year,
                        description = desc,
                        rating = score,
                        genres = genres,
                    ),
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.e(TAG, "AniList fetch error: ${e.message}")
            emptyList()
        }
    }
}
