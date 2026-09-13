package com.lagradost.cloudstream3.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.desktop.ui.components.darkDesktopColors
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopThemeTest {

    @Test
    fun testCustomDarkBackgroundParsingAndDerivation() {
        val draculaHex = "#282A36"
        val colors = darkDesktopColors(
            accent = Color(0xFFFF79C6),
            backgroundTheme = "Custom",
            isAmoled = false,
            customBgHex = draculaHex,
        )

        // Background should match parsed custom hex
        assertEquals(Color(0xFF282A36), colors.Background)

        // SurfaceCard should be lifted above background
        assertTrue(colors.SurfaceCard.red >= colors.Background.red)
        assertTrue(colors.SurfaceCard.green >= colors.Background.green)
        assertTrue(colors.SurfaceCard.blue >= colors.Background.blue)

        // SurfaceElevated should be lifted above SurfaceCard
        assertTrue(colors.SurfaceElevated.red >= colors.SurfaceCard.red)
        assertTrue(colors.SurfaceElevated.green >= colors.SurfaceCard.green)
        assertTrue(colors.SurfaceElevated.blue >= colors.SurfaceCard.blue)
    }

    @Test
    fun testContrastAwareOnPrimaryInLightAndDarkMode() {
        // In dark mode with high-luminance accent (Matcha Green #A3E635), onPrimary must be dark charcoal
        val matchaGreen = Color(0xFFA3E635)
        val darkColors = buildDesktopColors(
            primaryColor = matchaGreen,
            isLightMode = false,
            appThemeBackground = "Forest",
        )
        val darkScheme = buildColorScheme(matchaGreen, darkColors, isLightMode = false)
        assertEquals(Color(0xFF0F172A), darkScheme.onPrimary)

        // For dark purple accent (#7C6BFF), text on primary should remain white
        val deepPurple = Color(0xFF7C6BFF)
        val purpleScheme = buildColorScheme(deepPurple, darkColors, isLightMode = false)
        assertEquals(Color.White, purpleScheme.onPrimary)
    }

    @Test
    fun testBuiltInPresetsIntegrity() {
        val presets = BuiltInPresets.presets
        assertTrue(presets.isNotEmpty(), "Presets list should not be empty")

        val ids = presets.map { it.id }
        assertEquals(ids.distinct().size, ids.size, "All preset IDs must be unique")

        presets.forEach { preset ->
            assertTrue(preset.name.isNotBlank(), "Preset name should not be blank")
            val accent = accentColorFromName(preset.themeAccent, preset.customThemeAccent)
            assertNotEquals(Color.Unspecified, accent)
        }
    }
}
