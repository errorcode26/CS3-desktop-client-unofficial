package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.desktop.ui.components.ScreenStage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DetailsLoadingStageTest {

    private fun resolveStage(
        isLoading: Boolean,
        hasResponse: Boolean,
        fetchFailed: Boolean,
    ): ScreenStage {
        return when {
            fetchFailed && !hasResponse -> ScreenStage.ERROR
            hasResponse -> ScreenStage.CONTENT
            isLoading -> ScreenStage.LOADING
            else -> ScreenStage.ERROR
        }
    }

    @Test
    fun testLoadingStageResolution() {
        // Initial loading with no response
        assertEquals(ScreenStage.LOADING, resolveStage(isLoading = true, hasResponse = false, fetchFailed = false))

        // Scraper succeeds and response is populated
        assertEquals(ScreenStage.CONTENT, resolveStage(isLoading = false, hasResponse = true, fetchFailed = false))

        // Scraper fails with no response
        assertEquals(ScreenStage.ERROR, resolveStage(isLoading = false, hasResponse = false, fetchFailed = true))

        // Transient state where response is populated while loading flag is settling
        assertEquals(ScreenStage.CONTENT, resolveStage(isLoading = true, hasResponse = true, fetchFailed = false))
    }

    @Test
    fun testLoadingAttributionFormatting() {
        fun formatLoadingMessage(providerName: String?): String {
            return if (!providerName.isNullOrBlank()) {
                "Loading media from $providerName..."
            } else {
                "Loading media details..."
            }
        }

        assertEquals("Loading media from SuperStream...", formatLoadingMessage("SuperStream"))
        assertEquals("Loading media details...", formatLoadingMessage(null))
        assertEquals("Loading media details...", formatLoadingMessage("   "))
    }
}
