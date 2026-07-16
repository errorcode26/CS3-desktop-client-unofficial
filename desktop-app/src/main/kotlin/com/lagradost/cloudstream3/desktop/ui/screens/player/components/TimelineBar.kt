package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineBar(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onEpisodesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSourcesClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onAspectRatioClick: () -> Unit = {},
    onSkipPrevious: (() -> Unit)? = null,
    onSkipNext: (() -> Unit)? = null,
) {
    val positionMs by playerState.positionMs.collectAsState()
    val durationMs by playerState.durationMs.collectAsState()
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }

    val currentMs = if (isDragging) dragPositionMs else positionMs

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Top Row: Scrubber + Time Text
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.CenterStart) {
                TimelineScrubber(
                    playerState = playerState,
                    isDragging = isDragging,
                    dragPositionMs = dragPositionMs,
                    onDragStart = {
                        isDragging = true
                        dragPositionMs = it
                    },
                    onDragEnd = {
                        isDragging = false
                        playerState.seekTo(dragPositionMs)
                    },
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "${formatTime(currentMs)} / ${formatTime(durationMs)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Bottom Row: Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left Side Controls (Play/Pause, Rewind, Forward, Volume)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isPaused by playerState.isPaused.collectAsState()
                IconButton(onClick = { playerState.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { playerState.seekBy(-10000L) }) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                IconButton(onClick = { playerState.seekBy(10000L) }) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                val volume by playerState.volume.collectAsState()
                IconButton(onClick = {
                    if (volume > 0f) playerState.setVolume(0f) else playerState.setVolume(100f)
                }) {
                    Icon(
                        imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
                VolumeScrubber(playerState)
            }

            // Right Side Controls (Cloud/Sources, Episodes, Settings, Fullscreen)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSourcesClick) {
                    Icon(Icons.Default.Cloud, contentDescription = "Sources", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onEpisodesClick) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Episodes", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = onFullscreenClick) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineScrubber(
    playerState: PlayerState,
    isDragging: Boolean,
    dragPositionMs: Long,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val positionMs by playerState.positionMs.collectAsState()
    val durationMs by playerState.durationMs.collectAsState()
    val bufferMs by playerState.bufferMs.collectAsState()

    val currentMs = if (isDragging) dragPositionMs else positionMs
    val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferProgress = if (durationMs > 0) (bufferMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val trackHeight by animateDpAsState(if (isHovered || isDragging) 6.dp else 4.dp)
    val thumbSize by animateDpAsState(if (isHovered || isDragging) 14.dp else 0.dp)

    Slider(
        value = progress,
        onValueChange = {
            onDragStart((it * durationMs).toLong())
        },
        onValueChangeFinished = {
            onDragEnd()
        },
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        },
        track = { sliderState ->
            Box(
                modifier = Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Buffer track
                Box(
                    modifier = Modifier.fillMaxWidth(fraction = bufferProgress).height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.5f)),
                )
                // Played track
                Box(
                    modifier = Modifier.fillMaxWidth(fraction = sliderState.value).height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color(0xFF9151FF)),
                )
            }
        },
    )

    if (isDragging) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth(progress), contentAlignment = Alignment.TopEnd) {
                Text(
                    text = formatTime(dragPositionMs),
                    modifier = Modifier
                        .offset(y = (-30).dp, x = 15.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun VolumeScrubber(playerState: PlayerState) {
    val volume by playerState.volume.collectAsState()
    val volInteractionSource = remember { MutableInteractionSource() }
    val isVolHovered by volInteractionSource.collectIsHoveredAsState()
    val volumeHeight by animateDpAsState(if (isVolHovered) 6.dp else 3.dp)

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(24.dp)
            .hoverable(volInteractionSource)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val pct = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    playerState.setVolume(pct * 100f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val pct = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    playerState.setVolume(pct * 100f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Background track
        Box(modifier = Modifier.fillMaxWidth().height(volumeHeight).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))
        // Foreground track
        Box(modifier = Modifier.fillMaxWidth(fraction = (volume / 100f).coerceIn(0f, 1f)).height(volumeHeight).clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}
