package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

data class EpisodePresentation(
    val releaseStatus: EpisodeReleaseStatus,
    val isEpisodeLocked: Boolean,
    val targetUrl: String?,
    val progress: Float,
    val isWatched: Boolean,
    val hasStartedPlayback: Boolean,
    val shouldHideSpoilers: Boolean,
    val finalTitle: String,
    val runTimeStr: String?,
    val cleanDesc: String,
    val hasDesc: Boolean,
    val formattedDate: String?,
    val durationText: String?,
)

object EpisodePresentationHelper {
    fun compute(
        ep: Episode,
        history: WatchHistory?,
        provider: MainAPI,
        data: LoadResponse,
        uiState: DetailsUiState?,
        isAntiSpoiler: Boolean,
        lockUnreleasedEpisodes: Boolean,
    ): EpisodePresentation {
        val releaseStatus = parseEpisodeReleaseStatus(ep)
        val isEpisodeLocked = releaseStatus.isUnreleased && lockUnreleasedEpisodes

        val epImg = (provider.fixUrlNull(ep.posterUrl) ?: ep.posterUrl)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { it.isNotBlank() && !it.contains("imgbb") }

        val fallbackBackdrop = (uiState?.enrichedBackdropUrl ?: provider.fixUrlNull(data.backgroundPosterUrl) ?: data.backgroundPosterUrl)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { it.isNotBlank() }

        val fallbackPoster = (provider.fixUrlNull(data.posterUrl) ?: data.posterUrl)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { it.isNotBlank() && !it.contains("imgbb") }

        val targetUrl = epImg ?: fallbackBackdrop ?: fallbackPoster

        val progress = if (history != null && history.duration > 0) {
            if (PlayerLinkHandler.isCompleted(history.position, history.duration)) {
                1f
            } else {
                (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
            }
        } else {
            0f
        }

        val isWatched = progress > 0.9f
        val hasStartedPlayback = progress > 0f || (history != null && history.position > 5)
        val shouldHideSpoilers = isAntiSpoiler && !hasStartedPlayback && !isWatched

        val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"
        val titleCleaned = rawTitle
            .replace(EPISODE_E_PREFIX_REGEX, "")
            .replace(EPISODE_WORD_PREFIX_REGEX, "")
            .trim()
        val finalTitle = if (titleCleaned.isBlank()) "Episode ${ep.episode ?: "?"}" else titleCleaned

        val epRunTime = ep.runTime ?: data.duration
        val runTimeStr = epRunTime?.let { dur ->
            val mins = if (dur > 1000) dur / 60 else dur
            if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else {
                "${mins}m"
            }
        }

        val rawDesc = ep.description ?: ""
        val formattedDate = releaseStatus.formattedDate
        val cleanDesc = TitleUtils.cleanHtml(rawDesc.replace(EPISODE_DATE_REGEX, "").trim()) ?: ""
        val hasDesc = cleanDesc.isNotBlank()

        val durationText = if (history != null && history.duration > 0) {
            if (progress > 0f && progress < 1f) {
                val leftSeconds = history.duration - history.position
                val leftMins = leftSeconds / 60L
                if (leftMins >= 60) {
                    "${leftMins / 60}h ${leftMins % 60}m left"
                } else if (leftMins > 0) {
                    "${leftMins}m left"
                } else {
                    "<1m left"
                }
            } else {
                val totalMins = history.duration / 60L
                if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
            }
        } else if (epRunTime != null) {
            val totalMins = if (epRunTime > 1000) epRunTime / 60 else epRunTime
            if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
        } else {
            null
        }

        return EpisodePresentation(
            releaseStatus = releaseStatus,
            isEpisodeLocked = isEpisodeLocked,
            targetUrl = targetUrl,
            progress = progress,
            isWatched = isWatched,
            hasStartedPlayback = hasStartedPlayback,
            shouldHideSpoilers = shouldHideSpoilers,
            finalTitle = finalTitle,
            runTimeStr = runTimeStr,
            cleanDesc = cleanDesc,
            hasDesc = hasDesc,
            formattedDate = formattedDate,
            durationText = durationText,
        )
    }
}

@Composable
fun rememberEpisodePresentation(
    ep: Episode,
    history: WatchHistory?,
    provider: MainAPI,
    data: LoadResponse,
    uiState: DetailsUiState?,
    isAntiSpoiler: Boolean,
    lockUnreleasedEpisodes: Boolean,
): EpisodePresentation {
    return remember(ep, history, data, uiState?.enrichedBackdropUrl, isAntiSpoiler, lockUnreleasedEpisodes) {
        EpisodePresentationHelper.compute(
            ep = ep,
            history = history,
            provider = provider,
            data = data,
            uiState = uiState,
            isAntiSpoiler = isAntiSpoiler,
            lockUnreleasedEpisodes = lockUnreleasedEpisodes,
        )
    }
}
