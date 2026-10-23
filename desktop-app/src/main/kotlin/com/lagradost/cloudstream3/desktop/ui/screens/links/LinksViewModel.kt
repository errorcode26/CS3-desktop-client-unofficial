package com.lagradost.cloudstream3.desktop.ui.screens.links

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

class LinksViewModel : BaseMviViewModel<LinksUiState, LinksUiEvent, LinksUiEffect>(
    initialState = LinksUiState(
        preferredPlayer = "mpv",
        autoPlayEnabled = true,
    ),
) {
    private var scrapeJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefPlayer = DesktopDataStore.getKey<String>("preferred_player") ?: "mpv"
            val autoPlay = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            val p2p = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: false
            updateState { copy(preferredPlayer = prefPlayer, autoPlayEnabled = autoPlay, isP2pEnabled = p2p) }
        }
    }

    override fun handleEvent(event: LinksUiEvent) {
        when (event) {
            is LinksUiEvent.OnScrape -> scrapeLinks(
                provider = event.provider,
                dataUrl = event.dataUrl,
                title = event.title,
                forceRefresh = event.forceRefresh,
                isSeriesParam = event.isSeries,
                seasonParam = event.season,
                episodeParam = event.episode,
            )
            is LinksUiEvent.OnCancelScrape -> cancelScrape()
            is LinksUiEvent.OnStatusTextChanged -> updateState { copy(statusText = event.text) }
            is LinksUiEvent.OnSaveWatchPosition -> saveWatchPosition(event.history, event.positionMs, event.durationMs)
            is LinksUiEvent.OnPreferredPlayerChanged -> {
                updateState { copy(preferredPlayer = event.player) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey("preferred_player", event.player)
                }
            }
            is LinksUiEvent.OnAddLinks -> {
                updateState {
                    val combined = (links + event.links).distinctBy { it.url }
                    val sorted = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.sortLinks(combined)
                    copy(links = sorted, statusText = "Ready — ${sorted.size} stream${if (sorted.size == 1) "" else "s"} available.")
                }
            }
            is LinksUiEvent.OnPlayLink -> handlePlayLink(event)
            is LinksUiEvent.OnFilterQuality -> updateState { copy(selectedQuality = event.quality) }
            is LinksUiEvent.OnFilterFormat -> updateState { copy(selectedFormat = event.format) }
            is LinksUiEvent.OnFilterSource -> updateState { copy(selectedSource = event.source) }
            is LinksUiEvent.OnPlayerLaunchFinished -> updateState {
                copy(
                    isLaunchingPlayer = false,
                    currentPlayingUrl = null,
                    playerLaunchError = event.error,
                )
            }
            is LinksUiEvent.OnP2pEnabledChanged -> {
                updateState { copy(isP2pEnabled = event.enabled) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(DesktopDataStore.PREF_P2P_ENABLED, event.enabled)
                }
            }
            is LinksUiEvent.OnSetEmbeddedError -> updateState { copy(embeddedError = event.error) }
            is LinksUiEvent.OnSetLinkToDownload -> updateState { copy(linkToDownload = event.link) }
            is LinksUiEvent.OnUpdateVlcSavedPosition -> updateState { copy(lastVlcSavedPositionSec = event.posSec) }
        }
    }

    private fun scrapeLinks(
        provider: MainAPI,
        dataUrl: String,
        title: String? = null,
        forceRefresh: Boolean = false,
        isSeriesParam: Boolean? = null,
        seasonParam: Int? = null,
        episodeParam: Int? = null,
    ) {
        scrapeJob?.cancel()

        val parts = if (dataUrl.contains(":")) dataUrl.split(":") else emptyList()
        val cleanImdb = if (dataUrl.startsWith("tt", ignoreCase = true)) parts.firstOrNull() else null
        val isSeriesEpisode = dataUrl.contains(":") || seasonParam != null || episodeParam != null || isSeriesParam == true
        val cacheKeys = buildList {
            add(dataUrl)
            // Only attach show-level IMDb ID as a cache key if this is a standalone Movie (never for series episodes)
            if (!isSeriesEpisode && cleanImdb != null) {
                add(cleanImdb)
            }
        }.distinct()

        if (!forceRefresh) {
            val cached = cacheKeys.firstNotNullOfOrNull { com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.get(it) }
            if (cached != null && cached.links.isNotEmpty()) {
                AppLogger.i("LinksViewModel", "Restored ${cached.links.size} streams from LinkCache for $dataUrl")
                updateState {
                    copy(
                        links = cached.links,
                        subtitles = cached.subtitles,
                        isScraping = false,
                        statusText = "Ready — ${cached.links.size} stream${if (cached.links.size == 1) "" else "s"} available.",
                        selectedSource = null,
                    )
                }
                return
            }
        } else {
            cacheKeys.forEach { com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.remove(it) }
        }

        updateState {
            copy(
                links = emptyList(),
                subtitles = emptyList(),
                isScraping = true,
                statusText = "Finding streams for you...",
                selectedSource = null,
            )
        }

        scrapeJob = viewModelScope.launch {
            val pendingLinks = ConcurrentLinkedQueue<ExtractorLink>()
            val pendingSubs = ConcurrentLinkedQueue<SubtitleFile>()

            suspend fun flushBatch() = withContext(Dispatchers.Default) {
                if (pendingLinks.isEmpty() && pendingSubs.isEmpty()) return@withContext

                val drainedLinks = mutableListOf<ExtractorLink>()
                while (true) {
                    val link = pendingLinks.poll() ?: break
                    drainedLinks.add(link)
                }

                val drainedSubs = mutableListOf<SubtitleFile>()
                while (true) {
                    val sub = pendingSubs.poll() ?: break
                    drainedSubs.add(sub)
                }

                if (drainedLinks.isEmpty() && drainedSubs.isEmpty()) return@withContext

                if (drainedLinks.isNotEmpty()) {
                    com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.addLinks(cacheKeys, drainedLinks)
                }
                if (drainedSubs.isNotEmpty()) {
                    com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.addLinks(cacheKeys, emptyList(), drainedSubs)
                }

                val currentLinks = uiState.value.links
                val combinedLinks = if (drainedLinks.isNotEmpty()) {
                    com.lagradost.cloudstream3.desktop.player.QualityDataHelper.sortLinks((currentLinks + drainedLinks).distinctBy { it.url })
                } else {
                    currentLinks
                }

                val currentSubs = uiState.value.subtitles
                val combinedSubs = if (drainedSubs.isNotEmpty()) {
                    (currentSubs + drainedSubs).distinctBy { it.url.trim().lowercase() }
                } else {
                    currentSubs
                }

                updateState {
                    copy(
                        links = combinedLinks,
                        subtitles = combinedSubs,
                        statusText = "Found ${combinedLinks.size} stream${if (combinedLinks.size == 1) "" else "s"}...",
                    )
                }
            }

            val linkCallback = SafePluginInvoker.wrapCallback("LinkCallback") { link: ExtractorLink ->
                AppLogger.i("Plugin:${provider.name}", "Extracted link: ${link.name} (quality=${link.quality}, source=${link.source}) -> ${link.url}")
                pendingLinks.add(link)
            }

            val subCallback = SafePluginInvoker.wrapCallback("SubtitleCallback") { sub: SubtitleFile ->
                val cleanUrl = sub.url.trim()
                if (cleanUrl.isNotBlank()) {
                    val cleanSub = sub.copy(url = cleanUrl, lang = sub.lang.trim())
                    AppLogger.i("Plugin:${provider.name}", "Extracted subtitle: [${cleanSub.lang}] ${cleanSub.url}")
                    pendingSubs.add(cleanSub)
                }
            }

            val flusherJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(150L)
                    flushBatch()
                }
            }

            val lastNum = parts.lastOrNull()?.toIntOrNull()
            val secondLastNum = if (parts.size >= 3) {
                parts[parts.size - 2].toIntOrNull()?.takeIf { it in 1..200 }
            } else {
                null
            }

            val (parsedSeason, parsedEpisode) = when {
                lastNum != null && secondLastNum != null -> secondLastNum to lastNum
                lastNum != null && secondLastNum == null -> 1 to lastNum
                else -> null to null
            }
            val season = seasonParam ?: parsedSeason
            val episode = episodeParam ?: parsedEpisode
            val isSeries = isSeriesParam ?: (season != null || episode != null)

            val isMeta = provider.providerType == ProviderType.MetaProvider || provider.name.equals("Stremio", ignoreCase = true)
            if (isMeta) {
                try {
                    com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.searchStreams(
                        imdbId = cleanImdb,
                        season = season,
                        episode = episode,
                        title = title,
                        mediaId = dataUrl,
                        isSeries = isSeries,
                        onSubtitle = { subCallback(it) },
                        onLink = { linkCallback(it) },
                    )
                } finally {
                    flusherJob.cancel()
                    flushBatch()
                }
                val finalLinks = uiState.value.links
                val finalText = when {
                    finalLinks.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${finalLinks.size} stream${if (finalLinks.size == 1) "" else "s"} available."
                }
                if (finalLinks.isNotEmpty()) {
                    com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.set(cacheKeys, finalLinks, uiState.value.subtitles)
                }
                AppLogger.i("Plugin:${provider.name}", "Stream addon querying complete: ${finalLinks.size} streams")
                updateState { copy(isScraping = false, statusText = finalText) }
                return@launch
            }

            val stremioJob = viewModelScope.launch(Dispatchers.IO) {
                com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.searchStreams(
                    imdbId = cleanImdb,
                    season = season,
                    episode = episode,
                    title = title,
                    mediaId = dataUrl,
                    isSeries = isSeries,
                    onSubtitle = { subCallback(it) },
                    onLink = { linkCallback(it) },
                )
            }

            val result = try {
                SafePluginInvoker.invoke(
                    tag = "LinksViewModel:${provider.name}",
                    providerName = provider.name,
                    timeoutMs = SafePluginInvoker.TIMEOUT_SCRAPE_MS,
                    // Timeout on scraping is expected — links stream via callback and may already
                    // be in UI. A slow/dead extractor should not trip the circuit breaker.
                    penalizeOnTimeout = false,
                ) {
                    provider.loadLinks(
                        data = dataUrl,
                        isCasting = false,
                        subtitleCallback = subCallback,
                        callback = linkCallback,
                    )
                }
            } finally {
                flusherJob.cancel()
                flushBatch()
                stremioJob.cancel()
            }

            if (result.isSuccess) {
                val finalLinks = uiState.value.links
                val finalText = when {
                    finalLinks.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${finalLinks.size} stream${if (finalLinks.size == 1) "" else "s"} available."
                }
                if (finalLinks.isNotEmpty()) {
                    com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.set(cacheKeys, finalLinks, uiState.value.subtitles)
                }
                AppLogger.i("Plugin:${provider.name}", "Scraping complete: ${finalLinks.size} streams, ${uiState.value.subtitles.size} subtitles")
                updateState { copy(isScraping = false, statusText = finalText) }
            } else {
                val ex = result.exceptionOrNull()
                if (ex is kotlinx.coroutines.CancellationException) {
                    val finalLinks = uiState.value.links
                    val text = "Search stopped (${finalLinks.size} found)."
                    AppLogger.i("Plugin:${provider.name}", "Scraping cancelled by user")
                    updateState { copy(isScraping = false, statusText = text) }
                } else {
                    val finalLinks = uiState.value.links
                    if (finalLinks.isNotEmpty()) {
                        com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.set(cacheKeys, finalLinks, uiState.value.subtitles)
                        // Timeout fired after links were already delivered via callback — not an error.
                        val text = "Ready — ${finalLinks.size} stream${if (finalLinks.size == 1) "" else "s"} available."
                        AppLogger.d("Plugin:${provider.name}", "Scrape timed out but ${finalLinks.size} links already found — suppressing error")
                        updateState { copy(isScraping = false, statusText = text) }
                    } else {
                        AppLogger.e("Plugin:${provider.name}", "Error loading links: ${ex?.message}", ex)
                        val text = "Error: ${ex?.message ?: "Failed to load streams"}"
                        updateState { copy(isScraping = false, statusText = text) }
                    }
                }
            }
        }
    }

    private fun cancelScrape() {
        scrapeJob?.cancel()
    }

    private fun saveWatchPosition(history: com.lagradost.common.storage.WatchHistory, positionMs: Long, durationMs: Long) {
        val posSec = positionMs / 1000L
        val durSec = durationMs / 1000L
        if (posSec > 0 && durSec > 0) {
            val updated = history.copy(
                position = posSec,
                duration = durSec,
                updateTime = System.currentTimeMillis(),
            )
            viewModelScope.launch(Dispatchers.IO) {
                DesktopDataStore.setLastWatched(updated)
            }
        }
    }

    override fun dispose() {
        scrapeJob?.cancel()
        super.dispose()
    }

    private fun handlePlayLink(event: LinksUiEvent.OnPlayLink) {
        val state = uiState.value
        if (state.isLaunchingPlayer) return

        val link = event.link
        val displayTitle = event.displayTitle
        val history = event.history
        val loadResponse = event.loadResponse

        val validation = com.lagradost.player.impl.PlayerLinkHandler.validate(link, displayTitle)
        if (validation.isFailure) {
            updateState { copy(statusText = validation.exceptionOrNull()?.message ?: "Invalid stream") }
            return
        }

        updateState {
            copy(
                isLaunchingPlayer = true,
                currentPlayingUrl = link.url,
                playerLaunchError = null,
            )
        }

        val effectivePlayer = if (state.preferredPlayer == "vlc" && com.lagradost.player.impl.PlayerLinkHandler.shouldPreferMpv(link)) {
            "mpv"
        } else {
            state.preferredPlayer
        }

        updateState { copy(statusText = "Launching ${effectivePlayer.uppercase()}...") }

        val isLive = loadResponse?.type == com.lagradost.cloudstream3.TvType.Live
        val startSec = if (isLive) 0L else com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(history.position, history.duration)
        val startMs = startSec * 1000L

        if (effectivePlayer == "vlc") {
            val srtSubtitles = state.subtitles.filter { it.url.endsWith(".srt", ignoreCase = true) }.map { it.url }
            sendEffect(LinksUiEffect.LaunchVlc(link, displayTitle, srtSubtitles, startMs))
        } else {
            val initialIndex = state.links.indexOfFirst { it.url == link.url }.coerceAtLeast(0)
            val launchData = com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                links = state.links,
                initialIndex = initialIndex,
                title = displayTitle,
                subtitles = state.subtitles,
                startPositionMs = startMs,
                history = history,
                loadResponse = loadResponse,
                enrichedActors = event.enrichedActors,
                enrichedLogoUrl = event.enrichedLogoUrl,
                enrichedBackdropUrl = event.enrichedBackdropUrl,
            )
            sendEffect(LinksUiEffect.LaunchEmbeddedPlayer(launchData))
            updateState { copy(statusText = "Playing in embedded player: ${link.name}") }
        }
    }
}
