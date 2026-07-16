package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.utils.DesktopStrings

@Composable
fun UpdatesNotificationBell(
    modifier: Modifier = Modifier,
    hasUnreadUpdates: Boolean,
    updatesHistory: List<com.lagradost.common.storage.PluginUpdateRecord>,
    onMarkUpdatesRead: () -> Unit,
) {
    var isUpdatesDialogExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(start = 16.dp, bottom = 16.dp)) {
        Surface(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            shadowElevation = 4.dp,
            shape = androidx.compose.foundation.shape.CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                DockItem(
                    icon = PremiumIcons.Updates,
                    label = DesktopStrings.UPDATES,
                    showLabel = false,
                    selected = false,
                    badge = if (hasUnreadUpdates) "!" else null,
                    onClick = {
                        isUpdatesDialogExpanded = true
                    },
                )
            }
        }

        if (isUpdatesDialogExpanded) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(16, -16),
                onDismissRequest = { isUpdatesDialogExpanded = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DesktopUi.SurfaceElevated.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    modifier = Modifier.width(360.dp).heightIn(max = 500.dp),
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DesktopStrings.UPDATES,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hasUnreadUpdates) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { onMarkUpdatesRead() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                    ) {
                                        Text("Mark as read", fontSize = 12.sp)
                                    }
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { isUpdatesDialogExpanded = false },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (updatesHistory.isEmpty()) {
                            Text(
                                DesktopStrings.NO_UPDATES,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(updatesHistory.size) { i ->
                                    val update = updatesHistory[i]
                                    val timeString = java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(update.timestamp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        coil3.compose.AsyncImage(
                                            model = update.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                update.pluginName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                            )
                                            Text(
                                                "${DesktopStrings.PLUGIN_UPDATED} v${update.version} • $timeString",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DockItem(
    icon: ImageVector,
    label: String,
    showLabel: Boolean = false,
    selected: Boolean,
    badge: String? = null,
    isHorizontal: Boolean = false,
    indicatorAtTop: Boolean = false,
    onClick: () -> Unit,
) {
    val itemInteraction = remember { MutableInteractionSource() }
    val isHovered by itemInteraction.collectIsHoveredAsState()

    val theme = LocalDesktopTheme.current
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> theme.TextPrimary
        else -> theme.TextMuted
    }
    val bgAlpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        label = "dockItemBgAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockItemScale",
    )

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) {
                    Modifier.size(56.dp)
                } else {
                    Modifier.fillMaxWidth().height(56.dp)
                },
            )
            .clip(RoundedCornerShape(14.dp))
            .hoverable(itemInteraction)
            .clickable(onClick = onClick),
    ) {
        // Hover Background layer (using graphicsLayer to avoid Compose Desktop alpha blending bugs)
        if (bgAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = bgAlpha }
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.SurfaceElevated),
            )
        }

        // Active indicator pill
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + when {
                indicatorAtTop -> slideInVertically { -it / 2 }
                isHorizontal -> slideInHorizontally { it / 2 }
                else -> slideInVertically { it / 2 }
            },
            exit = fadeOut() + when {
                indicatorAtTop -> slideOutVertically { -it / 2 }
                isHorizontal -> slideOutHorizontally { it / 2 }
                else -> slideOutVertically { it / 2 }
            },
            modifier = Modifier.align(
                when {
                    indicatorAtTop -> Alignment.TopCenter
                    isHorizontal -> Alignment.BottomCenter
                    else -> Alignment.CenterStart
                },
            ),
        ) {
            Box(
                modifier = Modifier
                    .run {
                        when {
                            indicatorAtTop -> width(20.dp).height(3.dp)
                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            isHorizontal -> width(20.dp).height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            else -> width(3.dp).height(20.dp)
                                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        }
                    }
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                )

                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }

            if (showLabel) {
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn() + expandVertically() + slideInVertically { it / 2 },
                    exit = fadeOut() + shrinkVertically() + slideOutVertically { it / 2 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = if (selected) theme.TextPrimary else theme.TextMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}
