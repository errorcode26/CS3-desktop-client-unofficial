package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.desktop.sandbox.host.PluginLoadFailureException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class PluginFailureTest {

    @Test
    fun testPluginFailureStateReportAndDismiss() {
        val testFile = File("test-plugin.jar")
        PluginFailureState.dismiss()
        assertNull(PluginFailureState.activeFailure.value)

        PluginFailureState.reportFailure(
            pluginName = "BrokenProvider",
            file = testFile,
            error = "Failed to transpile Dalvik DEX bytecode",
        )

        val report = PluginFailureState.activeFailure.value
        assertNotNull(report)
        assertEquals("BrokenProvider", report?.pluginName)
        assertEquals(testFile, report?.file)
        assertEquals("Failed to transpile Dalvik DEX bytecode", report?.errorMessage)

        PluginFailureState.dismiss()
        assertNull(PluginFailureState.activeFailure.value)
    }

    @Test
    fun testPluginLoadFailureExceptionProperties() {
        val file = File("sample.jar")
        val permanentEx = PluginLoadFailureException(
            pluginFile = file,
            errorMessage = "Corrupted bytecode",
            isPermanent = true,
        )
        assertTrue(permanentEx.isPermanent)
        assertEquals("Corrupted bytecode", permanentEx.errorMessage)
        assertEquals(file, permanentEx.pluginFile)

        val transientEx = PluginLoadFailureException(
            pluginFile = file,
            errorMessage = "IPC timeout",
            isPermanent = false,
        )
        assertFalse(transientEx.isPermanent)
        assertEquals("IPC timeout", transientEx.errorMessage)
    }

    @Test
    fun testIsQuarantinedDetection() {
        val tempDir = Files.createTempDirectory("cs3_quarantine_test").toFile()
        try {
            val validJar = File(tempDir, "ValidPlugin.jar").apply { createNewFile() }
            assertFalse(isQuarantined(validJar), "Valid plugin without marker must not be quarantined")

            val failedJar = File(tempDir, "BrokenPlugin.jar").apply { createNewFile() }
            val marker = File(tempDir, "BrokenPlugin.jar.load_failed").apply { createNewFile() }
            assertTrue(isQuarantined(failedJar), "Plugin with .load_failed marker must be quarantined")

            marker.delete()
            assertFalse(isQuarantined(failedJar), "Plugin after removing marker must not be quarantined")

            val d2jMarker = File(tempDir, "BrokenPlugin.d2j_failed").apply { createNewFile() }
            assertTrue(isQuarantined(failedJar), "Plugin with .d2j_failed marker must be quarantined")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
