package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.utils.DesktopStrings
import com.lagradost.common.storage.PluginUpdateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PluginUpdatesModal(
    show: Boolean,
    hasUnreadUpdates: Boolean,
    updatesHistory: List<PluginUpdateRecord>,
    onDismissRequest: () -> Unit,
    onMarkUpdatesRead: () -> Unit,
    onCheckForUpdates: suspend () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val isAmoled = appThemeBackground == "Pure Black"

    val filteredUpdates = remember(updatesHistory, searchQuery) {
        if (searchQuery.isBlank()) {
            updatesHistory
        } else {
            updatesHistory.filter { update ->
                update.pluginName.contains(searchQuery, ignoreCase = true) ||
                    "v${update.version}".contains(searchQuery, ignoreCase = true)
            }
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isAmoled) Color.Black else if (isLightMode) Color(0xFFF9FAFB) else Color(0xFF13141D))
            .border(
                1.dp,
                if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(22.dp),
            )
            .padding(28.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header Section ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    ),
                                ),
                            )
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PremiumIcons.Updates,
                            contentDescription = "Updates",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = DesktopStrings.UPDATES,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (hasUnreadUpdates) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                ) {
                                    Text(
                                        text = "NEW",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (updatesHistory.isEmpty()) "All plugins & extensions up to date" else "${updatesHistory.size} total update logs tracked",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Header Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (!isCheckingUpdates) {
                                isCheckingUpdates = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        onCheckForUpdates()
                                    } finally {
                                        isCheckingUpdates = false
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCheckingUpdates,
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...", fontSize = 13.sp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Updates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (hasUnreadUpdates) {
                        OutlinedButton(
                            onClick = { onMarkUpdatesRead() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Mark Read",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark Read", fontSize = 13.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(18.dp))

            // ── Search Bar ─────────────────────────────────────────────
            if (updatesHistory.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Filter updates by extension name or version...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = "${filteredUpdates.size} of ${updatesHistory.size} updates",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = if (isLightMode) Color.White else Color.White.copy(alpha = 0.05f),
                        focusedContainerColor = if (isLightMode) Color.White else Color.White.copy(alpha = 0.08f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 2-Column Grid Updates Content ───────────────────────────
            if (updatesHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp),
                            )
                        }

                        Text(
                            text = "Everything is up to date",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Text(
                            text = "All installed extensions and plugins are running their latest available versions.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.widthIn(max = 420.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else if (filteredUpdates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No updates matching \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(filteredUpdates, key = { "${it.pluginName}_${it.version}_${it.timestamp}" }) { update ->
                        val date = remember(update.timestamp) { Date(update.timestamp) }
                        val timeString = remember(date) {
                            SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(date)
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLightMode) Color.White else Color.White.copy(alpha = 0.04f),
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.09f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    // Plugin Icon
                                    val resolvedIcon = update.iconUrl?.replace("%size%", "32")
                                    if (!resolvedIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = resolvedIcon,
                                            contentDescription = update.pluginName,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isLightMode) Color(0xFFEEEEEE) else Color(0xFF222430))
                                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = update.pluginName.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = update.pluginName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )

                                            // Version Pill
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                            ) {
                                                Text(
                                                    text = "v${update.version}",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Text(
                                                text = "Auto-Updated • $timeString",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Status Pill
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.14f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50)),
                                        )
                                        Text(
                                            text = "Installed",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
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
