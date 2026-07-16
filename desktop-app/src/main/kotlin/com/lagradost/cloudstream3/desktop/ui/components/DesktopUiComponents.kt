package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class DesktopThemeColors(
    val Accent: Color,
    val AccentSoft: Color,
    val Background: Color,
    val SurfaceCard: Color,
    val SurfaceElevated: Color,
    val TextPrimary: Color,
    val TextMuted: Color,
    val Divider: Color,
)

fun darkDesktopColors(accent: Color, amoled: Boolean) = DesktopThemeColors(
    Accent = accent,
    AccentSoft = accent.copy(alpha = 0.22f),
    Background = if (amoled) Color.Black else Color(0xFF0C0C16), // Deep charcoal with a subtle cool tone
    SurfaceCard = if (amoled) Color.Black else Color(0xFF161624), // Slightly lifted card surface
    SurfaceElevated = if (amoled) Color(0xFF0A0A0A) else Color(0xFF20202E), // Hover/elevated state
    TextPrimary = Color(0xFFF3F4F6),
    TextMuted = Color(0xFF9CA3AF),
    Divider = Color(0xFF2A2A38),
)

fun lightDesktopColors(accent: Color) = DesktopThemeColors(
    Accent = accent,
    AccentSoft = accent.copy(alpha = 0.15f), // Softer accent bg for light mode
    Background = Color(0xFFF8FAFC), // Slate 50: Crisp, bright off-white with a tiny hint of cool blue
    SurfaceCard = Color(0xFFFFFFFF), // Pure White for elevated cards
    SurfaceElevated = Color(0xFFF1F5F9), // Slate 100: Soft contrast for hover states and secondary surfaces
    TextPrimary = Color(0xFF0F172A), // Slate 900: Deep navy-black for strong, premium contrast
    TextMuted = Color(0xFF64748B), // Slate 500: Clear but soft muted text
    Divider = Color(0xFFE2E8F0), // Slate 200: Subtle divider
)

val LocalDesktopTheme = staticCompositionLocalOf<DesktopThemeColors> { error("No DesktopTheme provided") }

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.heightIn(max = 400.dp).widthIn(max = 350.dp),
        offset = offset,
        content = content
    )
}

object DesktopUi {
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Accent
    val AccentSoft: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.AccentSoft
    val Background: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Background
    val SurfaceCard: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.SurfaceCard
    val SurfaceElevated: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.SurfaceElevated
    val TextPrimary: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.TextPrimary
    val TextMuted: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.TextMuted
    val Divider: Color
        @Composable @ReadOnlyComposable
        get() = LocalDesktopTheme.current.Divider
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = DesktopUi.TextPrimary,
        )
        trailing?.invoke()
    }
}

@Composable
fun CategoryRowWithHeader(
    title: String,
    modifier: Modifier = Modifier,
    itemCount: Int,
    scrollStep: Int = 4,
    isInfinite: Boolean = false,
    onViewAll: (() -> Unit)? = null,
    trailingHeaderExtra: @Composable (() -> Unit)? = null,
    // Padding applied to the LazyRow's content — lets it extend full-width while items
    // align with the constrained header above. PaddingValues.Absolute avoids RTL mirroring.
    rowContentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    content: LazyListScope.() -> Unit,
) {
    val initialIndex = remember(isInfinite, itemCount) {
        if (isInfinite && itemCount > 0) (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % itemCount) else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val canScrollBack by remember(isInfinite) { derivedStateOf { isInfinite || listState.canScrollBackward } }
    val canScrollForward by remember(isInfinite) { derivedStateOf { isInfinite || listState.canScrollForward } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DesktopUi.TextPrimary,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingHeaderExtra != null) {
                    trailingHeaderExtra()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (onViewAll != null) {
                    TextButton(onClick = onViewAll) {
                        Text("View All", color = DesktopUi.Accent)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                ScrollChevron(
                    enabled = canScrollBack,
                    onClick = {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - scrollStep).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                )

                Spacer(modifier = Modifier.width(8.dp))

                ScrollChevron(
                    enabled = canScrollForward,
                    onClick = {
                        scope.launch {
                            val last = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + scrollStep
                            val maxBound = if (isInfinite) Int.MAX_VALUE else (itemCount - 1).coerceAtLeast(0)
                            listState.animateScrollToItem(last.coerceAtMost(maxBound))
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
            }
        }

        LazyRow(
            state = listState,
            // fillMaxWidth() so the list extends edge-to-edge; contentPadding indents items
            // to align with the header. clipToBounds=false lets the last card peek fully
            // without being hard-cut by the container boundary.
            modifier = Modifier.fillMaxWidth(),
            contentPadding = rowContentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollChevron(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp),
        shape = CircleShape,
        color = DesktopUi.SurfaceElevated.copy(alpha = alpha),
        shadowElevation = if (enabled) 4.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) DesktopUi.Accent else DesktopUi.TextMuted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun PosterTitleLabel(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(DesktopUi.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = DesktopUi.TextPrimary,
            lineHeight = 16.sp,
        )
    }
}

@Composable
fun Modifier.posterHoverEffect(): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = tween(180),
        label = "posterScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (hovered) 12f else 4f,
        animationSpec = tween(180),
        label = "posterElevation",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "posterBorderColor",
    )
    return this
        .scale(scale)
        .hoverable(interaction)
        .shadow(elevation.dp, RoundedCornerShape(12.dp))
        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
}
