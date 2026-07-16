package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow

object AppearanceConfig {
    private const val PREF_THEME_ACCENT = "pref_theme_accent"
    private const val PREF_AMOLED_MODE = "pref_amoled_mode"
    private const val PREF_LIGHT_MODE = "pref_light_mode"
    private const val PREF_GRID_SCALE = "pref_grid_scale"
    private const val PREF_LAYOUT_WIDTH = "pref_layout_width"
    private const val PREF_SEARCH_BAR_MODE = "pref_search_bar_mode"
    private const val PREF_AMBIENT_GLOW = "pref_ambient_glow"
    private const val PREF_AMBIENT_GLOW_INTENSITY = "pref_ambient_glow_intensity"
    private const val PREF_AMBIENT_GLOW_POSITION = "pref_ambient_glow_position"
    private const val PREF_HERO_DYNAMIC_COLOR = "pref_hero_dynamic_color"
    private const val PREF_DOCK_POSITION = "pref_dock_position"
    private const val PREF_FONT = "pref_font"

    val themeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_THEME_ACCENT) ?: "Purple")
    val amoledMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMOLED_MODE) ?: false)
    val isLightMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LIGHT_MODE) ?: false)
    val gridScale = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_GRID_SCALE) ?: "Normal")
    val layoutWidth = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_LAYOUT_WIDTH) ?: "Modern")
    val searchBarMode = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_SEARCH_BAR_MODE) ?: "Always Visible")
    val ambientGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMBIENT_GLOW) ?: true)
    val ambientGlowIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_AMBIENT_GLOW_INTENSITY) ?: 0.15f)
    val ambientGlowPositions = MutableStateFlow((DesktopDataStore.getKey<String>(PREF_AMBIENT_GLOW_POSITION) ?: "Center").split(",").filter { it.isNotBlank() }.toSet())
    val heroDynamicColorEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_DYNAMIC_COLOR) ?: true)
    val dockPosition = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_DOCK_POSITION) ?: "Left")
    val selectedFont = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_FONT) ?: "Inter")

    fun setThemeAccent(colorName: String) {
        themeAccent.value = colorName
        DesktopDataStore.setKey(PREF_THEME_ACCENT, colorName)
    }

    fun setAmoledMode(enabled: Boolean) {
        amoledMode.value = enabled
        DesktopDataStore.setKey(PREF_AMOLED_MODE, enabled)
    }

    fun setLightMode(enabled: Boolean) {
        isLightMode.value = enabled
        DesktopDataStore.setKey(PREF_LIGHT_MODE, enabled)
    }

    fun setGridScale(scale: String) {
        gridScale.value = scale
        DesktopDataStore.setKey(PREF_GRID_SCALE, scale)
    }

    fun setLayoutWidth(width: String) {
        layoutWidth.value = width
        DesktopDataStore.setKey(PREF_LAYOUT_WIDTH, width)
    }

    fun setSearchBarMode(mode: String) {
        searchBarMode.value = mode
        DesktopDataStore.setKey(PREF_SEARCH_BAR_MODE, mode)
    }

    fun setAmbientGlowEnabled(enabled: Boolean) {
        ambientGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW, enabled)
    }

    fun setAmbientGlowIntensity(intensity: Float) {
        ambientGlowIntensity.value = intensity
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_INTENSITY, intensity)
    }

    fun toggleAmbientGlowPosition(position: String) {
        val current = ambientGlowPositions.value.toMutableSet()
        if (current.contains(position)) {
            current.remove(position)
        } else {
            current.add(position)
        }
        if (current.isEmpty()) current.add("Center")
        ambientGlowPositions.value = current
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_POSITION, current.joinToString(","))
    }

    fun setHeroDynamicColorEnabled(enabled: Boolean) {
        heroDynamicColorEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_DYNAMIC_COLOR, enabled)
    }

    fun setDockPosition(position: String) {
        dockPosition.value = position
        DesktopDataStore.setKey(PREF_DOCK_POSITION, position)
    }

    fun setSelectedFont(font: String) {
        selectedFont.value = font
        DesktopDataStore.setKey(PREF_FONT, font)
    }
}
