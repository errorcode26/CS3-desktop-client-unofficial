package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerLauncher
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsJobObject
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsSandboxAcl
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsSandboxTest {

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    @Test
    fun testJobObjectCreationWith1GbLimit() {
        assumeTrue(isWindows(), "Job Objects are only available on Windows")

        val job = WindowsJobObject(
            jobName = "CS3_Test_Job_${System.currentTimeMillis()}",
            memoryLimitBytes = 1024L * 1024L * 1024L
        )

        try {
            assertNotNull(job.handle, "Job Object handle should not be null")
            assertTrue(job.handle?.pointer != null, "Job Object handle pointer should be valid")
        } finally {
            job.close()
        }
    }

    @Test
    fun testAppContainerSidDerivation() {
        assumeTrue(isWindows(), "AppContainer is only available on Windows")

        val containerName = "CloudStream.Worker.DeriveTest"
        val sid = WindowsAppContainerApi.deriveAppContainerSid(containerName)

        assertNotNull(sid, "AppContainer SID derivation should succeed on Windows 8+")
        assertTrue(
            sid.startsWith("S-1-15-2-"),
            "AppContainer SID must begin with standard prefix 'S-1-15-2-', but got: $sid"
        )
    }

    @Test
    fun testAppContainerProfileLifecycle() {
        assumeTrue(isWindows(), "AppContainer is only available on Windows")

        val containerName = "CloudStream.Worker.LifecycleTest"

        val sid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            displayName = "CS3 Test Worker",
            description = "Ephemeral test container for sandbox verification",
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT)
        )

        assertNotNull(sid, "AppContainer creation should return a valid SID")
        assertTrue(
            sid.startsWith("S-1-15-2-"),
            "AppContainer SID must begin with standard prefix 'S-1-15-2-', but got: $sid"
        )

        val deleted = WindowsAppContainerApi.deleteAppContainer(containerName)
        assertTrue(deleted, "AppContainer profile should be successfully deleted")
    }

    @Test
    fun testSandboxAclAssignment() {
        assumeTrue(isWindows(), "ACL assignment is only available on Windows")

        val containerName = "CloudStream.Worker.AclTest"
        val sid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT)
        )
        assertNotNull(sid)

        val tempDir = File(System.getProperty("java.io.tmpdir"), "cs3_acl_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            val granted = WindowsSandboxAcl.grantFolderAccess(tempDir, sid)
            assertTrue(granted, "Granting folder DACL to AppContainer SID should succeed")
        } finally {
            tempDir.deleteRecursively()
            WindowsAppContainerApi.deleteAppContainer(containerName)
        }
    }

    @Test
    fun testLaunchJvmInsideAppContainer() {
        assumeTrue(isWindows(), "AppContainer process launching is only available on Windows")

        val containerName = "CloudStream.Worker.JvmLaunchTest"
        val sid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT)
        )
        assertNotNull(sid)

        val javaHome = File(System.getProperty("java.home"))
        val javaExe = File(javaHome, "bin/java.exe")
        assertTrue(javaExe.exists(), "java.exe must exist in current java.home")

        // Grant read access to JRE directory so sandboxed JVM can load its native runtime DLLs and modules
        WindowsSandboxAcl.grantFolderAccess(javaHome, sid, readOnly = true)

        val job = WindowsJobObject("CS3_Jvm_Job", 512L * 1024L * 1024L)

        try {
            val process = WindowsAppContainerLauncher.launch(
                commandLine = "\"${javaExe.absolutePath}\" -version",
                containerName = containerName,
                workingDir = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32"),
                capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT),
                jobObject = job
            )

            val stdout = process.stdout.bufferedReader().readText()
            val stderr = process.stderr.bufferedReader().readText()
            process.waitFor(5000)

            val exitCode = process.exitValue()
            val combinedOutput = "$stdout\n$stderr"
            println("Sandboxed JVM Output:\n$combinedOutput")

            assertEquals(0, exitCode, "Sandboxed JVM (java.exe -version) must exit with code 0. Stderr: $stderr")
            assertTrue(
                combinedOutput.lowercase().contains("version") || combinedOutput.lowercase().contains("runtime"),
                "Output must contain JVM runtime version confirmation"
            )

            process.close()
        } finally {
            job.close()
            WindowsAppContainerApi.deleteAppContainer(containerName)
        }
    }

    @Test
    fun testLaunchWhoamiLowIntegrityInsideAppContainer() {
        assumeTrue(isWindows(), "AppContainer process launching is only available on Windows")

        val containerName = "CloudStream.Worker.IntegrityTest"
        val job = WindowsJobObject("CS3_Integrity_Job", 512L * 1024L * 1024L)
        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val system32Dir = File(systemRoot, "System32")
        val whoamiBinary = File(system32Dir, "whoami.exe").absolutePath

        try {
            val process = WindowsAppContainerLauncher.launch(
                commandLine = "\"$whoamiBinary\" /groups",
                containerName = containerName,
                workingDir = system32Dir,
                capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT),
                jobObject = job
            )

            val stdout = process.stdout.bufferedReader().readText()
            val stderr = process.stderr.bufferedReader().readText()
            process.waitFor(5000)

            val exitCode = process.exitValue()
            println("Sandboxed whoami /groups Output:\n$stdout\nStderr: $stderr")

            assertEquals(0, exitCode, "whoami /groups should exit with code 0")

            assertTrue(
                stdout.contains("Low Mandatory Level") && stdout.contains("S-1-16-4096"),
                "Output must verify Low Mandatory Level integrity containment (S-1-16-4096)"
            )
            assertTrue(
                stdout.contains("Group used for deny only"),
                "Output must verify administrator privileges are stripped (deny only)"
            )

            process.close()
        } finally {
            job.close()
            WindowsAppContainerApi.deleteAppContainer(containerName)
        }
    }
}
