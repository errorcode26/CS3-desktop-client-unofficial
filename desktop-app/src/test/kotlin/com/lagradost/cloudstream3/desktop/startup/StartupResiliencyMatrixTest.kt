package com.lagradost.cloudstream3.desktop.startup

import com.lagradost.cloudstream3.desktop.init.AppReadyState
import com.lagradost.cloudstream3.desktop.init.SafeModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StartupResiliencyMatrixTest {

    @BeforeEach
    fun setUp() {
        SafeModeState.disable()
    }

    @AfterEach
    fun tearDown() {
        SafeModeState.disable()
    }

    @Test
    fun testCascadeFailureNeverBlocksAppEntry() = runBlocking {
        var isAppReady = false

        // Simulate multi-failure cascade: Storage corrupted + Network dead + Extension hanging
        val startupJob = launch(Dispatchers.IO) {
            try {
                withTimeout(200L) { // Short timeout simulating the 8s failsafe
                    // 1. Storage fails
                    runCatching {
                        throw IllegalStateException("Database disk image is malformed (corrupt SQLite file)")
                    }.onFailure {
                        SafeModeState.enable("Pre-startup storage failure: ${it.message}")
                    }

                    // 2. Network fails
                    val networkJob = async {
                        runCatching {
                            throw java.net.ConnectException("Network unreachable")
                        }
                    }

                    // 3. Plugin worker hangs indefinitely
                    val pluginsJob = async {
                        delay(5_000L) // Stalled worker RPC
                    }

                    networkJob.await()
                    pluginsJob.await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                SafeModeState.enable("Startup timed out: extension worker or network stalled.")
            } catch (t: Throwable) {
                SafeModeState.enable("Startup error: ${t.message}")
            } finally {
                // UI splash dismissal guarantee
                withContext(Dispatchers.Default) {
                    AppReadyState.markReady()
                    isAppReady = true
                }
            }
        }

        startupJob.join()

        // Assert UI splash is unconditionally dismissed
        assertTrue(isAppReady, "isAppReady MUST be true even when multiple subsystems fail simultaneously")
        assertTrue(AppReadyState.isReady, "AppReadyState.isReady MUST be true")

        // Assert Safe Mode auto-engaged with diagnostic failure reason
        assertTrue(SafeModeState.isSafeMode.value, "Safe Mode must be engaged for crash recovery")
        val reason = SafeModeState.failureReason.value
        assertNotNull(reason, "SafeModeState must capture failure reason for the user")
        assertTrue(
            reason.contains("timed out") || reason.contains("corrupt") || reason.contains("failure"),
            "Reason must explain the root cause, got: $reason"
        )
    }

    @Test
    fun testSafeModeReasonDiagnosis() {
        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value)
        assertEquals(null, SafeModeState.failureReason.value)

        SafeModeState.enable("Corrupted plugin: Provider 'BadPlugin' threw OutOfMemoryError")
        assertTrue(SafeModeState.isSafeMode.value)
        assertEquals("Corrupted plugin: Provider 'BadPlugin' threw OutOfMemoryError", SafeModeState.failureReason.value)

        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value)
        assertEquals(null, SafeModeState.failureReason.value)
    }
}
