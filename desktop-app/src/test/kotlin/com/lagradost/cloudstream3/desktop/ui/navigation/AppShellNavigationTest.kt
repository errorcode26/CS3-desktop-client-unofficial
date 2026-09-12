package com.lagradost.cloudstream3.desktop.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState
import com.lagradost.cloudstream3.desktop.ui.components.GlobalDialogState
import com.lagradost.cloudstream3.desktop.ui.components.parseBasicMarkdown
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.navigation.DefaultRootComponent
import com.lagradost.cloudstream3.desktop.ui.navigation.RootComponent
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppShellNavigationTest {

    @Test
    fun `test Decompose root component navigation stack transitions`() {
        val lifecycle = LifecycleRegistry()
        val root = DefaultRootComponent(DefaultComponentContext(lifecycle))
        lifecycle.resume()

        // 1. Initial configuration should always be Home
        val initialStack = root.childStack.value
        assertEquals(1, initialStack.items.size)
        assertTrue(initialStack.active.instance is RootComponent.Child.Home)
        assertEquals(Config.Home, initialStack.active.configuration)

        // 2. Navigating to a top-level screen (Search) via bringToFront
        root.bringToFront(Config.Search)
        val searchStack = root.childStack.value
        assertEquals(2, searchStack.items.size)
        assertEquals(Config.Home, searchStack.items[0].configuration)
        assertEquals(Config.Search, searchStack.items[1].configuration)
        assertTrue(searchStack.active.instance is RootComponent.Child.Search)

        // 3. Popping Search returns directly to Home
        root.pop()
        val poppedStack = root.childStack.value
        assertEquals(1, poppedStack.items.size)
        assertTrue(poppedStack.active.instance is RootComponent.Child.Home)

        // 4. Navigating to Library, then switching to Extensions anchors back to Home
        root.bringToFront(Config.Library)
        assertEquals(2, root.childStack.value.items.size)
        assertTrue(root.childStack.value.active.instance is RootComponent.Child.Library)

        root.bringToFront(Config.Extensions())
        assertEquals(2, root.childStack.value.items.size)
        assertEquals(Config.Home, root.childStack.value.items[0].configuration)
        assertTrue(root.childStack.value.active.instance is RootComponent.Child.Extensions)

        // 5. Selecting Home replaces stack back to single Home destination
        root.bringToFront(Config.Home)
        assertEquals(1, root.childStack.value.items.size)
        assertTrue(root.childStack.value.active.instance is RootComponent.Child.Home)

        // Clean lifecycle teardown
        lifecycle.destroy()
    }

    @Test
    fun `test network stream URL classification and title parser`() {
        data class StreamTestCase(val url: String, val expectedType: ExtractorLinkType, val expectedName: String)

        val testCases = listOf(
            StreamTestCase("https://stream.example.com/live/master.m3u8?token=xyz", ExtractorLinkType.M3U8, "master.m3u8"),
            StreamTestCase("https://stream.example.com/vod/playlist.mpd", ExtractorLinkType.DASH, "playlist.mpd"),
            StreamTestCase("https://cdn.example.org/videos/sample_1080p.mp4", ExtractorLinkType.VIDEO, "sample_1080p.mp4"),
            StreamTestCase("https://video.host/stream/720p.mkv?sig=123&exp=456", ExtractorLinkType.VIDEO, "720p.mkv"),
        )

        for (case in testCases) {
            val trimmed = case.url.trim()
            val isM3u8 = trimmed.contains(".m3u8", ignoreCase = true)
            val isDash = trimmed.contains(".mpd", ignoreCase = true)
            val linkType = when {
                isM3u8 -> ExtractorLinkType.M3U8
                isDash -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            val streamName = trimmed.substringAfterLast("/").substringBefore("?").ifEmpty { "Network Stream" }

            assertEquals(case.expectedType, linkType, "Failed link type for ${case.url}")
            assertEquals(case.expectedName, streamName, "Failed name extraction for ${case.url}")
        }
    }

    @Test
    fun `test markdown parser bold tag styling in update dialog`() {
        val input = "Version 2.0 brings **massive speedups** and **bug fixes** to player."
        val annotated = parseBasicMarkdown(input)

        assertEquals("Version 2.0 brings massive speedups and bug fixes to player.", annotated.text)
        assertEquals(2, annotated.spanStyles.size)

        // Verify bold spans were applied
        val span1 = annotated.spanStyles[0]
        assertEquals(19, span1.start)
        assertEquals(35, span1.end)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, span1.item.fontWeight)

        val span2 = annotated.spanStyles[1]
        assertEquals(40, span2.start)
        assertEquals(49, span2.end)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, span2.item.fontWeight)
    }

    @Suppress("DEPRECATION_ERROR")
    @Test
    fun `test global context menu state lifecycle and dismissal`() {
        GlobalContextMenuState.clear()
        assertFalse(GlobalContextMenuState.isActive)
        assertNull(GlobalContextMenuState.searchResponse)

        val mockPoster = com.lagradost.cloudstream3.MovieSearchResponse(
            name = "Test Movie",
            url = "https://example.com/movie/test",
            apiName = "TestAPI",
            type = TvType.Movie,
            posterUrl = "https://example.com/poster.jpg",
            id = 1234,
        )

        GlobalContextMenuState.showForPoster(
            bounds = androidx.compose.ui.geometry.Rect(10f, 10f, 100f, 150f),
            item = mockPoster,
            provider = null,
            onClick = {},
        )

        assertTrue(GlobalContextMenuState.isActive)
        assertEquals("Test Movie", GlobalContextMenuState.searchResponse?.name)
        assertEquals(com.lagradost.cloudstream3.desktop.ui.components.ContextMenuType.POSTER, GlobalContextMenuState.menuType)

        // Dismiss keeps item state until animation finishes
        GlobalContextMenuState.dismiss()
        assertFalse(GlobalContextMenuState.isActive)
        assertNotNull(GlobalContextMenuState.searchResponse)

        // Clear resets all references
        GlobalContextMenuState.clear()
        assertNull(GlobalContextMenuState.searchResponse)
        assertNull(GlobalContextMenuState.episode)
        assertNull(GlobalContextMenuState.downloadTask)
    }

    @Test
    fun `test global dialog state tracking counter`() {
        assertEquals(0, GlobalDialogState.activeDialogCount)
        assertFalse(GlobalDialogState.isAnyDialogOpen)

        GlobalDialogState.activeDialogCount++
        assertTrue(GlobalDialogState.isAnyDialogOpen)
        assertEquals(1, GlobalDialogState.activeDialogCount)

        GlobalDialogState.activeDialogCount++
        assertEquals(2, GlobalDialogState.activeDialogCount)
        assertTrue(GlobalDialogState.isAnyDialogOpen)

        GlobalDialogState.activeDialogCount--
        GlobalDialogState.activeDialogCount--
        assertEquals(0, GlobalDialogState.activeDialogCount)
        assertFalse(GlobalDialogState.isAnyDialogOpen)
    }
}
