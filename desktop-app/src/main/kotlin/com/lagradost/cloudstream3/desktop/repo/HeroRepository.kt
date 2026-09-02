package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCache
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRepository
import com.lagradost.cloudstream3.desktop.ui.screens.details.HybridEnrichmentService
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class HeroMeta(
    val title: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val tags: List<String>?,
    val plot: String?,
    val score: String?,
    val year: Int?,
    val type: com.lagradost.cloudstream3.TvType?,
    val contentRating: String?,
    val duration: Int?,
    val cachedAtMs: Long = 0L,
)

private const val HERO_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

object HeroCache {
    private val cache = ConcurrentHashMap<String, HeroMeta>()
    fun get(key: String): HeroMeta? = cache[key]
    fun put(key: String, meta: HeroMeta) {
        cache[key] = meta
    }
    fun remove(key: String) {
        cache.remove(key)
    }
}

object HeroRepository {
    private val prefetchingUrls = ConcurrentHashMap.newKeySet<String>()

    // Limit concurrent background TMDB/Cinemeta enrichment calls.
    private val backgroundSemaphore = Semaphore(3)

    /** Delegates to the shared TitleUtils cleaner. Kept for call-sites in Composables. */
    fun cleanHeroTitle(title: String): String = TitleUtils.cleanProviderTitle(title).first

    suspend fun prefetchTopHistory(topHistory: List<WatchHistory>, providers: List<MainAPI>) {
        withContext(Dispatchers.IO) {
            for (history in topHistory) {
                val provider = providers.find { it.name == history.apiName }
                val cacheKey = "${history.apiName}_${history.showUrl}"
                if (provider != null && !DetailsCache.containsKey(history.showUrl) && HeroCache.get(cacheKey) == null) {
                    if (!prefetchingUrls.add(cacheKey)) continue
                    try {
                        val raw = DetailsRepository.fetchRaw(provider, history.showUrl)
                        if (raw != null) {
                            HybridEnrichmentService.enrich(raw, history.showUrl, fetchCast = false, onScreenshotsLoaded = {})
                        }
                    } catch (e: Exception) {
                        AppLogger.e("HeroRepository", "Failed to prefetch history item", e)
                    } finally {
                        prefetchingUrls.remove(cacheKey)
                    }
                }
            }
        }
    }

    sealed interface HeroUpdate {
        data class Meta(val url: String, val meta: HeroMeta) : HeroUpdate
        data class ColorTarget(val url: String, val posterUrl: String) : HeroUpdate
    }

    // Fetch only lightweight TMDB/Cinemeta metadata for hero display.
    // provider.load() (full scrape) is intentionally NOT called here — only on details open.
    fun prefetchHeroItem(
        provider: MainAPI?,
        item: SearchResponse,
    ): kotlinx.coroutines.flow.Flow<HeroUpdate> = kotlinx.coroutines.flow.callbackFlow {
        val cacheKey = "${provider?.name}_${item.url}"
        var existing = HeroCache.get(cacheKey)
        if (existing == null) {
            val persisted = com.lagradost.common.storage.DesktopDataStore.getKey<HeroMeta>("herometa_$cacheKey")
            // Discard stale entries so corrected enrichment logic takes effect after the TTL
            if (persisted != null && (System.currentTimeMillis() - persisted.cachedAtMs) < HERO_CACHE_TTL_MS) {
                existing = persisted
                HeroCache.put(cacheKey, existing)
            } else if (persisted != null) {
                // Expired — remove from disk so it gets re-fetched
                com.lagradost.common.storage.DesktopDataStore.removeKey("herometa_$cacheKey")
            }
        }

        if (existing != null) {
            trySend(HeroUpdate.Meta(item.url, existing))
            val colorTarget = existing.backdropUrl ?: provider?.fixUrlNull(item.posterUrl)
            if (colorTarget != null) trySend(HeroUpdate.ColorTarget(item.url, colorTarget))
            close()
            return@callbackFlow
        }

        if (!prefetchingUrls.add(cacheKey)) {
            close()
            return@callbackFlow
        }

        try {
            backgroundSemaphore.withPermit {
                val isSeriesPattern = Regex("""(?i)\b(?:season|series|s\d{1,2}|episodes?|complete|all-episodes|web-series|tv-series)\b""").containsMatchIn(item.name)
                    || Regex("""(?i)\b(?:season|series|s\d{1,2}|episodes?|all-episodes|web-series|tv-series)\b""").containsMatchIn(item.url)
                val isAnime = item.type == com.lagradost.cloudstream3.TvType.Anime || item.type == com.lagradost.cloudstream3.TvType.AnimeMovie
                val actualType = when {
                    isAnime -> com.lagradost.cloudstream3.TvType.Anime
                    isSeriesPattern -> com.lagradost.cloudstream3.TvType.TvSeries
                    item.type != null && item.type != com.lagradost.cloudstream3.TvType.Movie -> item.type!!
                    else -> com.lagradost.cloudstream3.TvType.Movie
                }

                val (dummyTitle, parsedYear) = TitleUtils.cleanProviderTitle(item.name)
                AppLogger.i("Enrichment", "[HERO] prefetch | raw='${item.name}' | clean='$dummyTitle' | type=$actualType | url=${item.url}")

                if (provider != null) {
                    val dummy =
                        provider.newMovieLoadResponse(
                            name = item.name,
                            url = item.url,
                            type = actualType,
                            dataUrl = item.url,
                        ) {
                            this.posterUrl = item.posterUrl
                            if (this.year == null && parsedYear != null) this.year = parsedYear
                        }

                    com.lagradost.cloudstream3.desktop.ui.screens.details.HybridEnrichmentService.enrich(
                        loaded = dummy,
                        url = "dummy_${item.url}",
                        fetchCast = false,
                        onScreenshotsLoaded = {},
                    )

                    val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle && it != item.name } ?: dummyTitle
                    val tags = dummy.tags?.take(4) ?: emptyList()
                    val plot = dummy.plot?.take(200)
                    val score = dummy.score?.toString()

                    val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type, dummy.contentRating, dummy.duration, cachedAtMs = System.currentTimeMillis())
                    AppLogger.i("Enrichment", "[HERO] RESULT | title='$title' | backdrop=${backdropUrl != null} | logo=${logoUrl != null} | tags=$tags | score=$score")
                    HeroCache.put(cacheKey, meta)
                    com.lagradost.common.storage.DesktopDataStore.setKey("herometa_$cacheKey", meta)
                    trySend(HeroUpdate.Meta(item.url, meta))

                    val colorTarget = backdropUrl ?: provider.fixUrlNull(item.posterUrl)
                    if (colorTarget != null) trySend(HeroUpdate.ColorTarget(item.url, colorTarget))
                } else {
                    val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null, null, null)
                    HeroCache.put(cacheKey, meta)
                    trySend(HeroUpdate.Meta(item.url, meta))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HeroCache.remove(cacheKey)
            AppLogger.e("Enrichment", "[HERO] CRASHED for '${item.name}' — ${e::class.simpleName}: ${e.message}", e)
        } finally {
            prefetchingUrls.remove(cacheKey)
        }
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}
