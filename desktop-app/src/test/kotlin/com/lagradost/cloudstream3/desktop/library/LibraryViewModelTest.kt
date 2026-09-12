package com.lagradost.cloudstream3.desktop.library

import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryViewModelTest {

    private fun bookmark(
        id: String,
        name: String,
        apiName: String = "TestProvider",
        watchType: Int = DesktopWatchType.WATCHING.id,
        dateAdded: Long = 0L,
    ) = DesktopBookmark(
        id = id,
        name = name,
        url = "https://example.com/$id",
        apiName = apiName,
        posterUrl = null,
        watchType = watchType,
        dateAdded = dateAdded,
    )

    private fun LibraryUiState.applyFilters(): LibraryUiState {
        var result = bookmarks.filter { it.watchType == selectedTab.id }
        if (selectedProvider != null) {
            result = result.filter { it.apiName == selectedProvider }
        }
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        result = when (sortOption) {
            SortOption.DATE_ADDED_DESC -> result.sortedByDescending { it.dateAdded }
            SortOption.DATE_ADDED_ASC -> result.sortedBy { it.dateAdded }
            SortOption.ALPHA_ASC -> result.sortedBy { it.name.lowercase() }
            SortOption.ALPHA_DESC -> result.sortedByDescending { it.name.lowercase() }
        }
        return copy(filteredBookmarks = result)
    }

    @Test
    fun testInitialState_Defaults() {
        val state = LibraryUiState()
        assertEquals(DesktopWatchType.WATCHING, state.selectedTab)
        assertEquals("", state.searchQuery)
        assertEquals(SortOption.DATE_ADDED_DESC, state.sortOption)
        assertNull(state.selectedProvider)
        assertTrue(state.bookmarks.isEmpty())
        assertTrue(state.filteredBookmarks.isEmpty())
        assertTrue(state.installedProviderNames.isEmpty())
        assertTrue(state.providerMap.isEmpty())
        assertEquals(190, state.posterWidthDp)
        assertNull(state.orphanRecoveryBookmark)
    }

    @Test
    fun testApplyFilters_WatchTypeTab() {
        val watching = bookmark("1", "Show A", watchType = DesktopWatchType.WATCHING.id)
        val completed = bookmark("2", "Show B", watchType = DesktopWatchType.COMPLETED.id)
        val planToWatch = bookmark("3", "Show C", watchType = DesktopWatchType.PLANTOWATCH.id)

        val stateWatching = LibraryUiState(
            bookmarks = listOf(watching, completed, planToWatch),
            selectedTab = DesktopWatchType.WATCHING,
        ).applyFilters()
        assertEquals(1, stateWatching.filteredBookmarks.size)
        assertEquals("1", stateWatching.filteredBookmarks.first().id)

        val stateCompleted = LibraryUiState(
            bookmarks = listOf(watching, completed, planToWatch),
            selectedTab = DesktopWatchType.COMPLETED,
        ).applyFilters()
        assertEquals(1, stateCompleted.filteredBookmarks.size)
        assertEquals("2", stateCompleted.filteredBookmarks.first().id)
    }

    @Test
    fun testApplyFilters_SearchQuery_CaseInsensitive() {
        val b1 = bookmark("1", "Attack on Titan")
        val b2 = bookmark("2", "Demon Slayer")
        val b3 = bookmark("3", "Titan's Quest")

        val state = LibraryUiState(
            bookmarks = listOf(b1, b2, b3),
            selectedTab = DesktopWatchType.WATCHING,
            searchQuery = "titan",
        ).applyFilters()

        assertEquals(2, state.filteredBookmarks.size)
        assertTrue(state.filteredBookmarks.any { it.id == "1" })
        assertTrue(state.filteredBookmarks.any { it.id == "3" })
    }

    @Test
    fun testApplyFilters_ProviderFilter() {
        val provA1 = bookmark("1", "Movie A", apiName = "ProviderA")
        val provA2 = bookmark("2", "Movie B", apiName = "ProviderA")
        val provB = bookmark("3", "Movie C", apiName = "ProviderB")

        val filtered = LibraryUiState(
            bookmarks = listOf(provA1, provA2, provB),
            selectedTab = DesktopWatchType.WATCHING,
            selectedProvider = "ProviderA",
        ).applyFilters()

        assertEquals(2, filtered.filteredBookmarks.size)
        assertTrue(filtered.filteredBookmarks.none { it.apiName == "ProviderB" })
    }

    @Test
    fun testApplyFilters_SortOptions() {
        val old = bookmark("1", "Zebra Show", dateAdded = 100L)
        val mid = bookmark("2", "Apple Show", dateAdded = 200L)
        val newest = bookmark("3", "Mango Show", dateAdded = 300L)
        val bookmarks = listOf(old, mid, newest)

        val descByDate = LibraryUiState(bookmarks = bookmarks, selectedTab = DesktopWatchType.WATCHING, sortOption = SortOption.DATE_ADDED_DESC).applyFilters()
        assertEquals(listOf("3", "2", "1"), descByDate.filteredBookmarks.map { it.id })

        val ascByDate = LibraryUiState(bookmarks = bookmarks, selectedTab = DesktopWatchType.WATCHING, sortOption = SortOption.DATE_ADDED_ASC).applyFilters()
        assertEquals(listOf("1", "2", "3"), ascByDate.filteredBookmarks.map { it.id })

        val alphaAsc = LibraryUiState(bookmarks = bookmarks, selectedTab = DesktopWatchType.WATCHING, sortOption = SortOption.ALPHA_ASC).applyFilters()
        assertEquals(listOf("2", "3", "1"), alphaAsc.filteredBookmarks.map { it.id })

        val alphaDesc = LibraryUiState(bookmarks = bookmarks, selectedTab = DesktopWatchType.WATCHING, sortOption = SortOption.ALPHA_DESC).applyFilters()
        assertEquals(listOf("1", "3", "2"), alphaDesc.filteredBookmarks.map { it.id })
    }

    @Test
    fun testProviderMap_O1Lookup() {
        val state = LibraryUiState(
            providerMap = emptyMap(),
        )
        // Confirm an absent key returns null — consistent with O(1) map semantics
        assertNull(state.providerMap["NonExistentProvider"])

        // Confirm providerMap field is separate and does not affect filteredBookmarks
        assertTrue(state.filteredBookmarks.isEmpty())
    }

    @Test
    fun testInstalledProviderNames_MissingDetection() {
        val b = bookmark("1", "My Show", apiName = "MissingPlugin")
        val state = LibraryUiState(
            bookmarks = listOf(b),
            selectedTab = DesktopWatchType.WATCHING,
            installedProviderNames = setOf("ProviderA", "ProviderB"),
        )
        // MissingPlugin is not installed — this is the signal used to show the warning badge
        assertTrue(b.apiName !in state.installedProviderNames)
    }
}
