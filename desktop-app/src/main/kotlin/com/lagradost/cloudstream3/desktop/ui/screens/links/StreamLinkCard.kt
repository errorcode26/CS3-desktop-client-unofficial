package com.lagradost.cloudstream3.desktop.ui.screens.links

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

private val SERVER_BRACKET_REGEX = Regex("\\[(.*?)\\]")
private val SIZE_BRACKET_REGEX = Regex("\\[([0-9.]+\\s*(?:MB|GB|KB|TB|GiB|MiB))\\]", RegexOption.IGNORE_CASE)
private val SIZE_FALLBACK_REGEX = Regex("([0-9.]+\\s*(?:MB|GB|KB|TB|GiB|MiB))", RegexOption.IGNORE_CASE)

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
    return sizeMatch?.groupValues?.get(1)?.replace("GiB", "GB", ignoreCase = true)?.replace("MiB", "MB", ignoreCase = true)
}

internal fun extractTechTags(rawName: String): List<String> {
    val tags = mutableListOf<String>()
    val upper = rawName.uppercase()

    if (upper.contains("DOLBY VISION") || upper.contains("DV ") || upper.contains(".DV.") || upper.contains("-DV-") || upper.contains("[DV]")) {
        tags.add("Dolby Vision")
    } else if (upper.contains("HDR10+")) {
        tags.add("HDR10+")
    } else if (upper.contains("HDR10") || upper.contains(" HDR ") || upper.contains(".HDR.") || upper.contains("[HDR]")) {
        tags.add("HDR")
    }
    if (upper.contains("10BIT") || upper.contains("10-BIT") || upper.contains("10 BIT")) {
        tags.add("10-bit")
    }

    if (upper.contains("AV1")) {
        tags.add("AV1")
    } else if (upper.contains("HEVC") || upper.contains("X265") || upper.contains("H.265") || upper.contains("H265")) {
        tags.add("HEVC")
    } else if (upper.contains("X264") || upper.contains("H.264") || upper.contains("H264") || upper.contains("AVC")) {
        tags.add("AVC")
    }

    if (upper.contains("ATMOS")) {
        tags.add("Atmos")
    }
    if (upper.contains("TRUEHD")) {
        tags.add("TrueHD")
    } else if (upper.contains("DTS-HD") || upper.contains("DTSHD")) {
        tags.add("DTS-HD")
    } else if (upper.contains("DDP5.1") || upper.contains("DDP 5.1") || upper.contains("EAC3 5.1") || upper.contains("E-AC-3 5.1")) {
        tags.add("DDP 5.1")
    } else if (upper.contains("5.1") || upper.contains("6CH")) {
        tags.add("5.1")
    } else if (upper.contains("7.1") || upper.contains("8CH")) {
        tags.add("7.1")
    }

    return tags.distinct()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    tooltipText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val containerBg = when {
        isPrimary && isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isHovered -> Color.White.copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    val borderColor = when {
        isPrimary && isHovered -> MaterialTheme.colorScheme.primary
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
        isHovered -> Color.White.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val iconTint = when {
        isPrimary && isHovered -> MaterialTheme.colorScheme.onPrimary
        isPrimary -> MaterialTheme.colorScheme.primary
        isHovered -> Color.White
        else -> DesktopUi.TextPrimary.copy(alpha = 0.70f)
    }

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E1E28),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = tooltipText,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        delayMillis = 250,
    ) {
        Surface(
            modifier = modifier
                .size(36.dp)
                .hoverable(interaction)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = containerBg,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun StreamLinkCard(
    link: ExtractorLink,
    isP2pEnabled: Boolean = false,
    isPlaying: Boolean = false,
    isBusy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.005f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )

    val effectiveQuality = remember(link) { com.lagradost.cloudstream3.desktop.player.QualityDataHelper.extractEffectiveQuality(link) }
    val formattedQuality = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(effectiveQuality)
    val is4k = effectiveQuality >= 2160
    val is1080 = effectiveQuality in 1080..2159
    val is720 = effectiveQuality in 720..1079

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

    val qualityBadgeText = when {
        is4k -> "🔥 4K UHD"
        is1080 -> "🚀 FHD"
        is720 -> "HD 720p"
        else -> formattedQuality
    }

    val formatTag = when {
        link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8") -> "HLS"
        link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd") -> "DASH"
        link.type == ExtractorLinkType.TORRENT ||
            link.type == ExtractorLinkType.MAGNET ||
            link.url.startsWith("magnet:") -> "TORRENT"
        link.name.contains("MKV", ignoreCase = true) || link.url.contains(".mkv", ignoreCase = true) -> "MKV"
        else -> "MP4"
    }

    val isAdaptive = formatTag == "HLS" || formatTag == "DASH" || link.isM3u8 || link.isDash ||
        link.url.contains(".m3u8", ignoreCase = true) || link.url.contains(".mpd", ignoreCase = true)

    val cleanSize = remember(link.name) { extractCleanSize(link.name) }
    val addonSource = remember(link.source) { link.source.trim().ifBlank { null } }
    val hostSource = remember(link.name, link.source) {
        if (link.source.isNotBlank() && link.source != link.name) link.source else extractCleanServer(link.name, "")
    }
    val techTags = remember(link.name) { extractTechTags(link.name) }

    val rawLines = remember(link.name) {
        link.name.lines().map { it.trim() }.filter { it.isNotBlank() }
    }
    val firstLine = rawLines.firstOrNull() ?: ""
    val detailLines = if (rawLines.size > 1) rawLines.drop(1) else emptyList()

    val cleanTitle = remember(firstLine, addonSource, cleanSize) {
        var t = firstLine.removePrefix("⚡").trim()
        if (addonSource != null) {
            t = t.replace("[$addonSource]", "").trim()
        }
        if (cleanSize != null) {
            t = t.replace("[$cleanSize]", "").trim()
        }
        t.ifBlank { firstLine }
    }

    val cardShape = RoundedCornerShape(12.dp)
    val cardBackground = when {
        isPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        hovered -> DesktopUi.SurfaceElevated.copy(alpha = 0.85f)
        else -> DesktopUi.SurfaceCard.copy(alpha = 0.55f)
    }
    val cardBorder = when {
        isPlaying -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        hovered -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interaction)
            .clip(cardShape)
            .clickable(enabled = !isBusy, onClick = onPlay),
        shape = cardShape,
        color = cardBackground,
        tonalElevation = if (hovered) 4.dp else 1.dp,
        border = cardBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: Badges Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    // Quality Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = qualityContainerColor,
                        border = BorderStroke(1.dp, qualityTextColor.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text = qualityBadgeText,
                            color = qualityTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    // Addon / Source Badge
                    if (addonSource != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = addonSource,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Format Badge
                    val isTorrentBadge = formatTag == "TORRENT"
                    val isP2pOff = isTorrentBadge && !isP2pEnabled
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isP2pOff) Color(0xFFF59E0B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isP2pOff) Color(0xFFF59E0B).copy(alpha = 0.4f) else DesktopUi.Divider.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = if (isP2pOff) "TORRENT • P2P OFF" else formatTag,
                            color = if (isP2pOff) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isP2pOff) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }

                    // Host / Server Tag (if different from addon source)
                    if (hostSource.isNotBlank() && !hostSource.equals(addonSource, ignoreCase = true)) {
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                // If currently playing, show animated/styled PLAYING indicator
                if (isPlaying) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PLAYING",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Row 2: Title / Release Name
            Text(
                text = cleanTitle,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else DesktopUi.TextPrimary,
                softWrap = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Row 3: Detail Lines (e.g. from Stremio) OR Tech Tags
            if (detailLines.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    detailLines.take(5).forEach { dLine ->
                        Text(
                            text = dLine,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = DesktopUi.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (techTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    techTags.take(4).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, DesktopUi.Divider.copy(alpha = 0.25f)),
                        ) {
                            Text(
                                text = tag,
                                color = DesktopUi.TextMuted,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Row 4: Size Badge on Left, Action Buttons on Right
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Size Badge
                if (cleanSize != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    ) {
                        Text(
                            text = "SIZE $cleanSize",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Copy Stream URL
                    StreamActionIconButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copy Stream URL",
                        tooltipText = "Copy Stream URL",
                        enabled = !isBusy,
                        onClick = onCopy,
                    )

                    // Download Stream (Direct links only)
                    if (!isAdaptive) {
                        StreamActionIconButton(
                            icon = Icons.Default.Download,
                            contentDescription = "Download Stream",
                            tooltipText = "Download Stream",
                            enabled = !isBusy,
                            onClick = onDownload,
                        )
                    }

                    // Play Button
                    StreamActionIconButton(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = "Play Stream",
                        tooltipText = "Play Stream",
                        enabled = !isBusy,
                        onClick = onPlay,
                        isPrimary = true,
                    )
                }
            }
        }
    }
}
