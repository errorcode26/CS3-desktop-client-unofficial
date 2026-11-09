package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailsViewModelTest {

    private var activeVm: DetailsViewModel? = null

    @AfterEach
    fun tearDown() {
        activeVm?.dispose()
        activeVm = null
    }

    private val testApi = object : MainAPI() {
        override var name = "TestDetailsProvider"
        override var mainUrl = "https://details.example.com"
    }

    @Test
    fun testInitialState_WithPreloadedData() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
            preloadedName = "Cyberpunk 2077",
            preloadedPoster = "https://details.example.com/poster.jpg",
            preloadedBg = "https://details.example.com/backdrop.jpg",
            initialSeason = 2,
        ).also { activeVm = it }

        val state = vm.uiState.value
        assertEquals("Cyberpunk 2077", state.preloadedName)
        assertEquals(2, state.selectedSeason)
        assertTrue(state.hasUserSelectedSeason)
        assertTrue(state.isLoading)
        assertFalse(state.isPanelOpen)
        assertNull(state.playbackError)
    }

    @Test
    fun testOnSelectSeason_UpdatesSelection() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
            initialSeason = 1,
        ).also { activeVm = it }

        assertEquals(1, vm.uiState.value.selectedSeason)

        vm.onEvent(DetailsUiEvent.OnSelectSeason(4))
        assertEquals(4, vm.uiState.value.selectedSeason)
        assertTrue(vm.uiState.value.hasUserSelectedSeason)

        vm.onEvent(DetailsUiEvent.OnSelectSeason(null))
        assertNull(vm.uiState.value.selectedSeason)
    }

    @Test
    fun testOnOpenAndCloseLinksPanel_TogglesPanelState() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
        ).also { activeVm = it }

        assertFalse(vm.uiState.value.isPanelOpen)
        assertNull(vm.uiState.value.activeLinkData)

        val history = WatchHistory(
            parentId = "https://details.example.com/show/42",
            showName = "Cyberpunk",
            showUrl = "https://details.example.com/show/42",
            apiName = testApi.name,
            posterUrl = null,
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 1,
            season = 1,
            episodeId = "ep1",
            position = 0L,
            duration = 1000L,
            updateTime = 1000L,
        )
        val data = Triple(testApi, "ep1_data", history)

        vm.onEvent(DetailsUiEvent.OnOpenLinksPanel(data))
        assertTrue(vm.uiState.value.isPanelOpen)
        assertEquals(data, vm.uiState.value.activeLinkData)

        vm.onEvent(DetailsUiEvent.OnCloseLinksPanel)
        assertFalse(vm.uiState.value.isPanelOpen)
    }

    @Test
    fun testOnPlaybackError_ShowAndDismiss() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
        ).also { activeVm = it }

        assertNull(vm.uiState.value.playbackError)

        vm.onEvent(DetailsUiEvent.OnShowPlaybackError("Stream 404 Not Found"))
        assertEquals("Stream 404 Not Found", vm.uiState.value.playbackError)

        vm.onEvent(DetailsUiEvent.OnDismissPlaybackError)
        assertNull(vm.uiState.value.playbackError)
    }

    @Test
    fun testOnTogglePinRightColumn_InvertsPinnedState() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
        ).also { activeVm = it }

        val initialPinned = vm.uiState.value.isRightColumnPinned
        vm.onEvent(DetailsUiEvent.OnTogglePinRightColumn)
        assertEquals(!initialPinned, vm.uiState.value.isRightColumnPinned)

        vm.onEvent(DetailsUiEvent.OnTogglePinRightColumn)
        assertEquals(initialPinned, vm.uiState.value.isRightColumnPinned)
    }

    @Test
    fun testOnToggleEpisodesStackedView_And_SetEpisodeViewMode() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
        ).also { activeVm = it }

        vm.onEvent(DetailsUiEvent.OnToggleEpisodesStackedView(true))
        assertTrue(vm.uiState.value.isEpisodesStackedView)

        vm.onEvent(DetailsUiEvent.OnToggleEpisodesStackedView(false))
        assertFalse(vm.uiState.value.isEpisodesStackedView)

        vm.onEvent(DetailsUiEvent.OnSetEpisodeViewMode(2))
        assertEquals(2, vm.uiState.value.episodeViewMode)
    }

    @Test
    fun testOnMarkAutoPlayHandled() {
        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
        ).also { activeVm = it }

        assertFalse(vm.uiState.value.hasAutoPlayed)
        vm.onEvent(DetailsUiEvent.OnMarkAutoPlayHandled)
        assertTrue(vm.uiState.value.hasAutoPlayed)
    }

    @Test
    fun testOnToggleEpisodeWatched_OptimisticStateUpdates() = runBlocking {
        val seriesResponse = testApi.newTvSeriesLoadResponse("Test Show", "https://details.example.com/show/42", TvType.TvSeries, emptyList())

        val vm = DetailsViewModel(
            provider = testApi,
            url = "https://details.example.com/show/42",
            cachedResponse = seriesResponse,
        ).also { activeVm = it }

        val ep = testApi.newEpisode("ep_payload_1") {
            this.name = "Episode 1"
            this.season = 1
            this.episode = 1
        }

        // Initially not in watch history
        assertTrue(vm.uiState.value.watchHistory.isEmpty())

        // Mark watched
        vm.onEvent(DetailsUiEvent.OnToggleEpisodeWatched(ep, isWatched = true))
        val entry = vm.uiState.value.watchHistory[ep.data]
        assertNotNull(entry)
        assertEquals(ep.data, entry.episodeId)
        assertEquals(entry.duration, entry.position) // Should be marked completed

        // Mark unwatched
        vm.onEvent(DetailsUiEvent.OnToggleEpisodeWatched(ep, isWatched = false))
        assertNull(vm.uiState.value.watchHistory[ep.data])
    }
}
