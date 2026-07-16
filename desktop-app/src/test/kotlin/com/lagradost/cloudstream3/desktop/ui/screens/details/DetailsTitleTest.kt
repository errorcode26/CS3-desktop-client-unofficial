package com.lagradost.cloudstream3.desktop.ui.screens.details

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailsTitleTest {

    @Test
    fun testSplitTitleWithVariousScrapedFormats() {
        // Case 1: Standard TV Series with season details and bracket noise
        val res1 = splitTitle("House of the Dragon (Season 1 - 3) [S03 E03 Added] Hindi-Dubbed (ORG)")
        assertEquals("House of the Dragon", res1.first)
        assertEquals("(Season 1 - 3) [S03 E03 Added] Hindi-Dubbed (ORG)", res1.second)

        // Case 2: Movie with year and brackets
        val res2 = splitTitle("Spider-Man: No Way Home (2021) [Dual Audio] [1080p]")
        assertEquals("Spider-Man: No Way Home (2021)", res2.first)
        assertEquals("[Dual Audio] [1080p]", res2.second)

        // Case 3: Simple title with only year (should keep year, extra should be null)
        val res3 = splitTitle("The Batman (2022)")
        assertEquals("The Batman (2022)", res3.first)
        assertNull(res3.second)

        // Case 4: Title containing a season suffix
        val res4 = splitTitle("Vikings Season 6")
        assertEquals("Vikings", res4.first)
        assertEquals("Season 6", res4.second)

        // Case 5: Empty or blank inputs fallback
        val res5 = splitTitle("  ")
        assertEquals("  ", res5.first)
        assertNull(res5.second)
    }
}
