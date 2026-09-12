package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.common.storage.WatchHistory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerViewModelTest {

    private val testApi = object : MainAPI() {
        override var name = "TestPlayerProvider"
        override var mainUrl = "https://test.example.com"
    }

    @Suppress("DEPRECATION")
    private fun createLink(name: String, quality: Int, url: String = "https://test.example.com/$name.m3u8") =
        ExtractorLink(
            source = "TestPlayerProvider",
            name = name,
            url = url,
            referer = "",
            quality = quality,
            type = ExtractorLinkType.M3U8,
        )

    // --- 1. PlayerUiState Initial Defaults ---

    @Test
    fun testPlayerUiState_InitialDefaults() {
        val state = PlayerUiState()

        assertEquals(PlayerPhase.Idle, state.phase)
        assertNull(state.launchData)
        assertNull(state.nextEpisodeError)
        assertTrue(state.nextEpisodeLinks.isEmpty())
        assertTrue(state.nextEpisodeSubtitles.isEmpty())
        assertNull(state.targetEpisodeData)
        assertTrue(state.failedLinks.isEmpty())
        assertNull(state.countdownToNextEpisode)
        assertTrue(state.autoPlayEnabled)

        // Derived properties
        assertNull(state.activeLink)
        assertFalse(state.isProbingOverlay)
        assertFalse(state.isScrapingLinks)
        assertFalse(state.isLoadingNextEpisode)
        assertTrue(state.episodes.isEmpty())
        assertFalse(state.hasNextEpisode)
        assertFalse(state.hasPrevEpisode)
        assertNull(state.nextEpisodeData)
    }

    // --- 2. PlayerPhase Derived Properties Across Pipeline ---

    @Test
    fun testPlayerPhase_DerivedProperties() {
        val linkA = createLink("Stream 1080p", Qualities.P1080.value)

        // Phase: Idle
        val idleState = PlayerUiState(phase = PlayerPhase.Idle)
        assertNull(idleState.activeLink)
        assertFalse(idleState.isProbingOverlay)
        assertFalse(idleState.isScrapingLinks)
        assertFalse(idleState.isLoadingNextEpisode)

        // Phase: Scraping (Initial scrape before any link is returned)
        val scrapingState = PlayerUiState(phase = PlayerPhase.Scraping)
        assertNull(scrapingState.activeLink)
        assertTrue(scrapingState.isProbingOverlay)
        assertTrue(scrapingState.isScrapingLinks)
        assertTrue(scrapingState.isLoadingNextEpisode)

        // Phase: Probing while still scraping
        val probingStillScraping = PlayerUiState(
            phase = PlayerPhase.Probing(link = linkA, stillScraping = true, isInitial = true),
        )
        assertEquals(linkA, probingStillScraping.activeLink)
        assertTrue(probingStillScraping.isProbingOverlay)
        assertTrue(probingStillScraping.isScrapingLinks)
        assertFalse(probingStillScraping.isLoadingNextEpisode)

        // Phase: Probing after scraper completed
        val probingDoneScraping = PlayerUiState(
            phase = PlayerPhase.Probing(link = linkA, stillScraping = false, isInitial = false),
        )
        assertEquals(linkA, probingDoneScraping.activeLink)
        assertTrue(probingDoneScraping.isProbingOverlay)
        assertFalse(probingDoneScraping.isScrapingLinks)
        assertFalse(probingDoneScraping.isLoadingNextEpisode)

        // Phase: Playing while still accumulating background links
        val playingStillScraping = PlayerUiState(
            phase = PlayerPhase.Playing(link = linkA, stillScraping = true),
        )
        assertEquals(linkA, playingStillScraping.activeLink)
        assertFalse(playingStillScraping.isProbingOverlay)
        assertTrue(playingStillScraping.isScrapingLinks)
        assertFalse(playingStillScraping.isLoadingNextEpisode)

        // Phase: Playing steady state
        val playingDoneScraping = PlayerUiState(
            phase = PlayerPhase.Playing(link = linkA, stillScraping = false),
        )
        assertEquals(linkA, playingDoneScraping.activeLink)
        assertFalse(playingDoneScraping.isProbingOverlay)
        assertFalse(playingDoneScraping.isScrapingLinks)
        assertFalse(playingDoneScraping.isLoadingNextEpisode)
    }

    // --- 3. Episode Navigation Indexing Helpers ---

    @Test
    fun testPlayerUiState_EpisodeNavigation() {
        val ep1 = testApi.newEpisode("https://test.example.com/ep_1") {
            name = "Episode 1"
            season = 1
            episode = 1
        }
        val ep2 = testApi.newEpisode("https://test.example.com/ep_2") {
            name = "Episode 2"
            season = 1
            episode = 2
        }
        val ep3 = testApi.newEpisode("https://test.example.com/ep_3") {
            name = "Episode 3"
            season = 1
            episode = 3
        }
        val episodeList = listOf(ep1, ep2, ep3)

        fun createLaunchDataWithCurrentEpisode(currentEpId: String, epNum: Int): VideoLaunchData {
            val history = WatchHistory(
                parentId = "series_100",
                showName = "Test Series",
                showUrl = "https://test.example.com/series/100",
                apiName = "TestPlayerProvider",
                posterUrl = null,
                episodeThumbnailUrl = null,
                screenshotUrl = null,
                episode = epNum,
                season = 1,
                episodeId = currentEpId,
                position = 100L,
                duration = 1800L,
            )
            return VideoLaunchData(
                links = listOf(createLink("Default", Qualities.P1080.value)),
                initialIndex = 0,
                title = "Test Series - S1E$epNum",
                subtitles = emptyList(),
                startPositionMs = 0L,
                history = history,
                episodes = episodeList,
            )
        }

        // 1. At Episode 1 (First episode)
        val stateAtEp1 = PlayerUiState(launchData = createLaunchDataWithCurrentEpisode("https://test.example.com/ep_1", 1))
        assertEquals(3, stateAtEp1.episodes.size)
        assertFalse(stateAtEp1.hasPrevEpisode)
        assertTrue(stateAtEp1.hasNextEpisode)
        assertEquals(ep2, stateAtEp1.nextEpisodeData)

        // 2. At Episode 2 (Middle episode)
        val stateAtEp2 = PlayerUiState(launchData = createLaunchDataWithCurrentEpisode("https://test.example.com/ep_2", 2))
        assertTrue(stateAtEp2.hasPrevEpisode)
        assertTrue(stateAtEp2.hasNextEpisode)
        assertEquals(ep3, stateAtEp2.nextEpisodeData)

        // 3. At Episode 3 (Last episode)
        val stateAtEp3 = PlayerUiState(launchData = createLaunchDataWithCurrentEpisode("https://test.example.com/ep_3", 3))
        assertTrue(stateAtEp3.hasPrevEpisode)
        assertFalse(stateAtEp3.hasNextEpisode)
        assertNull(stateAtEp3.nextEpisodeData)

        // 4. Unknown Episode ID
        val stateAtUnknown = PlayerUiState(launchData = createLaunchDataWithCurrentEpisode("unknown_id", 99))
        assertFalse(stateAtUnknown.hasPrevEpisode)
        assertFalse(stateAtUnknown.hasNextEpisode)
        assertNull(stateAtUnknown.nextEpisodeData)
    }

    // --- 4. QualityDataHelper Priorities and Ranking ---

    @Test
    fun testQualityDataHelper_DefaultPrioritiesHierarchy() {
        val priorities = QualityDataHelper.DEFAULT_QUALITY_PRIORITIES

        val p2160 = priorities[Qualities.P2160.value] ?: 0
        val p1440 = priorities[Qualities.P1440.value] ?: 0
        val p1080 = priorities[Qualities.P1080.value] ?: 0
        val p720 = priorities[Qualities.P720.value] ?: 0
        val p480 = priorities[Qualities.P480.value] ?: 0
        val p360 = priorities[Qualities.P360.value] ?: 0

        assertTrue(p2160 >= p1440, "2160p should rank >= 1440p")
        assertTrue(p1440 >= p1080, "1440p should rank >= 1080p")
        assertTrue(p1080 > p720, "1080p should rank higher than 720p")
        assertTrue(p720 > p480, "720p should rank higher than 480p")
        assertTrue(p480 > p360, "480p should rank higher than 360p")
    }

    @Test
    fun testQualityDataHelper_PreferenceRankingLogic() {
        // Preferred = 1080p: 1080p gets highest score, followed by 1440p/4k, then 720p, then SD
        val rank1080 = QualityDataHelper.getQualityPreferenceRank(Qualities.P1080.value, "1080p")
        val rank4k = QualityDataHelper.getQualityPreferenceRank(Qualities.P2160.value, "1080p")
        val rank720 = QualityDataHelper.getQualityPreferenceRank(Qualities.P720.value, "1080p")
        val rank360 = QualityDataHelper.getQualityPreferenceRank(Qualities.P360.value, "1080p")

        assertEquals(100, rank1080)
        assertEquals(85, rank4k)
        assertEquals(70, rank720)
        assertEquals(20, rank360)
        assertTrue(rank1080 > rank4k)
        assertTrue(rank4k > rank720)
        assertTrue(rank720 > rank360)

        // Preferred = 4K: 4K gets highest score
        val rank4kUnder4k = QualityDataHelper.getQualityPreferenceRank(Qualities.P2160.value, "4k")
        val rank1080Under4k = QualityDataHelper.getQualityPreferenceRank(Qualities.P1080.value, "4k")
        val rank720Under4k = QualityDataHelper.getQualityPreferenceRank(Qualities.P720.value, "4k")

        assertEquals(100, rank4kUnder4k)
        assertEquals(80, rank1080Under4k)
        assertEquals(60, rank720Under4k)
        assertTrue(rank4kUnder4k > rank1080Under4k)
        assertTrue(rank1080Under4k > rank720Under4k)
    }

    // --- 5. PlayerPhase Retry and Initial States ---

    @Test
    fun testPlayerPhase_ProbingFlags() {
        val link = createLink("Test Link", Qualities.P1080.value)

        val initialProbe = PlayerPhase.Probing(link, stillScraping = true, isInitial = true, isRetry = false)
        assertTrue(initialProbe.isInitial)
        assertFalse(initialProbe.isRetry)
        assertTrue(initialProbe.stillScraping)

        val retryProbe = PlayerPhase.Probing(link, stillScraping = false, isInitial = false, isRetry = true)
        assertFalse(retryProbe.isInitial)
        assertTrue(retryProbe.isRetry)
        assertFalse(retryProbe.stillScraping)
    }
}
