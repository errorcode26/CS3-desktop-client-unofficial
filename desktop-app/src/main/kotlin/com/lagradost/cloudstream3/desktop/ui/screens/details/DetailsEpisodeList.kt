package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.Episode

data class EpisodeReleaseStatus(
    val isUnreleased: Boolean,
    val isMissingFromProvider: Boolean = false,
    val formattedDate: String?,
    val rawDate: String?,
    val statusBadgeText: String?,
    val daysUntilRelease: Long?,
)

internal val EPISODE_DATE_REGEX = Regex("""\|\|DATE:(.*?)\|\|""")
internal val EPISODE_E_PREFIX_REGEX = Regex("""^(?i)(E[0-9]+[\s\-:]*)+""")
internal val EPISODE_WORD_PREFIX_REGEX = Regex("""^(?i)(Episode[\s]*[0-9]+[\s\-:]*)+""")

private const val MAX_RELEASE_STATUS_CACHE_SIZE = 500
private val releaseStatusCache = object : java.util.LinkedHashMap<String, EpisodeReleaseStatus>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpisodeReleaseStatus>?): Boolean {
        return size > MAX_RELEASE_STATUS_CACHE_SIZE
    }
}
private val releaseStatusLock = Any()

fun parseEpisodeReleaseStatus(ep: Episode, providerName: String? = null): EpisodeReleaseStatus {
    val isMissingSource = ep.data.isBlank() ||
        ep.data.startsWith("unreleased_") ||
        ep.data.startsWith("synthetic_") ||
        ep.data == "null" ||
        ep.data == "{}" ||
        ep.data == "[]"

    val rawDesc = ep.description ?: ""
    val dateMatch = EPISODE_DATE_REGEX.find(rawDesc)
    val rawDate = dateMatch?.groupValues?.get(1)?.trim()

    val baseStatus = if (rawDate.isNullOrBlank()) {
        EpisodeReleaseStatus(
            isUnreleased = isMissingSource,
            isMissingFromProvider = isMissingSource,
            formattedDate = null,
            rawDate = null,
            statusBadgeText = if (isMissingSource) {
                if (!providerName.isNullOrBlank()) "Missing from $providerName" else "Unavailable"
            } else null,
            daysUntilRelease = null,
        )
    } else {
        synchronized(releaseStatusLock) {
            releaseStatusCache.getOrPut(rawDate) {
                computeEpisodeReleaseStatus(rawDate)
            }
        }
    }

    // Protocol Ground Truth: If actual cached links exist, the stream is confirmed playable.
    val hasCachedLinks = com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.get(ep.data) != null
    if (hasCachedLinks) {
        return baseStatus.copy(
            isUnreleased = false,
            isMissingFromProvider = false,
            statusBadgeText = null,
            daysUntilRelease = null,
        )
    }

    if (isMissingSource) {
        val effectiveBadge = when {
            baseStatus.isUnreleased -> baseStatus.statusBadgeText ?: "Unreleased"
            baseStatus.statusBadgeText == "Airs today" -> if (!providerName.isNullOrBlank()) "Airs today (Not on $providerName)" else "Airs today (Unavailable)"
            !providerName.isNullOrBlank() -> "Missing from $providerName"
            else -> "Unavailable"
        }
        return baseStatus.copy(
            isUnreleased = baseStatus.isUnreleased || baseStatus.statusBadgeText == "Airs today",
            isMissingFromProvider = true,
            statusBadgeText = effectiveBadge,
        )
    }

    return baseStatus
}

private val OUTPUT_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)
    .withZone(java.time.ZoneOffset.UTC)

private val ISO_DATE_FORMATTERS = listOf(
    java.time.format.DateTimeFormatter.ISO_DATE_TIME to false,
    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE to true,
)

private fun computeEpisodeReleaseStatus(rawDate: String): EpisodeReleaseStatus {
    var formattedOut: String? = null
    var releaseDate: java.time.LocalDate? = null

    for ((formatter, _) in ISO_DATE_FORMATTERS) {
        try {
            val temporal = formatter.parseBest(rawDate, java.time.Instant::from, java.time.LocalDate::from)
            when (temporal) {
                is java.time.Instant -> {
                    formattedOut = OUTPUT_DATE_FORMATTER.format(temporal)
                    releaseDate = temporal.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    break
                }
                is java.time.LocalDate -> {
                    val instant = temporal.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                    formattedOut = OUTPUT_DATE_FORMATTER.format(instant)
                    releaseDate = temporal
                    break
                }
            }
        } catch (_: Exception) {
        }
    }

    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    val isFuture = releaseDate != null && releaseDate.isAfter(today)
    val isToday = releaseDate != null && releaseDate.isEqual(today)
    val daysUntil = if (releaseDate != null && isFuture) {
        java.time.temporal.ChronoUnit.DAYS.between(today, releaseDate)
    } else {
        null
    }

    val badgeText = when {
        isFuture && daysUntil != null && daysUntil > 1 -> "Airs in $daysUntil days"
        isFuture && daysUntil == 1L -> "Airs tomorrow"
        isToday -> "Airs today"
        isFuture && formattedOut != null -> "Airs $formattedOut"
        isFuture -> "Unreleased"
        else -> null
    }

    return EpisodeReleaseStatus(
        isUnreleased = isFuture,
        formattedDate = formattedOut ?: rawDate,
        rawDate = rawDate,
        statusBadgeText = badgeText,
        daysUntilRelease = daysUntil,
    )
}
