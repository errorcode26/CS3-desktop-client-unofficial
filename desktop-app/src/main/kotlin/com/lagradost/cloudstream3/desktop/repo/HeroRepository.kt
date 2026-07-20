package com.lagradost.cloudstream3.desktop.repo

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsCache
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsRepository
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.desktop.utils.ImageColorExtractor
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
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
    val duration: Int?
)

object HeroCache {
    private val cache = ConcurrentHashMap<String, HeroMeta>()
    fun get(key: String): HeroMeta? = cache[key]
    fun put(key: String, meta: HeroMeta) { cache[key] = meta }
    fun remove(key: String) { cache.remove(key) }
}

object HeroRepository {
    private val prefetchingUrls = ConcurrentHashMap.newKeySet<String>()

    fun cleanHeroTitle(title: String): String {
        return title.replace(Regex("(?i)\\s*(?:tv|season|episode|\\d+).*$"), "").trim()
    }

    suspend fun getHeroColor(imageUrl: String): Color? {
        return ImageColorExtractor.getCachedColor(imageUrl)
            ?: withContext(Dispatchers.IO) { ImageColorExtractor.extractDominantColorFromUrl(imageUrl) }
    }
    
    suspend fun prefetchTopHistory(topHistory: List<WatchHistory>, providers: List<MainAPI>) {
        withContext(Dispatchers.IO) {
            for (history in topHistory) {
                val provider = providers.find { it.name == history.apiName }
                if (provider != null && !DetailsCache.containsKey(history.showUrl)) {
                    try {
                        val raw = DetailsRepository.fetchRaw(provider, history.showUrl)
                        if (raw != null) {
                            TmdbEnrichmentService.enrich(raw, history.showUrl, onScreenshotsLoaded = {})
                        }
                    } catch (e: Exception) {
                        AppLogger.e("HeroRepository", "Failed to prefetch history item", e)
                    }
                }
            }
        }
    }

    suspend fun prefetchHeroItem(
        provider: MainAPI?, 
        item: SearchResponse,
        currentMetaMap: Map<String, HeroMeta>,
        onMetaUpdate: (String, HeroMeta) -> Unit,
        onColorUpdate: (String, String) -> Unit
    ) {
        val cacheKey = "${provider?.name}_${item.url}"
        if (currentMetaMap.containsKey(item.url)) return

        val existing = HeroCache.get(cacheKey)
        if (existing != null) {
            onMetaUpdate(item.url, existing)
            return
        }

        if (!prefetchingUrls.add(cacheKey)) return

        withContext(Dispatchers.IO) {
            try {
                val dummyTitle = cleanHeroTitle(item.name)

                if (provider != null) {
                    val dummy = provider.newMovieLoadResponse(
                        name = dummyTitle,
                        url = item.url,
                        type = com.lagradost.cloudstream3.TvType.Movie,
                        dataUrl = item.url,
                    ) {
                        this.posterUrl = item.posterUrl
                    }

                    TmdbEnrichmentService.enrich(dummy, "dummy_${item.url}", onScreenshotsLoaded = {})

                    val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                    val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle } ?: cleanHeroTitle(item.name)
                    val tags = dummy.tags?.take(4) ?: emptyList()
                    val plot = dummy.plot?.take(200)
                    val score = dummy.score?.toString()

                    val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type, dummy.contentRating, dummy.duration)
                    HeroCache.put(cacheKey, meta)
                    onMetaUpdate(item.url, meta)
                    
                    val colorTarget = backdropUrl ?: provider.fixUrlNull(item.posterUrl)
                    if (colorTarget != null) onColorUpdate(item.url, colorTarget)
                } else {
                    val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null, null, null)
                    HeroCache.put(cacheKey, meta)
                    onMetaUpdate(item.url, meta)
                }

                if (provider != null) {
                    var details: com.lagradost.cloudstream3.LoadResponse? = null
                    var attempt = 0
                    while (attempt < 3 && details == null) {
                        try {
                            kotlinx.coroutines.delay(if (attempt == 0) 1500L else 2000L)
                            details = if (!DetailsCache.containsKey(item.url)) {
                                DetailsRepository.fetchRaw(provider, item.url)
                            } else {
                                DetailsCache.get(item.url)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            attempt++
                            if (attempt >= 3) throw e
                        }
                    }

                    if (details != null) {
                        val currentMeta = currentMetaMap[item.url]
                        val cleanDetailsName = cleanHeroTitle(details.name)
                        val newTitle = cleanDetailsName.takeIf { it.isNotBlank() } ?: currentMeta?.title
                        val newBackdrop = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.backdropUrl
                        val newLogo = details.logoUrl?.takeIf { it.isNotBlank() } ?: currentMeta?.logoUrl
                        val newTags = details.tags?.takeIf { it.isNotEmpty() } ?: currentMeta?.tags ?: emptyList()
                        val newPlot = details.plot?.takeIf { it.isNotBlank() } ?: currentMeta?.plot
                        val newScore = details.score?.toString() ?: currentMeta?.score
                        val newYear = details.year ?: currentMeta?.year
                        val newType = details.type ?: currentMeta?.type
                        val newContentRating = details.contentRating?.takeIf { it.isNotBlank() } ?: currentMeta?.contentRating
                        val newDuration = details.duration ?: currentMeta?.duration

                        val rawMeta = HeroMeta(newTitle, newBackdrop, newLogo, newTags, newPlot, newScore, newYear, newType, newContentRating, newDuration)
                        HeroCache.put(cacheKey, rawMeta)
                        onMetaUpdate(item.url, rawMeta)
                        if (newBackdrop != null) onColorUpdate(item.url, newBackdrop)

                        TmdbEnrichmentService.enrich(
                            loaded = details,
                            url = item.url,
                            onScreenshotsLoaded = {},
                            onEnrichmentComplete = {
                                val enrichedMeta = currentMetaMap[item.url] ?: rawMeta
                                val finalMeta = enrichedMeta.copy(
                                    title = cleanHeroTitle(details.name).takeIf { it.isNotBlank() } ?: enrichedMeta.title,
                                    backdropUrl = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.backdropUrl,
                                    logoUrl = details.logoUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.logoUrl,
                                )
                                HeroCache.put(cacheKey, finalMeta)
                                onMetaUpdate(item.url, finalMeta)
                                if (finalMeta.backdropUrl != null) onColorUpdate(item.url, finalMeta.backdropUrl)
                            },
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                HeroCache.remove(cacheKey)
            } finally {
                prefetchingUrls.remove(cacheKey)
            }
        }
    }
}
