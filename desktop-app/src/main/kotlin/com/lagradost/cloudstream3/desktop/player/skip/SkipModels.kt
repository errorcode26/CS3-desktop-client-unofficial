package com.lagradost.cloudstream3.desktop.player.skip

enum class SkipType {
    OPENING,
    ENDING,
    RECAP,
    PREVIEW,
    INTRO,
    OUTRO,
    MIXED_OP,
    MIXED_ED;

    companion object {
        fun fromString(type: String?): SkipType {
            return when (type?.lowercase()?.trim()) {
                "op", "opening" -> OPENING
                "ed", "ending" -> ENDING
                "recap" -> RECAP
                "preview" -> PREVIEW
                "intro" -> INTRO
                "outro" -> OUTRO
                "mixed-op" -> MIXED_OP
                "mixed-ed" -> MIXED_ED
                else -> INTRO
            }
        }
    }
}

data class SkipInterval(
    val startMs: Long,
    val endMs: Long,
    val type: SkipType,
    val label: String,
    val providerId: String
)

data class SkipQuery(
    val title: String,
    val episode: Int = 1,
    val season: Int = 1,
    val durationSeconds: Double = 0.0,
    val malId: Int? = null,
    val anilistId: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null
)
