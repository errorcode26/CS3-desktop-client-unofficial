package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.TaskStatus
@Composable
fun TopBarDownloadPill() {
    val tasks by AppDownloadManager.tasks.collectAsState()
    val activeTask = tasks.lastOrNull() ?: return

    var showDetailsDialog by remember { mutableStateOf(false) }

    val isLightMode = LocalDesktopTheme.current.isLightMode
    val buttonBg = when (activeTask.status) {
        TaskStatus.COMPLETED -> if (isLightMode) Color(0xFFE8F5E9) else Color(0xFF1B5E20).copy(alpha = 0.4f)
        TaskStatus.FAILED -> if (isLightMode) Color(0xFFFFEBEE) else Color(0xFFB71C1C).copy(alpha = 0.4f)
        else -> if (isLightMode) Color.White.copy(alpha = 0.85f) else Color(0xFF1E1E24).copy(alpha = 0.65f)
    }

    val buttonBorder = when (activeTask.status) {
        TaskStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.5f)
        TaskStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.5f)
        else -> if (isLightMode) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.15f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Surface(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { showDetailsDialog = true },
                )
                .border(
                    1.dp,
                    if (isHovered) buttonBorder.copy(alpha = 0.8f) else buttonBorder,
                    RoundedCornerShape(10.dp),
                ),
            color = buttonBg,
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (activeTask.status) {
                    TaskStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${activeTask.title}: Done",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLightMode) Color(0xFF2E7D32) else Color(0xFF81C784),
                        )
                    }
                    TaskStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Failed",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${activeTask.title}: Failed",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLightMode) Color(0xFFC62828) else Color(0xFFE57373),
                        )
                    }
                    else -> {
                        if (activeTask.progress >= 0f) {
                            CircularProgressIndicator(
                                progress = { activeTask.progress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Column {
                            Text(
                                text = "${activeTask.title} • ${activeTask.percent}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeTask.speedBytesPerSec > 0) {
                                Text(
                                    text = "${activeTask.downloadedMB} / ${activeTask.totalMB} • ${activeTask.speedFormatted}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetailsDialog) {
        CloudstreamCustomDialog(
            show = true,
            onDismissRequest = { showDetailsDialog = false },
            modifier = Modifier.width(480.dp).wrapContentHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (activeTask.status) {
                                    TaskStatus.COMPLETED -> Icons.Default.CheckCircle
                                    TaskStatus.FAILED -> Icons.Default.ErrorOutline
                                    else -> Icons.Default.FileDownload
                                },
                                contentDescription = null,
                                tint = when (activeTask.status) {
                                    TaskStatus.COMPLETED -> Color(0xFF4CAF50)
                                    TaskStatus.FAILED -> Color(0xFFF44336)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTask.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when (activeTask.status) {
                                TaskStatus.COMPLETED -> "Download completed successfully"
                                TaskStatus.FAILED -> activeTask.errorMessage ?: "Download failed"
                                TaskStatus.CANCELLED -> "Download cancelled"
                                TaskStatus.RUNNING -> if (activeTask.etaFormatted.isNotBlank()) activeTask.etaFormatted else "Downloading in background..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (activeTask.status == TaskStatus.RUNNING) {
                    if (activeTask.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { activeTask.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${activeTask.downloadedMB} of ${activeTask.totalMB} (${activeTask.percent}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = activeTask.speedFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activeTask.status == TaskStatus.RUNNING) {
                        TextButton(
                            onClick = {
                                AppDownloadManager.cancelDownload(activeTask.id)
                                showDetailsDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("Cancel Download")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showDetailsDialog = false }) {
                            Text("Hide to Background")
                        }
                    } else {
                        Button(
                            onClick = {
                                AppDownloadManager.dismissTask(activeTask.id)
                                showDetailsDialog = false
                            },
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
