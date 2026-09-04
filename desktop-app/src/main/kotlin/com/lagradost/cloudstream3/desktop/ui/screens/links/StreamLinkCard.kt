package com.lagradost.cloudstream3.desktop.ui.screens.links

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

private val SERVER_BRACKET_REGEX = Regex("\\[(.*?)\\]")
private val SIZE_BRACKET_REGEX = Regex("\\[([0-9.]+\\s*(?:MB|GB|KB))\\]", RegexOption.IGNORE_CASE)
private val SIZE_FALLBACK_REGEX = Regex("([0-9.]+\\s*(?:MB|GB|KB))", RegexOption.IGNORE_CASE)

internal fun extractCleanServer(rawName: String, fallback: String): String {
    val bracketMatch = SERVER_BRACKET_REGEX.find(rawName)
    if (bracketMatch != null) {
        val candidate = bracketMatch.groupValues[1].trim()
        if (!candidate.contains("MB", ignoreCase = true) && !candidate.contains("GB", ignoreCase = true)) {
            return candidate
        }
    }
    val parts = rawName.split(" ", "-", ".").filter { it.isNotBlank() }
    return parts.firstOrNull { it.length > 2 && !it.contains("720") && !it.contains("1080") && !it.contains("x264") } ?: fallback
}

internal fun extractCleanSize(rawName: String): String? {
    val sizeMatch = SIZE_BRACKET_REGEX.find(rawName) ?: SIZE_FALLBACK_REGEX.find(rawName)
    return sizeMatch?.groupValues?.get(1)
}

@Composable
fun StreamLinkCard(
    link: ExtractorLink,
    isP2pEnabled: Boolean = false,
    isBusy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.01f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )

    val formattedQuality = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(link.quality)
    val is4k = link.quality >= 2160
    val is1080 = link.quality in 1080..2159
    val is720 = link.quality in 720..1079

    val qualityContainerColor = when {
        is4k -> Color(0xFFE5A00D).copy(alpha = 0.2f)
        is1080 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        is720 -> Color(0xFF00B4D8).copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val qualityTextColor = when {
        is4k -> Color(0xFFFFC107)
        is1080 -> MaterialTheme.colorScheme.primary
        is720 -> Color(0xFF00D2FF)
        else -> DesktopUi.TextMuted
    }

    val formatTag = when {
        link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8") -> "HLS"
        link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd") -> "DASH"
        link.type == ExtractorLinkType.TORRENT ||
            link.type == ExtractorLinkType.MAGNET ||
            link.url.startsWith("magnet:") -> "TORRENT"
        else -> "MP4"
    }

    val cleanSize = remember(link.name) { extractCleanSize(link.name) }
    val hostSource = remember(link.name, link.source) {
        if (link.source.isNotBlank() && link.source != link.name) link.source else extractCleanServer(link.name, "")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interaction),
        shape = RoundedCornerShape(10.dp),
        color = if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        tonalElevation = if (hovered) 6.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else DesktopUi.Divider.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: Badges on Left, File Size on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    // Quality Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = qualityContainerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, qualityTextColor.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = formattedQuality,
                            color = qualityTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }

                    // Format Badge
                    val isTorrentBadge = formatTag == "TORRENT"
                    val isP2pOff = isTorrentBadge && !isP2pEnabled
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isP2pOff) Color(0xFFF59E0B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isP2pOff) Color(0xFFF59E0B).copy(alpha = 0.4f) else DesktopUi.Divider.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = if (isP2pOff) "TORRENT • P2P OFF" else formatTag,
                            color = if (isP2pOff) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isP2pOff) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Host / Source Tag
                    if (hostSource.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ) {
                            Text(
                                text = hostSource,
                                color = DesktopUi.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Extracted File Size Badge
                if (cleanSize != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = cleanSize,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Row 2: Full Raw Release Title (100% visible and unclipped)
            Text(
                text = link.name,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = DesktopUi.TextPrimary,
                softWrap = true,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Row 3: Action Buttons (Copy, Download, Play)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onCopy,
                    enabled = !isBusy,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Stream URL",
                        tint = DesktopUi.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onDownload,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Download",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onPlay,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopUi.Accent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Play",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
