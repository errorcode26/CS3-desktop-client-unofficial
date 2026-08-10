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

fun darkDesktopColors(accent: Color, backgroundTheme: String, customBgHex: String = "#0C0C16"): DesktopThemeColors {
    val (bg, surface, surfaceElevated) = when (backgroundTheme) {
        "Custom" -> {
            val base = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customBgHex, Color(0xFF0C0C16))

            // Generate surface colors by blending with white or just lightening
            // Since we don't have a full HSL library, we'll manually blend
            fun Color.lighten(fraction: Float): Color {
                return Color(
                    red = (this.red + (1f - this.red) * fraction).coerceIn(0f, 1f),
                    green = (this.green + (1f - this.green) * fraction).coerceIn(0f, 1f),
                    blue = (this.blue + (1f - this.blue) * fraction).coerceIn(0f, 1f),
                    alpha = this.alpha,
                )
            }
            Triple(base, base.lighten(0.04f), base.lighten(0.08f))
        }
        "Midnight Blue" -> Triple(Color(0xFF0B1120), Color(0xFF131B2D), Color(0xFF1E293B))
        "Slate Grey" -> Triple(Color(0xFF18181B), Color(0xFF27272A), Color(0xFF3F3F46))
        "Mocha" -> Triple(Color(0xFF1E1815), Color(0xFF2C2420), Color(0xFF3B302B))
        "Forest" -> Triple(Color(0xFF0F1714), Color(0xFF1B2722), Color(0xFF26372E))
        "Deep Purple" -> Triple(Color(0xFF130C1C), Color(0xFF1F162E), Color(0xFF2D2043))
        "Pure Black" -> Triple(Color.Black, Color.Black, Color(0xFF0A0A0A))
        else -> Triple(Color(0xFF0C0C16), Color(0xFF161624), Color(0xFF20202E)) // Navy
    }

    return DesktopThemeColors(
        Accent = accent,
        AccentSoft = accent.copy(alpha = 0.22f),
        Background = bg,
        SurfaceCard = surface,
        SurfaceElevated = surfaceElevated,
        TextPrimary = Color.White,
        TextMuted = Color.White.copy(alpha = 0.7f),
        Divider = Color(0xFF2A2A38),
    )
}

fun lightDesktopColors(accent: Color, backgroundTheme: String, customBgHex: String = "#0C0C16"): DesktopThemeColors {
    val (bg, surface, surfaceElevated) = when (backgroundTheme) {
        "Custom" -> {
            val base = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customBgHex, Color(0xFFF8FAFC))
            fun Color.darken(fraction: Float): Color {
                return Color(
                    red = (this.red * (1f - fraction)).coerceIn(0f, 1f),
                    green = (this.green * (1f - fraction)).coerceIn(0f, 1f),
                    blue = (this.blue * (1f - fraction)).coerceIn(0f, 1f),
                    alpha = this.alpha,
                )
            }
            Triple(base, base.darken(0.04f), base.darken(0.08f))
        }
        "Midnight Blue" -> Triple(Color(0xFFE0E7FF), Color(0xFFC7D2FE), Color(0xFFA5B4FC))
        "Slate Grey" -> Triple(Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFFCBD5E1))
        "Mocha" -> Triple(Color(0xFFF5F5F4), Color(0xFFE7E5E4), Color(0xFFD6D3D1))
        "Forest" -> Triple(Color(0xFFF0FDF4), Color(0xFFDCFCE7), Color(0xFFBBF7D0))
        "Deep Purple" -> Triple(Color(0xFFFAF5FF), Color(0xFFF3E8FF), Color(0xFFE9D5FF))
        "Pure Black" -> Triple(Color.White, Color(0xFFF5F5F5), Color(0xFFE5E5E5)) // Light mode equivalent
        else -> Triple(Color(0xFFF8FAFC), Color(0xFFFFFFFF), Color(0xFFF1F5F9)) // Slate 50 default
    }

    return DesktopThemeColors(
        Accent = accent,
        AccentSoft = accent.copy(alpha = 0.15f),
        Background = bg,
        SurfaceCard = surface,
        SurfaceElevated = surfaceElevated,
        TextPrimary = Color.Black.copy(alpha = 0.87f),
        TextMuted = Color.Black.copy(alpha = 0.6f),
        Divider = Color.Black.copy(alpha = 0.1f),
    )
}

val LocalDesktopTheme = staticCompositionLocalOf<DesktopThemeColors> { error("No DesktopTheme provided") }

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.heightIn(max = 400.dp).widthIn(max = 350.dp),
        offset = offset,
        content = content,
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
    rowContentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
    headerPadding: PaddingValues = PaddingValues(start = 10.dp, top = 12.dp, bottom = 8.dp, end = 10.dp),
    itemSpacing: androidx.compose.ui.unit.Dp = 12.dp,
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
                .padding(headerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DesktopUi.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingHeaderExtra != null) {
                    trailingHeaderExtra()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (onViewAll != null) {
                    Surface(
                        onClick = onViewAll,
                        shape = CircleShape,
                        color = DesktopUi.SurfaceElevated.copy(alpha = 0.6f),
                        shadowElevation = 2.dp,
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("View All", color = DesktopUi.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
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
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
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
        shadowElevation = (if (enabled) 4.dp else 0.dp).applyShadowMultiplier(),
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
fun Modifier.posterHoverEffect(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)): Modifier {
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
        .shadow(elevation.dp.applyShadowMultiplier(), shape)
        .border(2.dp, borderColor, shape)
}

@Composable
fun getTextShadow(): androidx.compose.ui.graphics.Shadow? {
    val enabled by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.textDropShadowEnabled.collectAsState()
    val blur by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.textDropShadowBlur.collectAsState()
    return if (enabled && blur > 0f) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
            blurRadius = blur,
        )
    } else {
        null
    }
}

@Composable
fun androidx.compose.ui.unit.Dp.applyShadowMultiplier(): androidx.compose.ui.unit.Dp {
    val enabled by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.elementShadowsEnabled.collectAsState()
    val multiplier by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.elementShadowMultiplier.collectAsState()
    return if (enabled) this * multiplier else 0.dp
}
