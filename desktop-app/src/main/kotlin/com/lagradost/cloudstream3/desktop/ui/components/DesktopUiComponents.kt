package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    val isLightMode: Boolean = false,
    val isAmoled: Boolean = false,
    val cardOpacity: Float = 1.0f,
)

fun darkDesktopColors(
    accent: Color,
    backgroundTheme: String,
    isAmoled: Boolean = false,
    customBgHex: String = "#0C0C16",
): DesktopThemeColors {
    val (bg, surface, surfaceElevated) = if (isAmoled) {
        Triple(Color.Black, Color.Black, Color(0xFF0A0A0A))
    } else {
        when (backgroundTheme) {
            "Midnight Blue", "Midnight" -> Triple(Color(0xFF0B1120), Color(0xFF131B2D), Color(0xFF1E293B))
            "Slate Grey", "Slate", "Dark Slate" -> Triple(Color(0xFF18181B), Color(0xFF27272A), Color(0xFF3F3F46))
            "Mocha", "Warm Mocha" -> Triple(Color(0xFF1E1815), Color(0xFF2C2420), Color(0xFF3B302B))
            "Forest", "Deep Forest" -> Triple(Color(0xFF0F1714), Color(0xFF1B2722), Color(0xFF26372E))
            "Deep Purple", "Deep Amethyst", "Amethyst" -> Triple(Color(0xFF130C1C), Color(0xFF1F162E), Color(0xFF2D2043))
            "Pure Black" -> Triple(Color.Black, Color.Black, Color(0xFF0A0A0A))
            else -> Triple(Color(0xFF0C0C16), Color(0xFF161624), Color(0xFF20202E)) // Navy
        }
    }

    return DesktopThemeColors(
        Accent = accent,
        AccentSoft = accent.copy(alpha = 0.22f),
        Background = bg,
        SurfaceCard = surface,
        SurfaceElevated = surfaceElevated,
        TextPrimary = Color.White,
        TextMuted = Color.White.copy(alpha = 0.7f),
        Divider = if (isAmoled) Color(0xFF1C1C22) else Color(0xFF2A2A38),
        isLightMode = false,
        isAmoled = isAmoled,
    )
}

fun lightDesktopColors(accent: Color, backgroundTheme: String, customBgHex: String = "#0C0C16"): DesktopThemeColors {
    data class LightPaletteDef(
        val bg: Color,
        val surface: Color,
        val surfaceElevated: Color,
        val textPrimary: Color,
        val textMuted: Color,
        val divider: Color,
    )

    val p = when (backgroundTheme) {
        "Alabaster", "Warm Alabaster", "Ivory" -> LightPaletteDef(
            bg = Color(0xFFF7F4EE),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEAE5DB),
            textPrimary = Color(0xFF26211E),
            textMuted = Color(0xFF786F66),
            divider = Color(0xFFE2DDD3),
        )
        "Nordic", "Nordic Snow", "Nord" -> LightPaletteDef(
            bg = Color(0xFFECEFF4),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE5E9F0),
            textPrimary = Color(0xFF2E3440),
            textMuted = Color(0xFF4C566A),
            divider = Color(0xFFD8DEE9),
        )
        "Latte", "Warm Latte", "Mocha", "Warm Mocha" -> LightPaletteDef(
            bg = Color(0xFFF3EDE5),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE8DECF),
            textPrimary = Color(0xFF382F2D),
            textMuted = Color(0xFF7D6E68),
            divider = Color(0xFFDDD2C2),
        )
        "Matcha", "Matcha Tea", "Mint", "Sage", "Forest", "Deep Forest" -> LightPaletteDef(
            bg = Color(0xFFEDF4EF),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFDFEBE2),
            textPrimary = Color(0xFF1B2B21),
            textMuted = Color(0xFF526859),
            divider = Color(0xFFD1E0D5),
        )
        "Sakura", "Sakura Blush", "Blush", "Rose" -> LightPaletteDef(
            bg = Color(0xFFFAF0F4),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFF5DDE7),
            textPrimary = Color(0xFF331B24),
            textMuted = Color(0xFF7A5665),
            divider = Color(0xFFECCBD8),
        )
        "Solar", "Solarized", "Solarized Light" -> LightPaletteDef(
            bg = Color(0xFFFDF6E3),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFF4ECCF),
            textPrimary = Color(0xFF073642),
            textMuted = Color(0xFF586E75),
            divider = Color(0xFFE0D7BE),
        )
        "Custom" -> {
            val customBg = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customBgHex, Color(0xFFF1F5F9))
            LightPaletteDef(
                bg = customBg,
                surface = Color(0xFFFFFFFF),
                surfaceElevated = Color(0xFFE2E8F0),
                textPrimary = Color(0xFF0F172A),
                textMuted = Color(0xFF475569),
                divider = Color(0xFFCBD5E1),
            )
        }
        else -> LightPaletteDef( // Clean Frost / Slate 100 default
            bg = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE2E8F0),
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xFF475569),
            divider = Color(0xFFCBD5E1),
        )
    }

    return DesktopThemeColors(
        Accent = accent,
        AccentSoft = accent.copy(alpha = 0.12f),
        Background = p.bg,
        SurfaceCard = p.surface,
        SurfaceElevated = p.surfaceElevated,
        TextPrimary = p.textPrimary,
        TextMuted = p.textMuted,
        Divider = p.divider,
        isLightMode = true,
        isAmoled = false,
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
    val appThemeBackground by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.appThemeBackground.collectAsState()
    val isAmoled = appThemeBackground == "Pure Black"

    val currentColorScheme = MaterialTheme.colorScheme
    val amoledColorScheme = currentColorScheme.copy(
        surface = if (isAmoled) Color(0xFF101010) else currentColorScheme.surface,
    )

    MaterialTheme(colorScheme = amoledColorScheme) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .heightIn(max = 400.dp)
                .widthIn(max = 350.dp)
                .then(if (isAmoled) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)) else Modifier),
            offset = offset,
            content = content,
        )
    }
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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(headerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DesktopUi.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingHeaderExtra != null) {
                        trailingHeaderExtra()
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (onViewAll != null) {
                        Surface(
                            onClick = onViewAll,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shadowElevation = 0.dp,
                        ) {
                            Box(modifier = Modifier.padding(horizontal = if (isCompact) 10.dp else 16.dp, vertical = if (isCompact) 4.dp else 8.dp)) {
                                Text("View All", color = MaterialTheme.colorScheme.primary, style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (!isCompact) {
                        Spacer(modifier = Modifier.width(8.dp))
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollChevron(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val alpha = if (enabled) 0.6f else 0.25f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(38.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
        border = BorderStroke(1.dp, if (enabled) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp),
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

fun Modifier.desktopDragScroll(
    state: androidx.compose.foundation.lazy.LazyListState,
): Modifier {
    return this.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val downEvent = awaitPointerEvent(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                var totalDx = 0f
                var totalDy = 0f
                var dragging = false

                while (true) {
                    val event = awaitPointerEvent(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val delta = change.position - change.previousPosition
                    totalDx += delta.x
                    totalDy += delta.y

                    if (!dragging) {
                        val horizontalDrag = kotlin.math.abs(totalDx) > viewConfiguration.touchSlop && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)
                        val verticalDrag = kotlin.math.abs(totalDy) > viewConfiguration.touchSlop && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)

                        when {
                            verticalDrag -> break
                            horizontalDrag -> dragging = true
                            else -> continue
                        }
                    }

                    state.dispatchRawDelta(-delta.x)
                    change.consume()
                }
            }
        }
    }
}
