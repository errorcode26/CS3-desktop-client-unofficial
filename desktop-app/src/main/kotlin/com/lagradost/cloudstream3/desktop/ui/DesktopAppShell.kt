package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.ui.components.DockItem
import com.lagradost.cloudstream3.desktop.ui.components.TopBar
import com.lagradost.cloudstream3.desktop.ui.components.UpdatesNotificationBell
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

// Removed DesktopUiState globally!
val LocalSafeArea = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

@Composable
fun DesktopAppShell(
    onNavigate: (Config) -> Unit,
    onBack: () -> Unit = {},
    title: String? = null,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
    showDock: Boolean = true,
    showTopBar: Boolean = true,
    applySafePadding: Boolean = false,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .collectAsState(initial = false)

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .collectAsState(initial = emptyList())

    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
            val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
            val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()

            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val primaryColor = MaterialTheme.colorScheme.primary

            val surfaceColor = MaterialTheme.colorScheme.surface
            val backgroundGradientEnabled by AppearanceConfig.backgroundGradientEnabled.collectAsState()
            val backgroundGradientType by AppearanceConfig.backgroundGradientType.collectAsState()
            val backgroundGradientIntensity by AppearanceConfig.backgroundGradientIntensity.collectAsState()

            val bgImagePath by AppearanceConfig.backgroundImagePath.collectAsState()
            val bgImageBlur by AppearanceConfig.backgroundImageBlur.collectAsState()
            val bgImageBrightness by AppearanceConfig.backgroundImageBrightness.collectAsState()
            val bgImageOpacity by AppearanceConfig.backgroundImageOpacity.collectAsState()
            val bgImageSaturation by AppearanceConfig.backgroundImageSaturation.collectAsState()
            val bgImageVignetteEnabled by AppearanceConfig.backgroundImageVignetteEnabled.collectAsState()
            val bgImageVignetteIntensity by AppearanceConfig.backgroundImageVignetteIntensity.collectAsState()
            val bgImageTintEnabled by AppearanceConfig.backgroundImageTintEnabled.collectAsState()
            val bgImageTintColor by AppearanceConfig.backgroundImageTintColor.collectAsState()
            val bgImageTintAlpha by AppearanceConfig.backgroundImageTintAlpha.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        Modifier.drawWithCache {
                            val radius = size.width.coerceAtLeast(size.height) * 0.8f

                            // 1. Base Ambient Glows (from positions)
                            val glowBrushes = if (ambientGlowEnabled && !isLightMode) {
                                ambientGlowPositions.map { position ->
                                    val yOffset = 0f
                                    val centerOffset = when (position) {
                                        "Top" -> Offset(size.width / 2f, yOffset)
                                        "Bottom" -> Offset(size.width / 2f, size.height)
                                        "Left" -> Offset(0f, size.height / 2f)
                                        "Right" -> Offset(size.width, size.height / 2f)
                                        "Top Left" -> Offset(0f, yOffset)
                                        "Top Right" -> Offset(size.width, yOffset)
                                        "Bottom Left" -> Offset(0f, size.height)
                                        "Bottom Right" -> Offset(size.width, size.height)
                                        else -> Offset(size.width / 2f, size.height / 2f)
                                    }
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to primaryColor.copy(alpha = ambientGlowIntensity),
                                            0.3f to primaryColor.copy(alpha = ambientGlowIntensity * 0.53f),
                                            0.6f to primaryColor.copy(alpha = ambientGlowIntensity * 0.2f),
                                            1.0f to Color.Transparent,
                                        ),
                                        center = centerOffset,
                                        radius = radius,
                                    )
                                }
                            } else {
                                emptyList()
                            }

                            // 2. Premium Background Gradient
                            val bgGradientBrush = if (backgroundGradientEnabled) {
                                val gradientAlpha = backgroundGradientIntensity
                                val endColor = if (isLightMode) Color.White.copy(alpha = gradientAlpha) else Color.Black.copy(alpha = gradientAlpha)
                                val startColor = surfaceColor

                                when (backgroundGradientType) {
                                    "Radial" -> androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(startColor, endColor),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = radius * 1.5f,
                                    )
                                    "Linear" -> androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(startColor, endColor),
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height),
                                    )
                                    else -> null
                                }
                            } else {
                                null
                            }

                            onDrawBehind {
                                if (bgGradientBrush != null) {
                                    drawRect(brush = bgGradientBrush)
                                } else {
                                    drawRect(color = surfaceColor)
                                }
                                glowBrushes.forEach { drawRect(brush = it) }
                            }
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Background image layer (rendered below all other content)
                if (bgImagePath.isNotEmpty()) {
                    val blurDp = bgImageBlur.dp
                    val scrimAlpha = 1f - bgImageBrightness
                    val tintColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(bgImageTintColor, Color(0xFF7C6BFF))

                    // Build saturation ColorMatrix: lerp between grayscale (0) and identity (1)
                    val colorFilter = remember(bgImageSaturation) {
                        if (bgImageSaturation < 0.999f) {
                            val s = bgImageSaturation
                            val invS = 1f - s
                            val rw = 0.213f
                            val gw = 0.715f
                            val bw = 0.072f
                            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(
                                    floatArrayOf(
                                        rw * invS + s, gw * invS, bw * invS, 0f, 0f,
                                        rw * invS, gw * invS + s, bw * invS, 0f, 0f,
                                        rw * invS, gw * invS, bw * invS + s, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        } else {
                            null
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().then(if (bgImageOpacity < 0.999f) Modifier.alpha(bgImageOpacity) else Modifier)) {
                        AsyncImage(
                            model = java.io.File(bgImagePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(blurDp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                        )
                        // Brightness scrim (black)
                        if (scrimAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 0.95f))),
                            )
                        }
                        // Color tint overlay
                        if (bgImageTintEnabled && bgImageTintAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tintColor.copy(alpha = bgImageTintAlpha.coerceIn(0f, 0.95f))),
                            )
                        }
                        // Vignette (radial gradient: transparent center → black edges)
                        if (bgImageVignetteEnabled) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.55f to Color.Transparent,
                                            1.0f to Color.Black.copy(alpha = bgImageVignetteIntensity),
                                        ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = (size.width.coerceAtLeast(size.height)) * 0.75f,
                                    ),
                                )
                            }
                        }
                    }
                }
                val safeTop = if (showTopBar) 64.dp else 0.dp
                val basePadding = 16.dp

                val contentPadding = if (showDock) {
                    when (dockPosition) {
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT -> PaddingValues(
                            start = 82.dp + basePadding,
                            top = safeTop + basePadding,
                            end = basePadding,
                            bottom = basePadding,
                        )
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT -> PaddingValues(
                            start = basePadding,
                            top = safeTop + basePadding,
                            end = 82.dp + basePadding,
                            bottom = basePadding,
                        )
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP -> PaddingValues(
                            start = basePadding,
                            top = 82.dp + safeTop + basePadding,
                            end = basePadding,
                            bottom = basePadding,
                        )
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM -> PaddingValues(
                            start = basePadding,
                            top = safeTop + basePadding,
                            end = basePadding,
                            bottom = 82.dp + basePadding,
                        )
                        else -> PaddingValues(
                            start = 82.dp + basePadding,
                            top = safeTop + basePadding,
                            end = basePadding,
                            bottom = basePadding,
                        )
                    }
                } else {
                    PaddingValues(
                        start = basePadding,
                        top = safeTop + basePadding,
                        end = basePadding,
                        bottom = basePadding,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().then(if (applySafePadding) Modifier.padding(contentPadding) else Modifier),
                ) {
                    CompositionLocalProvider(LocalSafeArea provides contentPadding) {
                        content()
                    }
                }

                if (showTopBar) {
                    // Global TopBar (Back button + Window Controls)
                    // Positioned outside the width-constrained box so it always anchors to the absolute edges of the window
                    TopBar(
                        isHome = title == "Home",
                        homeUiState = homeUiState,
                        homeActionDispatcher = homeActionDispatcher,
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }

            if (showDock) {
                // Navigation Dock
                val dockAlignment = when (dockPosition) {
                    com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT -> Alignment.CenterEnd
                    com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM -> Alignment.BottomCenter
                    com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP -> Alignment.TopCenter
                    else -> Alignment.CenterStart
                }
                NavigationDock(
                    modifier = Modifier.align(dockAlignment),
                    currentTitle = title ?: "",
                    dockPosition = dockPosition,
                    onNavigate = onNavigate,
                    onSearchClick = {
                        onNavigate(Config.Search)
                    },
                )
            }

            // Updates Notification Bell (Always bottom left)
            UpdatesNotificationBell(
                modifier = Modifier.align(Alignment.BottomStart),
                hasUnreadUpdates = hasUnreadUpdates,
                updatesHistory = updatesHistory,
                onMarkUpdatesRead = { DesktopDataStore.setUnreadUpdates(false) },
            )
        }
    }
}

