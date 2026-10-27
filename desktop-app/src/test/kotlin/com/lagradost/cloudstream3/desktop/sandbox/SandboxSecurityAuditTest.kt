package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.cloudstream3.desktop.sandbox.host.SandboxedPluginHost
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class SandboxSecurityAuditTest {

    @Test
    fun testLiveSandboxSecurityBoundaries() = runBlocking {
        if (PlatformPaths.currentOS != PlatformPaths.OS.WINDOWS) {
            println("Skipping test: AppContainer sandbox tests only run on Windows")
            return@runBlocking
        }

        // Delete any existing profile to test pristine capability creation
        com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi.deleteAppContainer("CloudStream.SecurityAudit.Test")

        val host = SandboxedPluginHost(containerName = "CloudStream.SecurityAudit.Test")
        try {
            host.start()
            val report = host.runSecurityAudit()

            println("==================================================")
            println("       SANDBOX SECURITY AUDIT PROBE REPORT        ")
            println("==================================================")
            report.details.forEach { (probe, result) ->
                println("  * $probe: $result")
            }
            println("--------------------------------------------------")
            println("  ALL BOUNDARIES SECURE: ${report.isFullySecure}")
            println("==================================================")

            assertTrue(report.subprocessBlocked, "Subprocess execution must be blocked by Job Object ActiveProcessLimit=1")
            assertTrue(report.winTempWriteBlocked, "Writing to OS Windows Temp directory must be blocked by AppContainer DACL")
            assertTrue(report.startupWriteBlocked, "Writing to Startup persistence folder must be blocked by AppContainer DACL")
            assertTrue(report.extensionTamperBlocked, "Writing/overwriting Extensions folder must be blocked by read-only DACL")
            assertTrue(report.sensitiveReadBlocked, "Reading sensitive user/database files must be blocked by DACL isolation")
            assertTrue(report.lanAccessBlocked, "Outbound connections to private LAN must be blocked by AppContainer firewall")
            assertTrue(report.sandboxTmpAllowed, "Isolated sandbox scratch folder write must succeed for normal operation")
            assertTrue(report.isFullySecure, "All live sandbox security probes must pass")
        } finally {
            host.close()
        }
    }
}
