package com.lagradost.cloudstream3.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.cloudstream3.desktop.ui.DockPosition

@Immutable
data class DesktopAppearance(
    val isLightMode: Boolean = false,
    val amoledMode: Boolean = false,
    val dockPosition: DockPosition = DockPosition.LEFT,
    val navigationStyle: NavigationStyle = NavigationStyle.FLOATING_DOCK,
    val ambientGlowEnabled: Boolean = false,
    val ambientGlowIntensity: Float = 0.15f,
    val ambientGlowPositions: Set<String> = setOf("Center"),
    val backgroundGradientEnabled: Boolean = true,
    val backgroundGradientType: String = "Radial",
    val backgroundGradientIntensity: Float = 0.5f,
    val backgroundImagePath: String = "",
    val backgroundImageBlur: Float = 20f,
    val backgroundImageBrightness: Float = 0.35f,
    val backgroundImageOpacity: Float = 1.0f,
    val backgroundImageSaturation: Float = 1.0f,
    val backgroundImageVignetteEnabled: Boolean = false,
    val backgroundImageVignetteIntensity: Float = 0.7f,
    val backgroundImageTintEnabled: Boolean = false,
    val backgroundImageTintColor: String = "#7C6BFF",
    val backgroundImageTintAlpha: Float = 0.3f,
)

val LocalDesktopAppearance = staticCompositionLocalOf { DesktopAppearance() }

@Composable
fun rememberDesktopAppearance(): DesktopAppearance {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val navigationStyle by AppearanceConfig.navigationStyle.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()
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

    return remember(
        isLightMode,
        amoledMode,
        dockPosition,
        navigationStyle,
        ambientGlowEnabled,
        ambientGlowIntensity,
        ambientGlowPositions,
        backgroundGradientEnabled,
        backgroundGradientType,
        backgroundGradientIntensity,
        bgImagePath,
        bgImageBlur,
        bgImageBrightness,
        bgImageOpacity,
        bgImageSaturation,
        bgImageVignetteEnabled,
        bgImageVignetteIntensity,
        bgImageTintEnabled,
        bgImageTintColor,
        bgImageTintAlpha,
    ) {
        DesktopAppearance(
            isLightMode = isLightMode,
            amoledMode = amoledMode,
            dockPosition = dockPosition,
            navigationStyle = navigationStyle,
            ambientGlowEnabled = ambientGlowEnabled,
            ambientGlowIntensity = ambientGlowIntensity,
            ambientGlowPositions = ambientGlowPositions,
            backgroundGradientEnabled = backgroundGradientEnabled,
            backgroundGradientType = backgroundGradientType,
            backgroundGradientIntensity = backgroundGradientIntensity,
            backgroundImagePath = bgImagePath,
            backgroundImageBlur = bgImageBlur,
            backgroundImageBrightness = bgImageBrightness,
            backgroundImageOpacity = bgImageOpacity,
            backgroundImageSaturation = bgImageSaturation,
            backgroundImageVignetteEnabled = bgImageVignetteEnabled,
            backgroundImageVignetteIntensity = bgImageVignetteIntensity,
            backgroundImageTintEnabled = bgImageTintEnabled,
            backgroundImageTintColor = bgImageTintColor,
            backgroundImageTintAlpha = bgImageTintAlpha,
        )
    }
}
