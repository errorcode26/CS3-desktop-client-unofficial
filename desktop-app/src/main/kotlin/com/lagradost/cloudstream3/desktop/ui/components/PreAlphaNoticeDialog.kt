package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.common.logging.LogBuffer
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.time.LocalDateTime

const val DISCORD_INVITE_URL = "https://discord.gg/cU6Fr7Wx4"
private const val PREF_KEY_DISMISSED_PRE_ALPHA = "has_dismissed_pre_alpha_notice"

object PreAlphaNoticeState {
    var isManuallyOpened by mutableStateOf(false)

    fun open() {
        isManuallyOpened = true
    }
}

fun openDiscordInvite() {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(DISCORD_INVITE_URL))
        }
    } catch (_: Throwable) {}
}

fun copyDiagnosticLogsToClipboard(): Boolean {
    return try {
        val diagnosticInfo = buildString {
            appendLine("========================================")
            appendLine("CloudStream Desktop - Diagnostic Log Dump")
            appendLine("Timestamp: ${LocalDateTime.now()}")
            appendLine("Version: ${AppConfig.APP_VERSION}")
            appendLine("OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
            appendLine("Java Runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
            appendLine("========================================")
            appendLine()
            appendLine("### RECENT LOG STREAM:")
            val snapshot = LogBuffer.getSnapshot()
            if (snapshot.isNotEmpty()) {
                appendLine(LogBuffer.exportLogsAsText(snapshot))
            } else {
                appendLine("(No logs recorded)")
            }
            appendLine()
            appendLine("### RECENT ERRORS:")
            appendLine(DesktopErrorReporter.getSnapshot())
        }
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(diagnosticInfo), null)
        true
    } catch (_: Throwable) {
        false
    }
}

@Composable
fun PreAlphaNoticeDialog() {
    val scope = rememberCoroutineScope()
    val hasDismissedBefore by remember {
        mutableStateOf(DesktopDataStore.getKey<Boolean>(PREF_KEY_DISMISSED_PRE_ALPHA) ?: false)
    }
    var isVisible by remember {
        mutableStateOf(!hasDismissedBefore)
    }
    var isCopied by remember { mutableStateOf(false) }

    val showDialog = isVisible || PreAlphaNoticeState.isManuallyOpened

    if (!showDialog) return

    val handleDismiss = {
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PREF_KEY_DISMISSED_PRE_ALPHA, true)
        }
        isVisible = false
        PreAlphaNoticeState.isManuallyOpened = false
    }

    CloudstreamAlertDialog(
        show = showDialog,
        onDismissRequest = handleDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Desktop Testing Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "You are running an active testing preview of the unofficial CloudStream Desktop client. Core playback and extension compatibility are under continuous development, and you may encounter occasional glitches or crashes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "How to Report Bugs Effectively:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "• Specify the extension/provider name and media title.\n• Describe exact steps to reproduce the issue.\n• Click below to copy diagnostic logs and paste (Ctrl+V) into your report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (copyDiagnosticLogsToClipboard()) {
                            isCopied = true
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isCopied) "Logs Copied to Clipboard!" else "Copy Diagnostic Logs to Clipboard",
                        color = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openDiscordInvite()
                    handleDismiss()
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Tester Discord")
            }
        },
        dismissButton = {
            TextButton(
                onClick = handleDismiss,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Got It")
            }
        },
    )
}
