package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object ExploreCatalogClient {
    private const val TAG = "ExploreCatalogClient"
    private val mapper = jacksonObjectMapper()
    private val inflightRequests = ConcurrentHashMap<String, Deferred<List<ExploreItem>>>()

    private data class CachedCatalogResult(
        val timestamp: Long,
        val items: List<ExploreItem>,
    )

    private val responseCache = ConcurrentHashMap<String, CachedCatalogResult>()
    private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL

    fun clearCache() {
        responseCache.clear()
        inflightRequests.clear()
    }

    suspend fun fetchCatalogItems(
        baseUrl: String,
        type: String,
        catalogId: String,
        genre: String? = null,
        skip: Int = 0,
        queryParams: String = "",
    ): List<ExploreItem> = coroutineScope {
        val queryParts = mutableListOf<String>()
        if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) {
            val encodedGenre = URLEncoder.encode(genre.trim(), "UTF-8").replace("+", "%20")
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
        val querySuffix = if (queryParams.isNotBlank()) {
            if (queryParams.startsWith("?")) queryParams else "?$queryParams"
        } else {
            ""
        }
        val url = "$cleanBase/catalog/$type/$catalogId$pathExtra.json$querySuffix"

        val cached = responseCache[url]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS && cached.items.isNotEmpty()) {
            return@coroutineScope cached.items
        }

        val existing = inflightRequests[url]
        if (existing != null && existing.isActive) {
            return@coroutineScope existing.await()
        }

        val deferred = async(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "Fetching catalog from: $url")
                val response = app.get(url, timeout = 6_000L, cacheTime = 60 * 6)
                val root = mapper.readTree(response.text)
                val metas = root["metas"] ?: return@async emptyList()

                if (!metas.isArray) return@async emptyList()

                val items = mutableListOf<ExploreItem>()
                for (node in metas) {
                    val id = node["id"]?.asText() ?: continue
                    val itemType = node["type"]?.asText() ?: type
                    val name = node["name"]?.asText() ?: continue
                    val poster = node["poster"]?.asText()?.takeIf { it.isNotBlank() }
                    val background = node["background"]?.asText()?.takeIf { it.isNotBlank() }
                    val logo = node["logo"]?.asText()?.takeIf { it.isNotBlank() }
                    val releaseInfo = (node["releaseInfo"]?.asText() ?: node["year"]?.asText())?.takeIf { it.isNotBlank() }
                    val description = node["description"]?.asText()?.takeIf { it.isNotBlank() }

                    val scoreStr = node["imdbRating"]?.asText()
                    val rating = scoreStr?.toDoubleOrNull() ?: node["imdbRating"]?.asDouble()
                    val posterShape = node["posterShape"]?.asText()?.takeIf { it.isNotBlank() }

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
                            logoUrl = logo,
                            releaseYear = releaseInfo,
                            description = description,
                            rating = rating,
                            genres = genresList,
                            posterShape = posterShape,
                        ),
                    )
                }

                if (items.isNotEmpty()) {
                    responseCache[url] = CachedCatalogResult(System.currentTimeMillis(), items)
                }
                items
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error fetching catalog for $type / $catalogId from $baseUrl: ${e.message}")
                emptyList()
            } finally {
                inflightRequests.remove(url)
            }
        }

        inflightRequests[url] = deferred
        deferred.await()
    }
}
