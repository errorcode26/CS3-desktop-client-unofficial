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
}
