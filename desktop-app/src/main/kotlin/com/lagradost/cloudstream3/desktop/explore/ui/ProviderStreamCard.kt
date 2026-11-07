package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme

@Composable
internal fun ProviderFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color.Black else theme.TextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun ProviderStreamCard(
    match: ProviderMatch,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg = if (isHovered) {
        if (theme.isAmoled) Color(0xFF161820) else theme.SurfaceElevated
    } else {
        if (theme.isAmoled) Color(0xFF0F1116) else theme.SurfaceCard
    }

    val cardBorder = if (isHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Provider Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                ) {
                    Text(
                        text = match.providerName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }

                // Title and Badges
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = match.displayTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (match.hasSub || match.hasDub) {
                        DesktopBadgeComponents.SubDubBadge(hasSub = match.hasSub, hasDub = match.hasDub)
                    }
                    if (!match.qualityText.isNullOrBlank()) {
                        DesktopBadgeComponents.QualityBadge(quality = match.qualityText)
                    }
                }
            }

            // Icon-only arrow circle — compact, keeps title readable
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isHovered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.07f)
                        },
                    )
                    .border(0.5.dp, if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Details",
                    tint = if (isHovered) Color.Black else theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
