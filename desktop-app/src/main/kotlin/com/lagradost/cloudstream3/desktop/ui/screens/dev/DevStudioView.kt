package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.common.net.NetworkRequestEntry
import com.lagradost.runtime.executor.PluginHealthStats
import com.lagradost.runtime.executor.PluginHealthStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// Dark Studio Palette
private val DevBgDark = Color(0xFF0F1117)
private val DevSurfaceDark = Color(0xFF161822)
private val DevCardDark = Color(0xFF1C1F2D)
private val DevBorderDark = Color(0xFF282C3E)
private val DevAccentCyan = Color(0xFF8BE9FD)
private val DevLevelError = Color(0xFFFF5555)
private val DevLevelWarn = Color(0xFFFFB86C)
private val DevLevelInfo = Color(0xFF8BE9FD)
private val DevLevelDebug = Color(0xFF50FA7B)
private val DevLevelVerbose = Color(0xFF6272A4)
private val DevMethodGet = Color(0xFF50FA7B)
private val DevMethodPost = Color(0xFF8BE9FD)
private val DevMethodOther = Color(0xFFFFB86C)

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

// -------------------------------------------------------------------------------------------------
// TAB 1: LogCat Stream & Live Logs
// -------------------------------------------------------------------------------------------------

@Composable
private fun LogCatTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter & Search Toolbar
        DevStudioLogToolbar(state = state, onEvent = onEvent)

        // Main Log Table + Inspector Drawer
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DevStudioLogTable(
                    logs = state.logs,
                    selectedEntry = state.selectedEntry,
                    isPaused = state.isPaused,
                    autoScrollEnabled = state.autoScrollEnabled,
                    searchQuery = state.searchQuery,
                    onSelectEntry = { onEvent(DevStudioUiEvent.SelectEntry(it)) },
                    onCopyLine = { onEvent(DevStudioUiEvent.CopyLogLine(it)) },
                    onToggleAutoScroll = { onEvent(DevStudioUiEvent.ToggleAutoScroll) },
                )
            }

            val entry = state.selectedEntry
            if (state.isInspectorOpen && entry != null) {
                Box(
                    modifier = Modifier
                        .width(440.dp)
                        .fillMaxHeight()
                        .background(DevSurfaceDark)
                        .border(1.dp, DevBorderDark),
                ) {
                    DevStudioLogInspector(
                        entry = entry,
                        pluginHealth = state.pluginHealth,
                        onClose = { onEvent(DevStudioUiEvent.CloseInspector) },
                        onCopyAiSnapshot = { onEvent(DevStudioUiEvent.CopyAiSnapshot(entry.id)) },
                        onResetCircuit = { onEvent(DevStudioUiEvent.ResetCircuit(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DevStudioLogToolbar(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Surface(
        color = Color(0xFF13151F),
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search Input with Regex and Case Sensitive Toggles
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(DevBgDark, RoundedCornerShape(6.dp))
                        .border(1.dp, DevBorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                "Search logs, tags, regex, threads...",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { onEvent(DevStudioUiEvent.UpdateSearchQuery(it)) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(DevAccentCyan),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Regex Toggle
                    Surface(
                        color = if (state.isRegexSearch) DevAccentCyan.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleRegexSearch) },
                    ) {
                        Text(
                            text = ".*",
                            color = if (state.isRegexSearch) DevAccentCyan else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Case Sensitivity Toggle
                    Surface(
                        color = if (state.isCaseSensitiveSearch) DevAccentCyan.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleCaseSensitiveSearch) },
                    ) {
                        Text(
                            text = "Aa",
                            color = if (state.isCaseSensitiveSearch) DevAccentCyan else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    if (state.searchQuery.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp).clickable { onEvent(DevStudioUiEvent.UpdateSearchQuery("")) },
                        )
                    }
                }

                // Subsystem Dropdown
                SubsystemPicker(
                    selectedSubsystem = state.selectedSubsystem,
                    onSelect = { onEvent(DevStudioUiEvent.SelectSubsystem(it)) },
                )

                // Plugin Filter Dropdown
                if (state.availablePlugins.isNotEmpty()) {
                    PluginPicker(
                        selectedPlugin = state.selectedPlugin,
                        availablePlugins = state.availablePlugins,
                        onSelect = { onEvent(DevStudioUiEvent.SelectPlugin(it)) },
                    )
                }

                // Pause / Resume Toggle
                Button(
                    onClick = { onEvent(DevStudioUiEvent.TogglePause) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isPaused) DevLevelWarn.copy(alpha = 0.25f) else Color(0xFF23273A)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = if (state.isPaused) DevLevelWarn else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.isPaused) "Resume" else "Pause", fontSize = 11.sp, color = Color.White)
                }

                // Export Logs
                OutlinedButton(
                    onClick = { onEvent(DevStudioUiEvent.ExportLogs) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Export", fontSize = 11.sp, color = Color.LightGray)
                }

                // Clear
                OutlinedButton(
                    onClick = { onEvent(DevStudioUiEvent.ClearLogs) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Clear", fontSize = 11.sp, color = Color.LightGray)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Level Filter Pills & Auto-Scroll status
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Level:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Surface(
                        color = if (state.exceptionsOnly) DevLevelError else DevBgDark,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = if (state.exceptionsOnly) 1f else 0.5f)),
                        modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleExceptionsOnly) },
                    ) {
                        Text(
                            "🔥 CRASHES ONLY",
                            color = if (state.exceptionsOnly) Color.White else DevLevelError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    LevelFilterChip("ALL", state.totalCount, state.selectedLevel == LogLevel.VERBOSE, Color.LightGray) {
                        onEvent(DevStudioUiEvent.SelectLevel(LogLevel.VERBOSE))
                    }
                    LevelFilterChip("ERROR", state.errorCount, state.selectedLevel == LogLevel.ERROR, DevLevelError) {
                        onEvent(DevStudioUiEvent.SelectLevel(LogLevel.ERROR))
                    }
                    LevelFilterChip("WARN", state.warnCount, state.selectedLevel == LogLevel.WARN, DevLevelWarn) {
                        onEvent(DevStudioUiEvent.SelectLevel(LogLevel.WARN))
                    }
                    LevelFilterChip("INFO", state.infoCount, state.selectedLevel == LogLevel.INFO, DevLevelInfo) {
                        onEvent(DevStudioUiEvent.SelectLevel(LogLevel.INFO))
                    }
                    LevelFilterChip("DEBUG", state.debugCount, state.selectedLevel == LogLevel.DEBUG, DevLevelDebug) {
                        onEvent(DevStudioUiEvent.SelectLevel(LogLevel.DEBUG))
                    }
                }

                // Auto-Scroll Toggle Pill
                Surface(
                    color = if (state.autoScrollEnabled) DevLevelDebug.copy(alpha = 0.15f) else Color(0xFF202330),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (state.autoScrollEnabled) DevLevelDebug.copy(alpha = 0.4f) else DevBorderDark),
                    modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleAutoScroll) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape).background(if (state.autoScrollEnabled) DevLevelDebug else Color.Gray),
                        )
                        Text(
                            text = if (state.autoScrollEnabled) "Auto-Scroll ON" else "Auto-Scroll OFF",
                            color = if (state.autoScrollEnabled) DevLevelDebug else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevStudioLogTable(
    logs: List<LogEntry>,
    selectedEntry: LogEntry?,
    isPaused: Boolean,
    autoScrollEnabled: Boolean,
    searchQuery: String,
    onSelectEntry: (LogEntry) -> Unit,
    onCopyLine: (LogEntry) -> Unit,
    onToggleAutoScroll: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to latest entry ONLY if auto-scroll is enabled and not paused
    LaunchedEffect(logs.size, isPaused, autoScrollEnabled) {
        if (autoScrollEnabled && !isPaused && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val isScrolledToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) {
                true
            } else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 2
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs matching current filters", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(logs, key = { it.id }) { entry ->
                        DevStudioLogRow(
                            entry = entry,
                            isSelected = entry.id == selectedEntry?.id,
                            searchQuery = searchQuery,
                            onClick = { onSelectEntry(entry) },
                            onCopy = { onCopyLine(entry) },
                        )
                    }
                }
            }
        }

        // Floating "Jump to Latest" button if scrolled up
        if (!isScrolledToBottom && logs.isNotEmpty()) {
            Surface(
                color = Color(0xFF23283E),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clickable {
                        scope.launch {
                            listState.animateScrollToItem(logs.size - 1)
                            if (!autoScrollEnabled) onToggleAutoScroll()
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
                    Text("Jump to Latest", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DevStudioLogRow(
    entry: LogEntry,
    isSelected: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> DevLevelError
        LogLevel.WARN -> DevLevelWarn
        LogLevel.INFO -> DevLevelInfo
        LogLevel.DEBUG -> DevLevelDebug
        LogLevel.VERBOSE -> DevLevelVerbose
    }

    val rowBg = if (isSelected) {
        Color(0xFF23283E)
    } else if (entry.level == LogLevel.ERROR) {
        DevLevelError.copy(alpha = 0.08f)
    } else if (entry.level == LogLevel.WARN) {
        DevLevelWarn.copy(alpha = 0.04f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entry.level == LogLevel.ERROR) {
            Spacer(modifier = Modifier.width(3.dp).height(18.dp).background(DevLevelError))
            Spacer(modifier = Modifier.width(3.dp))
        } else {
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Timestamp
        Text(
            text = entry.formattedTime,
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        // Level Badge
        Surface(
            color = levelColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.width(20.dp).height(18.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.level.shortLabel,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Subsystem Chip
        Surface(
            color = Color(0xFF1E2130),
            shape = RoundedCornerShape(3.dp),
        ) {
            Text(
                text = entry.subsystem.displayName.substringBefore(" "),
                color = Color.LightGray,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        // Tag
        Text(
            text = "[${entry.tag}]",
            color = DevAccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )

        // Thread
        Text(
            text = entry.threadName,
            color = Color(0xFF6272A4),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 110.dp),
        )

        // Highlighted Message Text
        val messageText = buildAnnotatedString {
            val full = entry.message
            if (searchQuery.isNotEmpty() && full.contains(searchQuery, ignoreCase = true)) {
                var currentIndex = 0
                val qLower = searchQuery.lowercase()
                val fLower = full.lowercase()
                while (currentIndex < full.length) {
                    val matchIndex = fLower.indexOf(qLower, currentIndex)
                    if (matchIndex == -1) {
                        append(full.substring(currentIndex))
                        break
                    }
                    append(full.substring(currentIndex, matchIndex))
                    withStyle(SpanStyle(background = Color(0xFF5E4B27), color = Color(0xFFF1FA8C), fontWeight = FontWeight.Bold)) {
                        append(full.substring(matchIndex, matchIndex + searchQuery.length))
                    }
                    currentIndex = matchIndex + searchQuery.length
                }
            } else {
                append(full)
            }
        }

        Text(
            text = messageText,
            color = if (entry.level == LogLevel.ERROR) DevLevelError else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Inline Quick Actions
        IconButton(onClick = onCopy, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Line", tint = Color.Gray, modifier = Modifier.size(12.dp))
        }

        // Exception Indicator
        if (entry.throwable != null) {
            Surface(
                color = DevLevelError.copy(alpha = 0.2f),
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = 0.5f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = DevLevelError, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("STACKTRACE", color = DevLevelError, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DevStudioLogInspector(
    entry: LogEntry,
    pluginHealth: Map<String, PluginHealthStats> = emptyMap(),
    onClose: () -> Unit,
    onCopyAiSnapshot: () -> Unit,
    onResetCircuit: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Log Inspector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Inspector", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Metadata grid
        InspectorField("Timestamp", entry.formattedTime)
        InspectorField("Level", entry.level.name)
        InspectorField("Subsystem", entry.subsystem.displayName)
        InspectorField("Tag", entry.tag)
        InspectorField("Thread", entry.threadName)
        val plugin = entry.pluginName
        if (plugin != null) {
            InspectorField("Plugin", plugin)
        }

        // Full Message Payload
        Spacer(Modifier.height(10.dp))
        Text("Message Payload:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        val mapper = remember { jacksonObjectMapper() }
        val jsonNode = remember(entry.message) {
            try {
                val trimmed = entry.message.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    mapper.readTree(trimmed)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        SelectionContainer {
            Surface(
                color = DevBgDark,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (jsonNode != null) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        JsonNodeViewer(jsonNode)
                    }
                } else {
                    Text(
                        text = entry.message,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        // Stack Trace
        if (entry.throwable != null) {
            Spacer(Modifier.height(12.dp))
            Text("Exception & Stack Trace:", color = DevLevelError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Surface(
                    color = DevBgDark,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val traceString = entry.stackTraceString ?: (entry.throwable?.toString().orEmpty())
                    StackTraceViewer(traceString)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onCopyAiSnapshot,
            colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevBgDark, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy Bug Report Snapshot", color = DevBgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 2: Network Inspector (Chrome DevTools Style)
// -------------------------------------------------------------------------------------------------

@Composable
private fun NetworkInspectorTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Network Toolbar
        Surface(
            color = Color(0xFF13151F),
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Search
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(DevBgDark, RoundedCornerShape(6.dp))
                            .border(1.dp, DevBorderDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (state.networkSearchQuery.isEmpty()) {
                                Text(
                                    "Filter URL, host, path, status...",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = state.networkSearchQuery,
                                onValueChange = { onEvent(DevStudioUiEvent.UpdateNetworkSearchQuery(it)) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(DevAccentCyan),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Pause Network recording
                    Button(
                        onClick = { onEvent(DevStudioUiEvent.ToggleNetworkPause) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (state.isNetworkPaused) DevLevelWarn.copy(alpha = 0.25f) else Color(0xFF23273A)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(
                            if (state.isNetworkPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (state.isNetworkPaused) DevLevelWarn else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isNetworkPaused) "Resume" else "Pause", fontSize = 11.sp, color = Color.White)
                    }

                    // Export
                    OutlinedButton(
                        onClick = { onEvent(DevStudioUiEvent.ExportNetworkLogs) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Export", fontSize = 11.sp, color = Color.LightGray)
                    }

                    // Clear
                    OutlinedButton(
                        onClick = { onEvent(DevStudioUiEvent.ClearNetworkLogs) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Clear", fontSize = 11.sp, color = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Method filter chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Method:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    val methods = listOf(null, "GET", "POST", "HEAD", "PUT", "DELETE")
                    methods.forEach { m ->
                        val isSelected = state.networkMethodFilter == m
                        val label = m ?: "ALL"
                        Surface(
                            color = if (isSelected) DevAccentCyan.copy(alpha = 0.25f) else DevBgDark,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) DevAccentCyan else DevBorderDark),
                            modifier = Modifier.clickable { onEvent(DevStudioUiEvent.SelectNetworkMethodFilter(m)) },
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DevAccentCyan else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        color = if (state.networkErrorsOnly) DevLevelError else DevBgDark,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = if (state.networkErrorsOnly) 1f else 0.5f)),
                        modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleNetworkErrorsOnly) },
                    ) {
                        Text(
                            "ERRORS ONLY (${state.networkErrorCount})",
                            color = if (state.networkErrorsOnly) Color.White else DevLevelError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        // Main Table + Inspector Drawer
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NetworkRequestsTable(
                    requests = state.networkRequests,
                    selectedRequest = state.selectedNetworkRequest,
                    onSelect = { onEvent(DevStudioUiEvent.SelectNetworkRequest(it)) },
                    onCopyCurl = { onEvent(DevStudioUiEvent.CopyCurlCommand(it)) },
                )
            }

            val req = state.selectedNetworkRequest
            if (state.isNetworkInspectorOpen && req != null) {
                Box(
                    modifier = Modifier
                        .width(460.dp)
                        .fillMaxHeight()
                        .background(DevSurfaceDark)
                        .border(1.dp, DevBorderDark),
                ) {
                    NetworkRequestInspector(
                        request = req,
                        onClose = { onEvent(DevStudioUiEvent.CloseNetworkInspector) },
                        onCopyCurl = { onEvent(DevStudioUiEvent.CopyCurlCommand(req)) },
                        onCopyPayload = { text, label -> onEvent(DevStudioUiEvent.CopyTextPayload(text, label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRequestsTable(
    requests: List<NetworkRequestEntry>,
    selectedRequest: NetworkRequestEntry?,
    onSelect: (NetworkRequestEntry) -> Unit,
    onCopyCurl: (NetworkRequestEntry) -> Unit,
) {
    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No network traffic matching filters", color = Color.Gray, fontSize = 13.sp)
        }
    } else {
        SelectionContainer {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(requests, key = { it.id }) { req ->
                    NetworkRequestRow(
                        request = req,
                        isSelected = req.id == selectedRequest?.id,
                        onClick = { onSelect(req) },
                        onCopyCurl = { onCopyCurl(req) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRequestRow(
    request: NetworkRequestEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopyCurl: () -> Unit,
) {
    val methodColor = when (request.method.uppercase()) {
        "GET" -> DevMethodGet
        "POST" -> DevMethodPost
        else -> DevMethodOther
    }

    val statusColor = when {
        request.isPending -> DevLevelWarn
        request.isSuccess -> DevLevelDebug
        request.isRedirect -> DevAccentCyan
        request.isClientError -> DevLevelWarn
        request.isServerError -> DevLevelError
        else -> Color.LightGray
    }

    val rowBg = if (isSelected) {
        Color(0xFF23283E)
    } else if (request.isError) {
        DevLevelError.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status Code Pill
        Surface(
            color = statusColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
            modifier = Modifier.width(42.dp).height(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (request.statusCode == -1) "FAIL" else request.statusCode.toString(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Method Pill
        Surface(
            color = methodColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(42.dp).height(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = request.method.uppercase(),
                    color = methodColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // URL Host & Path
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.url,
                color = if (request.isError) DevLevelError else Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (request.error != null) {
                Text(
                    text = "Error: ${request.error}",
                    color = DevLevelWarn,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Latency
        Text(
            text = "${request.durationMs}ms",
            color = if (request.durationMs > 2000) DevLevelWarn else Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(55.dp),
        )

        // Size
        Text(
            text = request.formattedSize,
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp),
        )

        // Timestamp
        Text(
            text = request.formattedTime,
            color = Color.DarkGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        // Copy cURL button
        IconButton(onClick = onCopyCurl, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Default.Terminal, contentDescription = "Copy cURL", tint = Color.Gray, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun NetworkRequestInspector(
    request: NetworkRequestEntry,
    onClose: () -> Unit,
    onCopyCurl: () -> Unit,
    onCopyPayload: (String, String) -> Unit,
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Headers, 1: Request Body, 2: Response Body

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Request Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Summary Card
        Surface(
            color = DevBgDark,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                InspectorField("URL", request.url)
                InspectorField("Method", request.method)
                InspectorField("Status", "${request.statusCode} ${request.statusMessage}")
                InspectorField("Latency", "${request.durationMs} ms")
                InspectorField("Size", request.formattedSize)
                request.contentType?.let {
                    InspectorField("Content-Type", it)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // cURL Button
        Button(
            onClick = onCopyCurl,
            colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy as cURL Command", color = DevAccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // Section Tabs: Headers | Request Body | Response Body
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Headers", "Request Body", "Response Body").forEachIndexed { index, title ->
                val isSel = selectedSection == index
                Surface(
                    color = if (isSel) DevCardDark else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.weight(1f).clickable { selectedSection = index },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = title,
                            color = if (isSel) DevAccentCyan else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (selectedSection) {
            0 -> {
                // Headers Section
                Text("Request Headers (${request.requestHeaders.size}):", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    HeadersTable(request.requestHeaders)
                }

                Spacer(Modifier.height(12.dp))
                Text("Response Headers (${request.responseHeaders.size}):", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    HeadersTable(request.responseHeaders)
                }
            }
            1 -> {
                // Request Body Section
                val body = request.requestBody
                if (body.isNullOrBlank()) {
                    Text("No request body present", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Payload:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onCopyPayload(body, "Request Body") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Surface(
                            color = DevBgDark,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = body,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
            2 -> {
                // Response Body Section
                val resp = request.responseBody
                if (resp.isNullOrBlank()) {
                    Text("No response body captured", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Response Data:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onCopyPayload(resp, "Response Body") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Surface(
                            color = DevBgDark,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = resp,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadersTable(headers: Map<String, String>) {
    Surface(
        color = DevBgDark,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (headers.isEmpty()) {
            Text("Empty headers", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
                headers.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(k, color = DevAccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(max = 140.dp))
                        Text(v, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 3: Live Player & Stream Diagnostics
// -------------------------------------------------------------------------------------------------

@Composable
private fun PlayerDiagnosticsTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    val diag = state.playerDiagnostics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live MPV Player & Stream Diagnostics", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (diag.isAttached) DevLevelDebug.copy(alpha = 0.2f) else DevLevelWarn.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = if (diag.isAttached) "PLAYER ATTACHED" else "PLAYER IDLE",
                        color = if (diag.isAttached) DevLevelDebug else DevLevelWarn,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = { onEvent(DevStudioUiEvent.RefreshPlayerDiagnostics) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh", fontSize = 11.sp, color = Color.LightGray)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Grid Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card 1: Playback State
            DiagnosticCard(
                title = "Playback Engine",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField(
                    "Status",
                    if (diag.isBuffering) {
                        "Buffering..."
                    } else if (diag.isPaused) {
                        "Paused"
                    } else {
                        "Playing"
                    },
                )
                InspectorField("Position", "${diag.positionMs / 1000}s / ${diag.durationMs / 1000}s")
                InspectorField("Buffer Ahead", "${diag.bufferMs / 1000}s")
                InspectorField("Probing Active", if (diag.isProbing) "YES" else "NO")
                InspectorField("Speed / Volume", "${diag.playbackSpeed}x • ${diag.volume.toInt()}%")
            }

            // Card 2: Video & Decoder
            DiagnosticCard(
                title = "Video & Decoder",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Resolution", diag.resolution.ifBlank { "N/A" })
                InspectorField("Video Codec", diag.videoCodec.ifBlank { "N/A" })
                InspectorField("HW Decoder", diag.hwdec.ifBlank { "Auto / CPU" })
                InspectorField("FPS / Dropped", "${"%.1f".format(diag.fps)} fps • ${diag.droppedFrames} dropped")
                InspectorField("Bitrate", if (diag.videoBitrate > 0) "${diag.videoBitrate / 1000} kbps" else "N/A")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card 3: Audio & Tracks
            DiagnosticCard(
                title = "Audio & Track Stream",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Audio Codec", diag.audioCodec.ifBlank { "N/A" })
                InspectorField("Audio Bitrate", if (diag.audioBitrate > 0) "${diag.audioBitrate / 1000} kbps" else "N/A")
                InspectorField("Subtitles Loaded", "${diag.subtitleTracksCount} tracks")
                InspectorField("Audio Tracks Loaded", "${diag.audioTracksCount} tracks")
                InspectorField("Qualities Loaded", "${diag.videoTracksCount} variants")
            }

            // Card 4: Proxy & Shaders
            DiagnosticCard(
                title = "Stream Proxy & Shaders",
                modifier = Modifier.weight(1f),
            ) {
                InspectorField("Active Shader", diag.activeShader)
                InspectorField("Interpolation (60fps)", if (diag.isInterpolationEnabled) "ENABLED" else "DISABLED")
                InspectorField("Discovered Proxy Audio", "${diag.proxyAudioTracksCount} tracks")
                InspectorField("Discovered Proxy Subs", "${diag.proxySubtitleTracksCount} tracks")
                InspectorField("Discovered Proxy Qualities", "${diag.proxyVideoTracksCount} variants")
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = DevCardDark,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = DevAccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 4: Provider Health & Circuit Breakers
// -------------------------------------------------------------------------------------------------

@Composable
private fun ProviderHealthTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plugin & Provider Circuit Breakers", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            if (state.pluginHealth.isNotEmpty()) {
                Button(
                    onClick = { onEvent(DevStudioUiEvent.ResetAllCircuits) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Reset All Circuits", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (state.pluginHealth.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("No provider traffic recorded yet in this session", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.pluginHealth.values.sortedBy { it.providerName }.forEach { health ->
                    ProviderHealthDetailCard(
                        health = health,
                        onReset = { onEvent(DevStudioUiEvent.ResetCircuit(health.providerName)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderHealthDetailCard(
    health: PluginHealthStats,
    onReset: () -> Unit,
) {
    val (statusColor, statusLabel) = when (health.status) {
        PluginHealthStatus.HEALTHY -> Pair(DevLevelDebug, "HEALTHY")
        PluginHealthStatus.DEGRADED -> Pair(DevLevelWarn, "DEGRADED (${health.consecutiveFailures} fails)")
        PluginHealthStatus.TRIPPED_AUTO_DISABLED -> Pair(DevLevelError, "TRIPPED / BLOCKED")
        PluginHealthStatus.HALF_OPEN -> Pair(DevAccentCyan, "HALF-OPEN (TESTING)")
    }

    Surface(
        color = DevCardDark,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) DevLevelError else DevBorderDark,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(health.providerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Calls: ${health.totalCalls} (${health.successfulCalls} OK / ${health.failedCalls} Failed) • Avg Latency: ${health.averageLatencyMs}ms",
                    color = Color.Gray,
                    fontSize = 11.sp,
                )

                if (health.lastFailureReason != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Last Failure: ${health.lastFailureReason}",
                        color = DevLevelWarn,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED || health.status == PluginHealthStatus.DEGRADED) {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Reset Circuit", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Common Helper Components
// -------------------------------------------------------------------------------------------------

@Composable
private fun LevelFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.25f) else DevBgDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) color else DevBorderDark),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (isSelected) color else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            if (count > 0) {
                Surface(
                    color = color.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = count.toString(),
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsystemPicker(
    selectedSubsystem: LogSubsystem,
    onSelect: (LogSubsystem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = DevBgDark,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
            modifier = Modifier.clickable { expanded = true }.height(36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
                Text(selectedSubsystem.displayName, color = Color.White, fontSize = 11.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DevSurfaceDark),
        ) {
            LogSubsystem.entries.forEach { sub ->
                DropdownMenuItem(
                    text = { Text(sub.displayName, color = if (sub == selectedSubsystem) DevAccentCyan else Color.White, fontSize = 12.sp) },
                    onClick = {
                        onSelect(sub)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginPicker(
    selectedPlugin: String?,
    availablePlugins: List<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = DevBgDark,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedPlugin != null) DevAccentCyan else DevBorderDark),
            modifier = Modifier.clickable { expanded = true }.height(36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                Text(selectedPlugin ?: "All Plugins", color = if (selectedPlugin != null) DevAccentCyan else Color.White, fontSize = 11.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DevSurfaceDark),
        ) {
            DropdownMenuItem(
                text = { Text("All Plugins", color = if (selectedPlugin == null) DevAccentCyan else Color.White, fontSize = 12.sp) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            availablePlugins.forEach { plugin ->
                DropdownMenuItem(
                    text = { Text(plugin, color = if (plugin == selectedPlugin) DevAccentCyan else Color.White, fontSize = 12.sp) },
                    onClick = {
                        onSelect(plugin)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun InspectorField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JsonNodeViewer(node: JsonNode, depth: Int = 0) {
    val padding = depth * 12
    when {
        node.isObject -> {
            var expanded by remember { mutableStateOf(depth < 2) }
            val fieldNames = node.fieldNames().asSequence().toList()
            Column(modifier = Modifier.padding(start = padding.dp)) {
                Row(
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("{...} ${fieldNames.size} keys", color = DevLevelVerbose, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (expanded) {
                    fieldNames.forEach { key ->
                        val child = node.get(key)
                        if (child.isObject || child.isArray) {
                            Text(
                                "\"$key\":",
                                color = DevAccentCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = (padding + 12).dp),
                            )
                            JsonNodeViewer(child, depth + 1)
                        } else {
                            Row(modifier = Modifier.padding(start = (padding + 12).dp)) {
                                Text("\"$key\": ", color = DevAccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                val valueColor = when {
                                    child.isTextual -> Color(0xFFF1FA8C)
                                    child.isNumber -> Color(0xFFFFB86C)
                                    child.isBoolean -> Color(0xFF8BE9FD)
                                    child.isNull -> Color(0xFFFF5555)
                                    else -> Color.White
                                }
                                Text(
                                    child.asText(),
                                    color = valueColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
        node.isArray -> {
            var expanded by remember { mutableStateOf(depth < 2) }
            Column(modifier = Modifier.padding(start = padding.dp)) {
                Row(
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("[...] ${node.size()} items", color = DevLevelVerbose, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (expanded) {
                    node.forEachIndexed { index, child ->
                        if (child.isObject || child.isArray) {
                            Text(
                                "[$index]:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = (padding + 12).dp),
                            )
                            JsonNodeViewer(child, depth + 1)
                        } else {
                            Row(modifier = Modifier.padding(start = (padding + 12).dp)) {
                                Text("[$index]: ", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                val valueColor = when {
                                    child.isTextual -> Color(0xFFF1FA8C)
                                    child.isNumber -> Color(0xFFFFB86C)
                                    child.isBoolean -> Color(0xFF8BE9FD)
                                    child.isNull -> Color(0xFFFF5555)
                                    else -> Color.White
                                }
                                Text(
                                    child.asText(),
                                    color = valueColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Text(node.asText(), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = padding.dp))
        }
    }
}

@Composable
private fun StackTraceViewer(stackTrace: String) {
    val lines = stackTrace.lines()
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        lines.forEach { line ->
            val isCore = line.contains("com.lagradost")
            val color = when {
                isCore -> DevLevelError
                line.contains("android.") || line.contains("java.") -> Color.Gray
                line.contains("kotlin.") -> Color.Gray
                else -> Color(0xFFFF8888)
            }
            Text(
                text = line.trim(),
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isCore) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
