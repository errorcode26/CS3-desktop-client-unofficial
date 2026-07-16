package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import androidx.compose.ui.zIndex

@Composable
fun EpisodesOverlay(
    launchData: VideoLaunchData,
    onClose: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
) {
    val loadResponse = launchData.loadResponse

    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var selectedDub by remember { mutableStateOf<DubStatus?>(null) }

    val seasons = remember(loadResponse) {
        if (loadResponse is TvSeriesLoadResponse) {
            loadResponse.episodes.mapNotNull { it.season }.distinct().sorted()
        } else {
            emptyList()
        }
    }

    val dubs = remember(loadResponse) {
        if (loadResponse is AnimeLoadResponse) {
            loadResponse.episodes.keys.toList()
        } else {
            emptyList()
        }
    }

    LaunchedEffect(loadResponse) {
        if (selectedSeason == null && seasons.isNotEmpty()) {
            // Find current episode season from history if possible
            selectedSeason = launchData.history.season ?: seasons.firstOrNull()
        }
        if (selectedDub == null && dubs.isNotEmpty()) {
            selectedDub = dubs.firstOrNull()
        }
    }

    val currentEpisodes = remember(loadResponse, selectedSeason, selectedDub) {
        when (loadResponse) {
            is TvSeriesLoadResponse -> {
                loadResponse.episodes.filter { selectedSeason == null || it.season == selectedSeason }
            }
            is AnimeLoadResponse -> {
                loadResponse.episodes[selectedDub] ?: emptyList<Episode>()
            }
            else -> emptyList<Episode>()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight(0.85f)
            .width(420.dp)
            .padding(end = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F16)) // Dark bluish/black exact to screenshot
            .pointerInput(Unit) { detectTapGestures(onTap = {}, onDoubleTap = {}) }
            .padding(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Close button (optional, but good for accessibility)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            if (loadResponse != null) {
                // Backdrop or Poster
                val headerImg = loadResponse.backgroundPosterUrl ?: loadResponse.posterUrl
                if (headerImg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        coil3.compose.AsyncImage(
                            model = headerImg,
                            contentDescription = "Show Backdrop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Title
                Text(
                    text = loadResponse.name.uppercase(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                    lineHeight = 24.sp,
                )

                // Meta info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val duration = if (loadResponse is TvSeriesLoadResponse) {
                        loadResponse.duration
                    } else if (loadResponse is AnimeLoadResponse) {
                        loadResponse.duration
                    } else {
                        null
                    }
                    val year = if (loadResponse is TvSeriesLoadResponse) {
                        loadResponse.year
                    } else if (loadResponse is AnimeLoadResponse) {
                        loadResponse.year
                    } else {
                        null
                    }
                    Text(
                        text = if (duration != null) "$duration min" else "",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = if (year != null) "$year-" else "",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }

                // Synopsis
                Text(
                    text = loadResponse.plot ?: "",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 24.dp),
                    lineHeight = 18.sp,
                )

                // Season Selector
                if (seasons.isNotEmpty() || dubs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hasPrev = if (loadResponse is TvSeriesLoadResponse) {
                            seasons.indexOf(selectedSeason) > 0
                        } else if (loadResponse is AnimeLoadResponse) {
                            dubs.indexOf(selectedDub) > 0
                        } else false

                        val hasNext = if (loadResponse is TvSeriesLoadResponse) {
                            seasons.indexOf(selectedSeason) < seasons.size - 1
                        } else if (loadResponse is AnimeLoadResponse) {
                            dubs.indexOf(selectedDub) < dubs.size - 1
                        } else false

                        Text(
                            text = "< Prev",
                            color = Color.White.copy(alpha = if (hasPrev) 1f else 0.5f),
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(enabled = hasPrev) {
                                if (loadResponse is TvSeriesLoadResponse) {
                                    val idx = seasons.indexOf(selectedSeason)
                                    if (idx > 0) selectedSeason = seasons[idx - 1]
                                } else if (loadResponse is AnimeLoadResponse) {
                                    val idx = dubs.indexOf(selectedDub)
                                    if (idx > 0) selectedDub = dubs[idx - 1]
                                }
                            },
                        )

                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.zIndex(10f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                val selectorText = if (loadResponse is TvSeriesLoadResponse) "Season $selectedSeason" else selectedDub?.name ?: ""
                                Text(
                                    text = selectorText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = expanded,
                                modifier = Modifier.align(Alignment.TopCenter).offset(y = 32.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E1E28),
                                    tonalElevation = 8.dp,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.widthIn(min = 120.dp).heightIn(max = 300.dp)
                                ) {
                                    LazyColumn {
                                        if (loadResponse is TvSeriesLoadResponse) {
                                            items(seasons) { season ->
                                                val isSelected = season == selectedSeason
                                                Text(
                                                    text = "Season $season",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF9151FF) else Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedSeason = season
                                                            expanded = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                )
                                            }
                                        } else if (loadResponse is AnimeLoadResponse) {
                                            items(dubs) { dub ->
                                                val isSelected = dub == selectedDub
                                                Text(
                                                    text = dub.name,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF9151FF) else Color.White,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedDub = dub
                                                            expanded = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Next >",
                            color = Color.White.copy(alpha = if (hasNext) 1f else 0.5f),
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(enabled = hasNext) {
                                if (loadResponse is TvSeriesLoadResponse) {
                                    val idx = seasons.indexOf(selectedSeason)
                                    if (idx < seasons.size - 1) selectedSeason = seasons[idx + 1]
                                } else if (loadResponse is AnimeLoadResponse) {
                                    val idx = dubs.indexOf(selectedDub)
                                    if (idx < dubs.size - 1) selectedDub = dubs[idx + 1]
                                }
                            },
                        )
                    }
                }

                // Episodes List
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(currentEpisodes) { ep ->
                        val isCurrent = ep.data == launchData.history.episodeId
                        EpisodeCard(episode = ep, showPosterUrl = loadResponse.posterUrl, isCurrent = isCurrent, onClick = { onPlayEpisode(ep) })
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No episode data available.", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(episode: Episode, showPosterUrl: String? = null, isCurrent: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(if (isCurrent) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
        ) {
            val fallback = episode.posterUrl ?: showPosterUrl
            if (fallback != null) {
                coil3.compose.AsyncImage(
                    model = fallback,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Placeholder
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episode}. ${episode.name ?: "Episode ${episode.episode}"}",
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Jul 11, 2024", // Mocked date for now since Episode doesn't always have one
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}
