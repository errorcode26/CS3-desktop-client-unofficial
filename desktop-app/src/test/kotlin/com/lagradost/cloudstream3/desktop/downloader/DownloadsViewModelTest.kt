package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsTab
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsViewModelTest {

    private fun createSampleTask(
        id: String,
        showName: String,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        filePath: String = "/downloads/$id.mkv",
        status: DownloadStatus = DownloadStatus.COMPLETED,
        totalBytes: Long = 1_000_000L,
        downloadedBytes: Long = 1_000_000L,
        existsOnDisk: Boolean = true,
    ): DownloadTask {
        return DownloadTask(
            id = id,
            canonicalKey = "test-$id",
            showName = showName,
            showUrl = "https://example.com/$id",
            season = season,
            episode = episode,
            episodeTitle = episodeTitle,
            filePath = filePath,
            streamUrl = "https://example.com/stream/$id.mkv",
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            status = status,
            existsOnDisk = existsOnDisk,
        )
    }

    @Test
    fun testInitialState_Defaults() {
        val state = DownloadsUiState()
        assertEquals(DownloadsTab.ALL, state.activeTab)
        assertEquals("", state.searchQuery)
        assertTrue(state.tasks.isEmpty())
        assertTrue(state.activeTasks.isEmpty())
        assertTrue(state.completedTasks.isEmpty())
        assertTrue(state.filteredCompletedTasks.isEmpty())
        assertEquals(0L, state.totalActiveSpeed)
        assertFalse(state.isSettingsOpen)
        assertNull(state.reclaimedBytesMessage)
        assertEquals(8f, state.downloadThreads)
        assertEquals(2f, state.maxConcurrent)
    }

    @Test
    fun testActiveTasks_Filter() {
        val downloading = createSampleTask("1", "Show A", status = DownloadStatus.DOWNLOADING)
        val queued = createSampleTask("2", "Show B", status = DownloadStatus.QUEUED)
        val paused = createSampleTask("3", "Show C", status = DownloadStatus.PAUSED)
        val completed = createSampleTask("4", "Show D", status = DownloadStatus.COMPLETED)
        val failed = createSampleTask("5", "Show E", status = DownloadStatus.FAILED)
        val cancelled = createSampleTask("6", "Show F", status = DownloadStatus.CANCELLED)

        val state = DownloadsUiState(
            tasks = listOf(downloading, queued, paused, completed, failed, cancelled)
        )

        val active = state.activeTasks
        assertEquals(3, active.size)
        assertTrue(active.any { it.id == "1" })
        assertTrue(active.any { it.id == "2" })
        assertTrue(active.any { it.id == "3" })
        assertFalse(active.any { it.id in listOf("4", "5", "6") })
    }

    @Test
    fun testCompletedTasks_RequiresCompletedAndExistsOnDisk() {
        val completedOnDisk = createSampleTask("1", "Show A", status = DownloadStatus.COMPLETED, existsOnDisk = true)
        val completedMissingDisk = createSampleTask("2", "Show B", status = DownloadStatus.COMPLETED, existsOnDisk = false)
        val downloadingOnDisk = createSampleTask("3", "Show C", status = DownloadStatus.DOWNLOADING, existsOnDisk = true)

        val state = DownloadsUiState(
            tasks = listOf(completedOnDisk, completedMissingDisk, downloadingOnDisk)
        )

        val completed = state.completedTasks
        assertEquals(1, completed.size)
        assertEquals("1", completed.first().id)
    }

    @Test
    fun testCompletedTasks_DistinctByFilePath() {
        val task1 = createSampleTask("1", "Show A", filePath = "/downloads/duplicate.mkv")
        val task2 = createSampleTask("2", "Show A (Re-download)", filePath = "/downloads/duplicate.mkv")
        val task3 = createSampleTask("3", "Show B", filePath = "/downloads/unique.mkv")

        val state = DownloadsUiState(
            tasks = listOf(task1, task2, task3)
        )

        val completed = state.completedTasks
        assertEquals(2, completed.size)
        assertTrue(completed.any { it.id == "1" })
        assertTrue(completed.any { it.id == "3" })
    }

    @Test
    fun testTabFiltering_ShowsAndMovies() {
        val movie = createSampleTask("1", "Inception", season = null, episode = null)
        val series = createSampleTask("2", "Arcane", season = 1, episode = 1, episodeTitle = "Welcome")

        val stateAll = DownloadsUiState(tasks = listOf(movie, series), activeTab = DownloadsTab.ALL)
        assertEquals(2, stateAll.filteredCompletedTasks.size)

        val stateShows = DownloadsUiState(tasks = listOf(movie, series), activeTab = DownloadsTab.SHOWS)
        val showResults = stateShows.filteredCompletedTasks
        assertEquals(1, showResults.size)
        assertEquals("2", showResults.first().id)

        val stateMovies = DownloadsUiState(tasks = listOf(movie, series), activeTab = DownloadsTab.MOVIES)
        val movieResults = stateMovies.filteredCompletedTasks
        assertEquals(1, movieResults.size)
        assertEquals("1", movieResults.first().id)
    }

    @Test
    fun testSearchQueryFiltering_ShowNameAndEpisodeTitle() {
        val show1 = createSampleTask("1", "Breaking Bad", season = 5, episode = 14, episodeTitle = "Ozymandias")
        val show2 = createSampleTask("2", "Better Call Saul", season = 1, episode = 1, episodeTitle = "Uno")
        val movie = createSampleTask("3", "El Camino", season = null, episode = null)

        val state = DownloadsUiState(tasks = listOf(show1, show2, movie))

        // Search by show name
        val searchShow = state.copy(searchQuery = "breaking")
        assertEquals(1, searchShow.filteredCompletedTasks.size)
        assertEquals("1", searchShow.filteredCompletedTasks.first().id)

        // Search by episode title
        val searchEpisode = state.copy(searchQuery = "ozymandias")
        assertEquals(1, searchEpisode.filteredCompletedTasks.size)
        assertEquals("1", searchEpisode.filteredCompletedTasks.first().id)

        // Case-insensitive movie match
        val searchMovie = state.copy(searchQuery = "CAMINO")
        assertEquals(1, searchMovie.filteredCompletedTasks.size)
        assertEquals("3", searchMovie.filteredCompletedTasks.first().id)

        // No match
        val searchNone = state.copy(searchQuery = "NonExistent")
        assertTrue(searchNone.filteredCompletedTasks.isEmpty())
    }

    @Test
    fun testDownloadTask_ProgressAndTitleCalculations() {
        val taskHalfway = createSampleTask(
            id = "1",
            showName = "Cyberpunk",
            season = 1,
            episode = 10,
            episodeTitle = "My Way",
            totalBytes = 2_000_000L,
            downloadedBytes = 1_000_000L,
        )

        assertEquals(0.5f, taskHalfway.progressPercent)
        assertFalse(taskHalfway.isMovie)
        assertEquals("Cyberpunk • S1 E10 - My Way", taskHalfway.displayTitle)

        val taskZeroTotal = taskHalfway.copy(totalBytes = 0L, downloadedBytes = 500L)
        assertEquals(0f, taskZeroTotal.progressPercent)

        val movieTask = createSampleTask(
            id = "2",
            showName = "Interstellar",
            season = null,
            episode = null,
        )
        assertTrue(movieTask.isMovie)
        assertEquals("Interstellar", movieTask.displayTitle)
    }

    @Test
    fun testSanitizeFileName_IllegalCharacters() {
        val raw = "Movie: Title / Subtitle * Special? <Edition> | Quality \"1080p\""
        val sanitized = DesktopDownloadManager.sanitizeFileName(raw)

        // All reserved characters should be replaced with hyphens and spaces normalized
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("?"))
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertFalse(sanitized.contains("|"))
        assertFalse(sanitized.contains("\""))

        val trailingDots = "Clean Title...   "
        assertEquals("Clean Title", DesktopDownloadManager.sanitizeFileName(trailingDots))
    }
}
