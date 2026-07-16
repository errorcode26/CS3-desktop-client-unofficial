package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState

@Composable
fun SettingsDialog(
    playerState: PlayerState,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    lazyAudioTracks: List<PlayerState.LazyTrack> = emptyList(),
    lazySubtitleTracks: List<PlayerState.LazyTrack> = emptyList(),
    initialTab: Int = 0,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) } // 0: Audio, 1: Subtitles, 2: Speed, 3: Video

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141414),
            modifier = Modifier
                .padding(end = 48.dp, bottom = 120.dp)
                .widthIn(max = 400.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}, onDoubleTap = {}) },
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Player Settings", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                // Custom Segmented Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val tabs = listOf("Audio", "Subtitles", "Speed", "Video")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = currentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { currentTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (currentTab) {
                    0 -> AudioTab(playerState, lazyAudioTracks)
                    1 -> SubtitlesTab(playerState, subtitles, lazySubtitleTracks)
                    2 -> SpeedTab(playerState)
                    3 -> VideoTab(playerState)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTab(playerState: PlayerState) {
    val isInterpolationEnabled by playerState.isInterpolationEnabled.collectAsState()
    val videoTracks by playerState.videoTracks.collectAsState()
    val lazyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()
    val isAutoSelected = videoTracks.none { it.isSelected }

    Column {
        Text("Video Quality", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
        ) {
            item {
                SubtitleItem(
                    name = "Auto (Highest Quality)",
                    isSelected = isAutoSelected,
                    onClick = { playerState.setVideoTrack(null) },
                )
            }
            items(videoTracks.size) { index ->
                val track = videoTracks[index]
                SubtitleItem(
                    name = track.name,
                    isSelected = track.isSelected,
                    onClick = { playerState.setVideoTrack(track.id) },
                )
            }
            items(lazyVideoTracks.size) { index ->
                val track = lazyVideoTracks[index]
                SubtitleItem(
                    name = "${track.name} (Stream)",
                    isSelected = false,
                    onClick = {
                        playerState.loadLazyVideoTrack(
                            PlayerState.LazyTrack(url = track.url, name = track.name, language = track.language)
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Video Rendering", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable { playerState.setInterpolation(!isInterpolationEnabled) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interpolation Blending",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Smooths motion for low-framerate video",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = isInterpolationEnabled,
                onCheckedChange = { playerState.setInterpolation(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray,
                ),
            )
        }
    }
}

@Composable
private fun SpeedTab(playerState: PlayerState) {
    val currentSpeed by playerState.playbackSpeed.collectAsState()

    Column {
        Text("Playback Speed", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            IconButton(
                onClick = {
                    val newSpeed = (currentSpeed - 0.25f).coerceAtLeast(0.25f)
                    playerState.setSpeed(newSpeed)
                },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease Speed", tint = Color.White)
            }

            Text(
                text = "${String.format("%.2f", currentSpeed)}x",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            IconButton(
                onClick = {
                    val newSpeed = (currentSpeed + 0.25f).coerceAtMost(3.0f)
                    playerState.setSpeed(newSpeed)
                },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase Speed", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AudioTab(playerState: PlayerState, lazyTracks: List<PlayerState.LazyTrack> = emptyList()) {
    val audioTracks by playerState.audioTracks.collectAsState()

    Column {
        Text("Audio Track", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
        ) {
            items(audioTracks.size) { index ->
                val track = audioTracks[index]
                SubtitleItem(
                    name = track.name,
                    isSelected = track.isSelected,
                    onClick = {
                        playerState.setAudioTrack(track.id)
                    },
                )
            }
            items(lazyTracks.size) { index ->
                val track = lazyTracks[index]
                SubtitleItem(
                    name = track.name + " (Fetch)",
                    isSelected = false,
                    onClick = {
                        playerState.loadLazyAudioTrack(track)
                    },
                )
            }
            if (audioTracks.isEmpty() && lazyTracks.isEmpty()) {
                item {
                    Text("No additional audio tracks found.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SubtitlesTab(
    playerState: PlayerState,
    externalSubtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    lazyTracks: List<PlayerState.LazyTrack> = emptyList(),
) {
    val subtitleDelay by playerState.subtitleDelayMs.collectAsState()
    val subtitleTracks by playerState.subtitleTracks.collectAsState()
    val isNoneSelected = subtitleTracks.none { it.isSelected }

    Column {
        Text("Subtitle Track", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Subtitles List
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        ) {
            item {
                SubtitleItem(
                    name = "None",
                    isSelected = isNoneSelected,
                    onClick = {
                        playerState.setSubtitleTrack(null)
                    },
                )
            }
            if (subtitleTracks.isNotEmpty()) {
                item {
                    Text("Embedded Tracks", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(subtitleTracks.size) { index ->
                    val track = subtitleTracks[index]
                    SubtitleItem(
                        name = track.name,
                        isSelected = track.isSelected,
                        onClick = {
                            playerState.setSubtitleTrack(track.id)
                        },
                    )
                }
            }
            if (externalSubtitles.isNotEmpty()) {
                item {
                    Text("External Tracks (Click to load)", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(externalSubtitles.size) { index ->
                    val sub = externalSubtitles[index]
                    SubtitleItem(
                        name = sub.lang,
                        isSelected = false, // External tracks become embedded tracks once loaded
                        onClick = {
                            playerState.loadExternalSubtitle(sub.url)
                        },
                    )
                }
            }
            if (lazyTracks.isNotEmpty()) {
                item {
                    Text("HLS Subtitles (Click to fetch)", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(lazyTracks.size) { index ->
                    val track = lazyTracks[index]
                    SubtitleItem(
                        name = "${track.name} (${track.language})",
                        isSelected = false,
                        onClick = {
                            playerState.loadLazySubtitleTrack(track)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Subtitle Delay (Sync)", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            IconButton(
                onClick = { playerState.setSubtitleDelay(subtitleDelay - 250) },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "-250ms", tint = Color.White)
            }

            Text(
                text = "${if (subtitleDelay > 0) "+" else ""}$subtitleDelay ms",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            IconButton(
                onClick = { playerState.setSubtitleDelay(subtitleDelay + 250) },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "+250ms", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SubtitleItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else Color.LightGray,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF1DB954), androidx.compose.foundation.shape.CircleShape), // Green dot indicator
            )
        }
    }
}
