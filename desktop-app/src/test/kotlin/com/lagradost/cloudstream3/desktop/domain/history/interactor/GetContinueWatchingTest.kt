package com.lagradost.cloudstream3.desktop.domain.history.interactor

import com.lagradost.common.storage.WatchHistory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GetContinueWatchingTest {

    private fun createHistory(
        parentId: String,
        showName: String = "Test Show",
        apiName: String = "TestProvider",
        episode: Int? = 1,
        season: Int? = 1,
        position: Long = 0L,
        duration: Long = 1000L,
        updateTime: Long = 1000L,
    ) = WatchHistory(
        parentId = parentId,
        showName = showName,
        showUrl = "https://example.com/$parentId",
        apiName = apiName,
        posterUrl = "https://example.com/$parentId/poster.jpg",
        episodeThumbnailUrl = null,
        screenshotUrl = null,
        episode = episode,
        season = season,
        episodeId = "$parentId-ep-$episode",
        position = position,
        duration = duration,
        updateTime = updateTime,
    )

    @Test
    fun testIgnoresOfflineAndLocalEntries() {
        val input = listOf(
            createHistory(parentId = "offline-1", apiName = "Offline"),
            createHistory(parentId = "offline-2", apiName = "TestProvider"),
            createHistory(parentId = "local", apiName = "TestProvider"),
            createHistory(parentId = "valid-show", position = 500L, duration = 1000L),
        )

        val result = GetContinueWatching.filterContinueWatching(input)
        assertEquals(1, result.size)
        assertEquals("valid-show", result[0].parentId)
    }

    @Test
    fun testDiscardsShowsWithNoProgressAndNoCompletion() {
        val input = listOf(
            createHistory(parentId = "zero-progress", position = 0L, duration = 0L),
            createHistory(parentId = "active-show", position = 300L, duration = 1000L),
        )

        val result = GetContinueWatching.filterContinueWatching(input)
        assertEquals(1, result.size)
        assertEquals("active-show", result[0].parentId)
    }

    @Test
    fun testAdvancesCompletedEpisodeToNext() {
        // 96% watched is considered completed by PlayerLinkHandler
        val input = listOf(
            createHistory(
                parentId = "series-1",
                episode = 3,
                season = 1,
                position = 960L,
                duration = 1000L,
                updateTime = 2000L,
            ),
        )

        val result = GetContinueWatching.filterContinueWatching(input)
        assertEquals(1, result.size)
        val advanced = result[0]
        assertEquals(4, advanced.episode)
        assertEquals(0L, advanced.position)
        assertEquals(0L, advanced.duration)
    }

    @Test
    fun testReturnsLatestInProgressEpisode() {
        val input = listOf(
            // Episode 1 completed earlier
            createHistory(
                parentId = "series-1",
                episode = 1,
                position = 960L,
                duration = 1000L,
                updateTime = 1000L,
            ),
            // Episode 2 in progress currently
            createHistory(
                parentId = "series-1",
                episode = 2,
                position = 400L,
                duration = 1000L,
                updateTime = 2000L,
            ),
        )

        val result = GetContinueWatching.filterContinueWatching(input)
        assertEquals(1, result.size)
        assertEquals(2, result[0].episode)
        assertEquals(400L, result[0].position)
    }

    @Test
    fun testSortsByUpdateTimeDescending() {
        val input = listOf(
            createHistory(parentId = "older", position = 200L, updateTime = 1000L),
            createHistory(parentId = "newer", position = 200L, updateTime = 5000L),
            createHistory(parentId = "middle", position = 200L, updateTime = 3000L),
        )

        val result = GetContinueWatching.filterContinueWatching(input)
        assertEquals(3, result.size)
        assertEquals("newer", result[0].parentId)
        assertEquals("middle", result[1].parentId)
        assertEquals("older", result[2].parentId)
    }
}
