package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.common.logging.AppLogger
import java.net.URLEncoder

object ExploreCatalogClient {
    private const val TAG = "ExploreCatalogClient"
    private val mapper = jacksonObjectMapper()

    suspend fun fetchCatalogItems(
        baseUrl: String,
        type: String,
        catalogId: String,
        genre: String? = null,
        skip: Int = 0,
    ): List<ExploreItem> {
        return try {
            val queryParts = mutableListOf<String>()
            if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) {
                val encodedGenre = URLEncoder.encode(genre.trim(), "UTF-8")
                queryParts.add("genre=$encodedGenre")
            }
            if (skip > 0) {
                queryParts.add("skip=$skip")
            }

            val pathExtra = if (queryParts.isNotEmpty()) {
                "/" + queryParts.joinToString("&")
            } else {
                ""
            }

            val cleanBase = baseUrl.trimEnd('/')
            val url = "$cleanBase/catalog/$type/$catalogId$pathExtra.json"
            AppLogger.d(TAG, "Fetching catalog from: $url")

            val response = app.get(url, timeout = 10_000L, cacheTime = 60 * 6)
            val root = mapper.readTree(response.text)
            val metas = root["metas"] ?: return emptyList()

            if (!metas.isArray) return emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in metas) {
                val id = node["id"]?.asText() ?: continue
                val itemType = node["type"]?.asText() ?: type
                val name = node["name"]?.asText() ?: continue
                val poster = node["poster"]?.asText()
                val background = node["background"]?.asText()
                val releaseInfo = node["releaseInfo"]?.asText() ?: node["year"]?.asText()
                val description = node["description"]?.asText()

                val scoreStr = node["imdbRating"]?.asText()
                val rating = scoreStr?.toDoubleOrNull() ?: node["imdbRating"]?.asDouble()

                val genresList = mutableListOf<String>()
                val genreNode = node["genres"] ?: node["genre"]
                if (genreNode != null && genreNode.isArray) {
                    for (g in genreNode) {
                        genresList.add(g.asText())
                    }
                }

                items.add(
                    ExploreItem(
                        id = id,
                        type = itemType,
                        name = name,
                        posterUrl = poster,
                        backgroundUrl = background,
                        releaseYear = releaseInfo,
                        description = description,
                        rating = rating,
                        genres = genresList,
                    )
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error fetching catalog for $type / $catalogId from $baseUrl: ${e.message}")
            emptyList()
        }
    }
}
