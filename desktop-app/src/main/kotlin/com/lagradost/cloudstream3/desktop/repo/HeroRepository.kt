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
import kotlinx.coroutines.channels.awaitClose
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
)

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

    sealed interface HeroUpdate {
        data class Meta(val url: String, val meta: HeroMeta) : HeroUpdate
        data class ColorTarget(val url: String, val posterUrl: String) : HeroUpdate
    }

    private const val INITIAL_RETRY_DELAY = 1500L
    private const val SUBSEQUENT_RETRY_DELAY = 2000L
    private const val MAX_RETRIES = 3

    fun prefetchHeroItem(
        provider: MainAPI?,
        item: SearchResponse,
    ): kotlinx.coroutines.flow.Flow<HeroUpdate> = kotlinx.coroutines.flow.callbackFlow {
        val cacheKey = "${provider?.name}_${item.url}"
        val existing = HeroCache.get(cacheKey)
        if (existing != null) {
            trySend(HeroUpdate.Meta(item.url, existing))
            close()
            return@callbackFlow
        }

        if (!prefetchingUrls.add(cacheKey)) {
            close()
            return@callbackFlow
        }

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

                com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.enrich(dummy, "dummy_${item.url}", onScreenshotsLoaded = {})

                val backdropUrl = dummy.backgroundPosterUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                val logoUrl = dummy.logoUrl?.takeIf { it.isNotBlank() }?.let { provider.fixUrlNull(it) }
                val title = dummy.name.takeIf { it.isNotBlank() && it != dummyTitle } ?: cleanHeroTitle(item.name)
                val tags = dummy.tags?.take(4) ?: emptyList()
                val plot = dummy.plot?.take(200)
                val score = dummy.score?.toString()

                val meta = HeroMeta(title, backdropUrl, logoUrl, tags, plot, score, dummy.year, dummy.type, dummy.contentRating, dummy.duration)
                HeroCache.put(cacheKey, meta)
                trySend(HeroUpdate.Meta(item.url, meta))

                val colorTarget = backdropUrl ?: provider.fixUrlNull(item.posterUrl)
                if (colorTarget != null) trySend(HeroUpdate.ColorTarget(item.url, colorTarget))
            } else {
                val meta = HeroMeta(dummyTitle, null, null, emptyList(), null, null, null, null, null, null)
                HeroCache.put(cacheKey, meta)
                trySend(HeroUpdate.Meta(item.url, meta))
            }

            if (provider != null) {
                var details: com.lagradost.cloudstream3.LoadResponse? = null
                var attempt = 0
                while (attempt < MAX_RETRIES && details == null) {
                    try {
                        kotlinx.coroutines.delay(if (attempt == 0) INITIAL_RETRY_DELAY else SUBSEQUENT_RETRY_DELAY)
                        details = if (!DetailsCache.containsKey(item.url)) {
                            DetailsRepository.fetchRaw(provider, item.url)
                        } else {
                            DetailsCache.get(item.url)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        attempt++
                        if (attempt >= MAX_RETRIES) throw e
                    }
                }

                if (details != null) {
                    val currentMeta = HeroCache.get(cacheKey)
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
                    trySend(HeroUpdate.Meta(item.url, rawMeta))
                    if (newBackdrop != null) trySend(HeroUpdate.ColorTarget(item.url, newBackdrop))

                    com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.enrich(
                        loaded = details,
                        url = item.url,
                        onScreenshotsLoaded = {},
                        onEnrichmentComplete = {
                            val enrichedMeta = HeroCache.get(cacheKey) ?: rawMeta
                            val finalMeta = enrichedMeta.copy(
                                title = cleanHeroTitle(details.name).takeIf { it.isNotBlank() } ?: enrichedMeta.title,
                                backdropUrl = details.backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.backdropUrl,
                                logoUrl = details.logoUrl?.takeIf { it.isNotBlank() } ?: enrichedMeta.logoUrl,
                            )
                            HeroCache.put(cacheKey, finalMeta)
                            trySend(HeroUpdate.Meta(item.url, finalMeta))
                            if (finalMeta.backdropUrl != null) trySend(HeroUpdate.ColorTarget(item.url, finalMeta.backdropUrl))
                            close()
                        },
                    )
                } else {
                    close()
                }
            } else {
                close()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HeroCache.remove(cacheKey)
            close(e)
        } finally {
            prefetchingUrls.remove(cacheKey)
        }
        awaitClose { }
    }
}
