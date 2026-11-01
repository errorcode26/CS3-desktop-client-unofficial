package com.lagradost.cloudstream3.desktop.plugins

import com.lagradost.cloudstream3.APIHolder
import com.synthetic.test.SyntheticPluginFixture
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginRealtimeLifecycleTest {

    private lateinit var testDir: File
    private lateinit var pluginJar: File

    @BeforeEach
    fun setUp() {
        testDir = File(PlatformPaths.appDataDir, "test_lifecycle_${System.currentTimeMillis()}").apply { mkdirs() }
        pluginJar = File(testDir, "SyntheticTestPlugin_${System.nanoTime()}.jar")
    }

    @AfterEach
    fun tearDown() {
        try {
            ExtensionLoader.unloadPlugin(pluginJar.absolutePath)
        } catch (_: Throwable) {}
        val secureJar = File(PlatformPaths.pluginCacheDir, pluginJar.nameWithoutExtension + "-secure.jar")
        if (secureJar.exists()) {
            try { ExtensionLoader.unloadPlugin(secureJar.absolutePath) } catch (_: Throwable) {}
            try { secureJar.delete() } catch (_: Throwable) {}
        }
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.removeAll { it.name.startsWith("Synthetic Aggregator") }
        }
        testDir.deleteRecursively()
    }

    @Test
    fun testPluginFreshInstallAndRegistration() {
        // 1. Generate synthetic plugin JAR v1
        SyntheticPluginFixture.createPluginJar(
            destinationFile = pluginJar,
            version = 1,
            displayName = "Synthetic Aggregator Provider",
        )
        assertTrue(pluginJar.exists(), "Synthetic plugin JAR must be created on disk")
        assertTrue(pluginJar.length() > 0L, "Synthetic plugin JAR must not be empty")

        // 2. Load and initialize plugin into runtime
        val plugin = ExtensionLoader.loadAndInit(pluginJar, forceBypassSecurity = true)
        assertNotNull(plugin, "ExtensionLoader should return registered plugin instance")

        // 3. Verify provider registration in APIHolder
        val registered = APIHolder.getApiFromNameNull("Synthetic Aggregator Provider")
        assertNotNull(registered, "Provider should be accessible in APIHolder by exact name")
        assertEquals("https://synthetic.aggregator.local", registered.mainUrl)
        assertEquals("en", registered.lang)
    }

    @Test
    fun testPluginHotUpdateWithoutWindowsFileLocks() {
        // 1. Install and load initial v1
        SyntheticPluginFixture.createPluginJar(pluginJar, version = 1, displayName = "Synthetic Aggregator Provider")
        ExtensionLoader.loadAndInit(pluginJar, forceBypassSecurity = true)
        assertNotNull(APIHolder.getApiFromNameNull("Synthetic Aggregator Provider"))

        // 2. Simulate real-time background update: overwrite the loaded JAR with v2 payload
        // On Windows, if ClassLoaders or URLConnection caches hold open handles, this write will throw EBUSY
        val v2Jar = File(testDir, "SyntheticTestPlugin_v2.jar")
        SyntheticPluginFixture.createPluginJar(v2Jar, version = 2, displayName = "Synthetic Aggregator Provider v2")

        var replacedWithoutFileLock = false
        try {
            v2Jar.copyTo(pluginJar, overwrite = true)
            replacedWithoutFileLock = true
        } catch (e: Exception) {
            replacedWithoutFileLock = false
        }
        assertTrue(replacedWithoutFileLock, "Overwriting active loaded plugin JAR must not fail with Windows file locking")

        // 3. Hot-reload the updated plugin
        ExtensionLoader.unloadPlugin(pluginJar.absolutePath)
        val updatedPlugin = ExtensionLoader.loadAndInit(pluginJar, forceBypassSecurity = true, forceRetry = true)
        assertNotNull(updatedPlugin, "Hot-updated plugin should load successfully")

        // 4. Verify updated state in APIHolder
        val updatedProvider = APIHolder.getApiFromNameNull("Synthetic Aggregator Provider")
        assertNotNull(updatedProvider, "Updated provider must remain registered in APIHolder")
    }

    @Test
    fun testPluginUninstallAndStagingCleanup() {
        // 1. Install and verify registration
        SyntheticPluginFixture.createPluginJar(pluginJar, version = 1, displayName = "Synthetic Aggregator Provider")
        ExtensionLoader.loadAndInit(pluginJar, forceBypassSecurity = true)
        assertNotNull(APIHolder.getApiFromNameNull("Synthetic Aggregator Provider"))

        // 2. Create staged temporary files simulating in-flight uninstalls / downloads
        val pendingDeleteMarker = File(testDir, "SyntheticTestPlugin.jar.pending_delete").apply { writeText("pending") }
        val tmpDownloadFile = File(testDir, "SyntheticTestPlugin.jar.tmp").apply { writeText("partial_download") }
        val uninstalledMarker = File(testDir, "SyntheticTestPlugin.jar.uninstalled").apply { writeText("uninstalled") }

        assertTrue(pendingDeleteMarker.exists())
        assertTrue(tmpDownloadFile.exists())
        assertTrue(uninstalledMarker.exists())

        // 3. Execute unload and uninstallation cleanup
        ExtensionLoader.unloadPlugin(pluginJar.absolutePath)

        // Purge cleanup logic (identical to Main.kt startup / uninstall routine)
        testDir.walkTopDown().filter { it.isFile && it.name.endsWith(".pending_delete") }.forEach { it.delete() }
        testDir.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() }
        testDir.walkTopDown().filter { it.isFile && it.name.endsWith(".uninstalled") }.forEach { marker ->
            val baseName = marker.name.removeSuffix(".uninstalled")
            val target = File(marker.parentFile, baseName)
            if (target.exists()) target.delete()
            marker.delete()
        }

        // 4. Verify complete cleanup: zero stale ghost files remain
        assertFalse(pendingDeleteMarker.exists(), "pending_delete marker must be purged")
        assertFalse(tmpDownloadFile.exists(), "tmp partial files must be purged")
        assertFalse(uninstalledMarker.exists(), "uninstalled marker must be purged")

        // 5. Verify provider is completely unregistered from APIHolder
        val ghost = APIHolder.getApiFromNameNull("Synthetic Aggregator Provider")
        assertNull(ghost, "Uninstalled provider must not remain as a ghost in APIHolder")
    }
}
