package com.lagradost.cloudstream3.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors
import com.lagradost.cloudstream3.desktop.ui.components.darkDesktopColors
import com.lagradost.cloudstream3.desktop.ui.components.lightDesktopColors

fun parseHexColor(hexString: String, fallback: Color): Color {
    return try {
        var hex = hexString.removePrefix("#")
        if (hex.length == 3) {
            hex = hex.map { "$it$it" }.joinToString("")
        }
        val colorInt = when (hex.length) {
            6 -> hex.toLong(16) or 0xFF000000L
            8 -> hex.toLong(16)
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

fun accentColorFromName(name: String, customHex: String = "#7C6BFF"): Color = when (name) {
    "Blue" -> Color(0xFF3B82F6)
    "Cyan" -> Color(0xFF06B6D4)
    "Green" -> Color(0xFF10B981)
    "Amber" -> Color(0xFFF59E0B)
    "Orange" -> Color(0xFFF97316)
    "Red" -> Color(0xFFEF4444)
    "Rose" -> Color(0xFFEC4899)
    "Ice" -> Color(0xFF94A3B8)
    "Custom" -> parseHexColor(customHex, Color(0xFF7C6BFF))
    else -> Color(0xFF7C6BFF) // Purple
}

fun calculateContrast(c1: Color, c2: Color): Float {
    val l1 = c1.luminance()
    val l2 = c2.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

fun ensureContrast(accent: Color, background: Color, isLightMode: Boolean): Color {
    var currentAccent = accent

    fun Color.adjust(fraction: Float): Color {
        return if (isLightMode) {
            Color(
                red = (this.red * (1f - fraction)).coerceIn(0f, 1f),
                green = (this.green * (1f - fraction)).coerceIn(0f, 1f),
                blue = (this.blue * (1f - fraction)).coerceIn(0f, 1f),
                alpha = this.alpha,
            )
        } else {
            Color(
                red = (this.red + (1f - this.red) * fraction).coerceIn(0f, 1f),
                green = (this.green + (1f - this.green) * fraction).coerceIn(0f, 1f),
                blue = (this.blue + (1f - this.blue) * fraction).coerceIn(0f, 1f),
                alpha = this.alpha,
            )
        }
    }

    var attempts = 0
    while (calculateContrast(currentAccent, background) < 3.5f && attempts < 10) {
        currentAccent = currentAccent.adjust(0.15f)
        attempts++
    }
    return currentAccent
}

fun buildDesktopColors(
    primaryColor: Color,
    isLightMode: Boolean,
    isAmoled: Boolean = false,
    appThemeBackground: String = "Navy",
    customBgHex: String = "#0C0C16",
): DesktopThemeColors {
    return if (isLightMode) {
        lightDesktopColors(primaryColor, appThemeBackground, customBgHex)
    } else {
        darkDesktopColors(primaryColor, appThemeBackground, isAmoled, customBgHex)
    }
}

fun buildColorScheme(primaryColor: Color, desktopColors: DesktopThemeColors, isLightMode: Boolean): ColorScheme {
    val safePrimary = ensureContrast(primaryColor, desktopColors.Background, isLightMode)

    return if (isLightMode) {
        lightColorScheme(
            primary = safePrimary,
            onPrimary = Color.White,
            secondary = safePrimary,
            onSecondary = Color.White,
            secondaryContainer = desktopColors.SurfaceElevated,
            onSecondaryContainer = desktopColors.TextPrimary,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            outline = desktopColors.Divider,
            outlineVariant = desktopColors.Divider.copy(alpha = 0.6f),
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    } else {
        darkColorScheme(
            primary = safePrimary,
            onPrimary = Color.White,
            surface = desktopColors.SurfaceCard,
            onSurface = desktopColors.TextPrimary,
            surfaceVariant = desktopColors.SurfaceElevated,
            onSurfaceVariant = desktopColors.TextMuted,
            background = desktopColors.Background,
            onBackground = desktopColors.TextPrimary,
            outline = Color.White.copy(alpha = 0.22f),
            outlineVariant = Color.White.copy(alpha = 0.12f),
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    }
}
