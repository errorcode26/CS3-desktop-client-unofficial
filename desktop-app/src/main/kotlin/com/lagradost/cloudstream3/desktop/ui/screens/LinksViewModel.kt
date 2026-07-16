package com.lagradost.cloudstream3.desktop.ui.screens

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages link scraping state and VLC watch-history saving for [LinksSidePanel].
 * Extracted from the god-composable to keep network calls out of the UI layer.
 */
class LinksViewModel(private val viewModelScope: CoroutineScope) {

    private val _links = MutableStateFlow<List<ExtractorLink>>(emptyList())
    val links: StateFlow<List<ExtractorLink>> = _links.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val subtitles: StateFlow<List<SubtitleFile>> = _subtitles.asStateFlow()

    private val _statusText = MutableStateFlow("Finding streams for you...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isScraping = MutableStateFlow(true)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private var scrapeJob: Job? = null

    /** Start (or restart) scraping links for the given [dataUrl]. */
    fun scrapeLinks(provider: MainAPI, dataUrl: String) {
        scrapeJob?.cancel()
        val linkBuffer = mutableListOf<ExtractorLink>()
        val subBuffer = mutableListOf<SubtitleFile>()
        _links.value = emptyList()
        _subtitles.value = emptyList()
        _isScraping.value = true
        _statusText.value = "Finding streams for you..."

        scrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                provider.loadLinks(
                    data = dataUrl,
                    isCasting = false,
                    subtitleCallback = { sub: SubtitleFile ->
                        subBuffer.add(sub)
                        _subtitles.value = subBuffer.toList()
                    },
                    callback = { link: ExtractorLink ->
                        linkBuffer.add(link)
                        _links.value = linkBuffer.toList()
                        _statusText.value = "Found ${linkBuffer.size} stream${if (linkBuffer.size == 1) "" else "s"}..."
                    },
                )
                _isScraping.value = false
                _statusText.value = when {
                    linkBuffer.isEmpty() -> "No streams found for this title."
                    else -> "Ready — ${linkBuffer.size} stream${if (linkBuffer.size == 1) "" else "s"} available."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isScraping.value = false
                _statusText.value = "Search stopped (${linkBuffer.size} found)."
            } catch (e: Throwable) {
                AppLogger.e("Error loading links", e)
                _isScraping.value = false
                _statusText.value = "Error: ${e.message}"
            }
        }
    }

    /** Cancel any in-progress scrape job. */
    fun cancelScrape() {
        scrapeJob?.cancel()
    }

    /** Update status text (e.g., after player launch). */
    fun setStatus(text: String) {
        _statusText.value = text
    }

    /** Persist watch position to DataStore every 5 seconds. */
    fun saveWatchPosition(history: WatchHistory, positionMs: Long, durationMs: Long) {
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
}
