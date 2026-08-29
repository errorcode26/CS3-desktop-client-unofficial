package com.lagradost.cloudstream3.desktop.utils

/**
 * Shared title cleaning used by all enrichment stages.
 *
 * Approach:
 *  1. Extract year from the raw string.
 *  2. Find the earliest position where "junk" starts — quality tags, audio
 *     tags, bracket/pipe delimiters, or an explicit year marker.
 *  3. Take only the text before that position and strip trailing punctuation.
 *
 * Mirrors Android's HubCloud.cleanTitle() token-based strategy rather than a
 * chain of regex replaces — more robust for providers that pack many tags into
 * titles/slugs.
 */
object TitleUtils {

    // Matches the first junk word in a raw provider title.
    private val JUNK_START_REGEX =
        Regex(
            """(?i)\b(""" +
                // Resolution / quality
                """720p|1080p|480p|360p|2160p|4k|uhd|hd(?=\b)|""" +
                // Cam / workprint
                """hdtc|hd-tc|hdcam|hq(?=\b)|cam(?=\b)|ts(?=\b)|r5(?=\b)|""" +
                // Version suffix
                """v\d(?=\b)|""" +
                // Source
                """webrip|web-dl|web(?=\b)|bluray|blu-ray|bdrip|brrip|hdrip|dvdrip|dvdscr|pdtv|hdtv|""" +
                // Language/dub tags
                """dual.audio|multi.audio|hindi.dubbed|hindi.dub|english.dubbed|korean.dubbed|dubbed(?=\b)|""" +
                // Audio codec
                """aac|ac3|dts|dd5|eac3|atmos(?=\b)|flac(?=\b)|mp3(?=\b)|""" +
                // Subtitle
                """esubs|esub(?=\b)|subs(?=\b)|multisub|nosub(?=\b)|""" +
                // Video codec
                """x264|x265|h264|h\.264|hevc|avc(?=\b)|""" +
                // HDR/SDR
                """sdr(?=\b)|hdr(?=\b)|dv(?=\b)|""" +
                // Misc junk words common in slugs
                """line(?=\b)|clean(?=\b)|org(?=\b)""" +
                """)""",
        )

    // Hard delimiters that always mean junk starts here.
    // Includes '(' to catch patterns like "(Season 1 – 3)", "(2026)", "[Hindi]".
    private val DELIMITER_REGEX = Regex("""[\[\]{}|(]""")

    // Season / episode / part / cour / arc markers — must come BEFORE digits to avoid matching sequel numbers.
    // Catches: "(Season 1", " Season 2", "- Season 3", "(S01", "Part 2", "Cour 2", "The Final Season"
    private val SEASON_REGEX = Regex("""(?i)\s*[\(-]?\s*\b(season|series|episode|ep\.?|part|cour|arc)\s*\d+""")
    private val FINAL_SEASON_REGEX = Regex("""(?i)\s*[\(-]?\s*\b(the\s+final\s+season|final\s+season)\b""")

    // Trailing punctuation / whitespace after slicing
    private val TRAILING_JUNK_REGEX = Regex("""[\s\-:(\[{|]+$""")

    // Year anywhere in raw string
    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    /**
     * Returns Pair(cleanTitle, year?).
     * cleanTitle is never blank — falls back to raw.trim() if cleaning removes everything.
     */
    fun cleanProviderTitle(raw: String): Pair<String, Int?> {
        val year = YEAR_REGEX.find(raw)?.groupValues?.get(1)?.toIntOrNull()

        val candidates =
            buildList {
                JUNK_START_REGEX.find(raw)?.range?.first?.let { add(it) }
                DELIMITER_REGEX.find(raw)?.range?.first?.let { pos ->
                    // Guard: ignore a '(' at position 0 (e.g. "(500) Days of Summer").
                    if (pos > 0) add(pos)
                }
                SEASON_REGEX.find(raw)?.range?.first?.let { pos ->
                    if (pos > 0) add(pos)
                }
                FINAL_SEASON_REGEX.find(raw)?.range?.first?.let { pos ->
                    if (pos > 0) add(pos)
                }
                // Dash-space-digit pattern: " - 720p", " - 2026"
                Regex("""\s+-\s*\d""").find(raw)?.range?.first?.let { add(it) }
            }

        val cutAt = candidates.minOrNull() ?: raw.length
        val sliced = raw.substring(0, cutAt)
        val cleaned = TRAILING_JUNK_REGEX.replace(sliced, "").trim()

        return Pair(if (cleaned.isBlank()) raw.trim() else cleaned, year)
    }

    /**
     * Normalizes attached punctuation like "-Starting" -> " - Starting" or "Re:ZERO" spacing
     * to prevent search engine tokenization failures.
     */
    fun normalizePunctuation(str: String): String {
        return str.replace(Regex("""-(?=[a-zA-Z])"""), " - ")
            .replace(Regex(""":(?=[a-zA-Z])"""), ": ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Returns an ordered list of title candidates for progressive fallback matching.
     * 1. Primary cleaned title
     * 2. Punctuation normalized title
     * 3. Pre-colon root title (e.g. "Re:ZERO - Starting..." -> "Re:ZERO")
     * 4. Pre-hyphen root title (e.g. "Solo Leveling - Arise" -> "Solo Leveling")
     */
    fun extractRootTitleCandidates(raw: String): List<Pair<String, Int?>> {
        val primary = cleanProviderTitle(raw)
        val list = mutableListOf(primary)
        val cleanName = primary.first
        val year = primary.second

        // Candidate 2: Normalized punctuation
        val normalized = normalizePunctuation(cleanName)
        if (!normalized.equals(cleanName, ignoreCase = true) && !list.any { it.first.equals(normalized, ignoreCase = true) }) {
            list.add(Pair(normalized, year))
        }

        // Candidate 3: Pre-colon root
        if (cleanName.contains(":")) {
            val preColon = cleanName.substringBefore(":").trim()
            val cleanedPre = TRAILING_JUNK_REGEX.replace(preColon, "").trim()
            if (cleanedPre.length >= 3 && !list.any { it.first.equals(cleanedPre, ignoreCase = true) }) {
                list.add(Pair(cleanedPre, year))
            }
        }

        // Candidate 4: Pre-hyphen root
        if (cleanName.contains(" - ") || cleanName.contains("-")) {
            val preHyphen = cleanName.substringBefore(" - ").substringBefore("-").trim()
            val cleanedPre = TRAILING_JUNK_REGEX.replace(preHyphen, "").trim()
            if (cleanedPre.length >= 3 && !list.any { it.first.equals(cleanedPre, ignoreCase = true) }) {
                list.add(Pair(cleanedPre, year))
            }
        }

        return list
    }

    /**
     * Android-style filterName: strip everything except [a-zA-Z0-9] and lowercase.
     * Used to validate search result names against the cleaned query.
     */
    fun filterName(name: String): String = name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
}
