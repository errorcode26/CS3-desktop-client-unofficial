package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
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

    /** Minimum score for immediate fast-play trigger (>=720p HD + preferred/multi language) */
    const val FAST_PLAY_SCORE_THRESHOLD = 1200

    // Default Quality Priority Map (Higher = Better)
    val DEFAULT_QUALITY_PRIORITIES = mapOf(
        Qualities.P2160.value to 10,
        Qualities.P1440.value to 9,
        Qualities.P1080.value to 8,
        Qualities.P720.value to 6,
        Qualities.P480.value to 4,
        Qualities.P360.value to 2,
        Qualities.P240.value to 1,
        Qualities.P144.value to 1,
        Qualities.Unknown.value to 5,
        0 to 8, // Auto
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
        if (quality == 0) return _qualityPriorities.value[0] ?: 8
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

    private val seekabilityCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val RESOLUTION_REGEX = Regex("(?i)(?:^|[^0-9a-z])(2160p|4k|uhd|1440p|2k|qhd|1080p|fhd|720p|hd|480p|sd|360p|1080|720)(?:[^0-9a-z]|$)")
    private val DEGRADED_REGEX = Regex("(?i)\\b(low\\s*quality|cam|camrip|telesync|telecine|hdcam|hd-ts|ts|dvdscr|scr|sample)\\b")
    private val SIZE_REGEX = Regex("(?i)(?:^|[^0-9a-z])([0-9]+(?:[.,][0-9]+)?)\\s*(gb|mb|gib|mib)(?:[^0-9a-z]|$)")

    fun isDegradedStream(link: ExtractorLink): Boolean {
        val text = "${link.name} ${link.url}"
        return text.contains("💩") || DEGRADED_REGEX.containsMatchIn(text)
    }

    fun extractEstimatedSizeBytes(link: ExtractorLink): Long {
        val match = SIZE_REGEX.find(link.name) ?: SIZE_REGEX.find(link.url) ?: return 0L
        val num = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].lowercase(java.util.Locale.US)
        return when {
            unit.startsWith("g") -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            unit.startsWith("m") -> (num * 1024.0 * 1024.0).toLong()
            else -> 0L
        }
    }

    fun extractEffectiveQuality(link: ExtractorLink): Int {
        if (link.quality > 0 && link.quality != Qualities.Unknown.value) {
            return link.quality
        }
        val textToSearch = "${link.name} ${link.url}"
        val match = RESOLUTION_REGEX.find(textToSearch)
        if (match != null) {
            val token = match.groupValues[1].lowercase()
            return when {
                token.contains("2160") || token == "4k" || token == "uhd" -> Qualities.P2160.value
                token.contains("1440") || token == "2k" || token == "qhd" -> Qualities.P1440.value
                token.contains("1080") || token == "fhd" -> Qualities.P1080.value
                token.contains("720") || token == "hd" -> Qualities.P720.value
                token.contains("480") || token == "sd" -> Qualities.P480.value
                token.contains("360") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
        }
        return Qualities.Unknown.value
    }

    /**
     * Language Match Tier:
     * 3 = Direct Target Language / Preferred Dub Match
     * 2 = Multi-Audio / Dual-Audio Stream
     * 1 = Neutral / Unspecified Stream (Single track / default)
     * 0 = Explicit non-preferred language stream
     */
    fun getLanguageMatchTier(link: ExtractorLink): Int {
        val targetLangs = LanguagePriorityHelper.getOrderedAudioLanguages()

        if (targetLangs.isEmpty()) {
            val hasMulti = LanguageMatcher.matchLinkPriority(link.name, link.source, link.audioTracks, "auto")
            return if (hasMulti > 0) 2 else 1
        }

        for (langCode in targetLangs) {
            val match = LanguageMatcher.matchLinkPriority(
                linkName = link.name,
                source = link.source,
                audioTracks = link.audioTracks,
                prefLangCode = langCode,
            )
            if (match == 2) return 3
            if (match == 1) return 2
        }

        val allKnownLangs = PlayerConfig.GLOBAL_LANGUAGE_OPTIONS.map { it.first }.filter { it != "auto" && it != "original" }
        for (otherLang in allKnownLangs) {
            if (otherLang !in targetLangs) {
                val otherMatch = LanguageMatcher.matchLinkPriority(
                    linkName = link.name,
                    source = link.source,
                    audioTracks = link.audioTracks,
                    prefLangCode = otherLang,
                )
                if (otherMatch == 2) return 0
            }
        }

        return 1
    }

    fun getQualityPreferenceRank(quality: Int, preferredQuality: String): Int {
        return when (preferredQuality.lowercase().trim()) {
            "2160p (4k)", "2160p", "4k" -> {
                when {
                    quality >= Qualities.P2160.value -> 100
                    quality >= Qualities.P1440.value -> 90
                    quality >= Qualities.P1080.value -> 80
                    quality >= Qualities.P720.value -> 60
                    quality >= Qualities.P480.value -> 40
                    quality >= Qualities.P360.value -> 20
                    else -> 10
                }
            }
            "1080p", "1080p (full hd)" -> {
                when {
                    quality == Qualities.P1080.value -> 100
                    quality == Qualities.P1440.value -> 90
                    quality >= Qualities.P2160.value -> 85
                    quality == Qualities.P720.value -> 70
                    quality == Qualities.P480.value -> 40
                    quality == Qualities.P360.value -> 20
                    else -> 10
                }
            }
            "720p", "720p (hd)" -> {
                when {
                    quality == Qualities.P720.value -> 100
                    quality == Qualities.P1080.value -> 85
                    quality == Qualities.P1440.value || quality >= Qualities.P2160.value -> 70
                    quality == Qualities.P480.value -> 50
                    quality == Qualities.P360.value -> 20
                    else -> 10
                }
            }
            "480p", "480p / sd" -> {
                when {
                    quality == Qualities.P480.value -> 100
                    quality == Qualities.P720.value -> 80
                    quality == Qualities.P360.value -> 60
                    quality >= Qualities.P1080.value -> 40
                    else -> 10
                }
            }
            else -> {
                // Auto / Highest available (uses custom priorities or default quality descending)
                val custom = getQualityPriority(quality)
                custom * 10
            }
        }
    }

    fun getLinkScore(link: ExtractorLink): Int {
        val effectiveQual = extractEffectiveQuality(link)
        val preferredQual = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
        val qualRank = getQualityPreferenceRank(effectiveQual, preferredQual)
        val langTier = getLanguageMatchTier(link)
        val srcPriority = getSourcePriority(link.source)
        val isHd = if (effectiveQual >= Qualities.P720.value) 1000 else 0
        val degradedPenalty = if (isDegradedStream(link)) -2000 else 0
        return isHd + (langTier * 200) + qualRank + srcPriority + degradedPenalty
    }

    fun isSeekableLink(link: ExtractorLink): Boolean {
        if (link.isM3u8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 ||
            link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH) {
            return true
        }
        val url = link.url.trim()
        val cached = seekabilityCache[url]
        if (cached != null) {
            return cached
        }
        val urlLower = url.lowercase(java.util.Locale.US)
        if (urlLower.contains(".m3u8") || urlLower.contains(".mpd")) return true

        if (link.name.contains("download only", ignoreCase = true) || urlLower.contains("download-only")) {
            return false
        }

        // Direct streams (MP4/MKV) and desktop player links are seekable by default unless probe explicitly failed
        return link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO ||
            urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") || urlLower.contains(".mp4?") || urlLower.contains(".mkv?") ||
            link.extractorData == "yt-dlp" || !urlLower.contains("live")
    }

    suspend fun probeRangeSeekability(link: ExtractorLink): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (link.isM3u8 || link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH) {
            return@withContext true
        }
        val url = link.url.trim()
        val cached = seekabilityCache[url]
        if (cached != null) return@withContext cached

        if (link.name.contains("download only", ignoreCase = true)) {
            seekabilityCache[url] = false
            return@withContext false
        }

        if (link.extractorData == "yt-dlp" || DesktopYtDlpBinary.isYouTubeUrl(url)) {
            val isLive = link.name.contains("Live", ignoreCase = true) || url.contains("live", ignoreCase = true)
            val isSeekable = !isLive
            seekabilityCache[url] = isSeekable
            return@withContext isSeekable
        }

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            seekabilityCache[url] = false
            return@withContext false
        }

        try {
            val reqBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")

            link.getAllHeaders().forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank() && !k.equals("Range", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }
            if (!link.getAllHeaders().any { it.key.equals("User-Agent", ignoreCase = true) }) {
                reqBuilder.header("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
            }

            val probeClient = com.lagradost.cloudstream3.app.baseClient.newBuilder()
                .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            probeClient.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val acceptRanges = response.header("Accept-Ranges")
                val contentRange = response.header("Content-Range")
                val contentType = response.header("Content-Type")?.lowercase(java.util.Locale.US) ?: ""

                val isMedia = !contentType.contains("text/html") &&
                    !contentType.contains("application/json") &&
                    !contentType.contains("text/plain")

                val totalBytes = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    ?: response.header("Content-Length")?.trim()?.toLongOrNull()
                    ?: -1L

                // Reject tiny payloads (< 500 KB) that cannot be real video containers
                val hasValidSize = totalBytes == -1L || totalBytes > 500_000L

                val hasRangeSupport = code == 206 || contentRange != null || (code == 200 && acceptRanges?.contains("bytes", ignoreCase = true) == true)
                val isSeekable = isMedia && hasValidSize && hasRangeSupport
                seekabilityCache[url] = isSeekable
                AppLogger.i("QualityDataHelper", "Range probe for ${link.name} (HTTP $code, Range=$contentRange, Type=$contentType, Size=$totalBytes) -> seekable=$isSeekable")
                isSeekable
            }
        } catch (e: Exception) {
            AppLogger.w("QualityDataHelper", "Range probe failed for ${link.name}: ${e.message}")
            seekabilityCache[url] = false
            false
        }
    }

    /**
     * Determines whether a discovered link satisfies the target auto-play criteria
     * (seekable video container and quality >= 720p/1080p).
     */
    fun isTargetSatisfied(link: ExtractorLink): Boolean {
        val isSeekable = isSeekableLink(link)
        val effQual = extractEffectiveQuality(link)
        val langTier = getLanguageMatchTier(link)
        return isSeekable && effQual >= Qualities.P720.value && langTier >= 1 && !isDegradedStream(link)
    }

    fun sortLinks(links: List<ExtractorLink>): List<ExtractorLink> {
        val preferredQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
        return links.sortedWith(
            // Tier 1: Seekability (must not freeze or fail on scrubbing)
            compareByDescending<ExtractorLink> { if (isSeekableLink(it)) 1 else 0 }
                // Tier 2: Non-preferred foreign language filter (Direct/Multi/Neutral [1..3] strictly prioritized over other language [0])
                .thenByDescending { if (getLanguageMatchTier(it) > 0) 1 else 0 }
                // Tier 3: Clean stream priority over degraded/cam/sample streams
                .thenByDescending { if (!isDegradedStream(it)) 1 else 0 }
                // Tier 4: Target Resolution / Quality Ranking (User-configured quality preferences)
                .thenByDescending { getQualityPreferenceRank(extractEffectiveQuality(it), preferredQuality) }
                // Tier 5: Language Match precision tie-breaker (Direct Match [3] > Multi-Audio [2] > Neutral [1])
                .thenByDescending { getLanguageMatchTier(it) }
                // Tier 6: High bitrate / parsed stream size preference (GB > MB)
                .thenByDescending { extractEstimatedSizeBytes(it) }
                // Tier 7: Server Source Priority (user-ranked servers break ties)
                .thenByDescending { getSourcePriority(it.source) }
                // Tier 8: Fast streaming protocol (HLS / DASH preferred when tied)
                .thenByDescending { if (it.isM3u8 || it.isDash) 1 else 0 }
                // Tier 9: Deterministic tie-breaker
                .thenBy { it.name }
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
