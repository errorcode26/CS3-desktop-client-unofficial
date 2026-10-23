package com.lagradost.cloudstream3.desktop.ui.theme

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThemePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isLightMode: Boolean,
    val themeAccent: String,
    val customThemeAccent: String = "#3B82F6",
    val appThemeBackground: String,
    val customAppThemeBackground: String = "#0C0C16",
    val backgroundGradientEnabled: Boolean = false,
    val backgroundGradientType: String = "Linear", // "Linear", "Radial"
    val backgroundGradientIntensity: Float = 0.5f,
    val isBuiltIn: Boolean = false,
)

object BuiltInPresets {
    val presets = listOf(
        ThemePreset(
            id = "preset_classic_dark",
            name = "Classic Dark",
            isLightMode = false,
            themeAccent = "Blue",
            appThemeBackground = "Slate",
            backgroundGradientEnabled = false,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_midnight",
            name = "Midnight",
            isLightMode = false,
            themeAccent = "Blue",
            appThemeBackground = "Midnight",
            backgroundGradientEnabled = false,
            backgroundGradientType = "Linear",
            backgroundGradientIntensity = 0.3f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_pure_black",
            name = "Pure Black",
            isLightMode = false,
            themeAccent = "Blue",
            appThemeBackground = "Pure Black",
            backgroundGradientEnabled = false,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_clean_light",
            name = "Clean Light",
            isLightMode = true,
            themeAccent = "Blue",
            appThemeBackground = "Frost",
            backgroundGradientEnabled = false,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_warm_light",
            name = "Warm",
            isLightMode = true,
            themeAccent = "Amber",
            appThemeBackground = "Latte",
            backgroundGradientEnabled = false,
            isBuiltIn = true,
        ),
    )
}
