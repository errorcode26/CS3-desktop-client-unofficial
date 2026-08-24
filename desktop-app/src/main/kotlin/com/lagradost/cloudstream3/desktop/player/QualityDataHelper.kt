package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Desktop Source & Quality Priority Manager.
 * Allows users to rank preferred resolutions and video server sources to automatically
 * sort links and pick the fastest/highest quality streams.
 */
object QualityDataHelper {
    private const val PREF_QUALITY_PRIORITIES = "cs_desktop_quality_priorities"
    private const val PREF_SOURCE_PRIORITIES = "cs_desktop_source_priorities"
    private const val PREF_DISCOVERED_SOURCES = "cs_desktop_discovered_sources"

    // Default Quality Priority Map (Higher = Better)
    val DEFAULT_QUALITY_PRIORITIES = mapOf(
        Qualities.P2160.value to 8,
        Qualities.P1440.value to 7,
        Qualities.P1080.value to 6,
        Qualities.P720.value to 5,
        Qualities.P480.value to 4,
        Qualities.P360.value to 3,
        Qualities.P240.value to 2,
        Qualities.P144.value to 1,
        Qualities.Unknown.value to 4,
        0 to 5, // Auto
    )

    private val _qualityPriorities = MutableStateFlow<Map<Int, Int>>(DEFAULT_QUALITY_PRIORITIES)
    val qualityPriorities: StateFlow<Map<Int, Int>> = _qualityPriorities.asStateFlow()

    private val _sourcePriorities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sourcePriorities: StateFlow<Map<String, Int>> = _sourcePriorities.asStateFlow()

    private val _discoveredSources = MutableStateFlow<Set<String>>(emptySet())
    val discoveredSources: StateFlow<Set<String>> = _discoveredSources.asStateFlow()

    init {
        loadPriorities()
    }

    fun loadPriorities() {
        try {
            val savedQuality = DesktopDataStore.getKey<Map<Int, Int>>(PREF_QUALITY_PRIORITIES)
            if (!savedQuality.isNullOrEmpty()) {
                _qualityPriorities.value = DEFAULT_QUALITY_PRIORITIES + savedQuality
            }

            val savedSources = DesktopDataStore.getKey<Map<String, Int>>(PREF_SOURCE_PRIORITIES)
            if (!savedSources.isNullOrEmpty()) {
                _sourcePriorities.value = savedSources
            }

            val savedDiscovered = DesktopDataStore.getKey<List<String>>(PREF_DISCOVERED_SOURCES)
            if (!savedDiscovered.isNullOrEmpty()) {
                _discoveredSources.value = savedDiscovered.toSet()
            }
        } catch (e: Exception) {
            AppLogger.e("QualityDataHelper", "Failed to load source & quality priorities", e)
        }
    }

    fun getQualityPriority(quality: Int): Int {
        val closest = closestQuality(quality).value
        return _qualityPriorities.value[closest] ?: DEFAULT_QUALITY_PRIORITIES[closest] ?: 4
    }

    fun setQualityPriority(quality: Int, priority: Int) {
        val updated = _qualityPriorities.value.toMutableMap()
        updated[quality] = priority
        _qualityPriorities.value = updated
        DesktopDataStore.setKey(PREF_QUALITY_PRIORITIES, updated)
    }

    fun getSourcePriority(source: String?): Int {
        if (source.isNullOrBlank()) return 0
        return _sourcePriorities.value[source] ?: 0
    }

    fun setSourcePriority(source: String, priority: Int) {
        val updated = _sourcePriorities.value.toMutableMap()
        if (priority == 0) {
            updated.remove(source)
        } else {
            updated[source] = priority
        }
        _sourcePriorities.value = updated
        DesktopDataStore.setKey(PREF_SOURCE_PRIORITIES, updated)
    }

    fun registerDiscoveredSource(source: String?) {
        if (source.isNullOrBlank()) return
        if (!_discoveredSources.value.contains(source)) {
            val updated = _discoveredSources.value + source
            _discoveredSources.value = updated
            DesktopDataStore.setKey(PREF_DISCOVERED_SOURCES, updated.toList())
        }
    }

    fun resetToDefaults() {
        _qualityPriorities.value = DEFAULT_QUALITY_PRIORITIES
        _sourcePriorities.value = emptyMap()
        DesktopDataStore.setKey(PREF_QUALITY_PRIORITIES, DEFAULT_QUALITY_PRIORITIES)
        DesktopDataStore.setKey(PREF_SOURCE_PRIORITIES, emptyMap<String, Int>())
    }

    fun getLinkScore(link: ExtractorLink): Int {
        val qualPriority = getQualityPriority(link.quality)
        val srcPriority = getSourcePriority(link.source)
        // Quality priority weighted higher by 10x, source priority adds preference within same quality tier
        return (qualPriority * 10) + srcPriority
    }

    fun isSeekableLink(link: ExtractorLink): Boolean {
        val urlLower = link.url.lowercase()
        return link.isM3u8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 ||
            link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH ||
            urlLower.contains(".m3u8") || urlLower.contains(".mpd") ||
            urlLower.contains(".mp4") || urlLower.contains(".mkv") ||
            urlLower.contains(".webm") || urlLower.contains(".avi") ||
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
    }

    fun sortLinks(links: List<ExtractorLink>): List<ExtractorLink> {
        return links.sortedWith(
            compareByDescending<ExtractorLink> { if (isSeekableLink(it)) 1 else 0 } // Seekable streams strictly prioritized, non-seekable streams pushed to bottom
                .thenByDescending { getLinkScore(it) }
                .thenByDescending { it.isM3u8 || it.isDash } // HLS/DASH fast streaming preferred when score tied
                .thenBy { it.name },
        )
    }

    fun closestQuality(target: Int?): Qualities {
        if (target == null || target == 0) return Qualities.Unknown
        return Qualities.entries.minByOrNull { abs(it.value - target) } ?: Qualities.Unknown
    }

    fun formatQuality(quality: Int): String {
        return when (quality) {
            0 -> "Auto"
            Qualities.P2160.value -> "4K"
            Qualities.P1440.value -> "1440p"
            Qualities.P1080.value -> "1080p"
            Qualities.P720.value -> "720p"
            Qualities.P480.value -> "480p"
            Qualities.P360.value -> "360p"
            Qualities.P240.value -> "240p"
            Qualities.P144.value -> "144p"
            Qualities.Unknown.value -> "Unknown"
            else -> "${quality}p"
        }
    }
}
