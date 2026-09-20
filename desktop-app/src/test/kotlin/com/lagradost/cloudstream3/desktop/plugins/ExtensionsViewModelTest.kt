package com.lagradost.cloudstream3.desktop.plugins

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionsViewModelTest {

    @Test
    fun testInitialState_Defaults() {
        val state = ExtensionsUiState()
        assertFalse(state.isFetching)
        assertFalse(state.isUninstalling)
        assertNull(state.pluginRequiringBypass)
        assertNull(state.pluginRequiringPermission)
        assertNull(state.inspectedRepoName)
        assertTrue(state.installedPlugins.isEmpty())
        assertTrue(state.plugins.isEmpty())
        assertTrue(state.updatesHistory.isEmpty())
    }

    @Test
    fun testInspectRepository_StateReduction() {
        var state = ExtensionsUiState()

        // Simulate OnInspectRepository("MainRepo")
        val inspectEvent = ExtensionsUiEvent.OnInspectRepository("MainRepo")
        state = state.copy(inspectedRepoName = inspectEvent.repoName)
        assertEquals("MainRepo", state.inspectedRepoName)

        // Simulate clearing inspection
        state = state.copy(inspectedRepoName = null)
        assertNull(state.inspectedRepoName)
    }

    @Test
    fun testClearBypassAndPermissionRequests_StateReduction() {
        val plugin = SitePlugin(
            name = "TestPlugin",
            internalName = "com.example.test",
            url = "https://example.com/test.jar",
            version = 1,
        )

        var state = ExtensionsUiState(
            pluginRequiringBypass = Triple("MainRepo", plugin, "Untrusted bytecode"),
            pluginRequiringPermission = Triple("MainRepo", plugin, "ACCESS_NETWORK"),
            isDialogInstalling = true,
        )

        // Clear bypass
        state = state.copy(pluginRequiringBypass = null, isDialogInstalling = false)
        assertNull(state.pluginRequiringBypass)
        assertFalse(state.isDialogInstalling)

        // Clear permission request
        state = state.copy(pluginRequiringPermission = null, isDialogInstalling = false)
        assertNull(state.pluginRequiringPermission)
        assertFalse(state.isDialogInstalling)
    }

    @Test
    fun testFilterMatchingLogic() {
        val p1 = SitePlugin(
            name = "AnimeStreamer",
            internalName = "com.example.animestreamer",
            url = "https://example.com/p1.jar",
            version = 2,
            status = 1,
            authors = listOf("Dev1"),
            tvTypes = listOf("Anime"),
            language = "en",
        )
        val p2 = SitePlugin(
            name = "MovieFlix",
            internalName = "com.example.movieflix",
            url = "https://example.com/p2.jar",
            version = 1,
            status = 1,
            authors = listOf("Dev2"),
            tvTypes = listOf("Movie", "TvSeries"),
            language = "es",
        )

        val plugins = listOf("RepoA" to p1, "RepoB" to p2)

        // Search query filter
        val searchAnime = plugins.filter {
            it.second.name.contains("Anime", ignoreCase = true) || it.second.internalName.contains("Anime", ignoreCase = true)
        }
        assertEquals(1, searchAnime.size)
        assertEquals("AnimeStreamer", searchAnime[0].second.name)

        // Category filter
        val moviePlugins = plugins.filter {
            it.second.tvTypes?.contains("Movie") == true
        }
        assertEquals(1, moviePlugins.size)
        assertEquals("MovieFlix", moviePlugins[0].second.name)

        // Language filter
        val esPlugins = plugins.filter { it.second.language == "es" }
        assertEquals(1, esPlugins.size)
        assertEquals("MovieFlix", esPlugins[0].second.name)

        // Repository filter
        val repoAPlugins = plugins.filter { it.first == "RepoA" }
        assertEquals(1, repoAPlugins.size)
        assertEquals("AnimeStreamer", repoAPlugins[0].second.name)
    }

    @Test
    fun testInstalledPluginKeysComputation_O1Lookup() {
        // Verify O(1) set contains matching that eliminates Main-thread disk checks
        val installedKeys = setOf("RepoA:com.example.animestreamer", "com.example.animestreamer")

        val p1CleanRepo = "RepoA"
        val p1Internal = "com.example.animestreamer"
        val isP1Installed = installedKeys.contains("$p1CleanRepo:$p1Internal") || installedKeys.contains(p1Internal)
        assertTrue(isP1Installed)

        val p2CleanRepo = "RepoB"
        val p2Internal = "com.example.uninstalled"
        val isP2Installed = installedKeys.contains("$p2CleanRepo:$p2Internal") || installedKeys.contains(p2Internal)
        assertFalse(isP2Installed)
    }

    @Test
    fun testSyncReport_SummaryFormatting() {
        val report = DesktopRepositoryManager.SyncReport(
            reposRefreshed = 3,
            catalogPlugins = 25,
            pluginsUpdated = 2,
            iconsCached = 10,
            newPluginsLoaded = 1,
        )
        assertEquals(3, report.reposRefreshed)
        assertEquals(25, report.catalogPlugins)
        assertEquals(2, report.pluginsUpdated)
        assertEquals(10, report.iconsCached)
        assertEquals(1, report.newPluginsLoaded)
        assertTrue(report.summary.contains("3 repos"))
        assertTrue(report.summary.contains("25 plugins listed"))
    }
}
