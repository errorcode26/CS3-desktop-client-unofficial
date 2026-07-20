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
        preferredPlayer = DesktopDataStore.getKey<String>("preferred_player") ?: "mpv",
        autoPlayEnabled = DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
    )
) {
    private var scrapeJob: Job? = null

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
        }
    }

    private fun scrapeLinks(provider: MainAPI, dataUrl: String) {
        scrapeJob?.cancel()
        val linkBuffer = mutableListOf<ExtractorLink>()
        val subBuffer = mutableListOf<SubtitleFile>()

        updateState {
            copy(
                links = emptyList(),
                subtitles = emptyList(),
                isScraping = true,
                statusText = "Finding streams for you..."
            )
        }

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                provider.loadLinks(
                    data = dataUrl,
                    isCasting = false,
                    subtitleCallback = { sub: SubtitleFile ->
                        subBuffer.add(sub)
                        updateState { copy(subtitles = subBuffer.toList()) }
                    },
                    callback = { link: ExtractorLink ->
                        linkBuffer.add(link)
                        val text = "Found ${linkBuffer.size} stream${if (linkBuffer.size == 1) "" else "s"}..."
                        updateState { copy(links = linkBuffer.toList(), statusText = text) }
                    },
                )
                val finalText = when {
                    linkBuffer.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${linkBuffer.size} stream${if (linkBuffer.size == 1) "" else "s"} available."
                }
                updateState { copy(isScraping = false, statusText = finalText) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                val text = "Search stopped (${linkBuffer.size} found)."
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
}
