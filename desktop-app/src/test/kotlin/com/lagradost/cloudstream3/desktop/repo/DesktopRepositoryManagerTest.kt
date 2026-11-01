package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRepositoryManagerTest {

    private val reposFile = File(PlatformPaths.extensionsDir, "repos.json")
    private val repoCacheFile = File(PlatformPaths.extensionsDir, "repo_cache.json")
    private val pluginsCacheFile = File(PlatformPaths.extensionsDir, "plugins_cache.json")

    private var originalReposText: String? = null
    private var originalRepoCacheText: String? = null
    private var originalPluginsCacheText: String? = null

    @BeforeEach
    fun setUp() {
        if (reposFile.exists()) originalReposText = reposFile.readText()
        if (repoCacheFile.exists()) originalRepoCacheText = repoCacheFile.readText()
        if (pluginsCacheFile.exists()) originalPluginsCacheText = pluginsCacheFile.readText()

        reposFile.delete()
        repoCacheFile.delete()
        pluginsCacheFile.delete()
    }

    @AfterEach
    fun tearDown() {
        if (originalReposText != null) reposFile.writeText(originalReposText!!) else reposFile.delete()
        if (originalRepoCacheText != null) repoCacheFile.writeText(originalRepoCacheText!!) else repoCacheFile.delete()
        if (originalPluginsCacheText != null) pluginsCacheFile.writeText(originalPluginsCacheText!!) else pluginsCacheFile.delete()
    }

    @Test
    fun testRepositorySaveAndRemovalLifecycle() = runBlocking {
        val testRepo = RepositoryData(
            name = "Test Community Repo",
            url = "https://raw.githubusercontent.com/test-org/repo/main/repo.json",
            iconUrl = "https://raw.githubusercontent.com/test-org/repo/main/icon.png"
        )

        // 1. Save repository
        val isNew = DesktopRepositoryManager.saveRepository(testRepo)
        assertTrue(isNew, "Initial repository save must report as new")

        val saved = DesktopRepositoryManager.getSavedRepositories()
        assertTrue(saved.any { it.url == testRepo.url }, "Repository must be present in saved list")
        assertEquals("Test Community Repo", saved.first { it.url == testRepo.url }.name)

        // 2. Idempotent update: updating existing repository metadata
        val updatedRepo = testRepo.copy(name = "Updated Community Repo")
        val isNewSecond = DesktopRepositoryManager.saveRepository(updatedRepo)
        assertFalse(isNewSecond, "Updating existing repository must report as not new")

        val updatedList = DesktopRepositoryManager.getSavedRepositories()
        assertEquals("Updated Community Repo", updatedList.first { it.url == testRepo.url }.name)

        // 3. Remove repository
        DesktopRepositoryManager.removeRepository(testRepo.url)
        val afterRemoval = DesktopRepositoryManager.getSavedRepositories()
        assertFalse(afterRemoval.any { it.url == testRepo.url }, "Repository must be purged from saved list")
    }

    @Test
    fun testShortcodeAndCustomSchemeParsing() = runBlocking {
        // 1. cloudstreamrepo:// custom protocol scheme
        val csScheme = "cloudstreamrepo://raw.githubusercontent.com/user/test-repo/main/repo.json"
        val parsedCs = PluginNetworkClient.parseRepoUrl(csScheme)
        assertEquals("https://raw.githubusercontent.com/user/test-repo/main/repo.json", parsedCs)

        // 2. https://cs.repo/? custom short scheme
        val shortScheme = "https://cs.repo/?raw.githubusercontent.com/user/test-repo/main/repo.json"
        val parsedShort = PluginNetworkClient.parseRepoUrl(shortScheme)
        assertEquals("https://raw.githubusercontent.com/user/test-repo/main/repo.json", parsedShort)

        // 3. Standard HTTPS passes through
        val standardHttps = "https://raw.githubusercontent.com/user/test-repo/main/repo.json"
        assertEquals(standardHttps, PluginNetworkClient.parseRepoUrl(standardHttps))

        // 4. Invalid protocol rejected
        val ftpUrl = "ftp://invalid.host/repo.json"
        assertNull(PluginNetworkClient.parseRepoUrl(ftpUrl), "Non-HTTP protocols must be rejected")
    }

    @Test
    fun testPluginsJsonUrlFallback() {
        val repoUrl = "https://example.com/repo/repo.json"
        val pluginsUrl = DesktopRepositoryManager.getPluginsJsonUrl(repoUrl)
        assertEquals("https://example.com/repo/builds/plugins.json", pluginsUrl)
    }

    @Test
    fun testCorruptDiskReposJsonDoesNotCrash() = runBlocking {
        // Write malformed non-JSON data into repos.json
        reposFile.parentFile?.mkdirs()
        reposFile.writeText("INVALID_CORRUPTED_JSON_DATA{{{###")

        DesktopRepositoryManager.initialize()
        val repos = DesktopRepositoryManager.getSavedRepositories()
        assertNotNull(repos, "Corrupt repos.json must not throw; should return fallback list")
        assertTrue(repos.isEmpty(), "Corrupt repos.json should produce empty list")
    }
}
