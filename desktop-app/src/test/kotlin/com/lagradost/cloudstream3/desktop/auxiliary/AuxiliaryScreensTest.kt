package com.lagradost.cloudstream3.desktop.auxiliary

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.CategoryGridCache
import com.lagradost.cloudstream3.desktop.ui.screens.details.FullCastCategory
import com.lagradost.cloudstream3.desktop.ui.screens.details.FullCastViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.FullCastUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.person.PersonUiState
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.FilmographyCategory
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit
import com.lagradost.cloudstream3.desktop.ui.screens.studio.StudioUiState
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioCategory
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuxiliaryScreensTest {

    // --- CategoryGridCache Tests ---

    @Test
    fun testCategoryGridCache_PutGetRemove() {
        val dummyItems = emptyList<SearchResponse>()
        CategoryGridCache.put("ProviderX", "Anime", dummyItems)

        val retrieved = CategoryGridCache.get("ProviderX", "Anime")
        assertNotNull(retrieved)
        assertEquals(0, retrieved.size)

        CategoryGridCache.remove("ProviderX", "Anime")
        assertNull(CategoryGridCache.get("ProviderX", "Anime"))
    }

    @Test
    fun testCategoryGridCache_LruEvictionBoundary() {
        // Cache is bounded to MAX_ENTRIES = 30
        for (i in 1..35) {
            CategoryGridCache.put("TestProvider", "Category$i", emptyList())
        }

        // Oldest entries (1 to 5) should be evicted
        assertNull(CategoryGridCache.get("TestProvider", "Category1"))
        assertNull(CategoryGridCache.get("TestProvider", "Category2"))
        assertNull(CategoryGridCache.get("TestProvider", "Category3"))
        assertNull(CategoryGridCache.get("TestProvider", "Category4"))
        assertNull(CategoryGridCache.get("TestProvider", "Category5"))

        // Latest entries (6 to 35) should be present
        for (i in 6..35) {
            assertNotNull(CategoryGridCache.get("TestProvider", "Category$i"))
        }
    }

    // --- PersonModels & PersonUiState Tests ---

    @Test
    fun testPersonUiState_Defaults() {
        val state = PersonUiState()
        assertTrue(state.isLoading)
        assertNull(state.personDetail)
        assertEquals(FilmographyCategory.ALL, state.selectedCategory)
        assertNull(state.selectedCreditForMatch)
        assertTrue(state.providerMatches.isEmpty())
        assertTrue(!state.isSearchingProviders)
        assertNull(state.error)
    }

    @Test
    fun testPersonDetail_AllCredits_SortedByPopularity() {
        val movieCredit1 = PersonMediaCredit(
            tmdbId = 1,
            title = "Movie A",
            posterUrl = null,
            backdropUrl = null,
            releaseYear = "2020",
            characterOrJob = "Lead Actor",
            mediaType = TvType.Movie,
            voteAverage = 7.5,
            popularity = 50.0,
        )
        val movieCredit2 = PersonMediaCredit(
            tmdbId = 2,
            title = "Movie B",
            posterUrl = null,
            backdropUrl = null,
            releaseYear = "2022",
            characterOrJob = "Supporting Actor",
            mediaType = TvType.Movie,
            voteAverage = 8.0,
            popularity = 95.0,
        )
        val tvCredit = PersonMediaCredit(
            tmdbId = 3,
            title = "Series C",
            posterUrl = null,
            backdropUrl = null,
            releaseYear = "2021",
            characterOrJob = "Recurring",
            mediaType = TvType.TvSeries,
            voteAverage = 6.5,
            popularity = 70.0,
        )

        val detail = PersonDetail(
            tmdbId = 100,
            name = "Famous Actor",
            biography = "Bio text",
            birthday = "1980-01-01",
            deathday = null,
            placeOfBirth = "City",
            profileUrl = null,
            knownForDepartment = "Acting",
            movieCredits = listOf(movieCredit1, movieCredit2),
            tvCredits = listOf(tvCredit),
        )

        assertEquals(2, detail.movieCredits.size)
        assertEquals(1, detail.tvCredits.size)
        assertEquals(3, detail.allCredits.size)

        // allCredits must be sorted by popularity descending: Movie B (95.0), Series C (70.0), Movie A (50.0)
        assertEquals("Movie B", detail.allCredits[0].title)
        assertEquals("Series C", detail.allCredits[1].title)
        assertEquals("Movie A", detail.allCredits[2].title)
    }

    // --- StudioModels & StudioUiState Tests ---

    @Test
    fun testStudioUiState_Defaults() {
        val state = StudioUiState()
        assertTrue(state.isLoading)
        assertNull(state.studioDetail)
        assertEquals(StudioCategory.ALL, state.selectedCategory)
        assertNull(state.selectedItemForMatch)
        assertTrue(state.providerMatches.isEmpty())
        assertTrue(!state.isSearchingProviders)
        assertNull(state.error)
    }

    @Test
    fun testStudioDetail_AllTitles_SortedByPopularity() {
        val movie1 = StudioMediaItem(
            tmdbId = 10,
            title = "Studio Movie A",
            posterUrl = null,
            backdropUrl = null,
            releaseYear = "2019",
            mediaType = TvType.Movie,
            voteAverage = 7.0,
            popularity = 30.0,
        )
        val tv1 = StudioMediaItem(
            tmdbId = 20,
            title = "Studio Series B",
            posterUrl = null,
            backdropUrl = null,
            releaseYear = "2023",
            mediaType = TvType.TvSeries,
            voteAverage = 8.5,
            popularity = 85.0,
        )

        val detail = StudioDetail(
            id = 500,
            name = "Major Studio",
            description = "Studio description",
            headquarters = "Location",
            originCountry = "US",
            homepage = null,
            logoUrl = null,
            movieTitles = listOf(movie1),
            tvTitles = listOf(tv1),
        )

        assertEquals(2, detail.allTitles.size)
        assertEquals("Studio Series B", detail.allTitles[0].title)
        assertEquals("Studio Movie A", detail.allTitles[1].title)
    }

    // --- FullCastViewModel & State Derivation Tests ---

    private fun actor(name: String, role: String? = null) = ActorData(
        actor = Actor(name, null),
        roleString = role,
        role = null,
        voiceActor = null,
    )

    @Test
    fun testFullCastViewModel_CategorizationAndCounts() {
        val config = Config.FullCast(
            mediaTitle = "Inception",
            cast = listOf(actor("Leonardo DiCaprio", "Cobb"), actor("Joseph Gordon-Levitt", "Arthur")),
            directors = listOf(actor("Christopher Nolan", "Director")),
            writers = listOf(actor("Christopher Nolan", "Writer")),
            producers = listOf(actor("Emma Thomas", "Producer")),
        )

        val viewModel = FullCastViewModel(config)
        val state = viewModel.uiState.value

        assertEquals(2, state.castCount)
        assertEquals(1, state.directorsCount)
        assertEquals(1, state.writersCount)
        assertEquals(1, state.producersCount)
        // Christopher Nolan appears in directors and writers, but unique member key (name + roleString) separates them
        assertEquals(5, state.allMembers.size)
        assertEquals(FullCastCategory.ALL, state.selectedCategory)
    }

    @Test
    fun testFullCastViewModel_FilterByCategory() {
        val config = Config.FullCast(
            mediaTitle = "Interstellar",
            cast = listOf(actor("Matthew McConaughey", "Cooper")),
            directors = listOf(actor("Christopher Nolan", "Director")),
            writers = listOf(actor("Jonathan Nolan", "Writer")),
            producers = listOf(actor("Emma Thomas", "Producer")),
        )

        val viewModel = FullCastViewModel(config)

        viewModel.onEvent(FullCastUiEvent.OnSelectCategory(FullCastCategory.CAST))
        var state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Matthew McConaughey", state.filteredMembers.first().actor.name)

        viewModel.onEvent(FullCastUiEvent.OnSelectCategory(FullCastCategory.DIRECTORS))
        state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Christopher Nolan", state.filteredMembers.first().actor.name)

        viewModel.onEvent(FullCastUiEvent.OnSelectCategory(FullCastCategory.WRITERS))
        state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Jonathan Nolan", state.filteredMembers.first().actor.name)

        viewModel.onEvent(FullCastUiEvent.OnSelectCategory(FullCastCategory.PRODUCERS))
        state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Emma Thomas", state.filteredMembers.first().actor.name)
    }

    @Test
    fun testFullCastViewModel_SearchQueryMatching() {
        val config = Config.FullCast(
            mediaTitle = "The Dark Knight",
            cast = listOf(
                actor("Christian Bale", "Bruce Wayne / Batman"),
                actor("Heath Ledger", "Joker"),
                actor("Michael Caine", "Alfred"),
            ),
            directors = listOf(actor("Christopher Nolan", "Director")),
        )

        val viewModel = FullCastViewModel(config)

        // Search by actor name
        viewModel.onEvent(FullCastUiEvent.OnUpdateSearchQuery("Heath"))
        var state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Heath Ledger", state.filteredMembers.first().actor.name)

        // Search by character name
        viewModel.onEvent(FullCastUiEvent.OnUpdateSearchQuery("Batman"))
        state = viewModel.uiState.value
        assertEquals(1, state.filteredMembers.size)
        assertEquals("Christian Bale", state.filteredMembers.first().actor.name)

        // Empty query restores full category list
        viewModel.onEvent(FullCastUiEvent.OnUpdateSearchQuery(""))
        state = viewModel.uiState.value
        assertEquals(4, state.filteredMembers.size)
    }
}
