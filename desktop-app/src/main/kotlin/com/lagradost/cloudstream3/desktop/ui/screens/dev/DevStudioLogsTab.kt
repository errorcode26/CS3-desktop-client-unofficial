package com.lagradost.cloudstream3.desktop.ui.screens.dev

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
import com.lagradost.runtime.executor.PluginHealthStats
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------------------
// TAB 1: LogCat Stream & Live Logs
// -------------------------------------------------------------------------------------------------

@Composable
internal fun LogCatTabContent(
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
// LogCat Dedicated Helper Components
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
