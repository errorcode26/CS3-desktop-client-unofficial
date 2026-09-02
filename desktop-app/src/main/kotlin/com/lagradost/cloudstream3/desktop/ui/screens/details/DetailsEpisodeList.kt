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
    val isSynthetic = ep.data.startsWith("unreleased_") || ep.data.startsWith("synthetic_") || ep.data.isBlank()
    val rawDesc = ep.description ?: ""
    val dateMatch = EPISODE_DATE_REGEX.find(rawDesc)
    val rawDate = dateMatch?.groupValues?.get(1)?.trim()

    val baseStatus = if (rawDate.isNullOrBlank()) {
        EpisodeReleaseStatus(
            isUnreleased = false,
            isMissingFromProvider = isSynthetic,
            formattedDate = null,
            rawDate = null,
            statusBadgeText = if (isSynthetic) "Unavailable" else null,
            daysUntilRelease = null,
        )
    } else {
        synchronized(releaseStatusLock) {
            releaseStatusCache.getOrPut(rawDate) {
                computeEpisodeReleaseStatus(rawDate)
            }
        }
    }

    val isMissing = isSynthetic && !baseStatus.isUnreleased
    val effectiveBadge = when {
        baseStatus.isUnreleased -> baseStatus.statusBadgeText
        isMissing -> if (!providerName.isNullOrBlank()) "Missing from $providerName" else "Unavailable"
        else -> null
    }

    return baseStatus.copy(
        isMissingFromProvider = isMissing,
        statusBadgeText = effectiveBadge,
    )
}

private val OUTPUT_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)
    .withZone(java.time.ZoneOffset.UTC)

private val ISO_DATE_FORMATTERS = listOf(
    java.time.format.DateTimeFormatter.ISO_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC) to true,
)

private fun computeEpisodeReleaseStatus(rawDate: String): EpisodeReleaseStatus {
    var releaseEpochMs: Long? = null
    var formattedOut: String? = null

    for ((formatter, isDateOnly) in ISO_DATE_FORMATTERS) {
        try {
            val temporal = formatter.parseBest(rawDate, java.time.Instant::from, java.time.LocalDate::from)
            val instant = when (temporal) {
                is java.time.Instant -> temporal
                is java.time.LocalDate -> temporal.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                else -> null
            }
            if (instant != null) {
                formattedOut = OUTPUT_DATE_FORMATTER.format(instant)
                releaseEpochMs = if (isDateOnly) {
                    instant.toEpochMilli() + 86_400_000L
                } else {
                    instant.toEpochMilli()
                }
                break
            }
        } catch (_: Exception) {
        }
    }

    val now = System.currentTimeMillis()
    val rEpoch = releaseEpochMs
    val isFuture = rEpoch != null && rEpoch > now
    val daysUntil = if (rEpoch != null && rEpoch > now) {
        val diffMs = rEpoch - now
        maxOf(1L, diffMs / 86_400_000L)
    } else {
        null
    }

    val badgeText = when {
        !isFuture -> null
        daysUntil != null && daysUntil > 1 -> "Airs in $daysUntil days"
        daysUntil == 1L -> "Airs tomorrow"
        formattedOut != null -> "Airs $formattedOut"
        else -> "Unreleased"
    }

    return EpisodeReleaseStatus(
        isUnreleased = isFuture,
        formattedDate = formattedOut ?: rawDate,
        rawDate = rawDate,
        statusBadgeText = badgeText,
        daysUntilRelease = daysUntil,
    )
}
