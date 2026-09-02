package com.lagradost.cloudstream3.desktop.ui.screens.search

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class SearchSuggestionItem(
    val title: String,
    val year: String? = null,
    val isHistory: Boolean = false,
)

object SearchSuggestionApi {
    private const val TAG = "SearchSuggestionApi"
    private const val TMDB_API_URL = "https://api.themoviedb.org/3/search/multi"
    private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"

    // In-memory cache for suggestions
    private val memoryCache = ConcurrentHashMap<String, List<SearchSuggestionItem>>()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TmdbSearchResult(
        @JsonProperty("results") val results: List<TmdbSearchItem>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TmdbSearchItem(
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
    )

    suspend fun getSuggestions(query: String, history: List<String> = emptyList()): List<SearchSuggestionItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val cacheKey = trimmed.lowercase()
        memoryCache[cacheKey]?.let { cached ->
            return@withContext mergeHistory(trimmed, history, cached)
        }

        val networkResults = mutableListOf<SearchSuggestionItem>()

        // 1. External Addon Catalog query
        try {
            var seriesMetas = StremioAddonClient.search(trimmed, "series") ?: emptyList()
            var movieMetas = StremioAddonClient.search(trimmed, "movie") ?: emptyList()

            // If user has no custom addon configured, use default catalog bridge
            if (seriesMetas.isEmpty() && movieMetas.isEmpty()) {
                val baseUrl = StremioAddonClient.getTransportBaseUrl(com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.URL_CINEMETA)
                if (baseUrl.isNotBlank()) {
                    val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                    try {
                        val sRes = app.get("$baseUrl/catalog/series/top/search=$encoded.json", timeout = 2500L)
                        seriesMetas = sRes.parsedSafe<StremioAddonClient.StremioCatalogResponse>()?.metas ?: emptyList()
                    } catch (_: Exception) {}
                    try {
                        val mRes = app.get("$baseUrl/catalog/movie/top/search=$encoded.json", timeout = 2500L)
                        movieMetas = mRes.parsedSafe<StremioAddonClient.StremioCatalogResponse>()?.metas ?: emptyList()
                    } catch (_: Exception) {}
                }
            }

            val combinedMetas = (seriesMetas + movieMetas).distinctBy { it.name?.lowercase() }

            for (meta in combinedMetas.take(8)) {
                val name = meta.name?.trim() ?: continue
                val year = meta.releaseInfo?.take(4)?.filter { it.isDigit() }?.takeIf { it.length == 4 }
                networkResults.add(SearchSuggestionItem(title = name, year = year))
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Catalog bridge query failed for '$trimmed': ${e.message}")
        }

        // 2. Secondary multi-search catalog fallback
        if (networkResults.size < 4) {
            try {
                val apiKey = com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.customTmdbApiKey.value.takeIf { it.isNotBlank() } ?: TMDB_API_KEY
                val response = app.get(
                    url = TMDB_API_URL,
                    params = mapOf(
                        "api_key" to apiKey,
                        "query" to trimmed,
                        "language" to "en-US",
                    ),
                    timeout = 3000L,
                    cacheTime = 60 * 24,
                )
                val parsed = response.parsedSafe<TmdbSearchResult>()
                parsed?.results
                    ?.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                    ?.forEach { item ->
                        val name = (item.title ?: item.name)?.trim() ?: return@forEach
                        val year = (item.releaseDate ?: item.firstAirDate)?.take(4)?.filter { it.isDigit() }?.takeIf { it.length == 4 }
                        if (networkResults.none { it.title.equals(name, ignoreCase = true) }) {
                            networkResults.add(SearchSuggestionItem(title = name, year = year))
                        }
                    }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Secondary multi-search failed for '$trimmed': ${e.message}")
            }
        }

        val finalNetwork = networkResults.take(10)
        memoryCache[cacheKey] = finalNetwork

        return@withContext mergeHistory(trimmed, history, finalNetwork)
    }

    private fun mergeHistory(
        query: String,
        history: List<String>,
        networkItems: List<SearchSuggestionItem>,
    ): List<SearchSuggestionItem> {
        val qLower = query.lowercase()
        val historyMatches = history
            .filter { it.lowercase().startsWith(qLower) || it.lowercase().contains(qLower) }
            .take(3)
            .map { SearchSuggestionItem(title = it, isHistory = true) }

        val seen = HashSet<String>()
        val result = mutableListOf<SearchSuggestionItem>()

        for (item in historyMatches) {
            val key = item.title.lowercase()
            if (seen.add(key)) {
                result.add(item)
            }
        }

        for (item in networkItems) {
            val key = item.title.lowercase()
            if (seen.add(key)) {
                result.add(item)
            }
        }

        return result.take(10)
    }
}
