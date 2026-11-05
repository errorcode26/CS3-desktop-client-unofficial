package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.runtime.executor.PluginHealthStatus
import kotlinx.coroutines.flow.collectLatest
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun DevStudioView(
    modifier: Modifier = Modifier,
    isDetached: Boolean = false,
    onClose: () -> Unit = { DevStudioState.close() },
    viewModel: DevStudioViewModel = remember { DevStudioViewModel() },
) {
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    val state by viewModel.uiState.collectAsState()
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is DevStudioUiEffect.CopyToClipboard -> {
                    try {
                        val selection = StringSelection(effect.text)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                    } catch (e: Throwable) {
                        // ignore clipboard errors
                    }
                }
                is DevStudioUiEffect.ShowToast -> {
                    snackbarMessage = effect.message
                }
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            kotlinx.coroutines.delay(2500)
            snackbarMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DevBgDark),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.isAdvancedMode) {
                // Simple Mode Header
                DevStudioSimpleTopBar(
                    state = state,
                    isDetached = isDetached,
                    onEvent = viewModel::onEvent,
                    onClose = onClose,
                )

                // Simple Mode Log Table
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    DevStudioLogTable(
                        logs = state.logs,
                        selectedEntry = state.selectedEntry,
                        isPaused = state.isPaused,
                        autoScrollEnabled = state.autoScrollEnabled,
                        searchQuery = "",
                        onSelectEntry = { viewModel.onEvent(DevStudioUiEvent.SelectEntry(it)) },
                        onCopyLine = { viewModel.onEvent(DevStudioUiEvent.CopyLogLine(it)) },
                        onToggleAutoScroll = { viewModel.onEvent(DevStudioUiEvent.ToggleAutoScroll) },
                    )
                }
            } else {
                // Advanced Dev Mode Top Bar with Tabs
                DevStudioTopBar(
                    state = state,
                    isDetached = isDetached,
                    onEvent = viewModel::onEvent,
                    onClose = onClose,
                )

                // Dynamic Tab Content
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (state.currentTab) {
                        DevStudioTab.LOGS -> LogCatTabContent(state = state, onEvent = viewModel::onEvent)
                        DevStudioTab.NETWORK -> NetworkInspectorTabContent(state = state, onEvent = viewModel::onEvent)
                        DevStudioTab.PLAYER -> PlayerDiagnosticsTabContent(state = state, onEvent = viewModel::onEvent)
                        DevStudioTab.PROVIDERS -> ProviderHealthTabContent(state = state, onEvent = viewModel::onEvent)
                    }
                }
            }
        }

        // Floating Toast / Feedback Badge
        AnimatedVisibility(
            visible = snackbarMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            Surface(
                color = Color(0xFF1E2233),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = snackbarMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Top Bar & Tab Navigation
// -------------------------------------------------------------------------------------------------

@Composable
private fun DevStudioTopBar(
    state: DevStudioUiState,
    isDetached: Boolean,
    onEvent: (DevStudioUiEvent) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = DevSurfaceDark,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Title & Tabs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "DevTools Suite",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )

                // Segmented Tabs
                Surface(
                    color = DevBgDark,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                ) {
                    Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        DevStudioTab.entries.forEach { tab ->
                            val isSelected = state.currentTab == tab
                            val errorBadge = when (tab) {
                                DevStudioTab.LOGS -> state.errorCount
                                DevStudioTab.NETWORK -> state.networkErrorCount
                                DevStudioTab.PROVIDERS -> state.pluginHealth.values.count { it.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED }
                                else -> 0
                            }

                            Surface(
                                color = if (isSelected) DevCardDark else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)) else null,
                                modifier = Modifier.clickable { onEvent(DevStudioUiEvent.SwitchTab(tab)) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) DevAccentCyan else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (errorBadge > 0) {
                                        Surface(
                                            color = DevLevelError.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = errorBadge.toString(),
                                                color = DevLevelError,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right: Global Actions
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Switch to Simple Mode
                FilledTonalButton(
                    onClick = { onEvent(DevStudioUiEvent.ToggleAdvancedMode) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DevBgDark,
                        contentColor = Color.LightGray,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Simple Mode", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Copy All Logs
                Button(
                    onClick = { onEvent(DevStudioUiEvent.CopyAllLogs) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy All Logs", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.SemiBold)
                }

                // Bug report
                Button(
                    onClick = { onEvent(DevStudioUiEvent.CopyAiSnapshot(null)) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Bug Report", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.SemiBold)
                }

                // Discord Community
                OutlinedButton(
                    onClick = {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI("https://discord.gg/cgy9GZcdAH"))
                        } catch (e: Throwable) { com.lagradost.common.logging.AppLogger.d("DevStudioView", "Discord browser launch note: ${e.message}") }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Discord (benene)", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.Medium)
                }

                // Pop-out / Dock Toggle
                IconButton(
                    onClick = {
                        if (isDetached) {
                            DevStudioState.dockToMain()
                        } else {
                            DevStudioState.detachToWindow()
                        }
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        if (isDetached) Icons.Default.VerticalAlignBottom else Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Toggle Window Mode",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Dev Studio", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DevStudioSimpleTopBar(
    state: DevStudioUiState,
    isDetached: Boolean,
    onEvent: (DevStudioUiEvent) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = DevSurfaceDark,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Title & Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = DevAccentCyan,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Application Logs",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                // Total Count badge
                Surface(
                    color = DevBgDark,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                ) {
                    Text(
                        text = "${state.totalCount} events",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                // Error badge if > 0
                if (state.errorCount > 0) {
                    Surface(
                        color = DevLevelError.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "${state.errorCount} errors",
                            color = DevLevelError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Right: Simple Actions + Switch to Dev Mode + Window controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Primary: Copy All Logs
                Button(
                    onClick = { onEvent(DevStudioUiEvent.CopyAllLogs) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All Logs", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }

                // Secondary: Copy Bug Report
                OutlinedButton(
                    onClick = { onEvent(DevStudioUiEvent.CopyAiSnapshot(null)) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Copy Bug Report", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.SemiBold)
                }

                // Discord Community
                OutlinedButton(
                    onClick = {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI("https://discord.gg/cgy9GZcdAH"))
                        } catch (e: Throwable) { com.lagradost.common.logging.AppLogger.d("DevStudioView", "Discord browser launch note: ${e.message}") }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Discord (benene)", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.SemiBold)
                }

                // Switch to Advanced Mode
                FilledTonalButton(
                    onClick = { onEvent(DevStudioUiEvent.ToggleAdvancedMode) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DevCardDark,
                        contentColor = Color.LightGray,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Advanced Mode", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Pop-out / Dock Toggle
                IconButton(
                    onClick = {
                        if (isDetached) {
                            DevStudioState.dockToMain()
                        } else {
                            DevStudioState.detachToWindow()
                        }
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        if (isDetached) Icons.Default.VerticalAlignBottom else Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Toggle Window Mode",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
