package com.lagradost.cloudstream3.desktop.ui.screens.links

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
            updateState { copy(preferredPlayer = prefPlayer, autoPlayEnabled = autoPlay) }
        }
    }

    override fun handleEvent(event: LinksUiEvent) {
        when (event) {
            is LinksUiEvent.OnScrape -> scrapeLinks(event.provider, event.dataUrl)
            is LinksUiEvent.OnCancelScrape -> cancelScrape()
            is LinksUiEvent.OnStatusTextChanged -> updateState { copy(statusText = event.text) }
            is LinksUiEvent.OnSaveWatchPosition -> saveWatchPosition(event.history, event.positionMs, event.durationMs)
            is LinksUiEvent.OnPreferredPlayerChanged -> {
                updateState { copy(preferredPlayer = event.player) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey("preferred_player", event.player)
                }
            }
            is LinksUiEvent.OnPlayLink -> handlePlayLink(event)
        }
    }

    private fun scrapeLinks(provider: MainAPI, dataUrl: String) {
        scrapeJob?.cancel()

        updateState {
            copy(
                links = emptyList(),
                subtitles = emptyList(),
                isScraping = true,
                statusText = "Finding streams for you...",
            )
        }

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                provider.loadLinks(
                    data = dataUrl,
                    isCasting = false,
                    subtitleCallback = { sub: SubtitleFile ->
                        updateState { copy(subtitles = subtitles + sub) }
                    },
                    callback = { link: ExtractorLink ->
                        updateState {
                            val newLinks = links + link
                            val text = "Found ${newLinks.size} stream${if (newLinks.size == 1) "" else "s"}..."
                            copy(links = newLinks, statusText = text)
                        }
                    },
                )
                val finalLinks = uiState.value.links
                val finalText = when {
                    finalLinks.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${finalLinks.size} stream${if (finalLinks.size == 1) "" else "s"} available."
                }
                updateState { copy(isScraping = false, statusText = finalText) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                val finalLinks = uiState.value.links
                val text = "Search stopped (${finalLinks.size} found)."
                updateState { copy(isScraping = false, statusText = text) }
            } catch (e: Throwable) {
                AppLogger.e("Error loading links", e)
                val text = "Error: ${e.message}"
                updateState { copy(isScraping = false, statusText = text) }
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
        val isLaunchingPlayer = false // Actually, LinksScreen has this state locally, but we just check if it's currently launching to prevent double launch

        val link = event.link
        val displayTitle = event.displayTitle
        val history = event.history
        val loadResponse = event.loadResponse

        val validation = com.lagradost.player.impl.PlayerLinkHandler.validate(link, displayTitle)
        if (validation.isFailure) {
            updateState { copy(statusText = validation.exceptionOrNull()?.message ?: "Invalid stream") }
            return
        }

        sendEffect(LinksUiEffect.NotifyLaunching(true))
        sendEffect(LinksUiEffect.NotifyCurrentUrl(link.url))

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
            )
            sendEffect(LinksUiEffect.LaunchEmbeddedPlayer(launchData))
            updateState { copy(statusText = "Playing in embedded player: ${link.name}") }
        }
    }
}
