package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// -------------------------------------------------------------------------------------------------
// TAB 3: Live Player & Stream Diagnostics
// -------------------------------------------------------------------------------------------------

@Composable
internal fun PlayerDiagnosticsTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    val diag = state.playerDiagnostics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live MPV Player & Stream Diagnostics", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (diag.isAttached) DevLevelDebug.copy(alpha = 0.2f) else DevLevelWarn.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = if (diag.isAttached) "PLAYER ATTACHED" else "PLAYER IDLE",
                        color = if (diag.isAttached) DevLevelDebug else DevLevelWarn,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = { onEvent(DevStudioUiEvent.RefreshPlayerDiagnostics) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh", fontSize = 11.sp, color = Color.LightGray)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Grid Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card 1: Playback State
            DiagnosticCard(
                title = "Playback Engine",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField(
                    "Status",
                    if (diag.isBuffering) {
                        "Buffering..."
                    } else if (diag.isPaused) {
                        "Paused"
                    } else {
                        "Playing"
                    },
                )
                InspectorField("Position", "${diag.positionMs / 1000}s / ${diag.durationMs / 1000}s")
                InspectorField("Buffer Ahead", "${diag.bufferMs / 1000}s")
                InspectorField("Probing Active", if (diag.isProbing) "YES" else "NO")
                InspectorField("Speed / Volume", "${diag.playbackSpeed}x • ${diag.volume.toInt()}%")
            }

            // Card 2: Video & Decoder
            DiagnosticCard(
                title = "Video & Decoder",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Resolution", diag.resolution.ifBlank { "N/A" })
                InspectorField("Video Codec", diag.videoCodec.ifBlank { "N/A" })
                InspectorField("HW Decoder", diag.hwdec.ifBlank { "Auto / CPU" })
                InspectorField("FPS / Dropped", "${"%.1f".format(diag.fps)} fps • ${diag.droppedFrames} dropped")
                InspectorField("Bitrate", if (diag.videoBitrate > 0) "${diag.videoBitrate / 1000} kbps" else "N/A")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card 3: Audio & Tracks
            DiagnosticCard(
                title = "Audio & Track Stream",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Audio Codec", diag.audioCodec.ifBlank { "N/A" })
                InspectorField("Audio Bitrate", if (diag.audioBitrate > 0) "${diag.audioBitrate / 1000} kbps" else "N/A")
                InspectorField("Subtitles Loaded", "${diag.subtitleTracksCount} tracks")
                InspectorField("Audio Tracks Loaded", "${diag.audioTracksCount} tracks")
                InspectorField("Qualities Loaded", "${diag.videoTracksCount} variants")
            }

            // Card 4: Proxy & Shaders
            DiagnosticCard(
                title = "Stream Proxy & Shaders",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Active Shader", diag.activeShader)
                InspectorField("Interpolation (60fps)", if (diag.isInterpolationEnabled) "ENABLED" else "DISABLED")
                InspectorField("Discovered Proxy Audio", "${diag.proxyAudioTracksCount} tracks")
                InspectorField("Discovered Proxy Subs", "${diag.proxySubtitleTracksCount} tracks")
                InspectorField("Discovered Proxy Qualities", "${diag.proxyVideoTracksCount} variants")
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = DevCardDark,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = DevAccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
