package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsAddons() {
    val addons by StremioAddonManager.addons.collectAsState()
    val scope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
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
                        StremioAddonManager.removeAddon(addon.manifestUrl)
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
                StremioAddonManager.refreshAddon(addon.manifestUrl)
                selectedAddonForDetails = null
            },
            onDelete = {
                addonToDelete = addon
                selectedAddonForDetails = null
            },
            onToggleEnabled = { enabled ->
                StremioAddonManager.setAddonEnabled(addon.manifestUrl, enabled)
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header Description Card with Distinct Stremio Branding
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF00B4D8).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B4D8).copy(alpha = 0.35f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFF00B4D8),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Stremio Addons & Manifests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00B4D8).copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "External Companion",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00B4D8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Install community web manifests (manifest.json) to expand video stream sources, subtitles, and external catalogs without modifying native CS3 plugins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Install Addon Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    text = "Install Manifest URL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            // Automatically normalize stremio:// links to clean web URLs
                            val cleaned = if (it.startsWith("stremio://", ignoreCase = true)) {
                                "https://" + it.removePrefix("stremio://").removePrefix("STREMIO://")
                            } else {
                                it
                            }
                            inputUrl = cleaned
                            statusMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Paste URL (e.g. https://.../manifest.json or stremio://...)") },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isInstalling,
                    )

                    Button(
                        onClick = {
                            if (inputUrl.isNotBlank()) {
                                isInstalling = true
                                statusMessage = null
                                scope.launch {
                                    val result = StremioAddonManager.addAddon(inputUrl)
                                    isInstalling = false
                                    result.onSuccess { addon ->
                                        inputUrl = ""
                                        isErrorStatus = false
                                        statusMessage = "Installed '${addon.name}' successfully!"
                                    }.onFailure { err ->
                                        isErrorStatus = true
                                        statusMessage = err.message ?: "Failed to install addon"
                                    }
                                }
                            }
                        },
                        enabled = inputUrl.isNotBlank() && !isInstalling,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        if (isInstalling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install")
                        }
                    }
                }

                // Status Message Feedback
                AnimatedVisibility(visible = statusMessage != null) {
                    statusMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Quick Community Presets
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    AssistChip(
                        onClick = {
                            inputUrl = StremioAddonManager.URL_OPENSUBTITLES_V3
                        },
                        label = { Text("OpenSubtitles v3") },
                        leadingIcon = {
                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(8.dp),
                    )

                    AssistChip(
                        onClick = {
                            inputUrl = StremioAddonManager.URL_CINEMETA
                        },
                        label = { Text("Cinemeta") },
                        leadingIcon = {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

        // Installed Addons List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Installed Stremio Manifests (${addons.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (addons.isEmpty()) {
                TextButton(
                    onClick = {
                        StremioAddonManager.loadAddons()
                    },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore Defaults")
                }
            }
        }

        // Addons List
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(addons, key = { _, item -> item.manifestUrl }) { index, addon ->
                    AddonItemCard(
                        addon = addon,
                        isFirst = index == 0,
                        isLast = index == addons.size - 1,
                        onToggleEnabled = { enabled ->
                            StremioAddonManager.setAddonEnabled(addon.manifestUrl, enabled)
                        },
                        onMoveUp = {
                            StremioAddonManager.moveAddon(index, index - 1)
                        },
                        onMoveDown = {
                            StremioAddonManager.moveAddon(index, index + 1)
                        },
                        onRefresh = {
                            StremioAddonManager.refreshAddon(addon.manifestUrl)
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

@Composable
private fun AddonItemCard(
    addon: ManagedStremioAddon,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onInspect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (addon.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reorder buttons (Priority ordering)
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(24.dp),
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
                    modifier = Modifier.size(24.dp),
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
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
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
                            .clip(RoundedCornerShape(10.dp)),
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
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Info Section
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = addon.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (addon.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00B4D8).copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "Stremio",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00B4D8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (addon.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = addon.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Capability Badges
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (addon.providesStreams) {
                        CapabilityChip(label = "⚡ Streams", color = MaterialTheme.colorScheme.primary)
                    }
                    if (addon.providesSubtitles) {
                        CapabilityChip(label = "💬 Subtitles", color = MaterialTheme.colorScheme.secondary)
                    }
                    if (addon.providesMetadata) {
                        CapabilityChip(label = "🎬 Metadata", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (addon.providesCatalogs || addon.catalogsSummary.isNotEmpty()) {
                        CapabilityChip(
                            label = "📂 ${addon.catalogsSummary.size.coerceAtLeast(1)} Catalogs",
                            color = Color(0xFF4CAF50),
                        )
                    }
                    if (addon.isP2P) {
                        CapabilityChip(label = "🌐 P2P", color = Color(0xFFFF9800))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onInspect, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Inspect Addon Details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Manifest",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Addon",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }
        }
    }
}

@Composable
private fun AddonDetailsDialog(
    addon: ManagedStremioAddon,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copiedToast by remember { mutableStateOf(false) }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .fillMaxHeight(0.80f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header: Big Logo, Name, Version, Enabled Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shadowElevation = 4.dp,
                ) {
                    if (!addon.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = addon.logoUrl,
                            contentDescription = addon.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = Color(0xFF00B4D8),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = addon.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "v${addon.version}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Stremio Community Web Manifest",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00B4D8),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Manifest URL & Actions Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Manifest Endpoint",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = addon.manifestUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(addon.manifestUrl))
                                copiedToast = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copiedToast) "Copied!" else "Copy URL")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(addon.manifestUrl)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                }
            }

            // Description
            if (addon.description.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = addon.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Capabilities Overview Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Capabilities & Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CapabilityCard(
                        title = "Video Streams",
                        active = addon.providesStreams,
                        icon = Icons.Default.Bolt,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityCard(
                        title = "Subtitles",
                        active = addon.providesSubtitles,
                        icon = Icons.Default.Subtitles,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityCard(
                        title = "Metadata",
                        active = addon.providesMetadata,
                        icon = Icons.Default.Movie,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    CapabilityCard(
                        title = "Catalogs",
                        active = addon.providesCatalogs || addon.catalogsSummary.isNotEmpty(),
                        icon = Icons.Default.Category,
                        activeColor = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Catalogs Breakdown
            if (addon.catalogsSummary.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Provided Catalogs (${addon.catalogsSummary.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        addon.catalogsSummary.take(10).forEach { catalogName ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = catalogName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // Supported Types & Prefixes
            if (addon.types.isNotEmpty() || addon.idPrefixes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (addon.types.isNotEmpty()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Supported Media Types",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = addon.types.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (addon.idPrefixes.isNotEmpty()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Supported ID Prefixes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = addon.idPrefixes.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Addon", color = MaterialTheme.colorScheme.error)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh Manifest")
                    }

                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    title: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        ),
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CapabilityChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )
    }
}
