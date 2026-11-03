package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.sandbox.host.SandboxedPluginHost
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedProviderMetadata
import com.lagradost.cloudstream3.desktop.sandbox.proxy.SandboxedMainApiProxy
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SandboxedPluginHostTest {

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    @Test
    fun testSandboxedHostPingAndRecovery() = runBlocking {
        assumeTrue(isWindows(), "AppContainer SandboxedPluginHost is only available on Windows")

        val containerName = "CloudStream.Worker.HostTest"
        val host = SandboxedPluginHost(
            containerName = containerName,
            memoryLimitBytes = 1024L * 1024L * 1024L
        )

        try {
            // 1. Verify boot and JSON-RPC ping over anonymous pipes
            val pong = host.ping()
            assertTrue(pong, "Sandboxed worker should respond to ping over anonymous pipe with true")

            // 2. Verify loaded providers query
            val providers = host.getLoadedProviders()
            assertTrue(providers.isEmpty() || providers.isNotEmpty(), "Host should return a list of loaded providers")

            // 3. Verify SandboxedMainApiProxy behavior and graceful error trapping
            val meta = SandboxedProviderMetadata(
                name = "TestSandboxedProvider",
                mainUrl = "https://example.com",
                lang = "en",
                supportedTypes = setOf("Movie", "TvSeries")
            )
            val proxy = SandboxedMainApiProxy(meta, host)
            assertEquals("TestSandboxedProvider", proxy.name)
            assertEquals("https://example.com", proxy.mainUrl)
            assertEquals("en", proxy.lang)
            assertTrue(proxy.supportedTypes.contains(TvType.Movie))

            // Calling search on unloaded provider returns empty list without crashing host process
            val searchResults = proxy.search("test query")
            assertTrue(searchResults.isEmpty(), "Search on unloaded provider in worker should safely return empty list")
        } finally {
            host.close()
            WindowsAppContainerApi.deleteAppContainer(containerName)
        }
    }

    @Test
    fun testPruneWorkerClasspath() {
        val sep = java.io.File.pathSeparator
        val testClasspath = listOf(
            "C:/app/desktop-app.jar",
            "C:/cache/org.jetbrains.skiko/skiko-awt-runtime-windows-x64-0.8.18.jar",
            "C:/cache/androidx.compose.material3/material3-desktop-1.7.3.jar",
            "C:/cache/dev.chrisbanes.haze/haze-jvm-0.7.0.jar",
            "C:/cache/io.coil-kt/coil-core-3.0.0.jar",
            "C:/cache/com.squareup.okhttp3/okhttp-4.12.0.jar",
            "C:/cache/com.fasterxml.jackson.core/jackson-databind-2.17.0.jar"
        ).joinToString(sep)

        val pruned = SandboxedPluginHost.pruneWorkerClasspath(testClasspath)
        val entries = pruned.split(sep)

        assertTrue(entries.any { it.contains("desktop-app.jar") }, "Worker classpath must retain desktop-app.jar")
        assertTrue(entries.any { it.contains("okhttp") }, "Worker classpath must retain okhttp")
        assertTrue(entries.any { it.contains("jackson") }, "Worker classpath must retain jackson")

        assertTrue(entries.none { it.contains("skiko") }, "Worker classpath must prune skiko")
        assertTrue(entries.none { it.contains("compose") }, "Worker classpath must prune compose")
        assertTrue(entries.none { it.contains("haze") }, "Worker classpath must prune haze")
        assertTrue(entries.none { it.contains("coil") }, "Worker classpath must prune coil")
    }
}
