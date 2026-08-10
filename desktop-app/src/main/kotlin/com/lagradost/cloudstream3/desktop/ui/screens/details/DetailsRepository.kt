package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.*
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    fun put(url: String, response: LoadResponse) {
        _cache[url] = response
    }
    fun remove(url: String) {
        _cache.remove(url)
    }
    fun containsKey(url: String): Boolean = _cache.containsKey(url)
}

object EnrichedDetailsCache {
    private val _cache: MutableMap<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState> = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState>?): Boolean {
                return size > 50
            }
        },
    )
    fun get(url: String): com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? = _cache[url]
    fun put(url: String, state: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState) {
        _cache[url] = state
    }
    fun remove(url: String) {
        _cache.remove(url)
    }
    fun containsKey(url: String): Boolean = _cache.containsKey(url)
}

object DetailsRepository {
    private val inflightMutexes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    suspend fun fetchRaw(provider: com.lagradost.cloudstream3.MainAPI, url: String, fallbackName: String? = null): LoadResponse? {
        DetailsCache.get(url)?.let { return it }

        val mutex = inflightMutexes.getOrPut(url) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        try {
            // Check cache again after acquiring lock
            DetailsCache.get(url)?.let { return it }
            
            return doFetchRaw(provider, url, fallbackName)
        } finally {
            mutex.unlock()
            inflightMutexes.remove(url, mutex)
        }
    }

    private suspend fun doFetchRaw(provider: com.lagradost.cloudstream3.MainAPI, url: String, fallbackName: String? = null): LoadResponse? {
        DetailsCache.get(url)?.let { return it }

        var targetProvider = provider
        var targetUrl = url

        if (targetUrl.contains("themoviedb.org") && !fallbackName.isNullOrBlank()) {
            try {
                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] TMDB link detected ($targetUrl). Searching active provider (${provider.name}) for: '$fallbackName'...")
                val searchResults = SafePluginInvoker.invokeOrNull(
                    tag = "DetailsRepo:Search:${provider.name}",
                    timeoutMs = 2500L,
                ) { provider.search(fallbackName, 1)?.items }

                val bestMatch = searchResults?.find { it.name.equals(fallbackName, ignoreCase = true) } ?: searchResults?.firstOrNull()
                if (bestMatch != null && bestMatch.url.isNotBlank() && !bestMatch.url.contains("themoviedb.org")) {
                    com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found exact match on provider (${provider.name}): ${bestMatch.name} -> ${bestMatch.url}")
                    targetUrl = bestMatch.url
                } else {
                    val candidateApis = com.lagradost.cloudstream3.APIHolder.allProviders
                        .filter { it.name != provider.name && it.name != "TMDB" }
                        .take(6)

                    if (candidateApis.isNotEmpty()) {
                        coroutineScope {
                            val matchDeferreds = candidateApis.map { api ->
                                async(Dispatchers.IO) {
                                    try {
                                        val altResults = SafePluginInvoker.invokeOrNull(
                                            tag = "DetailsRepo:Search:${api.name}",
                                            timeoutMs = 2500L,
                                        ) { api.search(fallbackName, 1)?.items }

                                        val altMatch = altResults?.find { it.name.equals(fallbackName, ignoreCase = true) }
                                            ?: altResults?.firstOrNull()
                                        if (altMatch != null && altMatch.url.isNotBlank() && !altMatch.url.contains("themoviedb.org")) {
                                            api to altMatch.url
                                        } else null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        null
                                    }
                                }
                            }
                            val firstMatch = matchDeferreds.awaitAll().filterNotNull().firstOrNull()
                            if (firstMatch != null) {
                                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Found match on candidate provider (${firstMatch.first.name}): -> ${firstMatch.second}")
                                targetProvider = firstMatch.first
                                targetUrl = firstMatch.second
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.w("[DetailsRepo] Provider search bridge failed for '$fallbackName': ${e.message}")
            }
        }

        repeat(3) { attempt ->
            try {
                com.lagradost.common.logging.AppLogger.i("Plugin:${targetProvider.name}", "Fetching media details for: $targetUrl (attempt ${attempt + 1}/3)")
                val loaded = SafePluginInvoker.invokeOrNull(
                    tag = "DetailsRepo:Load:${targetProvider.name}",
                    timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                ) { targetProvider.load(targetUrl) }

                if (loaded != null) {
                    com.lagradost.common.logging.AppLogger.i("Plugin:${targetProvider.name}", "Loaded details: title='${loaded.name}', type=${loaded.type}")
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
                com.lagradost.common.logging.AppLogger.e("Plugin:${targetProvider.name}", "fetchRaw attempt ${attempt + 1}/3 failed for $targetUrl", e)
                if (attempt < 2) kotlinx.coroutines.delay(500L * (attempt + 1)) // 0.5s then 1s backoff
            }
        }
        return null
    }
}

// TmdbEnrichmentService extracted to TmdbEnrichmentService.kt
