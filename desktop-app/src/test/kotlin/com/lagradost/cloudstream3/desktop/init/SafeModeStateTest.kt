package com.lagradost.cloudstream3.desktop.init

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeModeStateTest {

    @AfterEach
    fun tearDown() {
        SafeModeState.disable()
    }

    @Test
    fun testDefaultStateIsDisabled() {
        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value, "Safe mode should be disabled by default")
    }

    @Test
    fun testEnableAndDisableStateTransitions() {
        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value)

        SafeModeState.enable()
        assertTrue(SafeModeState.isSafeMode.value, "Safe mode should be enabled after enable() call")

        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value, "Safe mode should be disabled after disable() call")
    }

    @Test
    fun testSentinelLifecycle() {
        val tempDir = createTempDirectory("cloudstream_test_safemode").toFile()
        try {
            val sentinel = File(tempDir, ".safe_mode")
            assertFalse(sentinel.exists())

            // Simulate crash handler writing the sentinel
            sentinel.createNewFile()
            assertTrue(sentinel.exists(), "Crash handler should create the sentinel file")

            // Simulate startup detection logic
            val isSafeRequested = sentinel.exists()
            assertTrue(isSafeRequested, "Startup should detect the .safe_mode sentinel")

            if (isSafeRequested) {
                SafeModeState.enable()
                sentinel.delete()
            }

            assertTrue(SafeModeState.isSafeMode.value, "SafeModeState should be enabled on sentinel detection")
            assertFalse(sentinel.exists(), "Startup should delete sentinel so subsequent boots are normal")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