@Composable
private fun NavigationDock(
    modifier: Modifier = Modifier,
    currentTitle: String,
    dockPosition: com.lagradost.cloudstream3.desktop.ui.DockPosition,
    onNavigate: (Config) -> Unit,
    onSearchClick: () -> Unit,
) {
    val isBottom = dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM
    val isRight = dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT
    val isTop = dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP
    val isHorizontal = isBottom || isTop

    val dockItems = @Composable {
        DockItem(
            icon = PremiumIcons.Home,
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.HOME,
            selected = currentTitle == "Home",
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = {
                onNavigate(Config.Home)
            },
        )
        DockItem(
            icon = PremiumIcons.Search,
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SEARCH,
            selected = currentTitle == "Search",
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = onSearchClick,
        )
        DockItem(icon = PremiumIcons.Library, label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.LIBRARY, selected = currentTitle == "Library", isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Config.Library) })
        DockItem(
            icon = PremiumIcons.Extensions,
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.EXTENSIONS,
            selected = currentTitle == "Extensions",
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = { onNavigate(Config.Extensions(0)) },
        )
        DockItem(icon = PremiumIcons.Settings, label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SETTINGS, selected = currentTitle == "Settings", isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Config.Settings) })
    }

    val surfaceModifier = when {
        isBottom -> Modifier.padding(bottom = 14.dp).height(54.dp).wrapContentWidth()
        isTop -> Modifier.padding(top = 14.dp).height(54.dp).wrapContentWidth()
        isRight -> Modifier.padding(end = 14.dp).width(54.dp).wrapContentHeight()
        else -> Modifier.padding(start = 14.dp).width(54.dp).wrapContentHeight()
    }

    val paddingInsideSurface = if (isHorizontal) {
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    } else {
        Modifier.padding(vertical = 14.dp, horizontal = 6.dp)
    }

    val mainDockSurface = @Composable {
        Box(modifier = surfaceModifier) {
            // Drop shadow without occlusion to prevent weird whitish middle bar artifact
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(12.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .background(Color.Black.copy(alpha = 0.40f), RoundedCornerShape(20.dp)),
            )

            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val glassBase = if (isLightMode) Color.White else Color(0xFF1E1E24)
            val glassGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    glassBase.copy(alpha = 0.60f),
                    glassBase.copy(alpha = 0.45f),
                ),
            )
            val borderGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    if (isLightMode) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
                    if (isLightMode) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.25f),
                ),
            )

            Box(
                modifier = Modifier
                    .background(glassGradient, RoundedCornerShape(20.dp))
                    .border(1.5.dp, borderGradient, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                if (isHorizontal) {
                    Row(
                        modifier = paddingInsideSurface,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dockItems()
                    }
                } else {
                    Column(
                        modifier = paddingInsideSurface,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dockItems()
                    }
                }
            }
        }
    }

    Box(modifier = modifier) {
        mainDockSurface()
    }
}
