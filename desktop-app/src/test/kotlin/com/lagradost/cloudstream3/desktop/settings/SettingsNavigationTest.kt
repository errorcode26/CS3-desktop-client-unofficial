package com.lagradost.cloudstream3.desktop.settings

import com.lagradost.cloudstream3.desktop.ui.screens.settings.BOTTOM_NAV_ITEMS
import com.lagradost.cloudstream3.desktop.ui.screens.settings.LeafTab
import com.lagradost.cloudstream3.desktop.ui.screens.settings.MAIN_NAV_ITEMS
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSubScreen
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsNavigationTest {

    @Test
    fun testMainNavItems_ConsolidatedToEightDesktopCategories() {
        assertEquals(
            8,
            MAIN_NAV_ITEMS.size,
            "Main settings navigation must have exactly 8 category hubs to avoid desktop rail overcrowding",
        )

        val expectedTabs = listOf(
            LeafTab.APPEARANCE,
            LeafTab.PLAYER,
            LeafTab.ACCOUNTS,
            LeafTab.INTEGRATIONS,
            LeafTab.EXTENSIONS,
            LeafTab.NETWORK,
            LeafTab.DEVELOPER,
            LeafTab.ADVANCED,
        )
        assertEquals(expectedTabs, MAIN_NAV_ITEMS)
    }

    @Test
    fun testBottomNavItems_ContainsAboutTab() {
        assertEquals(1, BOTTOM_NAV_ITEMS.size)
        assertEquals(LeafTab.ABOUT, BOTTOM_NAV_ITEMS.first())
    }

    @Test
    fun testSettingsSubScreen_ContainsPlaybackAndStreamDestinations() {
        val names = SettingsSubScreen.entries.map { it.name }

        assertTrue(names.contains("STREAM_PRIORITIES"), "STREAM_PRIORITIES must be available as a SettingsSubScreen")
        assertTrue(names.contains("PLAYER_RENDERING_ENGINE"), "PLAYER_RENDERING_ENGINE must be available as a SettingsSubScreen")
        assertTrue(names.contains("PLAYER_AUDIO_EQ"), "PLAYER_AUDIO_EQ must be available as a SettingsSubScreen")
        assertTrue(names.contains("PLAYER_AUTOPLAY_SKIP"), "PLAYER_AUTOPLAY_SKIP must be available as a SettingsSubScreen")
        assertTrue(names.contains("PLAYER_DOWNLOADS"), "PLAYER_DOWNLOADS must be available as a SettingsSubScreen")
        assertTrue(names.contains("SUBTITLES"), "SUBTITLES must be available as a SettingsSubScreen")
        assertTrue(names.contains("KEYBOARD_SHORTCUTS"), "KEYBOARD_SHORTCUTS must be available as a SettingsSubScreen")
    }

    @Test
    fun testLegacyLeafTabAliases_RetainedForSearchIndexCompatibility() {
        val allTabs = LeafTab.entries.map { it.name }

        assertTrue(allTabs.contains("STREAM_PRIORITIES"))
        assertTrue(allTabs.contains("AUDIO"))
        assertTrue(allTabs.contains("SUBTITLES"))
        assertTrue(allTabs.contains("DOWNLOADS"))
        assertTrue(allTabs.contains("SHORTCUTS"))
        assertTrue(allTabs.contains("THEME"))
        assertTrue(allTabs.contains("LAYOUT"))
        assertTrue(allTabs.contains("DETAILS"))
    }
}
