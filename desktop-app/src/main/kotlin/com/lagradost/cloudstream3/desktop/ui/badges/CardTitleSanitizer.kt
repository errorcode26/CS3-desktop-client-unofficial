package com.lagradost.cloudstream3.desktop.ui.badges

import java.util.concurrent.ConcurrentHashMap

data class SanitizedCardMeta(
    val displayTitle: String,
    val year: Int? = null,
    val hasSub: Boolean = false,
    val hasDub: Boolean = false,
    val qualityText: String? = null,
    val seasonText: String? = null,
)

/**
 * Title cleanup and audio/subtitle/resolution tag extraction.
 */
object CardTitleSanitizer {

    private val cache = ConcurrentHashMap<String, SanitizedCardMeta>()
    private const val MAX_CACHE_SIZE = 1200

    private val JUNK_START_REGEX = Regex(
        """(?i)\b(720p|1080p|480p|360p|2160p|4k|uhd|hd(?=\b)|hdtc|hdcam|cam|ts|webrip|web-dl|web(?=\b)|bluray|blu-ray|bdrip|brrip|hdrip|dvdrip|dual.audio|multi.audio|hindi.dubbed|hindi.dub|english.dubbed|korean.dubbed|dubbed(?=\b)|aac|ac3|dts|dd5|eac3|atmos|flac|mp3|esubs|esub|subs|multisub|x264|x265|h264|h\.264|hevc|avc|sdr|hdr|dv|line|clean|org)\b"""
    )

    private val DELIMITER_REGEX = Regex("""[\[\]{}|(]""")
    private val SEASON_REGEX = Regex("""(?i)\s*[\(-]?\s*\b(season\s*\d+|series\s*\d+|s\d{1,2}|part\s*\d+)\b""")
    private val TRAILING_JUNK_REGEX = Regex("""[\s\-:(\[{|]+$""")
    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    // Token matchers for SUB/DUB detection
    private val DUAL_AUDIO_REGEX = Regex("""(?i)\b(dual[\s._-]*audio|multi[\s._-]*audio|\[\s*(?:hin|eng|jap|tam|tel|kor)[^\]]*(?:hin|eng|jap|tam|tel|kor)[^\]]*\])""")
    private val DUB_REGEX = Regex("""(?i)\b(dub|dubbed|dubs|hindi[\s._-]*dub|eng[\s._-]*dub|tamil[\s._-]*dub|telugu[\s._-]*dub)""")
    private val SUB_REGEX = Regex("""(?i)\b(sub|subbed|subs|esub|esubs|multisub|softsub|hardsub)""")

    // Quality token matchers
    private val QUALITY_4K_REGEX = Regex("""(?i)\b(4k|2160p|uhd|4k[\s._-]*uhd)\b""")
    private val QUALITY_1080P_REGEX = Regex("""(?i)\b(1080p|1080i|fhd)\b""")
    private val QUALITY_720P_REGEX = Regex("""(?i)\b(720p|hd)\b""")

    fun sanitize(
        rawTitle: String,
        pluginHasSub: Boolean = false,
        pluginHasDub: Boolean = false,
        pluginQuality: String? = null,
        autoClean: Boolean = true,
        autoDetectSubDub: Boolean = true,
        autoDetectQuality: Boolean = true,
    ): SanitizedCardMeta {
        if (rawTitle.isBlank()) {
            return SanitizedCardMeta(displayTitle = rawTitle)
        }

        // Cache key combining raw title and flags
        val cacheKey = "$rawTitle|$pluginHasSub|$pluginHasDub|$pluginQuality|$autoClean|$autoDetectSubDub|$autoDetectQuality"
        cache[cacheKey]?.let { return it }

        val isDualAudio = DUAL_AUDIO_REGEX.containsMatchIn(rawTitle)
        val hasDubDetected = isDualAudio || DUB_REGEX.containsMatchIn(rawTitle)
        val hasSubDetected = isDualAudio || SUB_REGEX.containsMatchIn(rawTitle)

        val finalHasSub = pluginHasSub || (autoDetectSubDub && hasSubDetected)
        val finalHasDub = pluginHasDub || (autoDetectSubDub && hasDubDetected)

        val detectedQuality = when {
            QUALITY_4K_REGEX.containsMatchIn(rawTitle) -> "4K"
            QUALITY_1080P_REGEX.containsMatchIn(rawTitle) -> "1080p"
            QUALITY_720P_REGEX.containsMatchIn(rawTitle) -> "720p"
            else -> null
        }
        val finalQuality = pluginQuality ?: if (autoDetectQuality) detectedQuality else null

        val seasonMatch = SEASON_REGEX.find(rawTitle)?.groupValues?.get(1)?.trim()

        if (!autoClean) {
            val result = SanitizedCardMeta(
                displayTitle = rawTitle.trim(),
                hasSub = finalHasSub,
                hasDub = finalHasDub,
                qualityText = finalQuality,
                seasonText = seasonMatch,
            )
            putCache(cacheKey, result)
            return result
        }

        // Title cleaning
        val yearMatches = YEAR_REGEX.findAll(rawTitle).toList()
        // If year is at index 0 (e.g. "1917 (2019)"), ignore the leading year as the release date
        val validYearMatch = yearMatches.lastOrNull { it.range.first > 2 } ?: yearMatches.firstOrNull()
        val year = validYearMatch?.groupValues?.get(1)?.toIntOrNull()

        val candidates = buildList {
            JUNK_START_REGEX.find(rawTitle)?.range?.first?.let { if (it > 0) add(it) }
            DELIMITER_REGEX.find(rawTitle)?.range?.first?.let { pos ->
                if (pos > 0) add(pos)
            }
            SEASON_REGEX.find(rawTitle)?.range?.first?.let { if (it > 0) add(it) }
            Regex("""\s+-\s*\d""").find(rawTitle)?.range?.first?.let { if (it > 0) add(it) }
        }

        val cutAt = candidates.minOrNull() ?: rawTitle.length
        val sliced = rawTitle.substring(0, cutAt)
        var cleaned = TRAILING_JUNK_REGEX.replace(sliced, "").trim()

        if (cleaned.isBlank()) {
            cleaned = rawTitle.trim()
        }

        val result = SanitizedCardMeta(
            displayTitle = cleaned,
            year = year,
            hasSub = finalHasSub,
            hasDub = finalHasDub,
            qualityText = finalQuality,
            seasonText = seasonMatch,
        )

        putCache(cacheKey, result)
        return result
    }

    /**
     * Sanitizes raw episode names to filter out scraper payloads, JSON link arrays, raw URLs,
     * and internal metadata tags (e.g. ||DATE:...||).
     */
    fun sanitizeEpisodeTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        // Check for raw JSON arrays or objects (e.g. [{"source":"..."}, ...] or {"source":...})
        if (trimmed.startsWith("[{") ||
            trimmed.startsWith("{\"") ||
            (trimmed.startsWith("[") && trimmed.endsWith("]") && (trimmed.contains("\"source\"") || trimmed.contains("\"url\"") || trimmed.contains("http"))) ||
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("magnet:?")
        ) {
            return null
        }

        // Clean out date markers and other metadata tags
        val clean = trimmed.replace(Regex("""\|\|DATE:[^|]+\|\|"""), "").trim()
        if (clean.isBlank() || clean.startsWith("http://") || clean.startsWith("https://")) {
            return null
        }
        return clean
    }

    private fun putCache(key: String, value: SanitizedCardMeta) {
        if (cache.size > MAX_CACHE_SIZE) {
            cache.clear()
        }
        cache[key] = value
    }
}

