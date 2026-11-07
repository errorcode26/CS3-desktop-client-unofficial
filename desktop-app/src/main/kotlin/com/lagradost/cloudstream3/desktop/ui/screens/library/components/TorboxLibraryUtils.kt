package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.common.logging.AppLogger

internal fun copyToClipboard(text: String) {
    try {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    } catch (e: Exception) {
        AppLogger.e("TorboxCloudLibraryView: Failed to copy to clipboard", e)
    }
}

private val VIDEO_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".mov", ".webm", ".ts", ".m4v")

internal fun isVideoFile(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return VIDEO_EXTENSIONS.any { lower.endsWith(it) } &&
        !lower.contains("sample") &&
        !lower.contains("trailer")
}

internal fun cleanItemTitle(raw: String): String {
    var title = raw.trim()
    if (!title.contains(' ') && (title.contains('.') || title.contains('_'))) {
        title = title.replace('.', ' ').replace('_', ' ')
    }
    return title.trim()
}

internal fun compareNatural(s1: String, s2: String): Int {
    var i1 = 0
    var i2 = 0
    while (i1 < s1.length && i2 < s2.length) {
        val c1 = s1[i1]
        val c2 = s2[i2]
        if (c1.isDigit() && c2.isDigit()) {
            var j1 = i1
            var j2 = i2
            while (j1 < s1.length && s1[j1].isDigit()) j1++
            while (j2 < s2.length && s2[j2].isDigit()) j2++
            val num1 = s1.substring(i1, j1).toLongOrNull() ?: 0L
            val num2 = s2.substring(i2, j2).toLongOrNull() ?: 0L
            val diff = num1.compareTo(num2)
            if (diff != 0) return diff
            i1 = j1
            i2 = j2
        } else {
            val diff = c1.lowercaseChar().compareTo(c2.lowercaseChar())
            if (diff != 0) return diff
            i1++
            i2++
        }
    }
    return s1.length.compareTo(s2.length)
}

internal val naturalNameComparator = Comparator<TorboxFile> { a, b ->
    compareNatural(a.name, b.name)
}

internal fun extractEpisodeIndex(fileName: String, fallbackIndex: Int): Pair<Int?, Int?> {
    val sRegex = Regex("""(?i)[sS](\d{1,2})[eE](\d{1,3})""")
    val match = sRegex.find(fileName)
    if (match != null) {
        val s = match.groupValues[1].toIntOrNull()
        val e = match.groupValues[2].toIntOrNull()
        return Pair(s ?: 1, e ?: (fallbackIndex + 1))
    }
    val xRegex = Regex("""(?i)(\d{1,2})x(\d{1,3})""")
    val xMatch = xRegex.find(fileName)
    if (xMatch != null) {
        val s = xMatch.groupValues[1].toIntOrNull()
        val e = xMatch.groupValues[2].toIntOrNull()
        return Pair(s ?: 1, e ?: (fallbackIndex + 1))
    }
    val epRegex = Regex("""(?i)(?:ep|episode|e)[._\s-]*(\d{1,3})""")
    val epMatch = epRegex.find(fileName)
    if (epMatch != null) {
        val e = epMatch.groupValues[1].toIntOrNull()
        return Pair(1, e ?: (fallbackIndex + 1))
    }
    return Pair(null, fallbackIndex + 1)
}

@Suppress("DEPRECATION_ERROR")
internal fun createTorboxEpisode(file: TorboxFile, index: Int): Episode {
    val (season, epNum) = extractEpisodeIndex(file.name, index)
    return Episode(
        data = file.id.toString(),
        name = file.name,
        season = season,
        episode = epNum,
    )
}

internal val TorboxFile.formattedFileSize: String
    get() {
        if (size <= 0L) return ""
        val gb = size.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else {
            val mb = size.toDouble() / (1024 * 1024)
            String.format(java.util.Locale.US, "%.0f MB", mb)
        }
    }
