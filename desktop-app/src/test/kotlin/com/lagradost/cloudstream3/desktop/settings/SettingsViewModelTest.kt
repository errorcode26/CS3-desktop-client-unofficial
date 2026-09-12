package com.lagradost.cloudstream3.desktop.settings

import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.ClearanceState
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.DiagnosticsState
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.ProviderTestReportState
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiState
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.StorageMetrics
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.TorrServerEngineState
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.UpdateCheckState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @Test
    fun testInitialState_Defaults() {
        val state = SettingsUiState()

        assertTrue(state.stringSettings.isEmpty())
        assertTrue(state.booleanSettings.isEmpty())
        assertTrue(state.intSettings.isEmpty())
        assertTrue(state.floatSettings.isEmpty())
        assertEquals("", state.downloadPath)
        assertEquals("", state.screenshotPath)
        assertFalse(state.isDevModeEnabled)
        assertNull(state.devModeError)
        assertFalse(state.isOptimizingDb)
        assertFalse(state.isRefreshingStorage)

        assertEquals(StorageMetrics(), state.storageMetrics)
        assertEquals(TorrServerEngineState(), state.engineState)
        assertEquals(UpdateCheckState(), state.updateCheckState)
        assertEquals(ClearanceState(), state.clearanceState)
        assertEquals(ProviderTestReportState(), state.providerTestState)
        assertEquals(DiagnosticsState(), state.diagnosticsState)
    }

    @Test
    fun testStorageMetrics_TotalBytesCalculation() {
        val emptyMetrics = StorageMetrics()
        assertEquals(0L, emptyMetrics.totalBytes)

        val partialMetrics = StorageMetrics(
            imageCacheBytes = 1024L,
            networkCacheBytes = null,
            databaseBytes = 2048L,
            logsBytes = null,
        )
        assertEquals(3072L, partialMetrics.totalBytes)

        val fullMetrics = StorageMetrics(
            imageCacheBytes = 100L,
            networkCacheBytes = 200L,
            databaseBytes = 300L,
            logsBytes = 400L,
        )
        assertEquals(1000L, fullMetrics.totalBytes)
    }

    @Test
    fun testDohProvider_EntriesIntegrity() {
        val entries = DohProvider.entries
        assertTrue(entries.isNotEmpty(), "DohProvider.entries must not be empty")

        entries.forEach { provider ->
            assertTrue(provider.title.isNotBlank(), "Provider title must be non-blank")
            if (provider.url != null) {
                assertTrue(provider.url!!.isNotBlank(), "Provider URL when present must be non-blank")
            }
        }

        val mapped = entries.mapIndexed { index, provider -> index to provider.title }
        assertEquals(entries.size, mapped.size)
        assertEquals(0, mapped.first().first)
        assertEquals(entries.first().title, mapped.first().second)
    }

    @Test
    fun testSettingsMaps_ImmutabilityAndUpdates() {
        var state = SettingsUiState()

        state = state.copy(stringSettings = state.stringSettings + ("sub_font" to "Inter"))
        assertEquals("Inter", state.stringSettings["sub_font"])

        state = state.copy(booleanSettings = state.booleanSettings + ("cf_bypass" to true))
        assertEquals(true, state.booleanSettings["cf_bypass"])

        state = state.copy(intSettings = state.intSettings + ("doh_provider" to 1))
        assertEquals(1, state.intSettings["doh_provider"])

        state = state.copy(floatSettings = state.floatSettings + ("audio_delay" to 250f))
        assertEquals(250f, state.floatSettings["audio_delay"])
    }

    @Test
    fun testDiagnosticsState_Transitions() {
        var state = DiagnosticsState()
        assertFalse(state.isNetworkTesting)
        assertFalse(state.isMetaTesting)
        assertTrue(state.results.isEmpty())

        state = state.copy(isNetworkTesting = true, currentTest = "DNS Resolution")
        assertTrue(state.isNetworkTesting)
        assertEquals("DNS Resolution", state.currentTest)

        state = state.copy(isNetworkTesting = false, currentTest = "", lastRunTime = "2026-09-12 12:00:00")
        assertFalse(state.isNetworkTesting)
        assertEquals("2026-09-12 12:00:00", state.lastRunTime)
    }

    @Test
    fun testTorrServerEngineState_Defaults() {
        val state = TorrServerEngineState()
        assertFalse(state.isInstalled)
        assertEquals(0f, state.fileSizeMB)
        assertFalse(state.isCheckingUpdates)
        assertNull(state.updateFeedback)
    }

    @Test
    fun testClearanceState_Defaults() {
        val state = ClearanceState()
        assertTrue(state.cookiesMap.isEmpty())
        assertFalse(state.isLaunchingManualBypass)
    }
}
