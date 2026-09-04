package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerStreamScraper(
    private val scope: CoroutineScope,
) {
    private var preScrapeJob: Job? = null
    private var lastPreScrapedEpisodeId: String? = null

    val isPreScrapeActive: Boolean
        get() = preScrapeJob?.isActive == true

    fun cancelPreScrape() {
        preScrapeJob?.cancel()
        preScrapeJob = null
    }

    fun preScrapeNextEpisode(
        nextEp: Episode,
        currentLaunchData: VideoLaunchData?,
    ) {
        val nextEpId = nextEp.data
        if (preScrapeJob?.isActive == true || lastPreScrapedEpisodeId == nextEpId) return
        if (LinkCache.get(nextEpId) != null) return

        val currentData = currentLaunchData ?: return
        val apiName = currentData.history.apiName.takeIf { it.isNotBlank() } ?: currentData.loadResponse?.apiName
        val provider = apiName?.let { APIHolder.getApiFromNameNull(it) } ?: return

        lastPreScrapedEpisodeId = nextEpId
        preScrapeJob = scope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("PlayerStreamScraper", "Starting background pre-scrape for next episode: ${nextEp.name ?: nextEp.data}")
                val collectedLinks = mutableListOf<ExtractorLink>()
                val collectedSubs = mutableListOf<SubtitleFile>()

                provider.loadLinks(
                    data = nextEpId,
                    isCasting = false,
                    subtitleCallback = { sub -> collectedSubs.add(sub) },
                    callback = { link ->
                        collectedLinks.add(link)
                        QualityDataHelper.registerDiscoveredSource(link.source)
                    },
                )

                if (collectedLinks.isNotEmpty()) {
                    val sorted = QualityDataHelper.sortLinks(collectedLinks)
                    LinkCache.set(nextEpId, sorted, collectedSubs)
                    AppLogger.i("PlayerStreamScraper", "Background pre-scrape complete: cached ${sorted.size} streams for ${nextEp.name ?: nextEp.data}")
                }
            } catch (e: Throwable) {
                AppLogger.w("PlayerStreamScraper", "Background pre-scrape error: ${e.message}")
            }
        }
    }

    suspend fun executeScrape(
        provider: MainAPI,
        targetEpisodeId: String,
        autoPlay: Boolean,
        currentLaunchData: VideoLaunchData,
        targetEpisodeData: Episode?,
        onSubtitle: (SubtitleFile) -> Unit,
        onLink: (ExtractorLink) -> Unit,
        onSeekableConfirmed: (ExtractorLink) -> Unit,
    ): Result<Unit> {
        val sharedSubtitleCallback = SafePluginInvoker.wrapCallback("SubtitleCallback") { sub: SubtitleFile ->
            val cleanUrl = sub.url.trim()
            if (cleanUrl.isNotBlank()) {
                val cleanSub = sub.copy(url = cleanUrl, lang = sub.lang.trim())
                AppLogger.i("Plugin:${provider.name}", "Extracted subtitle: [${cleanSub.lang}] ${cleanSub.url}")
                onSubtitle(cleanSub)
            }
        }

        val sharedLinkCallback = SafePluginInvoker.wrapCallback("LinkCallback") { link: ExtractorLink ->
            AppLogger.i("Plugin:${provider.name}", "Extracted link: ${link.name} (quality=${link.quality}) -> ${link.url}")
            QualityDataHelper.registerDiscoveredSource(link.source)

            // Probe range seekability for non-HLS/DASH streams in background
            if (!link.isM3u8 && !link.isDash && link.type != ExtractorLinkType.M3U8 && link.type != ExtractorLinkType.DASH) {
                scope.launch(Dispatchers.IO) {
                    val isSeekable = QualityDataHelper.probeRangeSeekability(link)
                    if (isSeekable) {
                        onSeekableConfirmed(link)
                    }
                }
            }

            onLink(link)
        }

        // Concurrently query installed Stremio stream addons
        val epHistoryId = currentLaunchData.history.episodeId
        val loadResp = currentLaunchData.loadResponse
        val resolvedImdbId = when {
            targetEpisodeId.startsWith("tt", ignoreCase = true) -> targetEpisodeId.substringBefore(":")
            epHistoryId?.startsWith("tt", ignoreCase = true) == true -> epHistoryId.substringBefore(":")
            loadResp?.syncData?.get("imdb")?.startsWith("tt", ignoreCase = true) == true -> loadResp.syncData["imdb"]
            loadResp?.url?.startsWith("tt", ignoreCase = true) == true -> loadResp.url.substringBefore(":")
            else -> com.lagradost.cloudstream3.desktop.metadata.MetadataPipeline.getCachedImdbId(currentLaunchData.history.showName)
        }

        val epNumber = currentLaunchData.history.episode ?: targetEpisodeData?.episode
        val seasonNumber = currentLaunchData.history.season ?: targetEpisodeData?.season
        scope.launch(Dispatchers.IO) {
            StremioAddonManager.searchStreams(
                imdbId = resolvedImdbId,
                season = seasonNumber,
                episode = epNumber,
                title = currentLaunchData.history.showName,
                onLink = { link -> sharedLinkCallback(link) },
            )
        }

        return SafePluginInvoker.invoke(
            tag = "PlayerStreamScraper:${provider.name}",
            providerName = provider.name,
            timeoutMs = SafePluginInvoker.TIMEOUT_SCRAPE_MS,
            penalizeOnTimeout = false,
        ) {
            AppLogger.i("Plugin:${provider.name}", "Scraping streams for episode: $targetEpisodeId (autoPlay=$autoPlay)")
            provider.loadLinks(
                data = targetEpisodeId,
                isCasting = false,
                subtitleCallback = sharedSubtitleCallback,
                callback = sharedLinkCallback,
            )
        }
    }

    suspend fun attemptSelfHealing(
        provider: MainAPI,
        targetEpisodeId: String,
        showUrl: String,
    ): Pair<String, MovieLoadResponse>? {
        val parentId = DesktopDataStore.watchHistoryId(provider.name, showUrl)
        DesktopDataStore.removeEpisodeWatched(parentId, targetEpisodeId)
        return try {
            val freshResp = SafePluginInvoker.invokeOrNull(
                tag = "SelfHealing:${provider.name}",
                timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
            ) {
                provider.load(showUrl)
            }
            if (freshResp is MovieLoadResponse && freshResp.dataUrl.isNotBlank() && freshResp.dataUrl != targetEpisodeId) {
                AppLogger.i("Plugin:${provider.name}", "Self-healing resolved valid movie data payload. Retrying scraping...")
                freshResp.dataUrl to freshResp
            } else {
                null
            }
        } catch (healEx: Throwable) {
            AppLogger.e("Plugin:${provider.name}", "Self-healing re-fetch failed: ${healEx.message}")
            null
        }
    }
}
