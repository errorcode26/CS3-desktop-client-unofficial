package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PlayerStreamScraper(
    private val scope: CoroutineScope,
) {
    private val probeSemaphore = Semaphore(3)
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

                if (provider.providerType == ProviderType.MetaProvider || provider.name.equals("Stremio", ignoreCase = true)) {
                    val resolvedImdbId = when {
                        nextEpId.startsWith("tt", ignoreCase = true) -> nextEpId.substringBefore(":")
                        currentData.history.episodeId?.startsWith("tt", ignoreCase = true) == true -> currentData.history.episodeId!!.substringBefore(":")
                        currentData.loadResponse?.syncData?.get("imdb")?.startsWith("tt", ignoreCase = true) == true -> currentData.loadResponse.syncData["imdb"]
                        currentData.loadResponse?.url?.startsWith("tt", ignoreCase = true) == true -> currentData.loadResponse.url.substringBefore(":")
                        else -> com.lagradost.cloudstream3.desktop.metadata.MetadataPipeline.getCachedImdbId(currentData.history.showName)
                    }
                    val parts = if (nextEpId.contains(":")) nextEpId.split(":") else emptyList()
                    val parsedSeason = parts.getOrNull(1)?.toIntOrNull()
                    val parsedEpisode = parts.getOrNull(2)?.toIntOrNull()
                    val epNumber = nextEp.episode ?: parsedEpisode ?: currentData.history.episode
                    val seasonNumber = nextEp.season ?: parsedSeason ?: currentData.history.season

                    StremioAddonManager.searchStreams(
                        imdbId = resolvedImdbId,
                        season = seasonNumber,
                        episode = epNumber,
                        title = currentData.history.showName,
                        onLink = { link -> collectedLinks.add(link) },
                    )
                } else {
                    provider.loadLinks(
                        data = nextEpId,
                        isCasting = false,
                        subtitleCallback = { sub -> collectedSubs.add(sub) },
                        callback = { link ->
                            collectedLinks.add(link)
                        },
                    )
                }

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

            // Probe range seekability for non-HLS/DASH streams in background
            if (!link.isM3u8 && !link.isDash && link.type != ExtractorLinkType.M3U8 && link.type != ExtractorLinkType.DASH) {
                scope.launch(Dispatchers.IO) {
                    val isSeekable = probeSemaphore.withPermit {
                        QualityDataHelper.probeRangeSeekability(link)
                    }
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

        val parts = if (targetEpisodeId.contains(":")) targetEpisodeId.split(":") else emptyList()
        val parsedSeason = parts.getOrNull(1)?.toIntOrNull()
        val parsedEpisode = parts.getOrNull(2)?.toIntOrNull()

        val epNumber = targetEpisodeData?.episode ?: parsedEpisode ?: currentLaunchData.history.episode
        val seasonNumber = targetEpisodeData?.season ?: parsedSeason ?: currentLaunchData.history.season

        if (provider.providerType == ProviderType.MetaProvider || provider.name.equals("Stremio", ignoreCase = true)) {
            AppLogger.i("PlayerStreamScraper", "Directly searching stream addons for MetaProvider: ${provider.name}")
            StremioAddonManager.searchStreams(
                imdbId = resolvedImdbId,
                season = seasonNumber,
                episode = epNumber,
                title = currentLaunchData.history.showName,
                onLink = { link -> sharedLinkCallback(link) },
            )
            return Result.success(Unit)
        }

        // Route YouTube streams directly to yt-dlp resolver
        val isYoutubeTarget = DesktopYtDlpBinary.isYouTubeUrl(targetEpisodeId) ||
            (provider.name.equals("YouTube", ignoreCase = true) && targetEpisodeId.isNotBlank())
        if (isYoutubeTarget) {
            val ytUrl = if (targetEpisodeId.startsWith("http")) targetEpisodeId else "https://www.youtube.com/watch?v=$targetEpisodeId"
            AppLogger.i("PlayerStreamScraper", "Routing YouTube episode to yt-dlp resolver: $ytUrl")
            val isLiveStream = currentLaunchData.loadResponse?.type == com.lagradost.cloudstream3.TvType.Live ||
                currentLaunchData.loadResponse?.tags?.contains("Live") == true ||
                targetEpisodeData?.name?.contains("Live", ignoreCase = true) == true
            val ytQualities = if (isLiveStream) {
                listOf(
                    Pair(0, "Live (Auto)"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P1080.value, "Live 1080p"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P720.value, "Live 720p"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P480.value, "Live 480p"),
                )
            } else {
                listOf(
                    Pair(0, "Auto"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P1080.value, "1080p"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P720.value, "720p"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P480.value, "480p"),
                    Pair(com.lagradost.cloudstream3.utils.Qualities.P360.value, "360p"),
                )
            }
            ytQualities.forEach { (qVal, label) ->
                val qFrag = if (qVal > 0) "#q=$qVal" else "#q=auto"
                sharedLinkCallback(
                    newExtractorLink(
                        source = provider.name,
                        name = "${provider.name} ($label)",
                        url = "$ytUrl$qFrag",
                    ) {
                        this.extractorData = "yt-dlp"
                        this.quality = qVal
                    }
                )
            }
            return Result.success(Unit)
        }

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
