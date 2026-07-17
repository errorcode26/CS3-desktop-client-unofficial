package com.lagradost.cloudstream3.desktop.ui.screens.details

/**
 * Splits a raw scraped content title into a clean display title and an optional subtitle
 * containing extra metadata noise (e.g. quality tags, season info, dubbed labels).
 *
 * Examples:
 *   "House of the Dragon (Season 1 - 3) [S03 E03 Added]" -> Pair("House of the Dragon", "(Season 1 - 3) [S03 E03 Added]")
 *   "Spider-Man: No Way Home (2021) [Dual Audio] [1080p]"  -> Pair("Spider-Man: No Way Home (2021)", "[Dual Audio] [1080p]")
 *   "The Batman (2022)"                                     -> Pair("The Batman (2022)", null)
 *   "Vikings Season 6"                                      -> Pair("Vikings", "Season 6")
 */
fun splitTitle(raw: String): Pair<String, String?> {
    if (raw.isBlank()) return Pair(raw, null)

    // Find the earliest split point from any of these noise markers:
    val candidates = mutableListOf<Int>()

    // 1. "(Season ...)" — split before the opening paren
    Regex("""\s*\(Season\b""", RegexOption.IGNORE_CASE).find(raw)
        ?.range?.first?.let { candidates.add(it) }

    // 2. " Season N" — standalone season keyword not inside parens
    Regex("""\s+Season\s+\d""", RegexOption.IGNORE_CASE).find(raw)
        ?.range?.first?.let { candidates.add(it) }

    // 3. " [...]" — square brackets that aren't a lone 4-digit year
    Regex("""\s+\[(?!\d{4}])""").find(raw)
        ?.range?.first?.let { candidates.add(it) }

    val splitIdx = candidates.minOrNull()

    if (splitIdx != null && splitIdx > 0) {
        val title = raw.substring(0, splitIdx).trimEnd()
        val subtitle = raw.substring(splitIdx).trim()
        return Pair(title, subtitle.ifEmpty { null })
    }

    return Pair(raw, null)
}
