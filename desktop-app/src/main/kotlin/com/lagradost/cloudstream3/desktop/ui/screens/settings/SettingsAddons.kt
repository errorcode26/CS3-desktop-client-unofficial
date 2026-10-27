package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonType
import com.lagradost.cloudstream3.desktop.stremio.matchesType
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.settings.dialogs.AddonDetailsDialog

@Composable
fun SettingsAddons(viewModel: ExtensionsViewModel) {
    val addons by StremioAddonManager.addons.collectAsState()

    var inputUrl by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf(StremioAddonType.ALL) }
    val filteredAddons = remember(addons, selectedTypeFilter) { addons.filter { it.matchesType(selectedTypeFilter) } }
    var isInstalling by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    var addonToDelete by remember { mutableStateOf<ManagedStremioAddon?>(null) }
    var selectedAddonForDetails by remember { mutableStateOf<ManagedStremioAddon?>(null) }

    // Confirmation Dialog for Deletion (Rule 9.1: CloudstreamAlertDialog)
    addonToDelete?.let { addon ->
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { addonToDelete = null },
            title = { Text("Remove Stremio Addon") },
            text = {
                Text("Are you sure you want to remove '${addon.name}'? You can re-install this manifest anytime.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(ExtensionsUiEvent.OnRemoveStremioAddon(addon.manifestUrl))
                        addonToDelete = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { addonToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Addon Details Inspector Dialog (Rule 9.1: CloudstreamCustomDialog)
    selectedAddonForDetails?.let { addon ->
        AddonDetailsDialog(
            addon = addon,
            onDismiss = { selectedAddonForDetails = null },
            onRefresh = {
                viewModel.onEvent(ExtensionsUiEvent.OnRefreshStremioAddon(addon.manifestUrl))
                selectedAddonForDetails = null
            },
            onDelete = {
                addonToDelete = addon
                selectedAddonForDetails = null
            },
            onToggleEnabled = { enabled ->
                viewModel.onEvent(ExtensionsUiEvent.OnSetStremioAddonEnabled(addon.manifestUrl, enabled))
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        val isCompactHeight = maxHeight < 780.dp
        val sectionSpacing = if (isCompactHeight) 10.dp else 16.dp
        val cardPadding = if (isCompactHeight) 12.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            // Unified Dynamic Header & Install Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(cardPadding),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
                ) {
                    // Top Row: Icon + Title + Companion Badge + Explore Shelves action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Surface(
                                modifier = Modifier.size(if (isCompactHeight) 34.dp else 40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF00B4D8).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF00B4D8).copy(alpha = 0.35f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = Color(0xFF00B4D8),
                                        modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp),
                                    )
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Stremio Addons & Manifests",
                                        style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFF00B4D8).copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "External Companion",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF00B4D8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }

                                if (!isCompactHeight) {
                                    Text(
                                        text = "Install community web manifests to expand video stream sources, subtitles, and external catalogs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                SettingsSession.selectedLeaf = LeafTab.INTEGRATIONS
                                SettingsSession.activeSubScreen = SettingsSubScreen.EXPLORE_CATALOGS
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(if (isCompactHeight) 30.dp else 34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Catalog Shelves", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Bottom Row: Inline URL Input + Install Button (Surface + BasicTextField to prevent squashing)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (inputUrl.isEmpty()) {
                                        Text(
                                            text = "Paste manifest URL (e.g. https://.../manifest.json or stremio://...)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = inputUrl,
                                        onValueChange = {
                                            val cleaned = if (it.startsWith("stremio://", ignoreCase = true)) {
                                                "https://" + it.removePrefix("stremio://").removePrefix("STREMIO://")
                                            } else {
                                                it
                                            }
                                            inputUrl = cleaned
                                            statusMessage = null
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isInstalling,
                                    )
                                }
                                if (inputUrl.isNotEmpty()) {
                                    IconButton(
                                        onClick = { inputUrl = ""; statusMessage = null },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (inputUrl.isNotBlank()) {
                                    isInstalling = true
                                    statusMessage = null
                                    viewModel.onEvent(
                                        ExtensionsUiEvent.OnAddStremioAddon(inputUrl) { result ->
                                            isInstalling = false
                                            result.onSuccess { addon ->
                                                inputUrl = ""
                                                isErrorStatus = false
                                                statusMessage = "Installed '${addon.name}' successfully!"
                                            }.onFailure { err ->
                                                isErrorStatus = true
                                                statusMessage = err.message ?: "Failed to install addon"
                                            }
                                        },
                                    )
                                }
                            },
                            enabled = inputUrl.isNotBlank() && !isInstalling,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(42.dp),
                        ) {
                            if (isInstalling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Install", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Status Message Feedback
                    AnimatedVisibility(visible = statusMessage != null) {
                        statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isErrorStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }

            // Installed Addons List Header & Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Installed Manifests (${addons.size})",
                    style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (addons.isNotEmpty()) {
                    val filterTypes = listOf(
                        StremioAddonType.ALL,
                        StremioAddonType.STREAMING,
                        StremioAddonType.SUBTITLES,
                        StremioAddonType.METADATA,
                        StremioAddonType.CATALOG,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        filterTypes.forEach { type ->
                            val isSelected = selectedTypeFilter == type
                            val count = when (type) {
                                StremioAddonType.ALL -> addons.size
                                StremioAddonType.STREAMING -> addons.count { it.providesStreams }
                                StremioAddonType.SUBTITLES -> addons.count { it.providesSubtitles }
                                StremioAddonType.METADATA -> addons.count { it.providesMetadata }
                                StremioAddonType.CATALOG -> addons.count { it.providesCatalogs }
                                StremioAddonType.OTHER -> 0
                            }
                            val chipBg = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            val chipBorder = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                            }

                            val icon = when (type) {
                                StremioAddonType.ALL -> Icons.Default.FilterList
                                StremioAddonType.STREAMING -> Icons.Default.Bolt
                                StremioAddonType.SUBTITLES -> Icons.Default.Subtitles
                                StremioAddonType.METADATA -> Icons.Default.Movie
                                StremioAddonType.CATALOG -> Icons.Default.Folder
                                StremioAddonType.OTHER -> Icons.Default.Extension
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipBg,
                                border = BorderStroke(1.dp, chipBorder),
                                modifier = Modifier.clickable { selectedTypeFilter = type },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = type.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (addons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No Stremio addons installed. Paste a manifest URL above to add stream sources or subtitles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 10.dp),
                ) {
                    if (filteredAddons.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No addons found in category '${selectedTypeFilter.label}'.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(filteredAddons, key = { _, item -> item.manifestUrl }) { _, addon ->
                        val actualIndex = addons.indexOf(addon)
                        AddonItemCard(
                            addon = addon,
                            isFirst = actualIndex == 0,
                            isLast = actualIndex == addons.size - 1,
                            isCompactHeight = isCompactHeight,
                            onToggleEnabled = { enabled ->
                                viewModel.onEvent(ExtensionsUiEvent.OnSetStremioAddonEnabled(addon.manifestUrl, enabled))
                            },
                            onMoveUp = {
                                if (actualIndex > 0) viewModel.onEvent(ExtensionsUiEvent.OnMoveStremioAddon(actualIndex, actualIndex - 1))
                            },
                            onMoveDown = {
                                if (actualIndex in 0 until addons.size - 1) viewModel.onEvent(ExtensionsUiEvent.OnMoveStremioAddon(actualIndex, actualIndex + 1))
                            },
                            onRefresh = {
                                viewModel.onEvent(ExtensionsUiEvent.OnRefreshStremioAddon(addon.manifestUrl))
                            },
                            onDelete = {
                                addonToDelete = addon
                            },
                            onInspect = {
                                selectedAddonForDetails = addon
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonItemCard(
    addon: ManagedStremioAddon,
    isFirst: Boolean,
    isLast: Boolean,
    isCompactHeight: Boolean = false,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onInspect: () -> Unit,
) {
    val cardPadding = if (isCompactHeight) 10.dp else 14.dp
    val logoSize = if (isCompactHeight) 44.dp else 50.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (addon.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reorder buttons (Priority ordering)
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    )
                }
            }

            // High-Resolution Addon Logo Surface
            Surface(
                modifier = Modifier.size(logoSize),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (addon.enabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                ),
            ) {
                if (!addon.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = addon.logoUrl,
                        contentDescription = addon.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = when {
                                addon.providesStreams -> Icons.Default.Bolt
                                addon.providesSubtitles -> Icons.Default.Subtitles
                                addon.providesMetadata -> Icons.Default.Movie
                                else -> Icons.Default.Public
                            },
                            contentDescription = null,
                            tint = Color(0xFF00B4D8),
                            modifier = Modifier.size(if (isCompactHeight) 22.dp else 26.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info Section
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = addon.name,
                        style = if (isCompactHeight) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (addon.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFF00B4D8).copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "Stremio",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00B4D8),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                        )
                    }
                }

                if (addon.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = addon.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isCompactHeight) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Capability Badges (Zero emojis, clean vector icons)
                Spacer(Modifier.height(if (isCompactHeight) 4.dp else 6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (addon.providesStreams) {
                        CapabilityChip(label = "Streams", color = MaterialTheme.colorScheme.primary, icon = Icons.Default.Bolt)
                    }
                    if (addon.providesSubtitles) {
                        CapabilityChip(label = "Subtitles", color = MaterialTheme.colorScheme.secondary, icon = Icons.Default.Subtitles)
                    }
                    if (addon.providesMetadata) {
                        CapabilityChip(label = "Metadata", color = MaterialTheme.colorScheme.tertiary, icon = Icons.Default.Movie)
                    }
                    if (addon.providesCatalogs || addon.catalogsSummary.isNotEmpty()) {
                        CapabilityChip(
                            label = "${addon.catalogsSummary.size.coerceAtLeast(1)} Catalogs",
                            color = Color(0xFF4CAF50),
                            icon = Icons.Default.Folder,
                        )
                    }
                    if (addon.isP2P) {
                        CapabilityChip(label = "P2P", color = Color(0xFFFF9800), icon = Icons.Default.Share)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onInspect, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Inspect Addon Details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Manifest",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Addon",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    color: Color,
    icon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            )
        }
    }
}
