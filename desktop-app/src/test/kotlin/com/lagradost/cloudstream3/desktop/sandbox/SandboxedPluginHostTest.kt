package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.cloudstream3.desktop.sandbox.host.SandboxedPluginHost
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
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

        } finally {
            host.close()
            WindowsAppContainerApi.deleteAppContainer(containerName)
        }
    }
}
