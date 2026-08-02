package com.lagradost.common.logging

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogBufferTest {

    @BeforeTest
    fun setup() {
        LogBuffer.clear()
    }

    @Test
    fun testRecordAndSnapshot() {
        AppLogger.i("TestTag", "Hello World")
        AppLogger.w("TestTag", "Warning Message", RuntimeException("Sample warn"))
        AppLogger.e("TestTag", "Error Message", IllegalStateException("Sample error"))

        val snapshot = LogBuffer.getSnapshot()
        assertEquals(3, snapshot.size)
        assertEquals(LogLevel.INFO, snapshot[0].level)
        assertEquals(LogLevel.WARN, snapshot[1].level)
        assertEquals(LogLevel.ERROR, snapshot[2].level)
        assertEquals("Hello World", snapshot[0].message)
        assertNotNull(snapshot[2].stackTraceString)
    }

    @Test
    fun testFilterByLevelAndSubsystem() {
        LogBuffer.record(LogLevel.DEBUG, "Network:OkHttp", "GET https://example.com/api")
        LogBuffer.record(LogLevel.INFO, "PluginLoader:ProviderX", "Loading provider X")
        LogBuffer.record(LogLevel.ERROR, "MpvBridge:Player", "Frame drop error", RuntimeException("MPV crash"))
        LogBuffer.record(LogLevel.VERBOSE, "UI:Home", "Recomposing category row")

        val snapshot = LogBuffer.getSnapshot()

        val errorsOnly = LogBuffer.filter(snapshot, minLevel = LogLevel.ERROR)
        assertEquals(1, errorsOnly.size)
        assertEquals("MpvBridge:Player", errorsOnly[0].tag)

        val pluginsOnly = LogBuffer.filter(snapshot, subsystem = LogSubsystem.PLUGINS)
        assertEquals(1, pluginsOnly.size)
        assertEquals("PluginLoader:ProviderX", pluginsOnly[0].tag)

        val networkOnly = LogBuffer.filter(snapshot, subsystem = LogSubsystem.PROXY_NETWORK)
        assertEquals(1, networkOnly.size)
        assertEquals("Network:OkHttp", networkOnly[0].tag)
    }

    @Test
    fun testFilterByQuery() {
        LogBuffer.record(LogLevel.INFO, "SafePluginInvoker:SFlix", "Scraping links for movie")
        LogBuffer.record(LogLevel.INFO, "SafePluginInvoker:SuperStream", "Scraping subtitles")

        val snapshot = LogBuffer.getSnapshot()

        val results = LogBuffer.filter(snapshot, query = "SFlix")
        assertEquals(1, results.size)
        assertEquals("SafePluginInvoker:SFlix", results[0].tag)
    }

    @Test
    fun testAiDebugSnapshotGeneration() {
        for (i in 1..10) {
            LogBuffer.record(LogLevel.DEBUG, "AppInit", "Step $i initialized")
        }
        val errEntry = LogBuffer.record(LogLevel.ERROR, "CrashHandler", "Fatal crash occurred", NullPointerException("test null pointer"))

        val aiSnapshot = LogBuffer.buildAiDebugSnapshot(errEntry.id, precedingCount = 5)

        assertTrue(aiSnapshot.contains("CloudStream Diagnostics & AI Debug Snapshot"))
        assertTrue(aiSnapshot.contains("Fatal crash occurred"))
        assertTrue(aiSnapshot.contains("NullPointerException"))
        assertTrue(aiSnapshot.contains("Step 10 initialized"))
    }
}
