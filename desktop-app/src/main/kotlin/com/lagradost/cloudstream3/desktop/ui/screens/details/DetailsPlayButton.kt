package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.common.storage.WatchHistory

@Composable
fun DetailsPlayButton(
    modifier: Modifier = Modifier,
    data: LoadResponse,
    provider: MainAPI,
    latestHistory: WatchHistory? = null,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
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

    val buttonLabel = remember(data, latestHistory, targetEp) {
        if (latestHistory != null) {
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

    val onPlayClick = {
        if (targetEp != null) {
            onPlay(targetEp)
        } else {
            val ep = when (data) {
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
            if (ep != null) {
                onPlay(ep)
            }
        }
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .widthIn(min = 190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onPlayClick() }
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF0F0F0F),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = buttonLabel,
                color = Color(0xFF0F0F0F),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
