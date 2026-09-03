package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.profile.ProfileAvatar
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.ui.components.DockItem
import com.lagradost.cloudstream3.desktop.ui.components.TopBar
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val LocalSafeArea = compositionLocalOf<PaddingValues> { PaddingValues(0.dp) }
val LocalHazeState = compositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

object TopBarScrollState {
    var isScrolled by mutableStateOf(false)
}

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
    onOpenProfileManager: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val posterCardStyle = com.lagradost.cloudstream3.desktop.ui.components.rememberPosterCardStyle()
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    CompositionLocalProvider(
        com.lagradost.cloudstream3.desktop.ui.components.LocalPosterCardStyle provides posterCardStyle,
        LocalHazeState provides hazeState,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp
                val effectiveDockPosition = if (isCompact) com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM else dockPosition

                val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
                val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
                val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()

            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val amoledMode by AppearanceConfig.amoledMode.collectAsState()
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
                            val glowBrushes = if (ambientGlowEnabled && !isLightMode && !amoledMode) {
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

                            // Background gradient
                            val bgGradientBrush = if (backgroundGradientEnabled && !amoledMode) {
                                val gradientAlpha = backgroundGradientIntensity
                                val endColor = if (isLightMode) Color.White.copy(alpha = gradientAlpha) else Color.Black.copy(alpha = gradientAlpha)
                                val startColor = surfaceColor

                                when (backgroundGradientType) {
                                    "Radial" -> androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(startColor, endColor),
                                        center = Offset(size.width * 0.5f, size.height * 0.25f),
                                        radius = size.width.coerceAtLeast(size.height) * 0.95f,
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
                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                .data(java.io.File(bgImagePath))
                                .size(coil3.size.Size(1920, 1080))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (blurDp > 0.dp) Modifier.blur(blurDp, edgeTreatment = BlurredEdgeTreatment.Rectangle) else Modifier),
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
                val safeTop = if (showTopBar) (if (isCompact) 54.dp else 64.dp) else 0.dp
                val basePadding = if (isCompact) 8.dp else 16.dp

                val contentPadding = if (showDock) {
                    when (effectiveDockPosition) {
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
                            top = (if (showTopBar) 68.dp else 54.dp) + basePadding,
                            end = basePadding,
                            bottom = basePadding,
                        )
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM -> PaddingValues(
                            start = basePadding,
                            top = safeTop + basePadding,
                            end = basePadding,
                            bottom = (if (isCompact) 64.dp else 82.dp) + basePadding,
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
                    // Global TopBar (Back button + Window Controls + Top Dock)
                    // Positioned outside the width-constrained box so it always anchors to the absolute edges of the window
                    TopBar(
                        isHome = title == "Home",
                        homeUiState = homeUiState,
                        homeActionDispatcher = homeActionDispatcher,
                        onBack = onBack,
                        onOpenProfileManager = onOpenProfileManager,
                        showDock = showDock,
                        currentTitle = title ?: "",
                        onNavigate = onNavigate,
                        onSearchClick = { onNavigate(Config.Search) },
                    )
                }

                // ── Offline Network Banner ──────────────────────────────────
                val isOnline by com.lagradost.cloudstream3.desktop.network.NetworkMonitor.isOnline.collectAsState()
                val isCheckingNetwork by com.lagradost.cloudstream3.desktop.network.NetworkMonitor.isChecking.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isOnline,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp).zIndex(99f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E140A).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.55f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.WifiOff,
                                contentDescription = "Offline Mode",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = "Offline Mode Active",
                                    color = Color.White,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = "No internet connection detected. Local playback, saved bookmarks, and downloaded content are available.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = { com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.openLocalFileDialog() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF3B2810),
                                    contentColor = Color(0xFFFFCC80),
                                ),
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play Local File", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch { com.lagradost.cloudstream3.desktop.network.NetworkMonitor.checkConnectivity() }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFF9800).copy(alpha = 0.25f),
                                    contentColor = Color(0xFFFFE0B2),
                                ),
                            ) {
                                if (isCheckingNetwork) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFFFFE0B2))
                                } else {
                                    Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Retry", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )

                com.lagradost.cloudstream3.desktop.ui.components.ProfileWelcomeToast()
            }

            if (showDock) {
                if (isCompact) {
                    MobileBottomNavBar(
                        modifier = Modifier.align(Alignment.BottomCenter).zIndex(90f),
                        currentTitle = title ?: "",
                        onNavigate = onNavigate,
                        onSearchClick = {
                            onNavigate(Config.Search)
                        },
                    )
                } else if (effectiveDockPosition != com.lagradost.cloudstream3.desktop.ui.DockPosition.TOP) {
                    // Navigation Dock (Desktop: LEFT, RIGHT, BOTTOM)
                    val dockAlignment = when (effectiveDockPosition) {
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT -> Alignment.CenterEnd
                        com.lagradost.cloudstream3.desktop.ui.DockPosition.BOTTOM -> Alignment.BottomCenter
                        else -> Alignment.CenterStart
                    }
                    NavigationDock(
                        modifier = Modifier.align(dockAlignment),
                        currentTitle = title ?: "",
                        dockPosition = effectiveDockPosition,
                        onNavigate = onNavigate,
                        onSearchClick = {
                            onNavigate(Config.Search)
                        },
                    )
                }
            }
        }
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
        com.lagradost.cloudstream3.desktop.ui.components.DockItemsList(
            currentTitle = currentTitle,
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onNavigate = onNavigate,
            onSearchClick = onSearchClick,
        )
    }

    val navStyle by AppearanceConfig.navigationStyle.collectAsState()
    val isSeamless = navStyle == com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.SEAMLESS_BAR
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val dockHazeState = LocalHazeState.current
    val isTopOrBottom = isTop || isBottom

    val barBase = when {
        isLightMode -> Color(0xFFFAFAFC)
        amoledMode -> Color.Black
        else -> Color(0xFF14141A)
    }

    if (isSeamless) {
        // Navigation bar mode
        val barModifier = when {
            isBottom -> Modifier.fillMaxWidth().height(56.dp)
            isTop -> Modifier.fillMaxWidth().height(56.dp)
            isRight -> Modifier.fillMaxHeight().width(64.dp)
            else -> Modifier.fillMaxHeight().width(64.dp)
        }

        val seamlessHazeModifier = if (dockHazeState != null && isTopOrBottom && !amoledMode) {
            Modifier.hazeEffect(
                state = dockHazeState,
                style = dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = barBase.copy(alpha = 0.65f),
                    tint = dev.chrisbanes.haze.HazeTint(barBase.copy(alpha = 0.65f)),
                    blurRadius = 24.dp,
                ),
            )
        } else {
            Modifier
        }

        val borderModifier = when {
            isBottom -> Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        color = if (isLightMode) Color.Black.copy(0.08f) else if (amoledMode) Color.White.copy(0.12f) else Color.White.copy(0.12f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            isTop -> Modifier.drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        color = if (isLightMode) Color.Black.copy(0.08f) else if (amoledMode) Color.White.copy(0.12f) else Color.White.copy(0.12f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            else -> Modifier
        }

        val seamlessBackgroundModifier = if (isTopOrBottom) Modifier.background(barBase) else Modifier

        Box(
            modifier = modifier
                .then(barModifier)
                .then(seamlessBackgroundModifier)
                .then(seamlessHazeModifier)
                .then(borderModifier)
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
        ) {
            if (isHorizontal) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    dockItems()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    dockItems()
                }
            }
        }
    } else {
        // ── Floating Dock Mode ──
        val pillShape = RoundedCornerShape(26.dp)
        val surfaceModifier = when {
            isBottom -> Modifier.padding(bottom = 16.dp).height(52.dp).wrapContentWidth()
            isTop -> Modifier.padding(top = 16.dp).height(52.dp).wrapContentWidth()
            isRight -> Modifier.padding(end = 16.dp).width(52.dp).wrapContentHeight()
            else -> Modifier.padding(start = 16.dp).width(52.dp).wrapContentHeight()
        }

        val paddingInsideSurface = if (isHorizontal) {
            Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        } else {
            Modifier.padding(vertical = 14.dp, horizontal = 5.dp)
        }

        val glassBase = when {
            isLightMode -> Color.White
            amoledMode -> Color.Black
            else -> Color(0xFF14141A)
        }
        val glassGradient = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                glassBase.copy(alpha = if (amoledMode) 0.85f else 0.70f),
                glassBase.copy(alpha = if (amoledMode) 0.70f else 0.55f),
            ),
        )
        val borderGradient = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                if (isLightMode) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f),
                if (isLightMode) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.20f),
            ),
        )

        val dockHazeModifier = if (dockHazeState != null && isTopOrBottom && !amoledMode) {
            Modifier.hazeEffect(
                state = dockHazeState,
                style = dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = glassBase.copy(alpha = 0.45f),
                    tint = dev.chrisbanes.haze.HazeTint(glassBase.copy(alpha = 0.45f)),
                    blurRadius = 20.dp,
                ),
            )
        } else {
            Modifier
        }

        Box(modifier = modifier) {
            Box(modifier = surfaceModifier) {
                // Drop shadow
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(14.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                        .background(Color.Black.copy(alpha = 0.45f), pillShape),
                )

                // Main Glass Pill Container (Strictly clipped to pillShape, with pointer consumption)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .then(dockHazeModifier)
                        .background(glassGradient)
                        .border(1.2.dp, borderGradient, pillShape)
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        },
                ) {
                    if (isHorizontal) {
                        Row(
                            modifier = paddingInsideSurface,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            dockItems()
                        }
                    } else {
                        Column(
                            modifier = paddingInsideSurface,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            dockItems()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileBottomNavBar(
    modifier: Modifier = Modifier,
    currentTitle: String,
    onNavigate: (Config) -> Unit,
    onSearchClick: () -> Unit,
) {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val bg = if (isLightMode) Color(0xFFF7F7F9) else Color(0xFF14141A)
    val border = if (isLightMode) Color(0xFFE5E5EA) else Color(0xFF282834)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp),
        color = bg.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            // 1. Home Tab
            MobileTabItem(
                icon = PremiumIcons.Home,
                label = "Home",
                selected = currentTitle == "Home",
                onClick = { onNavigate(Config.Home) },
            )

            // 2. Search & Explore Tab
            MobileTabItem(
                icon = PremiumIcons.Search,
                label = "Search",
                selected = currentTitle == "Search" || currentTitle == "Explore & Catalogs",
                onClick = onSearchClick,
            )

            // 3. Library Tab (Watchlist + History)
            MobileTabItem(
                icon = PremiumIcons.Library,
                label = "Library",
                selected = currentTitle == "Library" || currentTitle == "Watch History",
                onClick = { onNavigate(Config.Library) },
            )

            // 4. More Tab (Settings, Extensions, Profiles)
            MobileTabItem(
                icon = PremiumIcons.Settings,
                label = "Settings",
                selected = currentTitle == "Settings" || currentTitle == "Extensions",
                onClick = { onNavigate(Config.Settings) },
            )
        }
    }
}

@Composable
private fun MobileTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) primaryColor else unselectedColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) primaryColor else unselectedColor,
        )
    }
}

