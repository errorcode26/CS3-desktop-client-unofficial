package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
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
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.PluginPlaceholderAvatar
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UpdateHistoryTab(viewModel: ExtensionsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val updatesHistory = uiState.updatesHistory

    var searchQuery by remember { mutableStateOf("") }
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val unifiedGridMinSize = (posterWidthDp * 1.65f).dp
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Control Bar: Search and Clear History
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search update history...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            if (updatesHistory.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        viewModel.onEvent(ExtensionsUiEvent.OnClearUpdateHistory)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Logs")
                }
            }
        }

        if (updatesHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Text(
                        text = "All plugins up to date",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "When repositories sync and install newer versions, logs will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (filteredUpdates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No update logs match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = unifiedGridMinSize),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(filteredUpdates, key = { "${it.pluginName}_${it.version}_${it.timestamp}" }) { update ->
                    val date = remember(update.timestamp) { Date(update.timestamp) }
                    val timeString = remember(date) {
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(date)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity),
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Top Row: Avatar + Name + Version
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val iconUrl = update.iconUrl
                                if (!iconUrl.isNullOrBlank() && !DesktopRepositoryManager.isIconFailed(iconUrl)) {
                                    AsyncImage(
                                        model = iconUrl,
                                        contentDescription = update.pluginName,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    PluginPlaceholderAvatar(
                                        name = update.pluginName,
                                        internalName = update.pluginName,
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = update.pluginName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            ),
                                        ) {
                                            Text(
                                                text = "v${update.version}",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }

                                    if (update.isSuccess) {
                                        Text(
                                            text = "Auto-updated via repository sync",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Text(
                                            text = update.errorMessage ?: "Failed to install update",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 1.dp,
                            )

                            // Bottom Row: Timestamp + Status Pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = timeString,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                val isSuccess = update.isSuccess
                                val badgeBg = if (isSuccess) Color(0xFF4CAF50).copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                val badgeBorder = if (isSuccess) Color(0xFF4CAF50).copy(alpha = 0.35f) else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                val badgeColor = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                val badgeText = if (isSuccess) "Updated" else "Failed"

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = badgeBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        badgeBorder,
                                    ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor),
                                        )
                                        Text(
                                            text = badgeText,
                                            color = badgeColor,
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
