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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.runtime.executor.PluginHealthStats
import com.lagradost.runtime.executor.PluginHealthStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

// Dark Studio Palette
private val DevBgDark = Color(0xFF0F1117)
private val DevSurfaceDark = Color(0xFF161822)
private val DevBorderDark = Color(0xFF282C3E)
private val DevAccentCyan = Color(0xFF8BE9FD)
private val DevLevelError = Color(0xFFFF5555)
private val DevLevelWarn = Color(0xFFFFB86C)
private val DevLevelInfo = Color(0xFF8BE9FD)
private val DevLevelDebug = Color(0xFF50FA7B)
private val DevLevelVerbose = Color(0xFF6272A4)

@Composable
fun DevStudioView(
    modifier: Modifier = Modifier,
    isDetached: Boolean = false,
    onClose: () -> Unit = { DevStudioState.close() },
    viewModel: DevStudioViewModel = remember { DevStudioViewModel() },
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
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
            .background(DevBgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            DevStudioTopBar(
                state = state,
                isDetached = isDetached,
                onEvent = viewModel::onEvent,
                onClose = onClose,
            )

            // Filter & Search Toolbar
            DevStudioToolbar(
                state = state,
                onEvent = viewModel::onEvent,
            )

            // Main Content Area: Log Stream + Inspector Drawer
            Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                // Log Table
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    DevStudioLogTable(
                        logs = state.logs,
                        selectedEntry = state.selectedEntry,
                        isPaused = state.isPaused,
                        onSelectEntry = { viewModel.onEvent(DevStudioUiEvent.SelectEntry(it)) },
                    )
                }

                // Inspector Drawer
                if (state.isInspectorOpen && state.selectedEntry != null) {
                    Box(
                        modifier = Modifier
                            .width(420.dp)
                            .fillMaxHeight()
                            .background(DevSurfaceDark)
                            .border(1.dp, DevBorderDark)
                    ) {
                        DevStudioInspector(
                            entry = state.selectedEntry!!,
                            pluginHealth = state.pluginHealth,
                            onClose = { viewModel.onEvent(DevStudioUiEvent.CloseInspector) },
                            onCopyAiSnapshot = { viewModel.onEvent(DevStudioUiEvent.CopyAiSnapshot(state.selectedEntry?.id)) },
                            onResetCircuit = { viewModel.onEvent(DevStudioUiEvent.ResetCircuit(it)) },
                        )
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Title & Status
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dev Studio & Live LogCat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Surface(
                    color = if (state.isPaused) DevLevelWarn.copy(alpha = 0.2f) else DevLevelDebug.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = if (state.isPaused) "PAUSED" else "LIVE",
                        color = if (state.isPaused) DevLevelWarn else DevLevelDebug,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = "${state.logs.size} / ${state.totalCount} events",
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Pause / Resume Toggle
                Button(
                    onClick = { onEvent(DevStudioUiEvent.TogglePause) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isPaused) DevLevelWarn.copy(alpha = 0.25f) else Color(0xFF23273A)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
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

                // Copy Bug Report
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

                // Export Logs
                OutlinedButton(
                    onClick = { onEvent(DevStudioUiEvent.ExportLogs) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Export", fontSize = 11.sp, color = Color.LightGray)
                }

                // Clear
                OutlinedButton(
                    onClick = { onEvent(DevStudioUiEvent.ClearLogs) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Clear", fontSize = 11.sp, color = Color.LightGray)
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
                        if (isDetached) Icons.Default.VerticalAlignBottom else Icons.Default.OpenInNew,
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
private fun DevStudioToolbar(
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Custom Search Input
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
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(DevBgDark, RoundedCornerShape(6.dp))
                                .border(1.dp, DevBorderDark, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (state.searchQuery.isEmpty()) {
                                    Text(
                                        "Filter logs, tags, regex, threads...",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                            if (state.searchQuery.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onEvent(DevStudioUiEvent.UpdateSearchQuery("")) }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Subsystem Dropdown
                SubsystemPicker(
                    selectedSubsystem = state.selectedSubsystem,
                    onSelect = { onEvent(DevStudioUiEvent.SelectSubsystem(it)) },
                )

                // Plugin Filter Dropdown (if plugins detected)
                if (state.availablePlugins.isNotEmpty()) {
                    PluginPicker(
                        selectedPlugin = state.selectedPlugin,
                        availablePlugins = state.availablePlugins,
                        onSelect = { onEvent(DevStudioUiEvent.SelectPlugin(it)) },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Level Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Level:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Surface(
                    color = if (state.exceptionsOnly) DevLevelError else DevBgDark,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = if (state.exceptionsOnly) 1f else 0.5f)),
                    modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleExceptionsOnly) }
                ) {
                    Text(
                        "🔥 CRASHES ONLY",
                        color = if (state.exceptionsOnly) Color.White else DevLevelError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                LevelFilterChip(
                    label = "ALL",
                    count = state.totalCount,
                    isSelected = state.selectedLevel == LogLevel.VERBOSE,
                    color = Color.LightGray,
                    onClick = { onEvent(DevStudioUiEvent.SelectLevel(LogLevel.VERBOSE)) },
                )

                LevelFilterChip(
                    label = "ERROR",
                    count = state.errorCount,
                    isSelected = state.selectedLevel == LogLevel.ERROR,
                    color = DevLevelError,
                    onClick = { onEvent(DevStudioUiEvent.SelectLevel(LogLevel.ERROR)) },
                )

                LevelFilterChip(
                    label = "WARN",
                    count = state.warnCount,
                    isSelected = state.selectedLevel == LogLevel.WARN,
                    color = DevLevelWarn,
                    onClick = { onEvent(DevStudioUiEvent.SelectLevel(LogLevel.WARN)) },
                )

                LevelFilterChip(
                    label = "INFO",
                    count = state.infoCount,
                    isSelected = state.selectedLevel == LogLevel.INFO,
                    color = DevLevelInfo,
                    onClick = { onEvent(DevStudioUiEvent.SelectLevel(LogLevel.INFO)) },
                )

                LevelFilterChip(
                    label = "DEBUG",
                    count = state.debugCount,
                    isSelected = state.selectedLevel == LogLevel.DEBUG,
                    color = DevLevelDebug,
                    onClick = { onEvent(DevStudioUiEvent.SelectLevel(LogLevel.DEBUG)) },
                )
            }

            // Circuit Breaker & Provider Health Status Row
            if (state.pluginHealth.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Circuits:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    state.pluginHealth.values.sortedBy { it.providerName }.forEach { health ->
                        PluginHealthChip(
                            health = health,
                            isSelected = state.selectedPlugin == health.providerName,
                            onClick = {
                                onEvent(
                                    DevStudioUiEvent.SelectPlugin(
                                        if (state.selectedPlugin == health.providerName) null else health.providerName
                                    )
                                )
                            },
                            onReset = { onEvent(DevStudioUiEvent.ResetCircuit(health.providerName)) },
                        )
                    }

                    if (state.pluginHealth.values.any { it.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED }) {
                        OutlinedButton(
                            onClick = { onEvent(DevStudioUiEvent.ResetAllCircuits) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLevelError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = 0.5f)),
                            modifier = Modifier.height(26.dp),
                        ) {
                            Text("Reset All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

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
            modifier = Modifier.clickable { expanded = true }.height(40.dp),
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
            modifier = Modifier.clickable { expanded = true }.height(40.dp),
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
private fun DevStudioLogTable(
    logs: List<LogEntry>,
    selectedEntry: LogEntry?,
    isPaused: Boolean,
    onSelectEntry: (LogEntry) -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest entry if not paused
    LaunchedEffect(logs.size, isPaused) {
        if (!isPaused && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

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
                        onClick = { onSelectEntry(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DevStudioLogRow(
    entry: LogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
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
            .then(
                if (entry.level == LogLevel.ERROR && !isSelected) Modifier.border(width = 0.dp, color = Color.Transparent) // We'll just use the background, but add a spacer
                else Modifier
            )
            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Red Accent Left Border for Errors
        if (entry.level == LogLevel.ERROR) {
            Spacer(modifier = Modifier.width(4.dp).height(18.dp).background(DevLevelError))
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(12.dp))
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

        // Message
        Text(
            text = entry.message,
            color = if (entry.level == LogLevel.ERROR) DevLevelError else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Exception Indicator / Stacktrace Action
        if (entry.throwable != null) {
            Surface(
                color = DevLevelError.copy(alpha = 0.2f),
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = DevLevelError, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "VIEW STACKTRACE",
                        color = DevLevelError,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun DevStudioInspector(
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
        // Header
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

        // Provider Health Box in Inspector
        val health = plugin?.let { pluginHealth[it] }
        if (plugin != null && health != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = DevBgDark,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) DevLevelError else DevBorderDark
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Provider Circuit Status", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val statusColor = when (health.status) {
                            PluginHealthStatus.HEALTHY -> DevLevelDebug
                            PluginHealthStatus.DEGRADED -> DevLevelWarn
                            PluginHealthStatus.TRIPPED_AUTO_DISABLED -> DevLevelError
                            PluginHealthStatus.HALF_OPEN -> DevAccentCyan
                        }
                        Text(health.status.name, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Calls: ${health.totalCalls} (${health.successfulCalls} ok / ${health.failedCalls} fail) • Avg: ${health.averageLatencyMs}ms",
                        color = Color.Gray,
                        fontSize = 10.sp,
                    )
                    if (health.lastFailureReason != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Last Error: ${health.lastFailureReason}",
                            color = DevLevelWarn,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED || health.status == PluginHealthStatus.DEGRADED) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onResetCircuit(plugin) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevAccentCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(28.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Reset Circuit Breaker", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Full Message
        Text("Message (Payload):", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        
        val mapper = remember { jacksonObjectMapper() }
        val jsonNode = remember(entry.message) {
            try {
                val trimmed = entry.message.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    mapper.readTree(trimmed)
                } else null
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

        // Stack Trace (if present)
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
                    val traceString = entry.stackTraceString ?: entry.throwable.toString()
                    StackTraceViewer(traceString)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action Buttons
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

@Composable
private fun InspectorField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PluginHealthChip(
    health: PluginHealthStats,
    isSelected: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
) {
    val (statusColor, statusLabel) = when (health.status) {
        PluginHealthStatus.HEALTHY -> Pair(DevLevelDebug, if (health.averageLatencyMs > 0) "${health.averageLatencyMs}ms" else "OK")
        PluginHealthStatus.DEGRADED -> Pair(DevLevelWarn, "${health.consecutiveFailures} FAIL")
        PluginHealthStatus.TRIPPED_AUTO_DISABLED -> Pair(DevLevelError, "TRIPPED")
        PluginHealthStatus.HALF_OPEN -> Pair(DevAccentCyan, "TESTING")
    }

    Surface(
        color = if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) DevLevelError.copy(alpha = 0.2f)
                else if (isSelected) statusColor.copy(alpha = 0.2f) else DevBgDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) DevLevelError
            else if (isSelected) statusColor else DevBorderDark
        ),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(statusColor)
            )
            Text(
                text = health.providerName,
                color = if (isSelected) Color.White else Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Surface(
                color = statusColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) {
                Surface(
                    color = DevLevelError.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onReset() },
                ) {
                    Text(
                        text = "Reset",
                        color = Color.White,
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
private fun JsonNodeViewer(node: JsonNode, depth: Int = 0) {
    val padding = depth * 12
    when {
        node.isObject -> {
            var expanded by remember { mutableStateOf(depth < 2) }
            val fieldNames = node.fieldNames().asSequence().toList()
            Column(modifier = Modifier.padding(start = padding.dp)) {
                Row(
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
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
                                modifier = Modifier.padding(start = (padding + 12).dp)
                            )
                            JsonNodeViewer(child, depth + 1)
                        } else {
                            Row(modifier = Modifier.padding(start = (padding + 12).dp)) {
                                Text("\"$key\": ", color = DevAccentCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                val valueColor = when {
                                    child.isTextual -> Color(0xFFF1FA8C) // Yellow
                                    child.isNumber -> Color(0xFFFFB86C) // Orange
                                    child.isBoolean -> Color(0xFF8BE9FD) // Cyan
                                    child.isNull -> Color(0xFFFF5555) // Red
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
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
                                modifier = Modifier.padding(start = (padding + 12).dp)
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
                fontWeight = if (isCore) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
