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
            // Main Top Bar with Tabs & Global Actions
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
