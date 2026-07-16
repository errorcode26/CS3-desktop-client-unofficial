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
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        val logFile = File(System.getProperty("user.home"), "AppData/Roaming/CloudStreamDesktop/logs/app.log")
        var lastModified = 0L
        while (true) {
            if (logFile.exists()) {
                val modified = logFile.lastModified()
                if (modified != lastModified) {
                    lastModified = modified
                    try {
                        val lines = logFile.readLines()
                        val newText = if (lines.size > 1000) {
                            lines.takeLast(1000).joinToString("\n")
                        } else {
                            lines.joinToString("\n")
                        }
                        
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App Logcat", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row {
                Button(onClick = {
                    val logFile = File(System.getProperty("user.home"), "AppData/Roaming/CloudStreamDesktop/logs/app.log")
                    if (logFile.exists()) {
                        logFile.writeText("")
                        logText = ""
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(logText))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
                SelectionContainer {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
