package com.lagradost.cloudstream3.desktop.ui.screens.search

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.screens.search.contract.SearchUiEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchViewModelTest {

    private var activeVm: SearchViewModel? = null

    @AfterEach
    fun tearDown() {
        activeVm?.dispose()
        activeVm = null
    }

    @Test
    fun testInitialState_Defaults() {
        val vm = SearchViewModel().also { activeVm = it }
        val state = vm.uiState.value

        assertEquals("", state.searchQuery)
        assertNull(state.searchResultsGrouped)
        assertNull(state.addonSearchResults)
        assertFalse(state.isLoadingSearch)
        assertFalse(state.showSuggestions)
        assertTrue(state.searchSuggestions.isEmpty())
        assertNull(state.drilledSearchCategory)
    }

    @Test
    fun testOnSearchQueryChange_UpdatesQuery() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnSearchQueryChange("Interstellar"))
        assertEquals("Interstellar", vm.uiState.value.searchQuery)

        vm.onEvent(SearchUiEvent.OnSearchQueryChange("Dune"))
        assertEquals("Dune", vm.uiState.value.searchQuery)
    }

    @Test
    fun testOnClearSearch_ResetsSearchState() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnSearchQueryChange("Batman"))
        assertEquals("Batman", vm.uiState.value.searchQuery)

        vm.onEvent(SearchUiEvent.OnClearSearch)
        val state = vm.uiState.value
        assertEquals("", state.searchQuery)
        assertNull(state.searchResultsGrouped)
        assertNull(state.addonSearchResults)
        assertFalse(state.isLoadingSearch)
        assertFalse(state.showSuggestions)
        assertTrue(state.searchSuggestions.isEmpty())
    }

    @Test
    fun testOnToggleSearchMode_SwitchesAddonMode() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnToggleSearchMode(false))
        assertFalse(vm.uiState.value.isAddonMode)

        vm.onEvent(SearchUiEvent.OnToggleSearchMode(true))
        assertTrue(vm.uiState.value.isAddonMode)
    }

    @Test
    fun testOnSelectSuggestion_UpdatesQueryAndClosesDropdown() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnSelectSuggestion("Oppenheimer", submitSearch = false))
        assertEquals("Oppenheimer", vm.uiState.value.searchQuery)
        assertFalse(vm.uiState.value.showSuggestions)
    }

    @Test
    fun testOnDismissSuggestions_HidesSuggestions() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnDismissSuggestions)
        assertFalse(vm.uiState.value.showSuggestions)
    }

    @Test
    fun testOnToggleGlobalSearch_UpdatesGlobalFlag() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnToggleGlobalSearch(true))
        assertTrue(vm.uiState.value.isGlobalSearchEnabled)

        vm.onEvent(SearchUiEvent.OnToggleGlobalSearch(false))
        assertFalse(vm.uiState.value.isGlobalSearchEnabled)
    }

    @Test
    fun testOnProviderSelected_UpdatesSingleProviderSelection() {
        val vm = SearchViewModel().also { activeVm = it }

        vm.onEvent(SearchUiEvent.OnProviderSelected("SelectedSingleProvider", "plugin.source"))
        assertEquals("SelectedSingleProvider", vm.uiState.value.selectedProviderName)
        assertEquals("plugin.source", vm.uiState.value.selectedProviderSource)
        assertEquals(setOf("SelectedSingleProvider"), vm.uiState.value.selectedProviderNames)
        assertFalse(vm.uiState.value.isGlobalSearchEnabled)
    }

    @Test
    fun testOnToggleCategory_And_ClearCategories() {
        val vm = SearchViewModel().also { activeVm = it }
        assertTrue(vm.uiState.value.selectedCategories.isEmpty())

        // Toggle Movie on
        vm.onEvent(SearchUiEvent.OnToggleCategory(TvType.Movie))
        assertTrue(vm.uiState.value.selectedCategories.contains(TvType.Movie))

        // Toggle Anime on
        vm.onEvent(SearchUiEvent.OnToggleCategory(TvType.Anime))
        assertEquals(setOf(TvType.Movie, TvType.Anime), vm.uiState.value.selectedCategories)

        // Toggle Movie off
        vm.onEvent(SearchUiEvent.OnToggleCategory(TvType.Movie))
        assertEquals(setOf(TvType.Anime), vm.uiState.value.selectedCategories)

        // Clear all categories
        vm.onEvent(SearchUiEvent.OnClearCategories)
        assertTrue(vm.uiState.value.selectedCategories.isEmpty())
    }

    @Test
    fun testOnDrillSearchCategory_UpdatesDrillCategory() {
        val vm = SearchViewModel().also { activeVm = it }
        assertNull(vm.uiState.value.drilledSearchCategory)

        vm.onEvent(SearchUiEvent.OnDrillSearchCategory("Anime Movies"))
        assertEquals("Anime Movies", vm.uiState.value.drilledSearchCategory)

        vm.onEvent(SearchUiEvent.OnDrillSearchCategory(null))
        assertNull(vm.uiState.value.drilledSearchCategory)
    }
}
