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
    fun get(url: String): LoadResponse? {
        if (url.startsWith("dummy_")) return null
        return _cache[url]
    }
    fun put(url: String, response: LoadResponse) {
        if (url.startsWith("dummy_")) return
        _cache[url] = response
    }
    fun remove(url: String) {
        _cache.remove(url)
    }
    fun clear() {
        _cache.clear()
    }
    fun containsKey(url: String): Boolean = !url.startsWith("dummy_") && _cache.containsKey(url)
}

object EnrichedDetailsCache {
    private val _cache: MutableMap<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState> = Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState>?): Boolean {
                return size > 50
            }
        },
    )
    fun get(url: String): com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState? {
        val cached = _cache[url]
        if (cached?.response == null || cached.fakeData != null) {
            _cache.remove(url)
            return null
        }
        return cached
    }
    fun put(url: String, state: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState) {
        if (state.response != null && state.fakeData == null) {
            _cache[url] = state
        }
    }
    fun remove(url: String) {
        _cache.remove(url)
    }
    fun clear() {
        _cache.clear()
    }
    fun containsKey(url: String): Boolean = _cache.containsKey(url)
}

object DetailsRepository {
    fun findProviderForUrl(url: String): com.lagradost.cloudstream3.MainAPI? {
        if (url.isBlank() || url.startsWith("dummy_")) return null
        val targetHost = runCatching {
            java.net.URI(url).host?.lowercase()?.removePrefix("www.")
        }.getOrNull() ?: return null

        return com.lagradost.cloudstream3.APIHolder.allProviders.find { prov ->
            val provHost = runCatching {
                java.net.URI(prov.mainUrl).host?.lowercase()?.removePrefix("www.")
            }.getOrNull()
            provHost != null && (targetHost == provHost || targetHost.endsWith(".$provHost") || provHost.endsWith(".$targetHost"))
        }
    }
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

        if (com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYoutubeResolver.isYoutubeChannelOrPlaylistUrl(targetUrl) ||
            (targetProvider.name.equals("YouTube", ignoreCase = true) && com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYoutubeResolver.isYoutubeUrl(targetUrl))
        ) {
            val ytdlLoaded = com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYoutubeResolver.resolve(targetUrl, fallbackName)
            if (ytdlLoaded != null) {
                com.lagradost.common.logging.AppLogger.i("Plugin:YouTube", "Successfully resolved via DesktopYoutubeResolver: title='${ytdlLoaded.name}', episodes=${(ytdlLoaded as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.episodes?.size ?: 0}")
                DetailsCache.put(url, ytdlLoaded)
                if (targetUrl != url) DetailsCache.put(targetUrl, ytdlLoaded)
                return ytdlLoaded
            }
        }

        // Dynamic domain routing: if targetUrl host belongs to another registered provider in APIHolder, auto-route
        val domainMatchedProvider = findProviderForUrl(targetUrl)
        if (domainMatchedProvider != null && domainMatchedProvider.name != targetProvider.name) {
            com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Auto-routing provider based on URL domain: ${targetProvider.name} -> ${domainMatchedProvider.name} for $targetUrl")
            targetProvider = domainMatchedProvider
        }

        // Meta-provider handling: if targetProvider is an aggregator/meta-provider whose mainUrl does not match targetUrl
        val isMetaProvider = targetProvider.providerType == com.lagradost.cloudstream3.ProviderType.MetaProvider ||
            targetProvider is com.lagradost.cloudstream3.metaproviders.TmdbProvider
        val targetHost = runCatching { java.net.URI(targetUrl).host?.lowercase()?.removePrefix("www.") }.getOrNull()
        val providerHost = runCatching { java.net.URI(targetProvider.mainUrl).host?.lowercase()?.removePrefix("www.") }.getOrNull()
        val isHostMismatch = isMetaProvider && targetHost != null && providerHost != null &&
            targetHost != providerHost && !targetHost.endsWith(".$providerHost") && !providerHost.endsWith(".$targetHost")

        if (isHostMismatch && !targetUrl.contains("themoviedb.org") && !targetUrl.contains("imdb.com")) {
            val effectiveTitle = fallbackName?.takeIf { it.isNotBlank() }
                ?: com.lagradost.cloudstream3.desktop.utils.TitleUtils.extractTitleFromUrlSlug(targetUrl).first
            if (effectiveTitle.isNotBlank()) {
                try {
                    com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Host mismatch on meta-provider ${targetProvider.name}. Resolving catalog ID for '$effectiveTitle'...")
                    val searchResults = SafePluginInvoker.invokeOrNull(
                        tag = "DetailsRepo:SearchMeta:${targetProvider.name}",
                        timeoutMs = 3000L,
                    ) { targetProvider.search(effectiveTitle, 1)?.items }
                    val metaMatch = searchResults?.find { it.name.equals(effectiveTitle, ignoreCase = true) } ?: searchResults?.firstOrNull()
                    if (metaMatch != null && metaMatch.url.isNotBlank()) {
                        com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Resolved canonical catalog URL for ${targetProvider.name}: $targetUrl -> ${metaMatch.url}")
                        targetUrl = metaMatch.url
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.w("[DetailsRepo] Failed to resolve meta catalog ID: ${e.message}")
                }
            }
        }

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
                                        } else {
                                            null
                                        }
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

