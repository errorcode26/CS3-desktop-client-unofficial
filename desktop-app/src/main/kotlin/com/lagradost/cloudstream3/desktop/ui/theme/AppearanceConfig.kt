package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow

enum class PosterTitlePosition {
    INSIDE,
    BELOW,
    HIDDEN,
    ;

    companion object {
        fun fromString(value: String?): PosterTitlePosition {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: INSIDE
        }
    }
}

enum class ContinueWatchingStyle {
    THUMBNAIL,
    PREMIUM,
    ;

    companion object {
        fun fromString(value: String?): ContinueWatchingStyle {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: THUMBNAIL
        }
    }
}

enum class ClockDisplayMode {
    HIDDEN,
    TIME_ONLY,
    DATE_ONLY,
    BOTH,
    ;

    companion object {
        fun fromString(v: String?) = entries.find { it.name.equals(v, ignoreCase = true) } ?: HIDDEN
    }
}

object AppearanceConfig {
    private const val PREF_THEME_ACCENT = "pref_theme_accent"
    private const val PREF_AMOLED_MODE = "pref_amoled_mode"
    private const val PREF_LIGHT_MODE = "pref_light_mode"
    private const val PREF_GRID_SCALE = "pref_grid_scale"
    private const val PREF_AMBIENT_GLOW = "pref_ambient_glow"
    private const val PREF_AMBIENT_GLOW_INTENSITY = "pref_ambient_glow_intensity"
    private const val PREF_AMBIENT_GLOW_POSITION = "pref_ambient_glow_position"
    private const val PREF_HERO_BACKGROUND_BLUR = "pref_hero_background_blur"
    private const val PREF_HERO_BACKDROP_BLUR_RADIUS = "pref_hero_backdrop_blur_radius"
    private const val PREF_HERO_BACKDROP_DARKENING = "pref_hero_backdrop_darkening"
    private const val PREF_DOCK_POSITION = "pref_dock_position"
    private const val PREF_FONT = "pref_font"
    private const val PREF_SCREENSAVER_ENABLED = "pref_screensaver_enabled"
    private const val PREF_HERO_AUTO_SLIDE_DELAY = "pref_hero_auto_slide_delay"
    private const val PREF_CONTINUE_WATCHING_STYLE = "pref_continue_watching_style"
    private const val PREF_POSTER_HOVER_GLOW_ENABLED = "pref_poster_hover_glow_enabled"
    private const val PREF_POSTER_TITLE_POSITION = "pref_poster_title_position"
    private const val PREF_HOME_SPACING_DP = "pref_home_spacing_dp"
    private const val PREF_HOME_VERTICAL_SPACING_DP = "pref_home_vertical_spacing_dp"
    private const val PREF_POSTER_WIDTH = "pref_poster_width"
    private const val PREF_POSTER_ROUNDING = "pref_poster_rounding"
    private const val PREF_CUSTOM_THEME_ACCENT = "pref_custom_theme_accent"
    private const val PREF_APP_THEME_BACKGROUND = "pref_app_theme_background"
    private const val PREF_CUSTOM_APP_THEME_BACKGROUND = "pref_custom_app_theme_background"
    private const val PREF_HERO_ENABLED = "pref_hero_enabled"
    private const val PREF_SHOW_POSTER_RATING = "pref_show_poster_rating"
    private const val PREF_SHOW_POSTER_QUALITY = "pref_show_poster_quality"
    private const val PREF_SHOW_POSTER_LANGUAGE = "pref_show_poster_language"
    private const val PREF_TEXT_DROP_SHADOW_ENABLED = "pref_text_drop_shadow_enabled"
    private const val PREF_TEXT_DROP_SHADOW_BLUR = "pref_text_drop_shadow_blur"
    private const val PREF_ELEMENT_SHADOWS_ENABLED = "pref_element_shadows_enabled"
    private const val PREF_ELEMENT_SHADOW_MULTIPLIER = "pref_element_shadow_multiplier"
    private const val PREF_APP_PRESET_THEME = "pref_app_preset_theme"
    private const val PREF_BACKGROUND_GRADIENT_ENABLED = "pref_background_gradient_enabled"
    private const val PREF_BACKGROUND_GRADIENT_TYPE = "pref_background_gradient_type"
    private const val PREF_BACKGROUND_GRADIENT_INTENSITY = "pref_background_gradient_intensity"
    private const val PREF_CUSTOM_PRESETS = "pref_custom_presets"
    private const val PREF_CLOCK_MODE = "pref_clock_mode"
    private const val PREF_CLOCK_TIME_FORMAT = "pref_clock_time_format"
    private const val PREF_CLOCK_DATE_FORMAT = "pref_clock_date_format"
    private const val PREF_BG_IMAGE_PATH = "pref_bg_image_path"
    private const val PREF_BG_IMAGE_BLUR = "pref_bg_image_blur"
    private const val PREF_BG_IMAGE_BRIGHTNESS = "pref_bg_image_brightness"
    private const val PREF_BG_IMAGE_OPACITY = "pref_bg_image_opacity"
    private const val PREF_BG_IMAGE_SATURATION = "pref_bg_image_saturation"
    private const val PREF_BG_IMAGE_VIGNETTE = "pref_bg_image_vignette"
    private const val PREF_BG_IMAGE_VIGNETTE_INTENSITY = "pref_bg_image_vignette_intensity"
    private const val PREF_BG_IMAGE_TINT_ENABLED = "pref_bg_image_tint_enabled"
    private const val PREF_BG_IMAGE_TINT_COLOR = "pref_bg_image_tint_color"
    private const val PREF_BG_IMAGE_TINT_ALPHA = "pref_bg_image_tint_alpha"
    private const val PREF_ANTI_SPOILER_ENABLED = "pref_anti_spoiler_enabled"
    private const val PREF_UI_CARD_OPACITY = "pref_ui_card_opacity"
    private const val PREF_DETAILS_SHOW_CURRENT_TIME = "pref_details_show_current_time"
    private const val PREF_DETAILS_SHOW_END_TIME = "pref_details_show_end_time"
    private const val PREF_DETAILS_SECTION_ORDER = "pref_details_section_order"
    private const val PREF_DETAILS_DISABLED_SECTIONS = "pref_details_disabled_sections"

