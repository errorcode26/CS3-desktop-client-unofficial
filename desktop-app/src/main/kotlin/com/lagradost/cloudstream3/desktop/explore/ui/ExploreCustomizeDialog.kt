package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.settings.ExploreCatalogSettingsManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ExploreCustomizeDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    allCatalogs: List<ManifestCatalogDescriptor>,
    availableTypes: List<String>,
    initialType: String,
    formatTypeTitle: (String) -> Unit = {},
) {
    if (!show) return

    val theme = LocalDesktopTheme.current
    val coroutineScope = rememberCoroutineScope()
    val preferences by ExploreCatalogSettingsManager.preferences.collectAsState()

    var selectedTypeTab by remember(show) { mutableStateOf(initialType) }
    var editingDescriptor by remember { mutableStateOf<ManifestCatalogDescriptor?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    val currentTypeCatalogs = remember(allCatalogs, selectedTypeTab) {
        allCatalogs.filter {
            val t = if (it.type.equals("tv", ignoreCase = true)) "series" else it.type.lowercase(Locale.US)
            val target = if (selectedTypeTab.equals("tv", ignoreCase = true)) "series" else selectedTypeTab.lowercase(Locale.US)
            t == target
        }
    }

    // Sorted according to current user preferences (including disabled)
    val orderedCatalogs = remember(currentTypeCatalogs, preferences) {
        currentTypeCatalogs.sortedBy { descriptor ->
            preferences[descriptor.key]?.order ?: Int.MAX_VALUE
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header: Title + Subtitle + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Customize Shelves",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "Reorder, show/hide, or rename shelves on your Explore landing page",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.TextMuted,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Reset to Defaults", fontSize = 12.5.sp)
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Segmented Selector Pills
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    availableTypes.forEach { type ->
                        val isSelected = selectedTypeTab.equals(type, ignoreCase = true)
                        val typeTitle = when (type.lowercase(Locale.US)) {
                            "movie" -> "Movies"
                            "series", "tv" -> "TV Shows"
                            "anime" -> "Anime"
                            "collections", "collection" -> "Collections"
                            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                        }
                        val count = allCatalogs.count {
                            val t = if (it.type.equals("tv", ignoreCase = true)) "series" else it.type.lowercase(Locale.US)
                            val target = if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase(Locale.US)
                            t == target
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .clickable { selectedTypeTab = type }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = typeTitle,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else theme.TextMuted,
                                )
                                Surface(
                                    color = if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.Black else theme.TextMuted,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shelves Reorder & Visibility List
            if (orderedCatalogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No shelves available for this category.",
                        color = theme.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(
                        items = orderedCatalogs,
                        key = { _, item -> item.key },
                    ) { index, descriptor ->
                        val pref = preferences[descriptor.key]
                        val isEnabled = pref?.enabled != false
                        val customTitle = pref?.customTitle
                        val displayTitle = if (!customTitle.isNullOrBlank()) customTitle else descriptor.name

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isEnabled) 0.45f else 0.18f),
                            border = BorderStroke(
                                1.dp,
                                if (isEnabled) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Left: Reorder Buttons + Rank Index + Title & Addon tag
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    // Move Up / Move Down controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val orderedKeys = orderedCatalogs.map { it.key }
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    ExploreCatalogSettingsManager.moveByIndex(orderedKeys, index, index - 1)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = if (index > 0) theme.TextPrimary else theme.TextMuted.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val orderedKeys = orderedCatalogs.map { it.key }
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    ExploreCatalogSettingsManager.moveByIndex(orderedKeys, index, index + 1)
                                                }
                                            },
                                            enabled = index < orderedCatalogs.size - 1,
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = if (index < orderedCatalogs.size - 1) theme.TextPrimary else theme.TextMuted.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }

                                    // Position Badge
                                    Surface(
                                        color = Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.TextMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }

                                    // Title & Custom name indicator
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isEnabled) theme.TextPrimary else theme.TextMuted.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!customTitle.isNullOrBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp),
                                                ) {
                                                    Text(
                                                        text = "Renamed",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                        }

                                        // Source Tag
                                        Text(
                                            text = descriptor.addonName.takeIf { it.isNotBlank() } ?: "Native",
                                            fontSize = 11.5.sp,
                                            color = theme.TextMuted.copy(alpha = 0.7f),
                                        )
                                    }
                                }

                                // Right: Rename button + Visibility Switch
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingDescriptor = descriptor
                                            renameInputText = displayTitle
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = theme.TextMuted,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }

                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                ExploreCatalogSettingsManager.setEnabled(descriptor.key, checked)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Shelf Dialog (Simple input -> CloudstreamAlertDialog)
    if (editingDescriptor != null) {
        val target = editingDescriptor!!
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { editingDescriptor = null },
            title = {
                Text(
                    text = "Rename Shelf",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Original: ${target.name}",
                        fontSize = 12.sp,
                        color = theme.TextMuted,
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("Display Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTitle = renameInputText.trim()
                        coroutineScope.launch(Dispatchers.IO) {
                            ExploreCatalogSettingsManager.setCustomTitle(target.key, newTitle)
                        }
                        editingDescriptor = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDescriptor = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Reset Confirmation Dialog (Simple warning -> CloudstreamAlertDialog)
    if (showResetConfirm) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = "Reset Shelves to Defaults?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This will restore the original order, enable all shelves, and clear custom titles for the \"${selectedTypeTab.replaceFirstChar { it.titlecase(Locale.US) }}\" category.",
                    fontSize = 13.5.sp,
                    color = theme.TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            ExploreCatalogSettingsManager.resetToDefaults(currentTypeCatalogs)
                        }
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
