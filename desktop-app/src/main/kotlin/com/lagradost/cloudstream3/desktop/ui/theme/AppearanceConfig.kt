package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private const val PREF_LOCK_UNRELEASED_EPISODES = "pref_lock_unreleased_episodes"
    private const val PREF_DETAILS_SECTION_ORDER = "pref_details_section_order"
    private const val PREF_DETAILS_DISABLED_SECTIONS = "pref_details_disabled_sections"

    private val _themeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_THEME_ACCENT) ?: "Purple")
    val themeAccent: StateFlow<String> = _themeAccent.asStateFlow()
    private val _antiSpoilerEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ANTI_SPOILER_ENABLED) ?: true)
    val antiSpoilerEnabled: StateFlow<Boolean> = _antiSpoilerEnabled.asStateFlow()
    private val _lockUnreleasedEpisodes = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LOCK_UNRELEASED_EPISODES) ?: true)
    val lockUnreleasedEpisodes: StateFlow<Boolean> = _lockUnreleasedEpisodes.asStateFlow()
    private val _detailsShowCurrentTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_CURRENT_TIME) ?: true)
    val detailsShowCurrentTime: StateFlow<Boolean> = _detailsShowCurrentTime.asStateFlow()
    private val _detailsShowEndTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_END_TIME) ?: true)
    val detailsShowEndTime: StateFlow<Boolean> = _detailsShowEndTime.asStateFlow()
    private val _detailsSectionOrder = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseOrder(
            DesktopDataStore.getKey<String>(PREF_DETAILS_SECTION_ORDER)
        )
    )
    val detailsSectionOrder: StateFlow<List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>> = _detailsSectionOrder.asStateFlow()
    private val _detailsDisabledSections = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseDisabled(
            DesktopDataStore.getKey<String>(PREF_DETAILS_DISABLED_SECTIONS)
        )
    )
    val detailsDisabledSections: StateFlow<Set<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>> = _detailsDisabledSections.asStateFlow()
    private val _amoledMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMOLED_MODE) ?: false)
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()
    private val _isLightMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LIGHT_MODE) ?: false)
    val isLightMode: StateFlow<Boolean> = _isLightMode.asStateFlow()
    private val _gridScale = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_GRID_SCALE) ?: "Normal")
    val gridScale: StateFlow<String> = _gridScale.asStateFlow()
    private val _ambientGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMBIENT_GLOW) ?: true)
    val ambientGlowEnabled: StateFlow<Boolean> = _ambientGlowEnabled.asStateFlow()
    private val _ambientGlowIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_AMBIENT_GLOW_INTENSITY) ?: 0.15f)
    val ambientGlowIntensity: StateFlow<Float> = _ambientGlowIntensity.asStateFlow()
    private val _ambientGlowPositions = MutableStateFlow((DesktopDataStore.getKey<String>(PREF_AMBIENT_GLOW_POSITION) ?: "Center").split(",").filter { it.isNotBlank() }.toSet())
    val ambientGlowPositions: StateFlow<Set<String>> = _ambientGlowPositions.asStateFlow()
    private val _heroBackgroundBlurEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_BACKGROUND_BLUR) ?: true)
    val heroBackgroundBlurEnabled: StateFlow<Boolean> = _heroBackgroundBlurEnabled.asStateFlow()
    private val _heroBackdropBlurRadius = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_BLUR_RADIUS) ?: 80f)
    val heroBackdropBlurRadius: StateFlow<Float> = _heroBackdropBlurRadius.asStateFlow()
    private val _heroBackdropDarkening = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_DARKENING) ?: 0.65f)
    val heroBackdropDarkening: StateFlow<Float> = _heroBackdropDarkening.asStateFlow()
    private val _dockPosition = MutableStateFlow(DockPosition.fromString(DesktopDataStore.getKey<String>(PREF_DOCK_POSITION) ?: "Left"))
    val dockPosition: StateFlow<DockPosition> = _dockPosition.asStateFlow()
    private val _selectedFont = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_FONT) ?: "Inter")
    val selectedFont: StateFlow<String> = _selectedFont.asStateFlow()
    private val _screensaverEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SCREENSAVER_ENABLED) ?: true)
    val screensaverEnabled: StateFlow<Boolean> = _screensaverEnabled.asStateFlow()
    private val _heroAutoSlideDelaySeconds = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HERO_AUTO_SLIDE_DELAY) ?: 10)
    val heroAutoSlideDelaySeconds: StateFlow<Int> = _heroAutoSlideDelaySeconds.asStateFlow()
    private val _continueWatchingStyle = MutableStateFlow(ContinueWatchingStyle.fromString(DesktopDataStore.getKey<String>(PREF_CONTINUE_WATCHING_STYLE)))
    val continueWatchingStyle: StateFlow<ContinueWatchingStyle> = _continueWatchingStyle.asStateFlow()
    private val _posterHoverGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_POSTER_HOVER_GLOW_ENABLED) ?: true)
    val posterHoverGlowEnabled: StateFlow<Boolean> = _posterHoverGlowEnabled.asStateFlow()
    private val _posterTitlePosition = MutableStateFlow(PosterTitlePosition.fromString(DesktopDataStore.getKey<String>(PREF_POSTER_TITLE_POSITION)))
    val posterTitlePosition: StateFlow<PosterTitlePosition> = _posterTitlePosition.asStateFlow()
    private val _homeSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_SPACING_DP) ?: 12)
    val homeSpacingDp: StateFlow<Int> = _homeSpacingDp.asStateFlow()
    private val _homeVerticalSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_VERTICAL_SPACING_DP) ?: 0)
    val homeVerticalSpacingDp: StateFlow<Int> = _homeVerticalSpacingDp.asStateFlow()
    private val _posterWidthDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_WIDTH) ?: 190)
    val posterWidthDp: StateFlow<Int> = _posterWidthDp.asStateFlow()
    private val _posterRoundingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_ROUNDING) ?: 12)
    val posterRoundingDp: StateFlow<Int> = _posterRoundingDp.asStateFlow()
    private val _customThemeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_THEME_ACCENT) ?: "#7C6BFF")
    val customThemeAccent: StateFlow<String> = _customThemeAccent.asStateFlow()
    private val _appThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_THEME_BACKGROUND) ?: "Navy")
    val appThemeBackground: StateFlow<String> = _appThemeBackground.asStateFlow()
    private val _customAppThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_APP_THEME_BACKGROUND) ?: "#0C0C16")
    val customAppThemeBackground: StateFlow<String> = _customAppThemeBackground.asStateFlow()
    private val _heroEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_ENABLED) ?: true)
    val heroEnabled: StateFlow<Boolean> = _heroEnabled.asStateFlow()
    private val _showPosterRating = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_RATING) ?: true)
    val showPosterRating: StateFlow<Boolean> = _showPosterRating.asStateFlow()
    private val _showPosterQuality = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_QUALITY) ?: true)
    val showPosterQuality: StateFlow<Boolean> = _showPosterQuality.asStateFlow()
    private val _showPosterLanguage = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_LANGUAGE) ?: true)
    val showPosterLanguage: StateFlow<Boolean> = _showPosterLanguage.asStateFlow()
    private val _textDropShadowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_TEXT_DROP_SHADOW_ENABLED) ?: true)
    val textDropShadowEnabled: StateFlow<Boolean> = _textDropShadowEnabled.asStateFlow()
    private val _textDropShadowBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_TEXT_DROP_SHADOW_BLUR) ?: 8f)
    val textDropShadowBlur: StateFlow<Float> = _textDropShadowBlur.asStateFlow()
    private val _elementShadowsEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ELEMENT_SHADOWS_ENABLED) ?: true)
    val elementShadowsEnabled: StateFlow<Boolean> = _elementShadowsEnabled.asStateFlow()
    private val _elementShadowMultiplier = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_ELEMENT_SHADOW_MULTIPLIER) ?: 1.0f)
    val elementShadowMultiplier: StateFlow<Float> = _elementShadowMultiplier.asStateFlow()
    private val _appPresetTheme = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_PRESET_THEME) ?: "preset_cyberpunk")
    val appPresetTheme: StateFlow<String> = _appPresetTheme.asStateFlow()
    private val _backgroundGradientEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BACKGROUND_GRADIENT_ENABLED) ?: true)
    val backgroundGradientEnabled: StateFlow<Boolean> = _backgroundGradientEnabled.asStateFlow()
    private val _backgroundGradientType = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BACKGROUND_GRADIENT_TYPE) ?: "Radial")
    val backgroundGradientType: StateFlow<String> = _backgroundGradientType.asStateFlow()
    private val _backgroundGradientIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BACKGROUND_GRADIENT_INTENSITY) ?: 0.5f)
    val backgroundGradientIntensity: StateFlow<Float> = _backgroundGradientIntensity.asStateFlow()
    private val _clockMode = MutableStateFlow(ClockDisplayMode.fromString(DesktopDataStore.getKey<String>(PREF_CLOCK_MODE)))
    val clockMode: StateFlow<ClockDisplayMode> = _clockMode.asStateFlow()
    private val _clockTimeFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_TIME_FORMAT) ?: "HH:mm")
    val clockTimeFormat: StateFlow<String> = _clockTimeFormat.asStateFlow()
    private val _clockDateFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_DATE_FORMAT) ?: "EEE, dd MMM")
    val clockDateFormat: StateFlow<String> = _clockDateFormat.asStateFlow()
    private val _backgroundImagePath = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_PATH) ?: "")
    val backgroundImagePath: StateFlow<String> = _backgroundImagePath.asStateFlow()
    private val _backgroundImageBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BLUR) ?: 20f)
    val backgroundImageBlur: StateFlow<Float> = _backgroundImageBlur.asStateFlow()
    private val _backgroundImageBrightness = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BRIGHTNESS) ?: 0.35f)
    val backgroundImageBrightness: StateFlow<Float> = _backgroundImageBrightness.asStateFlow()
    private val _backgroundImageOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_OPACITY) ?: 1.0f)
    val backgroundImageOpacity: StateFlow<Float> = _backgroundImageOpacity.asStateFlow()
    private val _backgroundImageSaturation = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_SATURATION) ?: 1.0f)
    val backgroundImageSaturation: StateFlow<Float> = _backgroundImageSaturation.asStateFlow()
    private val _backgroundImageVignetteEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_VIGNETTE) ?: false)
    val backgroundImageVignetteEnabled: StateFlow<Boolean> = _backgroundImageVignetteEnabled.asStateFlow()
    private val _backgroundImageVignetteIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_VIGNETTE_INTENSITY) ?: 0.7f)
    val backgroundImageVignetteIntensity: StateFlow<Float> = _backgroundImageVignetteIntensity.asStateFlow()
    private val _backgroundImageTintEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_TINT_ENABLED) ?: false)
    val backgroundImageTintEnabled: StateFlow<Boolean> = _backgroundImageTintEnabled.asStateFlow()
    private val _backgroundImageTintColor = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_TINT_COLOR) ?: "#7C6BFF")
    val backgroundImageTintColor: StateFlow<String> = _backgroundImageTintColor.asStateFlow()
    private val _backgroundImageTintAlpha = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_TINT_ALPHA) ?: 0.3f)
    val backgroundImageTintAlpha: StateFlow<Float> = _backgroundImageTintAlpha.asStateFlow()
    private val _uiCardOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_UI_CARD_OPACITY) ?: 0.4f)
    val uiCardOpacity: StateFlow<Float> = _uiCardOpacity.asStateFlow()
    private val customPresetsJson = DesktopDataStore.getKey<String>(PREF_CUSTOM_PRESETS) ?: "[]"
    private val _customPresets = MutableStateFlow(
        try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(customPresetsJson, object : com.fasterxml.jackson.core.type.TypeReference<List<ThemePreset>>() {})
        } catch (e: Exception) {
            emptyList()
        },
    )
    val customPresets: StateFlow<List<ThemePreset>> = _customPresets.asStateFlow()

    fun setThemeAccent(colorName: String) {
        _themeAccent.value = colorName
        DesktopDataStore.setKey(PREF_THEME_ACCENT, colorName)
    }

    fun setAntiSpoilerEnabled(enabled: Boolean) {
        _antiSpoilerEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ANTI_SPOILER_ENABLED, enabled)
    }

    fun setAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
        DesktopDataStore.setKey(PREF_AMOLED_MODE, enabled)
    }

    fun setLightMode(enabled: Boolean) {
        if (_isLightMode.value == enabled) return
        _isLightMode.value = enabled
        DesktopDataStore.setKey(PREF_LIGHT_MODE, enabled)

        val currentPreset = (com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets + _customPresets.value).find { it.id == _appPresetTheme.value }
        if (currentPreset != null && currentPreset.isLightMode != enabled) {
            val defaultPreset = com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets.firstOrNull { it.isLightMode == enabled }
            if (defaultPreset != null) {
                applyPreset(defaultPreset)
            }
        }
    }

    fun setGridScale(scale: String) {
        _gridScale.value = scale
        DesktopDataStore.setKey(PREF_GRID_SCALE, scale)
    }

    fun setAmbientGlowEnabled(enabled: Boolean) {
        _ambientGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW, enabled)
    }

    fun setAmbientGlowIntensity(intensity: Float) {
        _ambientGlowIntensity.value = intensity
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_INTENSITY, intensity)
    }

    fun toggleAmbientGlowPosition(position: String) {
        val current = _ambientGlowPositions.value.toMutableSet()
        if (current.contains(position)) {
            current.remove(position)
        } else {
            current.add(position)
        }
        if (current.isEmpty()) current.add("Center")
        _ambientGlowPositions.value = current
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_POSITION, current.joinToString(","))
    }

    fun setHeroBackgroundBlurEnabled(enabled: Boolean) {
        _heroBackgroundBlurEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_BACKGROUND_BLUR, enabled)
    }

    fun setHeroBackdropBlurRadius(radius: Float) {
        _heroBackdropBlurRadius.value = radius
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_BLUR_RADIUS, radius)
    }

    fun setHeroBackdropDarkening(darkening: Float) {
        _heroBackdropDarkening.value = darkening
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_DARKENING, darkening)
    }

    fun setDockPosition(position: DockPosition) {
        _dockPosition.value = position
        DesktopDataStore.setKey(PREF_DOCK_POSITION, position.label)
    }

    fun setSelectedFont(font: String) {
        _selectedFont.value = font
        DesktopDataStore.setKey(PREF_FONT, font)
    }

    fun setScreensaverEnabled(enabled: Boolean) {
        _screensaverEnabled.value = enabled
        DesktopDataStore.setKey(PREF_SCREENSAVER_ENABLED, enabled)
    }

    fun setHeroAutoSlideDelaySeconds(seconds: Int) {
        _heroAutoSlideDelaySeconds.value = seconds
        DesktopDataStore.setKey(PREF_HERO_AUTO_SLIDE_DELAY, seconds)
    }

    fun setHomeSpacingDp(dp: Int) {
        _homeSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_SPACING_DP, dp)
    }

    fun setHomeVerticalSpacingDp(dp: Int) {
        _homeVerticalSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_VERTICAL_SPACING_DP, dp)
    }

    fun setPosterWidthDp(width: Int) {
        _posterWidthDp.value = width
        DesktopDataStore.setKey(PREF_POSTER_WIDTH, width)
    }

    fun setPosterRoundingDp(dp: Int) {
        _posterRoundingDp.value = dp
        DesktopDataStore.setKey(PREF_POSTER_ROUNDING, dp)
    }

    fun setCustomThemeAccent(hex: String) {
        _customThemeAccent.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_THEME_ACCENT, hex)
    }

    fun setAppThemeBackground(themeName: String) {
        _appThemeBackground.value = themeName
        DesktopDataStore.setKey(PREF_APP_THEME_BACKGROUND, themeName)
    }

    fun setCustomAppThemeBackground(hex: String) {
        _customAppThemeBackground.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_APP_THEME_BACKGROUND, hex)
    }

    fun setHeroEnabled(enabled: Boolean) {
        _heroEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_ENABLED, enabled)
    }

    fun setContinueWatchingStyle(style: ContinueWatchingStyle) {
        _continueWatchingStyle.value = style
        DesktopDataStore.setKey(PREF_CONTINUE_WATCHING_STYLE, style.name)
    }

    fun setPosterHoverGlowEnabled(enabled: Boolean) {
        _posterHoverGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_POSTER_HOVER_GLOW_ENABLED, enabled)
    }

    fun setPosterTitlePosition(position: PosterTitlePosition) {
        _posterTitlePosition.value = position
        DesktopDataStore.setKey(PREF_POSTER_TITLE_POSITION, position.name)
    }

    fun setShowPosterRating(enabled: Boolean) {
        _showPosterRating.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_RATING, enabled)
    }

    fun setShowPosterQuality(enabled: Boolean) {
        _showPosterQuality.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_QUALITY, enabled)
    }

    fun setShowPosterLanguage(show: Boolean) {
        _showPosterLanguage.value = show
        DesktopDataStore.setKey(PREF_SHOW_POSTER_LANGUAGE, show)
    }

    fun setTextDropShadowEnabled(enabled: Boolean) {
        _textDropShadowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_ENABLED, enabled)
    }

    fun setTextDropShadowBlur(blur: Float) {
        _textDropShadowBlur.value = blur
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_BLUR, blur)
    }

    fun setElementShadowsEnabled(enabled: Boolean) {
        _elementShadowsEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOWS_ENABLED, enabled)
    }

    fun setElementShadowMultiplier(multiplier: Float) {
        _elementShadowMultiplier.value = multiplier
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOW_MULTIPLIER, multiplier)
    }

    fun setAppPresetTheme(presetId: String) {
        _appPresetTheme.value = presetId
        DesktopDataStore.setKey(PREF_APP_PRESET_THEME, presetId)
    }

    fun setBackgroundGradientEnabled(enabled: Boolean) {
        _backgroundGradientEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_ENABLED, enabled)
    }

    fun setBackgroundGradientType(type: String) {
        _backgroundGradientType.value = type
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_TYPE, type)
    }

    fun setBackgroundGradientIntensity(intensity: Float) {
        _backgroundGradientIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_INTENSITY, intensity)
    }

    fun setClockMode(mode: ClockDisplayMode) {
        _clockMode.value = mode
        DesktopDataStore.setKey(PREF_CLOCK_MODE, mode.name)
    }

    fun setClockTimeFormat(format: String) {
        _clockTimeFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_TIME_FORMAT, format)
    }

    fun setClockDateFormat(format: String) {
        _clockDateFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_DATE_FORMAT, format)
    }

    fun setDetailsShowCurrentTime(enabled: Boolean) {
        _detailsShowCurrentTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_CURRENT_TIME, enabled)
    }

    fun setDetailsShowEndTime(enabled: Boolean) {
        _detailsShowEndTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_END_TIME, enabled)
    }

    fun setLockUnreleasedEpisodes(enabled: Boolean) {
        _lockUnreleasedEpisodes.value = enabled
        DesktopDataStore.setKey(PREF_LOCK_UNRELEASED_EPISODES, enabled)
    }

    fun setBackgroundImagePath(path: String) {
        _backgroundImagePath.value = path
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, path)
    }

    fun setBackgroundImageBlur(blur: Float) {
        _backgroundImageBlur.value = blur
        DesktopDataStore.setKey(PREF_BG_IMAGE_BLUR, blur)
    }

    fun setBackgroundImageBrightness(brightness: Float) {
        _backgroundImageBrightness.value = brightness
        DesktopDataStore.setKey(PREF_BG_IMAGE_BRIGHTNESS, brightness)
    }

    fun clearBackgroundImage() {
        _backgroundImagePath.value = ""
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, "")
    }

    fun setBackgroundImageOpacity(opacity: Float) {
        _backgroundImageOpacity.value = opacity
        DesktopDataStore.setKey(PREF_BG_IMAGE_OPACITY, opacity)
    }

    fun setBackgroundImageSaturation(saturation: Float) {
        _backgroundImageSaturation.value = saturation
        DesktopDataStore.setKey(PREF_BG_IMAGE_SATURATION, saturation)
    }

    fun setBackgroundImageVignetteEnabled(enabled: Boolean) {
        _backgroundImageVignetteEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE, enabled)
    }

    fun setBackgroundImageVignetteIntensity(intensity: Float) {
        _backgroundImageVignetteIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE_INTENSITY, intensity)
    }

    fun setBackgroundImageTintEnabled(enabled: Boolean) {
        _backgroundImageTintEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ENABLED, enabled)
    }

    fun setBackgroundImageTintColor(hex: String) {
        _backgroundImageTintColor.value = hex
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_COLOR, hex)
    }

    fun setBackgroundImageTintAlpha(alpha: Float) {
        _backgroundImageTintAlpha.value = alpha
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ALPHA, alpha)
    }

    fun setUiCardOpacity(opacity: Float) {
        _uiCardOpacity.value = opacity
        DesktopDataStore.setKey(PREF_UI_CARD_OPACITY, opacity)
    }

    fun saveCustomPreset(preset: ThemePreset) {
        val currentList = _customPresets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == preset.id }
        if (index != -1) {
            currentList[index] = preset
        } else {
            currentList.add(preset)
        }
        _customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        setAppPresetTheme(preset.id)
    }

    fun deleteCustomPreset(id: String) {
        val currentList = _customPresets.value.filter { it.id != id }
        _customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        if (_appPresetTheme.value == id) {
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
        _detailsSectionOrder.value = order
        DesktopDataStore.setKey(
            PREF_DETAILS_SECTION_ORDER,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(order),
        )
    }

    fun toggleDetailsSection(key: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey, enabled: Boolean) {
        val current = _detailsDisabledSections.value.toMutableSet()
        if (enabled) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _detailsDisabledSections.value = current
        DesktopDataStore.setKey(
            PREF_DETAILS_DISABLED_SECTIONS,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(current),
        )
    }

    fun moveDetailsSection(fromIndex: Int, toIndex: Int) {
        val current = _detailsSectionOrder.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setDetailsSectionOrder(current)
        }
    }

    fun resetDetailsSectionOrder() {
        setDetailsSectionOrder(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.defaultOrder)
        _detailsDisabledSections.value = emptySet()
        DesktopDataStore.removeKey(PREF_DETAILS_DISABLED_SECTIONS)
    }
}