    val themeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_THEME_ACCENT) ?: "Purple")
    val antiSpoilerEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ANTI_SPOILER_ENABLED) ?: true)
    val detailsShowCurrentTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_CURRENT_TIME) ?: true)
    val detailsShowEndTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_END_TIME) ?: true)
    val detailsSectionOrder = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseOrder(
            DesktopDataStore.getKey<String>(PREF_DETAILS_SECTION_ORDER)
        )
    )
    val detailsDisabledSections = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseDisabled(
            DesktopDataStore.getKey<String>(PREF_DETAILS_DISABLED_SECTIONS)
        )
    )
    val amoledMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMOLED_MODE) ?: false)
    val isLightMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LIGHT_MODE) ?: false)
    val gridScale = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_GRID_SCALE) ?: "Normal")
    val ambientGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMBIENT_GLOW) ?: true)
    val ambientGlowIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_AMBIENT_GLOW_INTENSITY) ?: 0.15f)
    val ambientGlowPositions = MutableStateFlow((DesktopDataStore.getKey<String>(PREF_AMBIENT_GLOW_POSITION) ?: "Center").split(",").filter { it.isNotBlank() }.toSet())
    val heroBackgroundBlurEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_BACKGROUND_BLUR) ?: true)
    val heroBackdropBlurRadius = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_BLUR_RADIUS) ?: 80f)
    val heroBackdropDarkening = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_DARKENING) ?: 0.65f)
    val dockPosition = MutableStateFlow(DockPosition.fromString(DesktopDataStore.getKey<String>(PREF_DOCK_POSITION) ?: "Left"))
    val selectedFont = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_FONT) ?: "Inter")
    val screensaverEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SCREENSAVER_ENABLED) ?: true)
    val heroAutoSlideDelaySeconds = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HERO_AUTO_SLIDE_DELAY) ?: 10)
    val continueWatchingStyle = MutableStateFlow(ContinueWatchingStyle.fromString(DesktopDataStore.getKey<String>(PREF_CONTINUE_WATCHING_STYLE)))
    val posterHoverGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_POSTER_HOVER_GLOW_ENABLED) ?: true)
    val posterTitlePosition = MutableStateFlow(PosterTitlePosition.fromString(DesktopDataStore.getKey<String>(PREF_POSTER_TITLE_POSITION)))
    val homeSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_SPACING_DP) ?: 12)
    val homeVerticalSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_VERTICAL_SPACING_DP) ?: 0)
    val posterWidthDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_WIDTH) ?: 190)
    val posterRoundingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_ROUNDING) ?: 12)
    val customThemeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_THEME_ACCENT) ?: "#7C6BFF")
    val appThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_THEME_BACKGROUND) ?: "Navy")
    val customAppThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_APP_THEME_BACKGROUND) ?: "#0C0C16")
    val heroEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_ENABLED) ?: true)
    val showPosterRating = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_RATING) ?: true)
    val showPosterQuality = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_QUALITY) ?: true)
    val showPosterLanguage = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_LANGUAGE) ?: true)
    val textDropShadowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_TEXT_DROP_SHADOW_ENABLED) ?: true)
    val textDropShadowBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_TEXT_DROP_SHADOW_BLUR) ?: 8f)
    val elementShadowsEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ELEMENT_SHADOWS_ENABLED) ?: true)
    val elementShadowMultiplier = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_ELEMENT_SHADOW_MULTIPLIER) ?: 1.0f)

    val appPresetTheme = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_PRESET_THEME) ?: "preset_cyberpunk")
    val backgroundGradientEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BACKGROUND_GRADIENT_ENABLED) ?: true)
    val backgroundGradientType = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BACKGROUND_GRADIENT_TYPE) ?: "Radial")
    val backgroundGradientIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BACKGROUND_GRADIENT_INTENSITY) ?: 0.5f)

    val clockMode = MutableStateFlow(ClockDisplayMode.fromString(DesktopDataStore.getKey<String>(PREF_CLOCK_MODE)))
    val clockTimeFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_TIME_FORMAT) ?: "HH:mm")
    val clockDateFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_DATE_FORMAT) ?: "EEE, dd MMM")

    // Background image wallpaper
    val backgroundImagePath = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_PATH) ?: "")
    val backgroundImageBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BLUR) ?: 20f)
    val backgroundImageBrightness = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BRIGHTNESS) ?: 0.35f)
    val backgroundImageOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_OPACITY) ?: 1.0f)
    val backgroundImageSaturation = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_SATURATION) ?: 1.0f)
    val backgroundImageVignetteEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_VIGNETTE) ?: false)
    val backgroundImageVignetteIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_VIGNETTE_INTENSITY) ?: 0.7f)
    val backgroundImageTintEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_TINT_ENABLED) ?: false)
    val backgroundImageTintColor = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_TINT_COLOR) ?: "#7C6BFF")
    val backgroundImageTintAlpha = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_TINT_ALPHA) ?: 0.3f)

    val uiCardOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_UI_CARD_OPACITY) ?: 0.4f)

    private val customPresetsJson = DesktopDataStore.getKey<String>(PREF_CUSTOM_PRESETS) ?: "[]"
    val customPresets = MutableStateFlow(
        try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(customPresetsJson, object : com.fasterxml.jackson.core.type.TypeReference<List<ThemePreset>>() {})
        } catch (e: Exception) {
            emptyList()
        },
    )

    fun setThemeAccent(colorName: String) {
        themeAccent.value = colorName
        DesktopDataStore.setKey(PREF_THEME_ACCENT, colorName)
    }

    fun setAntiSpoilerEnabled(enabled: Boolean) {
        antiSpoilerEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ANTI_SPOILER_ENABLED, enabled)
    }

    fun setAmoledMode(enabled: Boolean) {
        amoledMode.value = enabled
        DesktopDataStore.setKey(PREF_AMOLED_MODE, enabled)
    }

    fun setLightMode(enabled: Boolean) {
        if (isLightMode.value == enabled) return
        isLightMode.value = enabled
        DesktopDataStore.setKey(PREF_LIGHT_MODE, enabled)

        val currentPreset = (com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets + customPresets.value).find { it.id == appPresetTheme.value }
        if (currentPreset != null && currentPreset.isLightMode != enabled) {
            val defaultPreset = com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets.firstOrNull { it.isLightMode == enabled }
            if (defaultPreset != null) {
                applyPreset(defaultPreset)
            }
        }
    }

    fun setGridScale(scale: String) {
        gridScale.value = scale
        DesktopDataStore.setKey(PREF_GRID_SCALE, scale)
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

    fun setHeroBackgroundBlurEnabled(enabled: Boolean) {
        heroBackgroundBlurEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_BACKGROUND_BLUR, enabled)
    }

    fun setHeroBackdropBlurRadius(radius: Float) {
        heroBackdropBlurRadius.value = radius
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_BLUR_RADIUS, radius)
    }

    fun setHeroBackdropDarkening(darkening: Float) {
        heroBackdropDarkening.value = darkening
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_DARKENING, darkening)
    }

    fun setDockPosition(position: DockPosition) {
        dockPosition.value = position
        DesktopDataStore.setKey(PREF_DOCK_POSITION, position.label)
    }

    fun setSelectedFont(font: String) {
        selectedFont.value = font
        DesktopDataStore.setKey(PREF_FONT, font)
    }

    fun setScreensaverEnabled(enabled: Boolean) {
        screensaverEnabled.value = enabled
        DesktopDataStore.setKey(PREF_SCREENSAVER_ENABLED, enabled)
    }

    fun setHeroAutoSlideDelaySeconds(seconds: Int) {
        heroAutoSlideDelaySeconds.value = seconds
        DesktopDataStore.setKey(PREF_HERO_AUTO_SLIDE_DELAY, seconds)
    }

    fun setHomeSpacingDp(dp: Int) {
        homeSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_SPACING_DP, dp)
    }

    fun setHomeVerticalSpacingDp(dp: Int) {
        homeVerticalSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_VERTICAL_SPACING_DP, dp)
    }

    fun setPosterWidthDp(width: Int) {
        posterWidthDp.value = width
        DesktopDataStore.setKey(PREF_POSTER_WIDTH, width)
    }

    fun setPosterRoundingDp(dp: Int) {
        posterRoundingDp.value = dp
        DesktopDataStore.setKey(PREF_POSTER_ROUNDING, dp)
    }

    fun setCustomThemeAccent(hex: String) {
        customThemeAccent.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_THEME_ACCENT, hex)
    }

    fun setAppThemeBackground(themeName: String) {
        appThemeBackground.value = themeName
        DesktopDataStore.setKey(PREF_APP_THEME_BACKGROUND, themeName)
    }

    fun setCustomAppThemeBackground(hex: String) {
        customAppThemeBackground.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_APP_THEME_BACKGROUND, hex)
    }

    fun setHeroEnabled(enabled: Boolean) {
        heroEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_ENABLED, enabled)
    }

    fun setContinueWatchingStyle(style: ContinueWatchingStyle) {
        continueWatchingStyle.value = style
        DesktopDataStore.setKey(PREF_CONTINUE_WATCHING_STYLE, style.name)
    }

    fun setPosterHoverGlowEnabled(enabled: Boolean) {
        posterHoverGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_POSTER_HOVER_GLOW_ENABLED, enabled)
    }

    fun setPosterTitlePosition(position: PosterTitlePosition) {
        posterTitlePosition.value = position
        DesktopDataStore.setKey(PREF_POSTER_TITLE_POSITION, position.name)
    }

    fun setShowPosterRating(enabled: Boolean) {
        showPosterRating.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_RATING, enabled)
    }

    fun setShowPosterQuality(enabled: Boolean) {
        showPosterQuality.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_QUALITY, enabled)
    }

    fun setShowPosterLanguage(show: Boolean) {
        showPosterLanguage.value = show
        DesktopDataStore.setKey(PREF_SHOW_POSTER_LANGUAGE, show)
    }

    fun setTextDropShadowEnabled(enabled: Boolean) {
        textDropShadowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_ENABLED, enabled)
    }

    fun setTextDropShadowBlur(blur: Float) {
        textDropShadowBlur.value = blur
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_BLUR, blur)
    }

    fun setElementShadowsEnabled(enabled: Boolean) {
        elementShadowsEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOWS_ENABLED, enabled)
    }

    fun setElementShadowMultiplier(multiplier: Float) {
        elementShadowMultiplier.value = multiplier
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOW_MULTIPLIER, multiplier)
    }

    fun setAppPresetTheme(presetId: String) {
        appPresetTheme.value = presetId
        DesktopDataStore.setKey(PREF_APP_PRESET_THEME, presetId)
    }

    fun setBackgroundGradientEnabled(enabled: Boolean) {
        backgroundGradientEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_ENABLED, enabled)
    }

    fun setBackgroundGradientType(type: String) {
        backgroundGradientType.value = type
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_TYPE, type)
    }

    fun setBackgroundGradientIntensity(intensity: Float) {
        backgroundGradientIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_INTENSITY, intensity)
    }

    fun setClockMode(mode: ClockDisplayMode) {
        clockMode.value = mode
        DesktopDataStore.setKey(PREF_CLOCK_MODE, mode.name)
    }

    fun setClockTimeFormat(format: String) {
        clockTimeFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_TIME_FORMAT, format)
    }

    fun setClockDateFormat(format: String) {
        clockDateFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_DATE_FORMAT, format)
    }

    fun setDetailsShowCurrentTime(enabled: Boolean) {
        detailsShowCurrentTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_CURRENT_TIME, enabled)
    }

    fun setDetailsShowEndTime(enabled: Boolean) {
        detailsShowEndTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_END_TIME, enabled)
    }

    fun setBackgroundImagePath(path: String) {
        backgroundImagePath.value = path
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, path)
    }

    fun setBackgroundImageBlur(blur: Float) {
        backgroundImageBlur.value = blur
        DesktopDataStore.setKey(PREF_BG_IMAGE_BLUR, blur)
    }

    fun setBackgroundImageBrightness(brightness: Float) {
        backgroundImageBrightness.value = brightness
        DesktopDataStore.setKey(PREF_BG_IMAGE_BRIGHTNESS, brightness)
    }

    fun clearBackgroundImage() {
        backgroundImagePath.value = ""
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, "")
    }

    fun setBackgroundImageOpacity(opacity: Float) {
        backgroundImageOpacity.value = opacity
        DesktopDataStore.setKey(PREF_BG_IMAGE_OPACITY, opacity)
    }

    fun setBackgroundImageSaturation(saturation: Float) {
        backgroundImageSaturation.value = saturation
        DesktopDataStore.setKey(PREF_BG_IMAGE_SATURATION, saturation)
    }

    fun setBackgroundImageVignetteEnabled(enabled: Boolean) {
        backgroundImageVignetteEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE, enabled)
    }

    fun setBackgroundImageVignetteIntensity(intensity: Float) {
        backgroundImageVignetteIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE_INTENSITY, intensity)
    }

    fun setBackgroundImageTintEnabled(enabled: Boolean) {
        backgroundImageTintEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ENABLED, enabled)
    }

    fun setBackgroundImageTintColor(hex: String) {
        backgroundImageTintColor.value = hex
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_COLOR, hex)
    }

    fun setBackgroundImageTintAlpha(alpha: Float) {
        backgroundImageTintAlpha.value = alpha
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ALPHA, alpha)
    }

    fun setUiCardOpacity(opacity: Float) {
        uiCardOpacity.value = opacity
        DesktopDataStore.setKey(PREF_UI_CARD_OPACITY, opacity)
    }

    fun saveCustomPreset(preset: ThemePreset) {
        val currentList = customPresets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == preset.id }
        if (index != -1) {
            currentList[index] = preset
        } else {
            currentList.add(preset)
        }
        customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        setAppPresetTheme(preset.id)
    }

    fun deleteCustomPreset(id: String) {
        val currentList = customPresets.value.filter { it.id != id }
        customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        if (appPresetTheme.value == id) {
            setAppPresetTheme("preset_cyberpunk")
        }
    }

    private fun saveCustomPresetsToDisk(list: List<ThemePreset>) {
        try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val json = mapper.writeValueAsString(list)
            DesktopDataStore.setKey(PREF_CUSTOM_PRESETS, json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(preset: ThemePreset) {
        setAppPresetTheme(preset.id)
        setLightMode(preset.isLightMode)
        setThemeAccent(preset.themeAccent)
        if (preset.themeAccent == "Custom") {
            setCustomThemeAccent(preset.customThemeAccent)
        }
        setAppThemeBackground(preset.appThemeBackground)
        if (preset.appThemeBackground == "Custom") {
            setCustomAppThemeBackground(preset.customAppThemeBackground)
        }
        setBackgroundGradientEnabled(preset.backgroundGradientEnabled)
        setBackgroundGradientType(preset.backgroundGradientType)
        setBackgroundGradientIntensity(preset.backgroundGradientIntensity)
    }

    fun setDetailsSectionOrder(order: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>) {
        detailsSectionOrder.value = order
        DesktopDataStore.setKey(
            PREF_DETAILS_SECTION_ORDER,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(order),
        )
    }

    fun toggleDetailsSection(key: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey, enabled: Boolean) {
        val current = detailsDisabledSections.value.toMutableSet()
        if (enabled) {
            current.remove(key)
        } else {
            current.add(key)
        }
        detailsDisabledSections.value = current
        DesktopDataStore.setKey(
            PREF_DETAILS_DISABLED_SECTIONS,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(current),
        )
    }

    fun moveDetailsSection(fromIndex: Int, toIndex: Int) {
        val current = detailsSectionOrder.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setDetailsSectionOrder(current)
        }
    }

    fun resetDetailsSectionOrder() {
        setDetailsSectionOrder(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.defaultOrder)
        detailsDisabledSections.value = emptySet()
        DesktopDataStore.removeKey(PREF_DETAILS_DISABLED_SECTIONS)
    }
}
