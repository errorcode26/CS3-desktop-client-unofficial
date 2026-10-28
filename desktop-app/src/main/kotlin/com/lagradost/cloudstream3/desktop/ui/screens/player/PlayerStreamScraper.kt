package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxLibraryCache
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.subtitles.SubsourceSubtitleProvider
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
import kotlinx.coroutines.coroutineScope
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

        if (apiName.equals("Torbox", ignoreCase = true)) {
            lastPreScrapedEpisodeId = nextEpId
            preScrapeJob = scope.launch(Dispatchers.IO) {
                try {
                    val apiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)?.trim().orEmpty()
                    if (apiKey.isBlank()) return@launch
                    val fileId = nextEp.data.toIntOrNull()
                    val parentId = currentData.history.parentId.orEmpty()
                    val parentParts = parentId.split("_")
                    val itemId = parentParts.getOrNull(2)?.toIntOrNull()
                    val item = if (itemId != null) TorboxLibraryCache.findItem(itemId) else null
                    val streamUrl = if (item != null) {
                        TorboxApiClient.requestDownloadLink(apiKey, item, fileId)
                    } else if (itemId != null) {
                        TorboxApiClient.requestDownloadLink(apiKey, itemId, fileId)
                    } else {
                        null
                    }

                    if (!streamUrl.isNullOrBlank()) {
                        val file = item?.files?.find { it.id == fileId }
                        val videoTitle = file?.name?.takeIf { it.isNotBlank() } ?: nextEp.name ?: "Next Episode"
                        val effectiveBytes = file?.size ?: 0L
                        val sizeStr = if (effectiveBytes > 0L) {
                            val gb = effectiveBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            if (gb >= 1.0) {
                                String.format(java.util.Locale.US, "%.1f GB", gb)
                            } else {
                                String.format(java.util.Locale.US, "%.0f MB", effectiveBytes.toDouble() / (1024.0 * 1024.0))
                            }
                        } else {
                            null
                        }
                        val sizeTag = if (sizeStr != null) " [$sizeStr]" else ""
                        val extLink = newExtractorLink(
                            source = "Torbox ${item?.storageType?.label ?: "Cloud"}",
                            name = videoTitle + sizeTag,
                            url = streamUrl,
                            type = if (streamUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            this.referer = ""
                        }
                        LinkCache.set(nextEpId, listOf(extLink), emptyList())
                        AppLogger.i("PlayerStreamScraper", "Pre-scraped Torbox stream for next episode: $videoTitle")
                    }
                } catch (e: Exception) {
                    AppLogger.d("PlayerStreamScraper", "Torbox background pre-scrape error: ${e.message}")
                }
            }
            return
        }

        val provider = apiName?.let { APIHolder.getApiFromNameNull(it) } ?: return

        lastPreScrapedEpisodeId = nextEpId
        preScrapeJob = scope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("PlayerStreamScraper", "Starting background pre-scrape for next episode: ${nextEp.name ?: nextEp.data}")
                val collectedLinks = mutableListOf<ExtractorLink>()
                val collectedSubs = mutableListOf<SubtitleFile>()

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
                val isSeries = currentData.loadResponse?.type == com.lagradost.cloudstream3.TvType.TvSeries ||
                    currentData.loadResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse ||
                    seasonNumber != null || epNumber != null

                if (provider.providerType == ProviderType.MetaProvider || provider.name.equals("Stremio", ignoreCase = true)) {
                    StremioAddonManager.searchStreams(
                        imdbId = resolvedImdbId,
                        season = seasonNumber,
                        episode = epNumber,
                        title = currentData.history.showName,
                        mediaId = nextEpId,
                        isSeries = isSeries,
                        onSubtitle = { sub -> collectedSubs.add(sub) },
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

                SubsourceSubtitleProvider.searchAndCollectSubtitles(
                    title = currentData.history.showName,
                    imdbId = resolvedImdbId,
                    season = seasonNumber,
                    episode = epNumber,
                    year = currentData.loadResponse?.year,
                    isSeries = isSeries,
                    onSubtitle = { sub -> collectedSubs.add(sub) },
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
        val isSeries = currentLaunchData.loadResponse?.type == com.lagradost.cloudstream3.TvType.TvSeries ||
            currentLaunchData.loadResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse ||
            seasonNumber != null || epNumber != null

        if (provider.providerType == ProviderType.MetaProvider || provider.name.equals("Stremio", ignoreCase = true)) {
            val enabledAddons = StremioAddonManager.getEnabledStreamAddons()
            if (enabledAddons.isEmpty()) {
                AppLogger.w("PlayerStreamScraper", "No Stremio stream addons configured. Aborting scraper.")
                return Result.failure(IllegalStateException("No stream addons installed. Please configure a streaming addon in Settings."))
            }
            AppLogger.i("PlayerStreamScraper", "Directly searching stream addons (${enabledAddons.size}) for MetaProvider: ${provider.name}")
            scope.launch(Dispatchers.IO) {
                SubsourceSubtitleProvider.searchAndCollectSubtitles(
                    title = currentLaunchData.history.showName,
                    imdbId = resolvedImdbId,
                    season = seasonNumber,
                    episode = epNumber,
                    year = currentLaunchData.loadResponse?.year,
                    isSeries = isSeries,
                    onSubtitle = { sub -> sharedSubtitleCallback(sub) },
                )
            }
            StremioAddonManager.searchStreams(
                imdbId = resolvedImdbId,
                season = seasonNumber,
                episode = epNumber,
                title = currentLaunchData.history.showName,
                mediaId = targetEpisodeId,
                isSeries = isSeries,
                onSubtitle = { sub -> sharedSubtitleCallback(sub) },
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
                    },
                )
            }
            return Result.success(Unit)
        }

        val hasEmittedAnyLink = java.util.concurrent.atomic.AtomicBoolean(false)
        val wrappedLinkCallback = SafePluginInvoker.wrapCallback("LinkCallback") { link: ExtractorLink ->
            hasEmittedAnyLink.set(true)
            sharedLinkCallback(link)
        }

        return coroutineScope {
            launch(Dispatchers.IO) {
                try {
                    SubsourceSubtitleProvider.searchAndCollectSubtitles(
                        title = currentLaunchData.history.showName,
                        imdbId = resolvedImdbId,
                        season = seasonNumber,
                        episode = epNumber,
                        year = currentLaunchData.loadResponse?.year,
                        isSeries = isSeries,
                        onSubtitle = { sub -> sharedSubtitleCallback(sub) },
                    )
                } catch (t: Throwable) {
                    AppLogger.d("PlayerStreamScraper", "Subtitle collection encountered: ${t.message}")
                }
            }

            val addonJob = launch(Dispatchers.IO) {
                try {
                    StremioAddonManager.searchStreams(
                        imdbId = resolvedImdbId,
                        season = seasonNumber,
                        episode = epNumber,
                        title = currentLaunchData.history.showName,
                        mediaId = targetEpisodeId,
                        isSeries = isSeries,
                        onSubtitle = { sub -> sharedSubtitleCallback(sub) },
                        onLink = { link -> wrappedLinkCallback(link) },
                    )
                } catch (t: Throwable) {
                    AppLogger.d("PlayerStreamScraper", "Addon stream search encountered: ${t.message}")
                }
            }

            // Detect degenerate episode data payloads — these indicate the plugin's load() failed to
            // extract embed URLs. Failing fast here surfaces the Exhausted state immediately rather
            // than calling loadLinks with garbage and silently returning 0 results.
            val isDegenerate = targetEpisodeId.isBlank() ||
                targetEpisodeId == "[]" ||
                targetEpisodeId == "{}" ||
                targetEpisodeId == "null"

            val providerResult = if (isDegenerate) {
                AppLogger.w("Plugin:${provider.name}", "Episode data payload is degenerate ('$targetEpisodeId'). Plugin's load() likely failed to extract embed URLs. Aborting scrape.")
                Result.failure(IllegalStateException("Provider returned empty episode data. The plugin could not extract stream URLs from the source page."))
            } else {
                SafePluginInvoker.invoke(
                    tag = "PlayerStreamScraper:${provider.name}",
                    providerName = provider.name,
                    timeoutMs = SafePluginInvoker.TIMEOUT_SCRAPE_MS,
                    penalizeOnTimeout = false,
                    maxParallelism = 2,
                ) {
                    AppLogger.i("Plugin:${provider.name}", "Scraping streams for episode: $targetEpisodeId (autoPlay=$autoPlay)")
                    provider.loadLinks(
                        data = targetEpisodeId,
                        isCasting = false,
                        subtitleCallback = sharedSubtitleCallback,
                        callback = wrappedLinkCallback,
                    )
                }
            }

            addonJob.join()

            if (providerResult.isFailure && hasEmittedAnyLink.get()) {
                AppLogger.i("PlayerStreamScraper", "Primary provider scrape failed (${providerResult.exceptionOrNull()?.message}), but fallback streams were collected. Marking scrape as success.")
                Result.success(Unit)
            } else {
                providerResult.map { }
            }
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
