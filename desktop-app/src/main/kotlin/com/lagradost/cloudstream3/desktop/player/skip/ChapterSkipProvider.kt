package com.lagradost.cloudstream3.desktop.player.skip

import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState

object ChapterSkipProvider : ISkipProvider {
    override val id: String = "chapter"
    override val name: String = "Embedded Chapters (Offline)"

    private val OP_REGEX = Regex("""(?i)\b(op|opening|intro|prologue)\b""")
    private val ED_REGEX = Regex("""(?i)\b(ed|ending|outro|credits)\b""")
    private val RECAP_REGEX = Regex("""(?i)\b(recap)\b""")
    private val PREVIEW_REGEX = Regex("""(?i)\b(preview)\b""")

    fun parseChapters(chapters: List<PlayerState.Chapter>, totalDurationMs: Long): List<SkipInterval> {
        if (chapters.isEmpty()) return emptyList()

        val intervals = mutableListOf<SkipInterval>()
        val sorted = chapters.sortedBy { it.timeMs }

        for (i in sorted.indices) {
            val current = sorted[i]
            val nextTimeMs = if (i + 1 < sorted.size) sorted[i + 1].timeMs else totalDurationMs
            val rawTitle = current.title.trim()

            val type = when {
                OP_REGEX.containsMatchIn(rawTitle) -> SkipType.OPENING
                ED_REGEX.containsMatchIn(rawTitle) -> SkipType.ENDING
                RECAP_REGEX.containsMatchIn(rawTitle) -> SkipType.RECAP
                PREVIEW_REGEX.containsMatchIn(rawTitle) -> SkipType.PREVIEW
                else -> null
            }

            if (type != null && nextTimeMs > current.timeMs) {
                val label = when (type) {
                    SkipType.OPENING -> "Skip Opening"
                    SkipType.ENDING -> "Skip Ending"
                    SkipType.RECAP -> "Skip Recap"
                    SkipType.PREVIEW -> "Skip Preview"
                    else -> "Skip Intro"
                }
                intervals.add(
                    SkipInterval(
                        startMs = current.timeMs,
                        endMs = nextTimeMs,
                        type = type,
                        label = label,
                        providerId = id
                    )
                )
            }
        }

        return intervals
    }

    override suspend fun getSkipIntervals(query: SkipQuery): List<SkipInterval> {
        // Chapters are extracted live from MPV engine via parseChapters()
        return emptyList()
    }
}
