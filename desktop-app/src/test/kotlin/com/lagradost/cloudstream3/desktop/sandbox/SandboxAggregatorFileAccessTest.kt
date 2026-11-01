package com.lagradost.cloudstream3.desktop.sandbox

import com.synthetic.test.SyntheticAggregatorApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerLauncher
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsJobObject
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsSandboxAcl
import com.lagradost.common.platform.PlatformPaths
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SandboxAggregatorFileAccessTest {

    private fun isWindows(): Boolean = PlatformPaths.currentOS == PlatformPaths.OS.WINDOWS

    private val containerName = "CloudStream.Worker.AggregatorTest"
    private var containerSid: String? = null
    private lateinit var scratchDir: File

    @BeforeEach
    fun setUp() {
        if (!isWindows()) return
        containerSid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            displayName = "Aggregator File Access Test Container",
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT)
        )
        scratchDir = File(PlatformPaths.sandboxDir, "test_aggregator_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @AfterEach
    fun tearDown() {
        if (!isWindows()) return
        try { scratchDir.deleteRecursively() } catch (_: Throwable) {}
        WindowsAppContainerApi.deleteAppContainer(containerName)
    }

    @Test
    fun testAggregatorScratchWriteAllowedInSandboxDirectories() {
        assumeTrue(isWindows(), "AppContainer sandbox tests only run on Windows")
        val sid = containerSid
        assertNotNull(sid, "AppContainer SID must be created")

        // 1. Grant folder access to sandbox scratch root
        val granted = WindowsSandboxAcl.grantFolderAccess(scratchDir, sid, readOnly = false, allowExecute = false)
        assertTrue(granted, "Granting read/write access to sandbox scratch directory must succeed")

        // 2. Perform aggregator operations (create 25 nested JSON cache files)
        val api = SyntheticAggregatorApi()
        val successCount = api.performAggregatorOperations(scratchDir)
        assertEquals(25, successCount, "Aggregator routine must successfully write and verify 25 cache files")

        // 3. Verify subdirectories and files can be cleanly listed and cleaned up
        val files = scratchDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.size >= 25, "At least 25 files should exist in aggregator scratch directory")

        // 4. Verify cleanup / deletion without file locking errors
        val deleted = scratchDir.deleteRecursively()
        assertTrue(deleted, "Aggregator scratch files must delete cleanly without locking")
    }

    @Test
    fun testProtectedHostDirectoriesWriteDeniedToAppContainer() {
        assumeTrue(isWindows(), "AppContainer sandbox tests only run on Windows")
        val sid = containerSid
        assertNotNull(sid)

        // Extensions directory must strictly be read-only (allowExecute = true, readOnly = true)
        val extDir = PlatformPaths.extensionsDir
        WindowsSandboxAcl.grantFolderAccess(extDir, sid, readOnly = true, allowExecute = true)

        // Launch a low-integrity probe trying to write to the extensions folder
        val job = WindowsJobObject("CS3_Aggregator_Job", 512L * 1024L * 1024L)
        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val cmdExe = File(systemRoot, "System32\\cmd.exe").absolutePath

        val testProbeFile = File(extDir, "unauthorized_probe_${System.currentTimeMillis()}.tmp")

        try {
            val process = WindowsAppContainerLauncher.launch(
                commandLine = "\"$cmdExe\" /c echo tamper > \"${testProbeFile.absolutePath}\"",
                containerName = containerName,
                workingDir = File(systemRoot, "System32"),
                capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT),
                jobObject = job
            )

            process.waitFor(5000)
            val exitCode = process.exitValue()
            process.close()

            // Either the cmd process failed (non-zero), or the file was NOT created due to DACL Access Denied
            assertFalse(
                testProbeFile.exists(),
                "Sandboxed process must NOT be allowed to write files into extensions directory!"
            )
        } finally {
            job.close()
            if (testProbeFile.exists()) testProbeFile.delete()
        }
    }
}
