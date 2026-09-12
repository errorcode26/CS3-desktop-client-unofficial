package com.lagradost.cloudstream3.desktop.explore.viewmodel

import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExploreViewModelTest {

    private fun createItem(
        id: String,
        name: String,
        year: String? = null,
        genres: List<String> = emptyList(),
    ) = ExploreItem(
        id = id,
        type = "movie",
        name = name,
        posterUrl = "https://example.com/$id.jpg",
        releaseYear = year,
        genres = genres,
    )

    @Test
    fun testApplyFilters_YearFiltering() {
        val viewModel = ExploreViewModel()
        val items = listOf(
            createItem("1", "Modern Movie", "2024"),
            createItem("2", "Twenty Ten Movie", "2015"),
            createItem("3", "Two Thousand Movie", "2004"),
            createItem("4", "Nineties Classic", "1994"),
            createItem("5", "Eighties Classic", "1985"),
        )

        // All Years
        assertEquals(5, viewModel.applyFilters(items, "", "All Years").size)

        // Exact Year
        val y2024 = viewModel.applyFilters(items, "", "2024")
        assertEquals(1, y2024.size)
        assertEquals("Modern Movie", y2024[0].name)

        // 2010s decade
        val decade2010s = viewModel.applyFilters(items, "", "2010s")
        assertEquals(1, decade2010s.size)
        assertEquals("Twenty Ten Movie", decade2010s[0].name)

        // 2000s decade
        val decade2000s = viewModel.applyFilters(items, "", "2000s")
        assertEquals(1, decade2000s.size)
        assertEquals("Two Thousand Movie", decade2000s[0].name)

        // 1990s & Older
        val older = viewModel.applyFilters(items, "", "1990s & Older")
        assertEquals(2, older.size)
        assertTrue(older.any { it.name == "Nineties Classic" })
        assertTrue(older.any { it.name == "Eighties Classic" })
    }

    @Test
    fun testApplyFilters_QueryFiltering() {
        val viewModel = ExploreViewModel()
        val items = listOf(
            createItem("1", "Interstellar", "2014", listOf("Sci-Fi", "Drama")),
            createItem("2", "The Dark Knight", "2008", listOf("Action", "Crime")),
            createItem("3", "Inception", "2010", listOf("Action", "Sci-Fi")),
        )

        // Title query
        val byTitle = viewModel.applyFilters(items, "dark", "All Years")
        assertEquals(1, byTitle.size)
        assertEquals("The Dark Knight", byTitle[0].name)

        // Genre query
        val byGenre = viewModel.applyFilters(items, "sci-fi", "All Years")
        assertEquals(2, byGenre.size)

        // Non-matching query
        val empty = viewModel.applyFilters(items, "Avatar", "All Years")
        assertEquals(0, empty.size)
    }

    @Test
    fun testIsTitleRelevant() {
        val viewModel = ExploreViewModel()

        // Exact & case-insensitive
        assertTrue(viewModel.isTitleRelevant("Inception", "Inception"))
        assertTrue(viewModel.isTitleRelevant("Inception", "inception"))

        // Subtitle/tag junk cleaned
        assertTrue(viewModel.isTitleRelevant("The Dark Knight", "The Dark Knight 2008 1080p BluRay"))

        // Substring & word overlap with stop words
        assertTrue(viewModel.isTitleRelevant("A Quiet Place", "Quiet Place Part 1"))

        // Unrelated titles
        assertFalse(viewModel.isTitleRelevant("Batman Begins", "Superman Man of Steel"))
        assertFalse(viewModel.isTitleRelevant("Spider-Man", "Iron Man"))
    }

    @Test
    fun testItemDeduplication() {
        val items = listOf(
            createItem("tt1375666", "Inception", "2010"),
            createItem("tt0468569", "The Dark Knight", "2008"),
            createItem("tt1375666", "Inception (Duplicate)", "2010"),
        )

        val deduplicated = items.distinctBy { it.id }
        assertEquals(2, deduplicated.size)
        assertEquals("Inception", deduplicated[0].name)
        assertEquals("The Dark Knight", deduplicated[1].name)
    }
}
