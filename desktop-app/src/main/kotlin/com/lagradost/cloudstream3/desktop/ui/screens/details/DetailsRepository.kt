package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.LinkedHashMap

// TmdbRateLimiter extracted to TmdbEnrichmentService.kt

object DetailsCache {
    private val _cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, LoadResponse>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean {
                return size > 50
            }
        },
    )
    fun get(url: String): LoadResponse? = _cache[url]
    fun put(url: String, response: LoadResponse) { _cache[url] = response }
    fun remove(url: String) { _cache.remove(url) }
    fun containsKey(url: String): Boolean = _cache.containsKey(url)
}

object DetailsRepository {
    suspend fun fetchRaw(provider: com.lagradost.cloudstream3.MainAPI, url: String, fallbackName: String? = null): LoadResponse? {
        DetailsCache.get(url)?.let { return it }

        var targetProvider = provider
        var targetUrl = url

        if (targetUrl.contains("themoviedb.org") && !fallbackName.isNullOrBlank()) {
            try {
                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] TMDB link detected ($targetUrl). Searching active provider (${provider.name}) for: '$fallbackName'...")
                val searchResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { provider.search(fallbackName, 1)?.items }
                val bestMatch = searchResults?.find { it.name.equals(fallbackName, ignoreCase = true) } ?: searchResults?.firstOrNull()
                if (bestMatch != null && bestMatch.url.isNotBlank() && !bestMatch.url.contains("themoviedb.org")) {
                    com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found exact match on provider (${provider.name}): ${bestMatch.name} -> ${bestMatch.url}")
                    targetUrl = bestMatch.url
                } else {
                    val allApis = com.lagradost.cloudstream3.APIHolder.allProviders
                    for (api in allApis) {
                        if (api.name == provider.name || api.name == "TMDB") continue
                        try {
                            val altResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.search(fallbackName, 1)?.items }
                            val altMatch = altResults?.find { it.name.equals(fallbackName, ignoreCase = true) } ?: altResults?.firstOrNull()
                            if (altMatch != null && altMatch.url.isNotBlank() && !altMatch.url.contains("themoviedb.org")) {
                                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found exact match on alternate provider (${api.name}): ${altMatch.name} -> ${altMatch.url}")
                                targetProvider = api
                                targetUrl = altMatch.url
                                break
                            }
                        } catch (e: Exception) {
                            // continue searching next provider
                        }
                    }
                }
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.w("[DetailsRepo] Provider search bridge failed for '$fallbackName': ${e.message}")
            }
        }

        repeat(3) { attempt ->
            try {
                val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { targetProvider.load(targetUrl) }
                if (loaded != null) {
                    loaded.posterUrl = targetProvider.fixUrlNull(loaded.posterUrl)
                    loaded.backgroundPosterUrl = targetProvider.fixUrlNull(loaded.backgroundPosterUrl)
                    loaded.logoUrl = targetProvider.fixUrlNull(loaded.logoUrl)
                    if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                        loaded.episodes.forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                        loaded.episodes.values.flatten().forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                    }
                    if (loaded.url.isBlank()) loaded.url = targetUrl
                    DetailsCache.put(url, loaded)
                    if (targetUrl != url) DetailsCache.put(targetUrl, loaded)
                    return loaded
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Always re-throw cancellation immediately
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("[DetailsRepo] fetchRaw attempt ${attempt + 1}/3 failed for $targetUrl", e)
                if (attempt < 2) kotlinx.coroutines.delay(500L * (attempt + 1)) // 0.5s then 1s backoff
            }
        }
        return null
    }
}

// TmdbEnrichmentService extracted to TmdbEnrichmentService.kt

