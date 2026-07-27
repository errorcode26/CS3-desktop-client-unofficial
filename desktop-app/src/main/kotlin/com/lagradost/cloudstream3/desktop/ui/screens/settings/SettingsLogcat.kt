package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SettingsLogcat() {
    val clipboardManager = LocalClipboardManager.current
    var logText by remember { mutableStateOf("Loading logs...") }
    var filterEnrichmentOnly by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var currentLogLevel by remember {
        mutableStateOf(com.lagradost.common.storage.DesktopDataStore.getKey<String>("log_level") ?: "WARN")
    }

    LaunchedEffect(currentLogLevel) {
        com.lagradost.common.storage.DesktopDataStore.setKey("log_level", currentLogLevel)
        try {
            val loggerContext = org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
            val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            rootLogger.level = ch.qos.logback.classic.Level.toLevel(currentLogLevel.uppercase())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readLog(file: File): String {
        val lines = file.readLines()
        val filtered = if (filterEnrichmentOnly) {
            lines.filter {
                it.contains("[Enrichment]") ||
                    it.contains("HybridEnrichmentService") ||
                    it.contains("TmdbEnrichmentService") ||
                    it.contains("HeroRepository")
            }
        } else {
            lines
        }
        return if (filtered.size > 1000) filtered.takeLast(1000).joinToString("\n") else filtered.joinToString("\n")
    }

    LaunchedEffect(Unit) {
        val logFile = File(System.getProperty("user.home"), "AppData/Roaming/CloudStreamDesktop/logs/app.log")
        var lastModified = 0L
        while (true) {
            if (logFile.exists()) {
                val modified = logFile.lastModified()
                if (modified != lastModified) {
                    lastModified = modified
                    try {
                        val newText = readLog(logFile)
                        val isAtBottom = scrollState.value >= scrollState.maxValue - 50
                        logText = newText
                        if (isAtBottom && logText.isNotEmpty()) {
                            delay(50)
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    } catch (e: Exception) {}
                }
            } else {
                logText = "Log file not found at ${logFile.absolutePath}"
            }
            delay(1000)
        }
    }

    // Refresh immediately when filter or level changes
    LaunchedEffect(filterEnrichmentOnly) {
        val logFile = File(System.getProperty("user.home"), "AppData/Roaming/CloudStreamDesktop/logs/app.log")
        if (logFile.exists()) {
            try {
                logText = readLog(logFile)
                delay(50)
                scrollState.scrollTo(scrollState.maxValue)
            } catch (e: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("App Logcat", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Log level dropdown
                var isLevelMenuExpanded by remember { mutableStateOf(false) }
                val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR")
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { isLevelMenuExpanded = true },
                        label = { Text("Level: $currentLogLevel", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp),
                    )
                    DropdownMenu(
                        expanded = isLevelMenuExpanded,
                        onDismissRequest = { isLevelMenuExpanded = false },
                    ) {
                        logLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level) },
                                onClick = {
                                    currentLogLevel = level
                                    isLevelMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                // Enrichment filter toggle
                FilterChip(
                    selected = filterEnrichmentOnly,
                    onClick = { filterEnrichmentOnly = !filterEnrichmentOnly },
                    label = { Text("Enrichment Only", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(32.dp),
                )

                // Action buttons
                IconButton(
                    onClick = {
                        val logFile = File(System.getProperty("user.home"), "AppData/Roaming/CloudStreamDesktop/logs/app.log")
                        if (logFile.exists()) {
                            logFile.writeText("")
                            logText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(logText)) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Log content ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
                SelectionContainer {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
