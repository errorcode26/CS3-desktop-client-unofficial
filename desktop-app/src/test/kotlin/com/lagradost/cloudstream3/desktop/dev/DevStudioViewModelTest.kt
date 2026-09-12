package com.lagradost.cloudstream3.desktop.dev

import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioTab
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioViewModel
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.common.net.NetworkRequestEntry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevStudioViewModelTest {

    // --- 1. Initial State Defaults ---

    @Test
    fun testDevStudioUiState_InitialDefaults() {
        val vm = DevStudioViewModel()
        try {
            val state = vm.uiState.value

            assertEquals(DevStudioTab.LOGS, state.currentTab)
            assertEquals(LogLevel.VERBOSE, state.selectedLevel)
            assertEquals(LogSubsystem.ALL, state.selectedSubsystem)
            assertNull(state.selectedPlugin)
            assertEquals("", state.searchQuery)
            assertFalse(state.isPaused)
            assertTrue(state.autoScrollEnabled)
            assertFalse(state.isRegexSearch)
            assertFalse(state.isCaseSensitiveSearch)
            assertNull(state.selectedEntry)
            assertFalse(state.isInspectorOpen)

            assertNull(state.selectedNetworkRequest)
            assertFalse(state.isNetworkInspectorOpen)
            assertEquals("", state.networkSearchQuery)
            assertNull(state.networkMethodFilter)
            assertFalse(state.networkErrorsOnly)
            assertFalse(state.isNetworkPaused)
        } finally {
            vm.dispose()
        }
    }

    // --- 2. Tab Switching Events ---

    @Test
    fun testDevStudioUiEvent_TabSwitching() {
        val vm = DevStudioViewModel()
        try {
            assertEquals(DevStudioTab.LOGS, vm.uiState.value.currentTab)

            vm.onEvent(DevStudioUiEvent.SwitchTab(DevStudioTab.NETWORK))
            assertEquals(DevStudioTab.NETWORK, vm.uiState.value.currentTab)

            vm.onEvent(DevStudioUiEvent.SwitchTab(DevStudioTab.PLAYER))
            assertEquals(DevStudioTab.PLAYER, vm.uiState.value.currentTab)

            vm.onEvent(DevStudioUiEvent.SwitchTab(DevStudioTab.PROVIDERS))
            assertEquals(DevStudioTab.PROVIDERS, vm.uiState.value.currentTab)

            vm.onEvent(DevStudioUiEvent.SwitchTab(DevStudioTab.LOGS))
            assertEquals(DevStudioTab.LOGS, vm.uiState.value.currentTab)
        } finally {
            vm.dispose()
        }
    }

    // --- 3. Filter & Toggle Events ---

    @Test
    fun testDevStudioUiEvent_FilterAndToggles() {
        val vm = DevStudioViewModel()
        try {
            // Pause LogCat
            assertFalse(vm.uiState.value.isPaused)
            vm.onEvent(DevStudioUiEvent.TogglePause)
            assertTrue(vm.uiState.value.isPaused)
            vm.onEvent(DevStudioUiEvent.TogglePause)
            assertFalse(vm.uiState.value.isPaused)

            // AutoScroll
            assertTrue(vm.uiState.value.autoScrollEnabled)
            vm.onEvent(DevStudioUiEvent.ToggleAutoScroll)
            assertFalse(vm.uiState.value.autoScrollEnabled)
            vm.onEvent(DevStudioUiEvent.ToggleAutoScroll)
            assertTrue(vm.uiState.value.autoScrollEnabled)

            // Regex Search
            assertFalse(vm.uiState.value.isRegexSearch)
            vm.onEvent(DevStudioUiEvent.ToggleRegexSearch)
            assertTrue(vm.uiState.value.isRegexSearch)

            // Case Sensitive Search
            assertFalse(vm.uiState.value.isCaseSensitiveSearch)
            vm.onEvent(DevStudioUiEvent.ToggleCaseSensitiveSearch)
            assertTrue(vm.uiState.value.isCaseSensitiveSearch)

            // Exceptions Only
            assertFalse(vm.uiState.value.exceptionsOnly)
            vm.onEvent(DevStudioUiEvent.ToggleExceptionsOnly)
            assertTrue(vm.uiState.value.exceptionsOnly)

            // Log Level selection
            vm.onEvent(DevStudioUiEvent.SelectLevel(LogLevel.ERROR))
            assertEquals(LogLevel.ERROR, vm.uiState.value.selectedLevel)

            // Subsystem selection
            vm.onEvent(DevStudioUiEvent.SelectSubsystem(LogSubsystem.PLAYER_MPV))
            assertEquals(LogSubsystem.PLAYER_MPV, vm.uiState.value.selectedSubsystem)

            // Network Toggles
            assertFalse(vm.uiState.value.isNetworkPaused)
            vm.onEvent(DevStudioUiEvent.ToggleNetworkPause)
            assertTrue(vm.uiState.value.isNetworkPaused)

            assertFalse(vm.uiState.value.networkErrorsOnly)
            vm.onEvent(DevStudioUiEvent.ToggleNetworkErrorsOnly)
            assertTrue(vm.uiState.value.networkErrorsOnly)

            // Network Method Filter
            vm.onEvent(DevStudioUiEvent.SelectNetworkMethodFilter("POST"))
            assertEquals("POST", vm.uiState.value.networkMethodFilter)
        } finally {
            vm.dispose()
        }
    }

    // --- 4. Inspector Selection & Closing ---

    @Test
    fun testDevStudioUiEvent_LogInspectorSelectionAndClosing() {
        val vm = DevStudioViewModel()
        try {
            val sampleEntry = LogEntry(
                id = 9999L,
                timestamp = System.currentTimeMillis(),
                level = LogLevel.ERROR,
                tag = "TestDevTag",
                message = "Sample test error message",
                threadName = "main",
                subsystem = LogSubsystem.GENERAL,
            )

            assertNull(vm.uiState.value.selectedEntry)
            assertFalse(vm.uiState.value.isInspectorOpen)

            vm.onEvent(DevStudioUiEvent.SelectEntry(sampleEntry))
            assertEquals(sampleEntry, vm.uiState.value.selectedEntry)
            assertTrue(vm.uiState.value.isInspectorOpen)

            vm.onEvent(DevStudioUiEvent.CloseInspector)
            assertNull(vm.uiState.value.selectedEntry)
            assertFalse(vm.uiState.value.isInspectorOpen)
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun testDevStudioUiEvent_NetworkInspectorSelectionAndClosing() {
        val vm = DevStudioViewModel()
        try {
            val sampleReq = NetworkRequestEntry(
                id = 8888L,
                timestamp = System.currentTimeMillis(),
                method = "GET",
                url = "https://example.com/api/test",
                host = "example.com",
                path = "/api/test",
                statusCode = 200,
            )

            assertNull(vm.uiState.value.selectedNetworkRequest)
            assertFalse(vm.uiState.value.isNetworkInspectorOpen)

            vm.onEvent(DevStudioUiEvent.SelectNetworkRequest(sampleReq))
            assertEquals(sampleReq, vm.uiState.value.selectedNetworkRequest)
            assertTrue(vm.uiState.value.isNetworkInspectorOpen)

            vm.onEvent(DevStudioUiEvent.CloseNetworkInspector)
            assertNull(vm.uiState.value.selectedNetworkRequest)
            assertFalse(vm.uiState.value.isNetworkInspectorOpen)
        } finally {
            vm.dispose()
        }
    }

    // --- 5. Circuit Breaker & Lifecycle Disposal ---

    @Test
    fun testDevStudioUiEvent_CircuitResetAndDisposal() {
        val vm = DevStudioViewModel()
        // Reset operations should execute without exceptions
        vm.onEvent(DevStudioUiEvent.ResetCircuit("NonExistentProvider"))
        vm.onEvent(DevStudioUiEvent.ResetAllCircuits)

        // Verify lifecycle disposal works cleanly without error
        vm.dispose()
    }
}
