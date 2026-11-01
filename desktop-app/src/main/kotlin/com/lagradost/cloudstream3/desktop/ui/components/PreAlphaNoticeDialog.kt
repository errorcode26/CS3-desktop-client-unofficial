package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

const val DISCORD_INVITE_URL = "https://discord.gg/cgy9GZcdAH"
private const val PREF_KEY_NEVER_SHOW_NOTICE = "pre_alpha_notice_never_show"

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
    val neverShow = remember {
        DesktopDataStore.getKey<Boolean>(PREF_KEY_NEVER_SHOW_NOTICE) ?: false
    }
    var isVisible by remember { mutableStateOf(!neverShow) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val showDialog = isVisible || PreAlphaNoticeState.isManuallyOpened

    if (!showDialog) return

    val handleDismiss = {
        if (dontShowAgain) {
            scope.launch(Dispatchers.IO) {
                DesktopDataStore.setKey(PREF_KEY_NEVER_SHOW_NOTICE, true)
            }
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
                    text = "CloudStream Desktop Alpha",
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
                    text = "Welcome to the CloudStream Desktop client! This build is currently in active Alpha development, so you may encounter bugs, glitches, or missing features.\n\nJoin our Discord community to report bugs, get help and support, request features, and help us make the app better!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 2.dp),
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Don't show this again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
                Text("Join Discord")
            }
        },
        dismissButton = {
            TextButton(
                onClick = handleDismiss,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Close")
            }
        },
    )
}