        var lastError: Throwable? = null
        for (attempt in 0 until 3) {
            try {
                com.lagradost.common.logging.AppLogger.i("Plugin:${targetProvider.name}", "Fetching media details for: $targetUrl (attempt ${attempt + 1}/3)")
                val result = SafePluginInvoker.invoke(
                    tag = "DetailsRepo:Load:${targetProvider.name}",
                    timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                ) { targetProvider.load(targetUrl) }

                if (result.isSuccess) {
                    val loaded = result.getOrNull()
                    if (loaded != null) {
                        if (loaded.name.isBlank() && !fallbackName.isNullOrBlank()) {
                            com.lagradost.common.logging.AppLogger.w("Plugin:${targetProvider.name}", "Plugin returned blank title. Using fallbackName='$fallbackName'")
                            loaded.name = fallbackName
                        }
                        if (loaded.name.isBlank() || loaded.name.all { !it.isLetterOrDigit() }) {
                            val (slugTitle, slugYear) = com.lagradost.cloudstream3.desktop.utils.TitleUtils.extractTitleFromUrlSlug(targetUrl)
                            if (slugTitle.isNotBlank()) {
                                com.lagradost.common.logging.AppLogger.w("Plugin:${targetProvider.name}", "Plugin returned blank/invalid title. Extracted from slug: '$slugTitle' (year=$slugYear)")
                                loaded.name = slugTitle
                                if (loaded.year == null && slugYear != null) loaded.year = slugYear
                            }
                        }
                        com.lagradost.common.logging.AppLogger.i("Plugin:${targetProvider.name}", "Loaded details: title='${loaded.name}', type=${loaded.type}")
                        loaded.posterUrl = targetProvider.fixUrlNull(loaded.posterUrl)
                        loaded.backgroundPosterUrl = targetProvider.fixUrlNull(loaded.backgroundPosterUrl)
                        loaded.logoUrl = targetProvider.fixUrlNull(loaded.logoUrl)
                        if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                            if (loaded.episodes.isEmpty() && (targetProvider.name.equals("YouTube", ignoreCase = true) || com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYoutubeResolver.isYoutubeUrl(targetUrl))) {
                                com.lagradost.common.logging.AppLogger.w("Plugin:YouTube", "Plugin returned 0 episodes for YouTube series. Running DesktopYoutubeResolver fallback...")
                                val fallbackResponse = com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYoutubeResolver.resolve(targetUrl, loaded.name)
                                if (fallbackResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse && fallbackResponse.episodes.isNotEmpty()) {
                                    loaded.episodes = fallbackResponse.episodes
                                    if (loaded.posterUrl.isNullOrBlank()) loaded.posterUrl = fallbackResponse.posterUrl
                                    if (loaded.backgroundPosterUrl.isNullOrBlank()) loaded.backgroundPosterUrl = fallbackResponse.backgroundPosterUrl
                                }
                            }
                            loaded.episodes.forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                        } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                            loaded.episodes.values.flatten().forEach { ep -> ep.posterUrl = targetProvider.fixUrlNull(ep.posterUrl) }
                        }
                        if (loaded.url.isBlank()) loaded.url = targetUrl
                        DetailsCache.put(url, loaded)
                        if (targetUrl != url) DetailsCache.put(targetUrl, loaded)
                        return loaded
                    }
                } else {
                    lastError = result.exceptionOrNull()
                    val errMessage = lastError?.message ?: ""
                    if (errMessage.contains("No id found", ignoreCase = true) && attempt < 2) {
                        val effectiveTitle = fallbackName?.takeIf { it.isNotBlank() }
                            ?: com.lagradost.cloudstream3.desktop.utils.TitleUtils.extractTitleFromUrlSlug(targetUrl).first
                        if (effectiveTitle.isNotBlank()) {
                            val recoveryMatches = SafePluginInvoker.invokeOrNull(
                                tag = "DetailsRepo:RecoverySearch:${targetProvider.name}",
                                timeoutMs = 3000L,
                            ) { targetProvider.search(effectiveTitle, 1)?.items }
                            val recoveryMatch = recoveryMatches?.find { it.name.equals(effectiveTitle, ignoreCase = true) } ?: recoveryMatches?.firstOrNull()
                            if (recoveryMatch != null && recoveryMatch.url.isNotBlank() && recoveryMatch.url != targetUrl) {
                                com.lagradost.common.logging.AppLogger.i("[DetailsRepo] Dynamically recovered from 'No id found' on ${targetProvider.name}: $targetUrl -> ${recoveryMatch.url}")
                                targetUrl = recoveryMatch.url
                                continue
                            }
                        }
                    }
                    if (lastError is com.lagradost.cloudstream3.ErrorLoadingException ||
                        lastError is NotImplementedError ||
                        errMessage.contains("not implemented", ignoreCase = true) ||
                        errMessage.contains("GEO-LOCKED", ignoreCase = true) ||
                        errMessage.contains("not available in your region", ignoreCase = true) ||
                        errMessage.contains("VPN", ignoreCase = true)
                    ) {
                        break
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Always re-throw cancellation immediately
            } catch (e: Throwable) {
                lastError = e
                com.lagradost.common.logging.AppLogger.e("Plugin:${targetProvider.name}", "fetchRaw attempt ${attempt + 1}/3 failed for $targetUrl", e)
                if (attempt < 2) kotlinx.coroutines.delay(500L * (attempt + 1)) // 0.5s then 1s backoff
            }
        }

        val finalErrorMsg = lastError?.message?.takeIf { it.isNotBlank() } ?: "Server unreachable or returned empty response"
        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showPluginError(
            pluginName = targetProvider.name,
            action = "Details fetch",
            error = finalErrorMsg,
        )
        throw (lastError ?: RuntimeException(finalErrorMsg))
    }
}

// TmdbEnrichmentService extracted to TmdbEnrichmentService.kt
