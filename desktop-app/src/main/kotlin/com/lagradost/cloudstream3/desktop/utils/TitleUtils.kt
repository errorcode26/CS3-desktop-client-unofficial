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

    // Country / Regional disambiguation tags that must NOT be stripped as junk delimiters:
    // Automatically matches any 2-letter ISO country code or full country name
    private val COUNTRY_TAG_PREFIX_REGEX = Regex("""(?i)^\s*\(([A-Z]{2}|[A-Za-z]{3,15})\)""")
    private val COUNTRY_TAG_REGEX = Regex("""(?i)\s*\(([A-Z]{2}|[A-Za-z]{3,15})\)""")

    // Dynamic ISO 3166-1 country lookup covering all 249 international countries and territories
    private val ISO_COUNTRY_MAP: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        java.util.Locale.getISOCountries().forEach { code ->
            try {
                val locale = java.util.Locale.of("", code)
                val display = locale.getDisplayCountry(java.util.Locale.ENGLISH)
                if (display.isNotBlank()) {
                    map[code.uppercase()] = display
                }
            } catch (_: Exception) {}
        }
        // Streaming & broadcast industry aliases
        map["UK"] = "UK"
        map["USA"] = "US"
        map["IN"] = "India"
        map["KR"] = "South Korea"
        map["JP"] = "Japan"
        map["FR"] = "France"
        map["DE"] = "Germany"
        map["ES"] = "Spain"
        map["IT"] = "Italy"
        map["SE"] = "Sweden"
        map["NO"] = "Norway"
        map["DK"] = "Denmark"
        map["TR"] = "Turkey"
        map["AU"] = "Australia"
        map["CA"] = "Canada"
        map["BR"] = "Brazil"
        map["MX"] = "Mexico"
        map["RU"] = "Russia"
        map["TH"] = "Thailand"
        map["ID"] = "Indonesia"
        map["VN"] = "Vietnam"
        map["PH"] = "Philippines"
        map["MY"] = "Malaysia"
        map
    }

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
        val candidates =
            buildList {
                JUNK_START_REGEX.find(raw)?.range?.first?.let { add(it) }
                DELIMITER_REGEX.findAll(raw).forEach { match ->
                    val pos = match.range.first
                    if (pos > 0) {
                        val remainder = raw.substring(pos)
                        val isCountryTag = COUNTRY_TAG_PREFIX_REGEX.containsMatchIn(remainder) && remainder.startsWith("(")
                        if (!isCountryTag) {
                            add(pos)
                        }
                    }
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
        val finalTitle = if (cleaned.isBlank()) raw.trim() else cleaned

        // Extract year: if the 4-digit number starts at index 0 and is part of the title (e.g. "2012" or "1917"),
        // it is the title itself, not the release year. Look for a subsequent year tag.
        val yearMatches = YEAR_REGEX.findAll(raw).map { it.range.first to it.groupValues[1].toInt() }.toList()
        val year = yearMatches.firstOrNull { (pos, _) ->
            !(pos <= 2 && Regex("""^\d{4}\b""").containsMatchIn(finalTitle))
        }?.second

        return Pair(finalTitle, year)
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
     * 1. Primary cleaned title (e.g. "24 (IN)")
     * 2. Country expanded title (e.g. "24 India" / "24: India")
     * 3. Plain base title without country tag (e.g. "24")
     * 4. Punctuation normalized title
     * 5. Pre-colon root title
     * 6. Pre-hyphen root title
     */
    fun extractRootTitleCandidates(raw: String): List<Pair<String, Int?>> {
        val primary = cleanProviderTitle(raw)
        val list = mutableListOf(primary)
        val cleanName = primary.first
        val year = primary.second

        // Candidate: Universal ISO Country Tag expansion & strip
        val countryMatch = COUNTRY_TAG_REGEX.find(cleanName)
        if (countryMatch != null) {
            val rawTag = countryMatch.groupValues[1].trim()
            val baseName = cleanName.replace(countryMatch.value, "").trim()
            val countryFullName = ISO_COUNTRY_MAP[rawTag.uppercase()] ?: rawTag

            val expanded1 = "$baseName $countryFullName"
            val expanded2 = "$baseName: $countryFullName"
            val expanded3 = "$baseName ($countryFullName)"
            if (!list.any { it.first.equals(expanded1, ignoreCase = true) }) list.add(Pair(expanded1, year))
            if (!list.any { it.first.equals(expanded2, ignoreCase = true) }) list.add(Pair(expanded2, year))
            if (!list.any { it.first.equals(expanded3, ignoreCase = true) }) list.add(Pair(expanded3, year))
            if (baseName.isNotBlank() && !list.any { it.first.equals(baseName, ignoreCase = true) }) {
                list.add(Pair(baseName, year))
            }
        }

        // Candidate: Normalized punctuation
        val normalized = normalizePunctuation(cleanName)
        if (!normalized.equals(cleanName, ignoreCase = true) && !list.any { it.first.equals(normalized, ignoreCase = true) }) {
            list.add(Pair(normalized, year))
        }

        // Candidate: Pre-colon root
        if (cleanName.contains(":")) {
            val preColon = cleanName.substringBefore(":").trim()
            val cleanedPre = TRAILING_JUNK_REGEX.replace(preColon, "").trim()
            if (cleanedPre.length >= 3 && !list.any { it.first.equals(cleanedPre, ignoreCase = true) }) {
                list.add(Pair(cleanedPre, year))
            }
        }

        // Candidate: Pre-hyphen root
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
