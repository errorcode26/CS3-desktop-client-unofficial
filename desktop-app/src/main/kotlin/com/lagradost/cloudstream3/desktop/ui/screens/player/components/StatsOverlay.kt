package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState

@Composable
fun StatsOverlay(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    val showStats by playerState.showStats.collectAsState()

    if (!showStats) return

    val videoCodec by playerState.videoCodec.collectAsState()
    val audioCodec by playerState.audioCodec.collectAsState()
    val hwdecCurrent by playerState.hwdecCurrent.collectAsState()
    val droppedFrames by playerState.droppedFrames.collectAsState()
    val fps by playerState.fps.collectAsState()
    val resolution by playerState.resolution.collectAsState()
    val videoBitrate by playerState.videoBitrate.collectAsState()
    val audioBitrate by playerState.audioBitrate.collectAsState()

    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Column {
            Text("Stats for nerds", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            StatRow("Resolution", resolution)
            StatRow("FPS", String.format("%.2f", fps))
            StatRow("Dropped Frames", droppedFrames.toString())
            StatRow("Video Codec", videoCodec)
            StatRow("Audio Codec", audioCodec)
            StatRow("HW Decoder", hwdecCurrent)
            StatRow("Video Bitrate", "${videoBitrate / 1024} kbps")
            StatRow("Audio Bitrate", "${audioBitrate / 1024} kbps")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(0.3f),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
