package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.TopBarProviderStyle

/**
 * Universal animated Fullscreen Toggle Button.
 *
 * Monochromatic Neutral Glass Language:
 * - When floating (Details, Person, Studio screens): Circular glass orb (CircleShape, 42dp),
 *   symmetrically mirroring DesktopBackButton on the opposite side of the screen.
 * - When docked in TopBar: Rounded pill (RoundedCornerShape 10dp, 42dp height).
 * - Smooth reversible corner-bracket animation (180° rotation + icon morph via AnimatedContent).
 * - Zero distracting colored accents: clean luminance and border brightening.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenToggleButton(
    isFloating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen
        ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            isHovered -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "fullscreenButtonScale",
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isFullscreen) 180f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "fullscreenButtonRotation",
    )

    val buttonShape = RoundedCornerShape(10.dp)

    val activeBorderColor = if (isFloating) {
        when {
            isHovered -> Color.White.copy(alpha = 0.32f)
            isFullscreen -> Color.White.copy(alpha = 0.25f)
            else -> Color.White.copy(alpha = 0.15f)
        }
    } else {
        when {
            isHovered -> Color.White.copy(alpha = 0.24f)
            isFullscreen -> Color.White.copy(alpha = 0.18f)
            else -> Color.White.copy(alpha = 0.10f)
        }
    }

    val activeBgColor = if (isFloating) {
        when {
            isHovered -> Color(0xFF14141A).copy(alpha = 0.80f)
            isFullscreen -> Color(0xFF121217).copy(alpha = 0.70f)
            else -> Color(0xFF0F0F12).copy(alpha = 0.55f)
        }
    } else {
        when {
            isHovered -> Color.White.copy(alpha = 0.10f)
            isFullscreen -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.05f)
        }
    }

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = theme.SurfaceElevated,
                border = BorderStroke(1.dp, theme.Divider),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = if (isFullscreen) "Exit Full Screen (Esc / F11)" else "Full Screen (F11)",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        delayMillis = 400,
    ) {
        Surface(
            shape = buttonShape,
            color = activeBgColor,
            border = BorderStroke(1.dp, activeBorderColor),
            shadowElevation = 0.dp,
            modifier = modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .clip(buttonShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    fullscreenController?.toggle?.invoke() ?: run {
                        val newPlacement = if (isFullscreen) {
                            androidx.compose.ui.window.WindowPlacement.Floating
                        } else {
                            androidx.compose.ui.window.WindowPlacement.Fullscreen
                        }
                        windowState?.placement = newPlacement
                    }
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotationAngle
                    },
            ) {
                AnimatedContent(
                    targetState = isFullscreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                ),
                                initialScale = 0.60f,
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                    scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing), targetScale = 0.60f)
                            )
                    },
                    contentAlignment = Alignment.Center,
                    label = "fullscreenIconShapeMorph",
                ) { targetFullscreen ->
                    Icon(
                        imageVector = if (targetFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (targetFullscreen) "Exit Full Screen" else "Full Screen",
                        tint = when {
                            isHovered || targetFullscreen -> Color.White
                            else -> Color.White.copy(alpha = 0.75f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Active provider selector pill rendered on Home.
 * Styled in neutral monochromatic glass.
 */
@Composable
fun TopBarProviderPill(
    homeUiState: HomeUiState,
    homeActionDispatcher: ((HomeUiEvent) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val topBarProviderStyle by AppearanceConfig.topBarProviderStyle.collectAsState()

    val providers = homeUiState.providers
    val activeProviders = homeUiState.activeProviders
    val mergedPluginIcons = homeUiState.mergedPluginIcons

    val activeIconUrl: String? = if (activeProviders.size == 1) {
        val pRaw = activeProviders.first()
        val pClean = pRaw.substringAfter("::")
        DesktopRepositoryManager.getPluginIcon(pClean)
            ?: DesktopRepositoryManager.getPluginIcon(pRaw)
            ?: run {
                val pName = pClean.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
                mergedPluginIcons.entries.firstOrNull { (k, _) ->
                    val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
                    kName.length >= 3 && pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
                }?.value
            }
    } else {
        null
    }

    val activeApis = homeUiState.activeProviderApis
    val displayText = when {
        activeApis.size == 1 -> {
            val single = activeApis.first()
            val isDuplicate = providers.count { it.name == single.name } > 1
            if (isDuplicate) {
                val repo = single.sourcePlugin?.let { java.io.File(it).parentFile?.name?.replace("_", " ") }
                if (!repo.isNullOrBlank()) "${single.name} ($repo)" else single.name
            } else {
                single.name
            }
        }
        activeProviders.size == 1 -> activeProviders.first().substringAfter("::")
        activeProviders.size > 1 -> "Multi-Provider"
        else -> "Select Provider"
    }

    val isIconOnly = topBarProviderStyle == TopBarProviderStyle.ICON_ONLY
    val resolvedIcon = activeIconUrl ?: DesktopRepositoryManager.getPluginIcon(displayText)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val activeBorderColor = if (isHovered) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)
    val activeBgColor = if (isHovered) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.05f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = activeBgColor,
        border = BorderStroke(1.dp, activeBorderColor),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        if (isIconOnly) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { homeActionDispatcher?.invoke(HomeUiEvent.OnShowHomeManagement(true)) },
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedIcon != null) {
                    coil3.compose.AsyncImage(
                        model = resolvedIcon,
                        contentDescription = displayText,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else if (displayText != "Select Provider") {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayText.take(1).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                } else {
                    Icon(
                        PremiumIcons.Extensions,
                        contentDescription = displayText,
                        tint = if (isHovered) Color.White else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { homeActionDispatcher?.invoke(HomeUiEvent.OnShowHomeManagement(true)) }
                    .padding(horizontal = 14.dp),
            ) {
                if (resolvedIcon != null) {
                    coil3.compose.AsyncImage(
                        model = resolvedIcon,
                        contentDescription = "Provider Logo",
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            PremiumIcons.Extensions,
                            contentDescription = "Providers",
                            tint = if (isHovered) Color.White else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                Spacer(Modifier.width(9.dp))

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHovered) Color.White else theme.TextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Composite window controls pill.
 *
 * Automatically renders:
 * - Provider selector pill when on Home with providers.
 * - Symmetrical floating circular glass orb when in floating screens (Details, Person, Studio).
 * - Docked pill button when in TopBar.
 */
@Composable
fun WindowControlsPill(
    isHome: Boolean = false,
    isCompact: Boolean = false,
    homeUiState: HomeUiState? = null,
    homeActionDispatcher: ((HomeUiEvent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (isHome && homeUiState != null && homeUiState.providers.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            TopBarProviderPill(
                homeUiState = homeUiState,
                homeActionDispatcher = homeActionDispatcher,
            )

            FullscreenToggleButton(isFloating = false)
        }
    } else {
        FullscreenToggleButton(
            isFloating = !isHome,
            modifier = modifier,
        )
    }
}
