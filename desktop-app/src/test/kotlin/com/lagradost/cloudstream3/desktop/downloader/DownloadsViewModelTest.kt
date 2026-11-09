package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.cloudstream3.desktop.ui.screens.downloads.DownloadsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsTab
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsViewModelTest {

    private var activeVm: DownloadsViewModel? = null

    @AfterEach
    fun tearDown() {
        activeVm?.dispose()
        activeVm = null
    }

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

    // --- 1. State Defaults & Pure Data Class Tests ---

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
        val paused = createSampleTask("2", "Show B", status = DownloadStatus.PAUSED)
        val failed = createSampleTask("3", "Show C", status = DownloadStatus.FAILED)
        val completed = createSampleTask("4", "Show D", status = DownloadStatus.COMPLETED)

        val state = DownloadsUiState(tasks = listOf(downloading, paused, failed, completed))
        assertEquals(2, state.activeTasks.size)
        assertTrue(state.activeTasks.any { it.id == "1" })
        assertTrue(state.activeTasks.any { it.id == "2" })
        assertFalse(state.activeTasks.any { it.id == "3" })
        assertFalse(state.activeTasks.any { it.id == "4" })
    }

    @Test
    fun testCompletedTasks_Filter() {
        val downloading = createSampleTask("1", "Show A", status = DownloadStatus.DOWNLOADING)
        val completed1 = createSampleTask("2", "Show B", status = DownloadStatus.COMPLETED)
        val completed2 = createSampleTask("3", "Show C", status = DownloadStatus.COMPLETED)

        val state = DownloadsUiState(tasks = listOf(downloading, completed1, completed2))
        assertEquals(2, state.completedTasks.size)
        assertTrue(state.completedTasks.any { it.id == "2" })
        assertTrue(state.completedTasks.any { it.id == "3" })
        assertFalse(state.completedTasks.any { it.id == "1" })
    }

    @Test
    fun testFilteredCompletedTasks_AllTab_NoSearch() {
        val t1 = createSampleTask("1", "Show A", season = 1, episode = 1)
        val t2 = createSampleTask("2", "Show B", season = 1, episode = 2)

        val state = DownloadsUiState(
            tasks = listOf(t1, t2),
            activeTab = DownloadsTab.ALL,
            searchQuery = "",
        )
        assertEquals(2, state.filteredCompletedTasks.size)
    }

    @Test
    fun testFilteredCompletedTasks_ShowsTab() {
        val series = createSampleTask("1", "Series Show", season = 1, episode = 1)
        val movie = createSampleTask("2", "Movie Show", season = null, episode = null)

        val state = DownloadsUiState(
            tasks = listOf(series, movie),
            activeTab = DownloadsTab.SHOWS,
            searchQuery = "",
        )
        assertEquals(1, state.filteredCompletedTasks.size)
        assertEquals("1", state.filteredCompletedTasks.first().id)
    }

    @Test
    fun testFilteredCompletedTasks_MoviesTab() {
        val series = createSampleTask("1", "Series Show", season = 1, episode = 1)
        val movie = createSampleTask("2", "Movie Show", season = null, episode = null)

        val state = DownloadsUiState(
            tasks = listOf(series, movie),
            activeTab = DownloadsTab.MOVIES,
            searchQuery = "",
        )
        assertEquals(1, state.filteredCompletedTasks.size)
        assertEquals("2", state.filteredCompletedTasks.first().id)
    }

    @Test
    fun testFilteredCompletedTasks_SearchQuery_CaseInsensitive() {
        val t1 = createSampleTask("1", "Cyberpunk Edgerunners", episodeTitle = "Let You Down")
        val t2 = createSampleTask("2", "Attack on Titan", episodeTitle = "To You, 2000 Years")
        val t3 = createSampleTask("3", "Blade Runner 2049", season = null, episode = null)

        val state = DownloadsUiState(
            tasks = listOf(t1, t2, t3),
            activeTab = DownloadsTab.ALL,
        )

        // Search by show name
        val searchCyber = state.copy(searchQuery = "cyber")
        assertEquals(1, searchCyber.filteredCompletedTasks.size)
        assertEquals("1", searchCyber.filteredCompletedTasks.first().id)

        // Search by episode title
        val searchEp = state.copy(searchQuery = "2000 Years")
        assertEquals(1, searchEp.filteredCompletedTasks.size)
        assertEquals("2", searchEp.filteredCompletedTasks.first().id)

        // Search movie
        val searchMovie = state.copy(searchQuery = "blade")
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

    // --- 2. Real DownloadsViewModel MVI Event Handling Tests ---

    @Test
    fun testDownloadsViewModel_SelectTab() {
        val vm = DownloadsViewModel().also { activeVm = it }
        assertEquals(DownloadsTab.ALL, vm.uiState.value.activeTab)

        vm.onEvent(DownloadsUiEvent.SelectTab(DownloadsTab.SHOWS))
        assertEquals(DownloadsTab.SHOWS, vm.uiState.value.activeTab)

        vm.onEvent(DownloadsUiEvent.SelectTab(DownloadsTab.MOVIES))
        assertEquals(DownloadsTab.MOVIES, vm.uiState.value.activeTab)

        vm.onEvent(DownloadsUiEvent.SelectTab(DownloadsTab.ACTIVE_QUEUE))
        assertEquals(DownloadsTab.ACTIVE_QUEUE, vm.uiState.value.activeTab)
    }

    @Test
    fun testDownloadsViewModel_UpdateSearchQuery() {
        val vm = DownloadsViewModel().also { activeVm = it }
        assertEquals("", vm.uiState.value.searchQuery)

        vm.onEvent(DownloadsUiEvent.UpdateSearchQuery("Lord of the Rings"))
        assertEquals("Lord of the Rings", vm.uiState.value.searchQuery)

        vm.onEvent(DownloadsUiEvent.UpdateSearchQuery(""))
        assertEquals("", vm.uiState.value.searchQuery)
    }

    @Test
    fun testDownloadsViewModel_ToggleSettingsDialog() {
        val vm = DownloadsViewModel().also { activeVm = it }
        assertFalse(vm.uiState.value.isSettingsOpen)

        vm.onEvent(DownloadsUiEvent.ToggleSettingsDialog(true))
        assertTrue(vm.uiState.value.isSettingsOpen)

        vm.onEvent(DownloadsUiEvent.ToggleSettingsDialog(false))
        assertFalse(vm.uiState.value.isSettingsOpen)
    }

    @Test
    fun testDownloadsViewModel_DismissJunkMessage() {
        val vm = DownloadsViewModel().also { activeVm = it }

        vm.onEvent(DownloadsUiEvent.DismissJunkMessage)
        assertNull(vm.uiState.value.reclaimedBytesMessage)
    }

    @Test
    fun testDownloadsViewModel_UpdateDownloadThreads() {
        val vm = DownloadsViewModel().also { activeVm = it }

        vm.onEvent(DownloadsUiEvent.UpdateDownloadThreads(16f))
        assertEquals(16f, vm.uiState.value.downloadThreads)

        vm.onEvent(DownloadsUiEvent.UpdateDownloadThreads(4f))
        assertEquals(4f, vm.uiState.value.downloadThreads)
    }

    @Test
    fun testDownloadsViewModel_UpdateMaxConcurrent() {
        val vm = DownloadsViewModel().also { activeVm = it }

        vm.onEvent(DownloadsUiEvent.UpdateMaxConcurrent(5f))
        assertEquals(5f, vm.uiState.value.maxConcurrent)

        vm.onEvent(DownloadsUiEvent.UpdateMaxConcurrent(1f))
        assertEquals(1f, vm.uiState.value.maxConcurrent)
    }
}
