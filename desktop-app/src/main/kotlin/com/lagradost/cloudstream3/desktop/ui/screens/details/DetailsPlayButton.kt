package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun DetailsPlayButton(
    modifier: Modifier = Modifier,
    data: LoadResponse,
    provider: MainAPI,
    latestHistory: WatchHistory? = null,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
) {
    val allEpisodes = when (data) {
        is com.lagradost.cloudstream3.TvSeriesLoadResponse -> data.episodes
        is com.lagradost.cloudstream3.AnimeLoadResponse -> data.episodes.values.flatten()
        else -> emptyList()
    }
    val sortedEpisodes = allEpisodes.sortedWith(
        compareBy<com.lagradost.cloudstream3.Episode> { it.season ?: 1 }
            .thenBy { it.episode ?: 1 },
    )

    val isLatestCompleted = latestHistory != null && latestHistory.duration > 0 &&
        PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)

    val targetEp = if (latestHistory != null && sortedEpisodes.isNotEmpty()) {
        if (isLatestCompleted) {
            val currentIdx = sortedEpisodes.indexOfFirst { it.data == latestHistory.episodeId }
            if (currentIdx != -1 && currentIdx + 1 < sortedEpisodes.size) {
                sortedEpisodes[currentIdx + 1]
            } else {
                sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
            }
        } else {
            sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
        }
    } else {
        sortedEpisodes.firstOrNull()
    }

    val targetActionEp = targetEp ?: when (data) {
        is com.lagradost.cloudstream3.MovieLoadResponse -> {
            if (data.dataUrl.isNotBlank()) {
                provider.newEpisode(data.dataUrl) {
                    name = data.name
                    description = data.plot
                    posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                }
            } else null
        }
        is com.lagradost.cloudstream3.TorrentLoadResponse -> {
            val torrentUrl = data.torrent ?: data.magnet ?: ""
            if (torrentUrl.isNotBlank()) {
                provider.newEpisode(torrentUrl) {
                    name = data.name
                    description = data.plot
                    posterUrl = data.posterUrl
                }
            } else null
        }
        is com.lagradost.cloudstream3.LiveStreamLoadResponse -> {
            if (data.dataUrl.isNotBlank()) {
                provider.newEpisode(data.dataUrl) {
                    name = data.name
                    description = data.plot
                    posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                }
            } else null
        }
        else -> null
    }

    val isUnavailable = targetActionEp == null

    val buttonLabel = remember(data, latestHistory, targetEp, isLatestCompleted, isUnavailable) {
        if (isUnavailable) {
            "Unavailable on ${provider.name}"
        } else if (latestHistory != null && !isLatestCompleted && latestHistory.position > 0) {
            if (targetEp?.episode != null) {
                "Resume E${targetEp.episode}"
            } else {
                "Resume"
            }
        } else {
            if (targetEp?.season != null && targetEp.episode != null) {
                "Play S${targetEp.season} E${targetEp.episode}"
            } else if (targetEp?.episode != null) {
                "Play E${targetEp.episode}"
            } else {
                "Play"
            }
        }
    }

    val progress = remember(latestHistory, isLatestCompleted) {
        if (latestHistory != null && latestHistory.duration > 0 && !isLatestCompleted && latestHistory.position > 0) {
            (latestHistory.position.toFloat() / latestHistory.duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val timeLeftStr = remember(latestHistory, progress) {
        if (latestHistory != null && latestHistory.duration > 0 && progress > 0f && progress < 1f) {
            val leftSeconds = (latestHistory.duration - latestHistory.position).coerceAtLeast(0)
            val leftMins = leftSeconds / 60L
            val hours = leftMins / 60L
            val mins = leftMins % 60L
            when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m left"
                hours > 0 -> "${hours}h left"
                leftMins > 0 -> "${leftMins}m left"
                else -> "< 1m left"
            }
        } else {
            null
        }
    }

    val finalButtonText = remember(buttonLabel, timeLeftStr, isUnavailable) {
        if (!isUnavailable && !timeLeftStr.isNullOrBlank()) {
            "$buttonLabel  •  $timeLeftStr"
        } else {
            buttonLabel
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val isNarrow = maxWidth < 260.dp
        val shape = RoundedCornerShape(if (isNarrow) 10.dp else 12.dp)

        Box(
            modifier = Modifier
                .wrapContentWidth()
                .widthIn(min = if (isNarrow) 180.dp else 300.dp, max = 450.dp)
                .height(if (isNarrow) 44.dp else 52.dp)
                .clip(shape)
                .background(if (isUnavailable) Color(0xFFE50914).copy(alpha = 0.15f) else Color.White)
                .border(
                    BorderStroke(
                        1.2.dp,
                        if (isUnavailable) Color(0xFFE50914).copy(alpha = 0.5f) else Color.Transparent,
                    ),
                    shape,
                )
                .clickable {
                    if (isUnavailable) {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(
                            "No streaming links or episodes were found on ${provider.name} for this title.",
                        )
                    } else {
                        onPlay(targetActionEp)
                    }
                }
                .padding(horizontal = if (isNarrow) 20.dp else 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (isUnavailable) Icons.Default.CloudOff else Icons.Default.PlayArrow,
                    contentDescription = if (isUnavailable) "Unavailable" else "Play",
                    tint = if (isUnavailable) Color(0xFFFF6B6B) else Color(0xFF0F0F0F),
                    modifier = Modifier.size(if (isNarrow) 20.dp else 26.dp),
                )
                Spacer(Modifier.width(if (isNarrow) 6.dp else 10.dp))
                Text(
                    text = if (isUnavailable && isNarrow) "Unavailable" else finalButtonText,
                    color = if (isUnavailable) Color(0xFFFF6B6B) else Color(0xFF0F0F0F),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isNarrow) 14.sp else 16.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DetailsDownloadButton(
    modifier: Modifier = Modifier,
    data: LoadResponse,
    provider: MainAPI,
    latestHistory: WatchHistory? = null,
    onDownload: (com.lagradost.cloudstream3.Episode) -> Unit,
) {
    val allEpisodes = remember(data) {
        when (data) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> data.episodes
            is com.lagradost.cloudstream3.AnimeLoadResponse -> data.episodes.values.flatten()
            else -> emptyList()
        }
    }
    val sortedEpisodes = remember(allEpisodes) {
        allEpisodes.sortedWith(
            compareBy<com.lagradost.cloudstream3.Episode> { it.season ?: 1 }
                .thenBy { it.episode ?: 1 },
        )
    }
    val targetEp = remember(sortedEpisodes, latestHistory) {
        if (latestHistory != null && sortedEpisodes.isNotEmpty()) {
            sortedEpisodes.find { it.data == latestHistory.episodeId } ?: sortedEpisodes.firstOrNull()
        } else {
            sortedEpisodes.firstOrNull()
        }
    }
    val targetActionEp = remember(targetEp, data) {
        targetEp ?: when (data) {
            is com.lagradost.cloudstream3.MovieLoadResponse -> provider.newEpisode(data.dataUrl) {
                name = data.name
                description = data.plot
                posterUrl = data.backgroundPosterUrl ?: data.posterUrl
            }
            is com.lagradost.cloudstream3.TorrentLoadResponse -> provider.newEpisode(data.torrent ?: data.magnet ?: "") {
                name = data.name
                description = data.plot
                posterUrl = data.posterUrl
            }
            is com.lagradost.cloudstream3.LiveStreamLoadResponse -> provider.newEpisode(data.dataUrl) {
                name = data.name
                description = data.plot
                posterUrl = data.backgroundPosterUrl ?: data.posterUrl
            }
            else -> null
        }
    }

    Surface(
        onClick = { targetActionEp?.let { onDownload(it) } },
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
