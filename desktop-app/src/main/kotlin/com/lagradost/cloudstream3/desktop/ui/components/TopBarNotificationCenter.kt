package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.init.PluginWarmupState
import com.lagradost.cloudstream3.desktop.updates.PendingUpdate
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopBarNotificationCenter() {
    val scope = rememberCoroutineScope()
    val warmupProgress by PluginWarmupState.progress.collectAsState()
    val availableUpdates by UnifiedUpdateManager.availableUpdates.collectAsState()
    val history by AppToastManager.history.collectAsState()

    val isWarmingUp = warmupProgress.isWarmingUp
    var showWarmupComplete by remember { mutableStateOf(false) }

    LaunchedEffect(warmupProgress.isWarmingUp, warmupProgress.isComplete) {
        if (!warmupProgress.isWarmingUp && warmupProgress.isComplete && warmupProgress.total > 0) {
            showWarmupComplete = true
            delay(2200L)
            showWarmupComplete = false
        }
    }

    var isFlyoutOpen by remember { mutableStateOf(false) }

    val theme = LocalDesktopTheme.current
    val isLightMode = theme.isLightMode
    val isAmoled = theme.isAmoled

    val buttonBg = when {
        isWarmingUp -> if (isLightMode) Color(0xFFE8F0FE) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        showWarmupComplete -> if (isLightMode) Color(0xFFE8F5E9) else Color(0xFF1B5E20).copy(alpha = 0.28f)
        availableUpdates.isNotEmpty() -> if (isLightMode) Color(0xFFFFF3E0) else Color(0xFFE65100).copy(alpha = 0.25f)
        isLightMode -> Color.White.copy(alpha = 0.85f)
        else -> Color(0xFF1E1E24).copy(alpha = 0.50f)
    }

    val buttonBorder = when {
        isWarmingUp -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        showWarmupComplete -> Color(0xFF4CAF50).copy(alpha = 0.45f)
        availableUpdates.isNotEmpty() -> Color(0xFFFF9800).copy(alpha = 0.45f)
        isLightMode -> Color.Black.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    val hoverBorder = when {
        isWarmingUp -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        showWarmupComplete -> Color(0xFF4CAF50).copy(alpha = 0.8f)
        availableUpdates.isNotEmpty() -> Color(0xFFFF9800).copy(alpha = 0.8f)
        isLightMode -> Color.Black.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.25f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        Surface(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { isFlyoutOpen = true },
                )
                .border(
                    1.dp,
                    if (isHovered) hoverBorder else buttonBorder,
                    RoundedCornerShape(10.dp),
                ),
            color = buttonBg,
            shape = RoundedCornerShape(10.dp),
        ) {
            when {
                // 1. Live Background Warmup in progress
                isWarmingUp -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.8.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val label = if (warmupProgress.total > 0) {
                            "Loading extensions (${warmupProgress.current}/${warmupProgress.total})..."
                        } else {
                            "Warming up extensions..."
                        }
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLightMode) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                        )
                    }
                }

                // 2. Warmup just finished
                showWarmupComplete -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Extensions ready",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLightMode) Color(0xFF2E7D32) else Color(0xFF81C784),
                            maxLines = 1,
                        )
                    }
                }

                // 3. Pending App / Engine Updates available
                availableUpdates.isNotEmpty() -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "${availableUpdates.size} Update${if (availableUpdates.size > 1) "s" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLightMode) Color(0xFFE65100) else Color(0xFFFFB74D),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // 4. Default Bell Icon with unread notice badge
                else -> {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (history.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            // Unread indicator dot
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Notification & Activity Center Popover Flyout
        DropdownMenu(
            expanded = isFlyoutOpen,
            onDismissRequest = { isFlyoutOpen = false },
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isLightMode) {
                        Color(0xFFFAFAFC)
                    } else if (isAmoled) {
                        Color(0xFF0F0F12)
                    } else {
                        Color(0xFF16161A)
                    },
                )
                .border(
                    1.dp,
                    if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Activity & Notifications",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (history.isNotEmpty()) {
                    Text(
                        text = "Clear All",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { AppToastManager.clearHistory() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Section: Active Background Operations
                if (isWarmingUp) {
                    WarmupTaskCard(
                        current = warmupProgress.current,
                        total = warmupProgress.total,
                        currentPluginName = warmupProgress.currentPluginName,
                        isLightMode = isLightMode,
                    )
                }

                // Section: Available Updates
                if (availableUpdates.isNotEmpty()) {
                    availableUpdates.forEach { update ->
                        UpdateNoticeCard(
                            update = update,
                            isLightMode = isLightMode,
                            onUpdateClick = {
                                isFlyoutOpen = false
                                UnifiedUpdateManager.showDialogForUpdate(update)
                            },
                            onSkipClick = {
                                scope.launch(Dispatchers.IO) {
                                    UnifiedUpdateManager.ignoreUpdate(update)
                                    AppToastManager.showInfo("Skipped update ${update.newVersion}")
                                }
                            },
                        )
                    }
                }

                // Section: Notification History
                if (history.isNotEmpty()) {
                    history.reversed().forEach { toast ->
                        HistoryNotificationCard(
                            toast = toast,
                            isLightMode = isLightMode,
                            onDismiss = { AppToastManager.dismissHistory(toast.id) },
                        )
                    }
                }

                // Empty State
                if (!isWarmingUp && availableUpdates.isEmpty() && history.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "All caught up",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "No pending background operations or alerts",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarmupTaskCard(
    current: Int,
    total: Int,
    currentPluginName: String,
    isLightMode: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isLightMode) Color(0xFFEEF3FC) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Warming up extensions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "$current / $total",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val progressFraction = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            )

            if (currentPluginName.isNotBlank()) {
                Text(
                    text = "Loading: $currentPluginName",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UpdateNoticeCard(
    update: PendingUpdate,
    isLightMode: Boolean,
    onUpdateClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isLightMode) Color(0xFFFFF8E1) else Color(0xFF261D10),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFFF9800).copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = update.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${update.newVersion} available",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    onClick = onUpdateClick,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFF9800).copy(alpha = 0.25f),
                        contentColor = if (isLightMode) Color(0xFFE65100) else Color(0xFFFFCC80),
                    ),
                ) {
                    Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onSkipClick,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip this version",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryNotificationCard(
    toast: ToastMessage,
    isLightMode: Boolean,
    onDismiss: () -> Unit,
) {
    val (icon: ImageVector, iconColor: Color) = when (toast.type) {
        ToastType.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        ToastType.WARNING -> Icons.Default.Warning to Color(0xFFFFB300)
        ToastType.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
        ToastType.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isLightMode) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLightMode) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toast.text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val relativeTime = remember(toast.timestamp) {
                    val diffSec = (System.currentTimeMillis() - toast.timestamp) / 1000
                    when {
                        diffSec < 15 -> "Just now"
                        diffSec < 60 -> "${diffSec}s ago"
                        diffSec < 3600 -> "${diffSec / 60}m ago"
                        else -> "${diffSec / 3600}h ago"
                    }
                }
                Text(
                    text = relativeTime,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}
