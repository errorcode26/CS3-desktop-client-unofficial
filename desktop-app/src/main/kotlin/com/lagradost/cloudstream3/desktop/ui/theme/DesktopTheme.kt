package com.lagradost.cloudstream3.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors
import com.lagradost.cloudstream3.desktop.ui.components.darkDesktopColors
import com.lagradost.cloudstream3.desktop.ui.components.lightDesktopColors

fun accentColorFromName(name: String): Color = when (name) {
    "Blue" -> Color(0xFF3B82F6)
    "Green" -> Color(0xFF10B981)
    "Red" -> Color(0xFFEF4444)
    "Orange" -> Color(0xFFF59E0B)
    else -> Color(0xFF7C6BFF) // Purple
}

fun buildDesktopColors(primaryColor: Color, isLightMode: Boolean, amoledMode: Boolean): DesktopThemeColors {
    return if (isLightMode) {
        lightDesktopColors(primaryColor)
    } else {
        darkDesktopColors(primaryColor, amoledMode)
    }
}

fun buildColorScheme(primaryColor: Color, desktopColors: DesktopThemeColors, isLightMode: Boolean): ColorScheme {
    return if (isLightMode) {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    } else {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    }
}
