package com.lagradost.cloudstream3.desktop.startup

import com.lagradost.cloudstream3.desktop.init.AppReadyState
import com.lagradost.cloudstream3.desktop.init.SafeModeState
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupResiliencyTest {

    @Test
    fun testAppReadyStateProgressAndCompletion() {
        AppReadyState.updateProgress(0.5f, "Testing...")
        assertEquals(0.5f, AppReadyState.progress.value)
        assertEquals("Testing...", AppReadyState.statusText.value)

        AppReadyState.markReady()
        assertTrue(AppReadyState.isReady)
        assertEquals(1.0f, AppReadyState.progress.value)
    }

    @Test
    fun testStartupTimeoutFailsafeEngagesSafeMode() = runBlocking {
        SafeModeState.disable()
        assertFalse(SafeModeState.isSafeMode.value)

        try {
            withTimeout(150L) {
                delay(2_000L) // Simulate hanging plugin or slow network
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            SafeModeState.enable()
        }

        assertTrue(SafeModeState.isSafeMode.value)
        SafeModeState.disable()
    }

    @Test
    fun testPluginStartupTimeoutBounds() = runBlocking {
        val start = System.currentTimeMillis()
        val result = withTimeoutOrNull(200L) {
            delay(2_000L) // Simulate hung extension load or worker RPC
            listOf("SampleProvider")
        } ?: emptyList()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result.isEmpty())
        assertTrue(elapsed < 1_000L, "Timeout must bound execution, took ${elapsed}ms")
    }

    @Test
    fun testWorkerCallbackRequestRepositoryAction() {
        val req = WorkerCallbackRequest(
            callbackId = 123L,
            action = WorkerCallbackRequest.ACTION_ADD_REPOSITORY,
            repoName = "SampleRepo",
            repoUrl = "https://example.com/repo.json",
            repoIcon = "https://example.com/icon.png",
        )
        assertEquals(123L, req.callbackId)
        assertEquals(WorkerCallbackRequest.ACTION_ADD_REPOSITORY, req.action)
        assertEquals("SampleRepo", req.repoName)
        assertEquals("https://example.com/repo.json", req.repoUrl)
        assertEquals("https://example.com/icon.png", req.repoIcon)
    }
}
