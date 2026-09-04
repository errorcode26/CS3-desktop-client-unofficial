package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Real-time clock and estimated completion timestamp badge for media details screen.
 */
@Composable
fun DetailsClockPill(
    latestHistory: WatchHistory?,
    data: LoadResponse,
    viewportWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val showCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
    val showEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
    val clockTimeFormat by AppearanceConfig.clockTimeFormat.collectAsState()

    if (!showCurrentTime && !showEndTime) return

    val currentFormattedTime by produceState(initialValue = "", key1 = clockTimeFormat) {
        val pattern = if (clockTimeFormat.isNotBlank()) clockTimeFormat else "h:mm a"
        while (true) {
            val formatter = try {
                SimpleDateFormat(pattern, Locale.getDefault())
            } catch (_: Exception) {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            }
            value = formatter.format(Date())
            delay(1000L)
        }
    }

    val progress = remember(latestHistory) {
        if (latestHistory != null && latestHistory.duration > 0) {
            if (PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)) {
                1f
            } else {
                (latestHistory.position.toFloat() / latestHistory.duration.toFloat()).coerceIn(0f, 1f)
            }
        } else {
            0f
        }
    }

    val remainingSecondsForEnd = remember(latestHistory, data, progress) {
        if (latestHistory != null && latestHistory.duration > 0) {
            if (progress > 0f && progress < 1f) {
                latestHistory.duration - latestHistory.position
            } else {
                latestHistory.duration
            }
        } else if (data is MovieLoadResponse && data.duration != null) {
            data.duration?.toLong()?.times(60L)
        } else {
            null
        }
    }

    val formattedEndTime = remember(remainingSecondsForEnd, currentFormattedTime, clockTimeFormat) {
        remainingSecondsForEnd?.let { secs ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.SECOND, secs.toInt())
            val pattern = if (clockTimeFormat.isNotBlank()) clockTimeFormat else "h:mm a"
            val formatter = try {
                SimpleDateFormat(pattern, Locale.getDefault())
            } catch (_: Exception) {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            }
            "Ends at ${formatter.format(calendar.time)}"
        }
    }

    val showTimePill = (showCurrentTime && currentFormattedTime.isNotBlank()) || (showEndTime && formattedEndTime != null)
    if (!showTimePill) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                end = if (viewportWidth < 1100.dp) 24.dp else 64.dp,
                bottom = 32.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                )
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showCurrentTime && currentFormattedTime.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "🕒",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = currentFormattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                        )
                    }
                }

                if (showCurrentTime && currentFormattedTime.isNotBlank() && showEndTime && formattedEndTime != null) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f)),
                    )
                }

                if (showEndTime && formattedEndTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "⏳",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                        Text(
                            text = formattedEndTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}
