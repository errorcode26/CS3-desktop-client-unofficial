package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

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
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    showSettings: Boolean = false,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCheckbox && onCheckedChange != null) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (!iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).padding(end = 16.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).padding(end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FlagImage(language)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v$version • $repoName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!tvTypes.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tvTypes.take(3).forEach { type ->
                            AssistChip(
                                onClick = {},
                                label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp),
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                }
            }

            if (showSettings) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    FilledTonalButton(
                        onClick = onInstallClick,
                        enabled = !isInstalling && installStatus != "Installed",
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (isInstalling) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (installStatus == "Installed") "Installed" else "Install",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (installStatus.isNotEmpty() && installStatus != "Installed") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = installStatus,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
