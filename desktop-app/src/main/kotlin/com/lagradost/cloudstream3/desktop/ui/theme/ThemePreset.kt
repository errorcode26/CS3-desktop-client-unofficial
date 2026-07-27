package com.lagradost.cloudstream3.desktop.ui.theme

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class ThemePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isLightMode: Boolean,
    val themeAccent: String,
    val customThemeAccent: String = "#7C6BFF",
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
            id = "preset_cyberpunk",
            name = "Midnight Cyberpunk",
            isLightMode = false,
            themeAccent = "Custom",
            customThemeAccent = "#FF007F", // Neon Pink
            appThemeBackground = "Custom",
            customAppThemeBackground = "#0B0C10",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.8f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_nord",
            name = "Nordic Slate",
            isLightMode = false,
            themeAccent = "Custom",
            customThemeAccent = "#88C0D0", // Frost Blue
            appThemeBackground = "Custom",
            customAppThemeBackground = "#2E3440", // Nord Polar Night
            backgroundGradientEnabled = true,
            backgroundGradientType = "Linear",
            backgroundGradientIntensity = 0.4f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_matcha",
            name = "Forest Matcha",
            isLightMode = false,
            themeAccent = "Custom",
            customThemeAccent = "#A3E635", // Matcha Green
            appThemeBackground = "Custom",
            customAppThemeBackground = "#0F1714", // Deep Forest
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.5f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_dracula",
            name = "Deep Dracula",
            isLightMode = false,
            themeAccent = "Custom",
            customThemeAccent = "#FF79C6", // Dracula Pink
            appThemeBackground = "Custom",
            customAppThemeBackground = "#282A36", // Dracula Background
            backgroundGradientEnabled = true,
            backgroundGradientType = "Linear",
            backgroundGradientIntensity = 0.6f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_abyss",
            name = "Crimson Abyss",
            isLightMode = false,
            themeAccent = "Custom",
            customThemeAccent = "#E11D48", // Crimson
            appThemeBackground = "Custom",
            customAppThemeBackground = "#0A0002", // Near Black Red
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.7f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_mist",
            name = "Morning Mist",
            isLightMode = true,
            themeAccent = "Blue",
            appThemeBackground = "Custom",
            customAppThemeBackground = "#F8FAFC",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Linear",
            backgroundGradientIntensity = 0.3f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_latte",
            name = "Latte Macchiato",
            isLightMode = true,
            themeAccent = "Orange",
            appThemeBackground = "Custom",
            customAppThemeBackground = "#FAF7F2",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.4f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_sakura",
            name = "Cherry Blossom",
            isLightMode = true,
            themeAccent = "Custom",
            customThemeAccent = "#D81B60",
            appThemeBackground = "Custom",
            customAppThemeBackground = "#FFF0F5",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.35f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_solar",
            name = "Solarized Light",
            isLightMode = true,
            themeAccent = "Custom",
            customThemeAccent = "#B58900",
            appThemeBackground = "Custom",
            customAppThemeBackground = "#FDF6E3",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Linear",
            backgroundGradientIntensity = 0.2f,
            isBuiltIn = true,
        ),
        ThemePreset(
            id = "preset_mint",
            name = "Mint Breeze",
            isLightMode = true,
            themeAccent = "Green",
            appThemeBackground = "Custom",
            customAppThemeBackground = "#F0FFF0",
            backgroundGradientEnabled = true,
            backgroundGradientType = "Radial",
            backgroundGradientIntensity = 0.4f,
            isBuiltIn = true,
        ),
    )
}
