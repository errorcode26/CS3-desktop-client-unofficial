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

    fun getQualityPriority(quality: Int, preferredQualitySetting: String? = null): Int {
        val prefTarget = when (preferredQualitySetting) {
            "2160p (4K)", "4K", "2160" -> Qualities.P2160.value
            "1080p", "1080" -> Qualities.P1080.value
            "720p", "720" -> Qualities.P720.value
            "480p", "480" -> Qualities.P480.value
            else -> null
        }

        if (prefTarget != null) {
            val closest = closestQuality(quality).value
            val diff = abs(closest - prefTarget)
            return when {
                closest == prefTarget -> 10
                closest < prefTarget -> 8 - (diff / 360)
                else -> 6 - (diff / 720)
            }
        }

        val closest = closestQuality(quality).value
        return _qualityPriorities.value[closest] ?: DEFAULT_QUALITY_PRIORITIES[closest] ?: 4
    }

    fun getHostTierScore(link: ExtractorLink): Int {
        val urlLower = link.url.lowercase()
        val nameLower = link.name.lowercase()

        // Generic raw download endpoints (deprioritized below streaming links as fallbacks)
        val isDownload = urlLower.contains("download") || nameLower.contains("[download]") || nameLower.contains("download")
        if (isDownload) {
            return 10
        }

        // Tier 1: Adaptive streaming manifests (HLS / DASH) with segment indexing
        if (link.isM3u8 || link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 ||
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH ||
            urlLower.contains(".m3u8") || urlLower.contains(".mpd")) {
            return 30
        }

        // Tier 2: Direct Range-seekable media container streams (MP4, MKV, WebM)
        if (urlLower.contains(".mp4") || urlLower.contains(".mkv") || urlLower.contains(".webm") || urlLower.contains(".avi") ||
            nameLower.contains(".mp4") || nameLower.contains(".mkv") || nameLower.contains(".webm") || nameLower.contains(".avi") ||
            link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO) {
            return 25
        }

        return 20
    }

    fun getMetadataBonus(link: ExtractorLink): Int {
        val nameLower = link.name.lowercase()
        var bonus = 0

        // Bonus for multi-audio and high-fidelity audio tracks
        if (nameLower.contains("dual audio") || nameLower.contains("multi") || nameLower.contains("dual")) bonus += 5
        if (nameLower.contains("5.1") || nameLower.contains("7.1") || nameLower.contains("atmos") || nameLower.contains("ddp") || nameLower.contains("dts")) bonus += 3
        if (nameLower.contains("10bit") || nameLower.contains("hevc") || nameLower.contains("x265") || nameLower.contains("av1")) bonus += 2

        // Severe penalty for low-fidelity tele-sync and cam recordings
        if (nameLower.contains("camrip") || nameLower.contains("hdcam") || nameLower.contains("telesync") ||
            nameLower.contains("predvd") || nameLower.contains("cam")) {
            bonus -= 50
        }

        return bonus
    }

    fun getLinkScore(link: ExtractorLink, preferredQualitySetting: String? = null): Int {
        val qualPriority = getQualityPriority(link.quality, preferredQualitySetting)
        val srcPriority = getSourcePriority(link.source)
        val hostTier = getHostTierScore(link)
        val metaBonus = getMetadataBonus(link)
        return (qualPriority * 10) + srcPriority + hostTier + metaBonus
    }

    fun isSeekableLink(link: ExtractorLink): Boolean {
        val urlLower = link.url.lowercase()
        val nameLower = link.name.lowercase()

        // Explicit live or non-seekable streams
        if (urlLower.contains("live=true") || urlLower.contains("/live/") || urlLower.contains("is_live=1") ||
            nameLower.contains("live stream") || nameLower.contains("camrip") || nameLower.contains("iptv")) {
            return false
        }

        // Direct seekable video files in URL or filename
        if (urlLower.contains(".mp4") || urlLower.contains(".mkv") || urlLower.contains(".webm") || urlLower.contains(".avi") ||
            nameLower.contains(".mp4") || nameLower.contains(".mkv") || nameLower.contains(".webm") || nameLower.contains(".avi")) {
            return true
        }

        // Adaptive VOD HLS/DASH manifests
        if (link.isM3u8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 || urlLower.contains(".m3u8") ||
            link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH || urlLower.contains(".mpd")) {
            return true
        }

        // Generic direct media links
        return link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
    }

    fun sortLinks(
        links: List<ExtractorLink>,
        startPositionMs: Long = 0L,
        preferredQualitySetting: String? = null,
        isLive: Boolean = false,
    ): List<ExtractorLink> {
        val isResuming = !isLive && startPositionMs > 5000L
        return links.sortedWith(
            compareByDescending<ExtractorLink> { link ->
                if (isLive) {
                    1 // For live TV / live streams, seekability is not required
                } else {
                    val seekable = isSeekableLink(link)
                    if (isResuming) {
                        if (seekable) 2 else 0
                    } else {
                        if (seekable) 1 else 0
                    }
                }
            }.thenByDescending { link ->
                getLinkScore(link, preferredQualitySetting)
            }.thenByDescending { link ->
                if (isLive) {
                    // For live streams, HLS / DASH are the gold standard protocols
                    if (link.isM3u8 || link.isDash || link.url.contains(".m3u8", ignoreCase = true) || link.url.contains(".mpd", ignoreCase = true)) 3 else 1
                } else {
                    val urlLower = link.url.lowercase()
                    when {
                        urlLower.contains(".mp4") || urlLower.contains(".mkv") -> 3
                        link.isM3u8 || link.isDash || urlLower.contains(".m3u8") || urlLower.contains(".mpd") -> 2
                        else -> 1
                    }
                }
            }.thenBy { link ->
                link.name
            },
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
