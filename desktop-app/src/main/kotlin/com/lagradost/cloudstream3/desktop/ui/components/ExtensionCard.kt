package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExtensionCard(
    name: String,
    internalName: String,
    version: Int,
    repoName: String,
    language: String?,
    tvTypes: List<String>?,
    iconUrl: String?,
    isInstalled: Boolean,
    installStatus: String,
    isInstalling: Boolean,
    onInstallClick: () -> Unit,
    onUninstallClick: (() -> Unit)? = null,
    description: String? = null,
    fileSize: Long? = null,
    onRepoClick: (() -> Unit)? = null,
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    showSettings: Boolean = false,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
        show = showConfirmDialog && onUninstallClick != null,
        onDismissRequest = { showConfirmDialog = false },
        title = { Text("Confirm Uninstall") },
        text = { Text("Are you sure you want to uninstall '$name'? This will remove the extension from your app.") },
        confirmButton = {
            TextButton(
                onClick = {
                    showConfirmDialog = false
                    if (onUninstallClick != null) onUninstallClick()
                },
            ) {
                Text("Uninstall", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showConfirmDialog = false }) {
                Text("Cancel")
            }
        },
    )

    val uiCardOpacity by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.uiCardOpacity.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 172.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // TOP SECTION: Checkbox + Icon + Title/Version/Repo + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showCheckbox && onCheckedChange != null) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }

                val resolvedIconUrl = com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhanceIconUrl(iconUrl)
                if (!resolvedIconUrl.isNullOrEmpty() && !com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.isIconFailed(resolvedIconUrl)) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(resolvedIconUrl)
                            .size(128, 128)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        loading = {
                            PluginPlaceholderAvatar(name, internalName, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                        },
                        error = {
                            com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.markIconFailed(resolvedIconUrl)
                            PluginPlaceholderAvatar(name, internalName, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                        },
                    )
                } else {
                    PluginPlaceholderAvatar(name, internalName, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        FlagImage(language)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val sizeStr = if (fileSize != null && fileSize > 0L) {
                            val kb = fileSize / 1024f
                            if (kb >= 1024f) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
                        } else {
                            ""
                        }
                        Text(
                            text = "v$version" + if (sizeStr.isNotEmpty()) " • $sizeStr" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        if (onRepoClick != null) {
                            Surface(
                                onClick = onRepoClick,
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Text(
                                    text = "$repoName ↗",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        } else {
                            Text(
                                text = "• $repoName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }

                if (showSettings) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // MIDDLE SECTION: Description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM SECTION: Category Chips (Left) & Actions (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Category Chips
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!tvTypes.isNullOrEmpty()) {
                        tvTypes.take(3).forEach { type ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            ) {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Actions
                if (onUninstallClick != null && isInstalled) {
                    OutlinedButton(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Uninstall", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else if (isInstalled || installStatus == "Installed") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF1B4D2E).copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "Installed ✓",
                            color = androidx.compose.ui.graphics.Color(0xFF81C784),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onInstallClick,
                        enabled = !isInstalling,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (isInstalling) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "+ Install",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginPlaceholderAvatar(
    name: String,
    internalName: String,
    modifier: Modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = textStyle,
            fontWeight = FontWeight.Bold,
        )
    }
}
