package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

const val DISCORD_INVITE_URL = "https://discord.gg/cgy9GZcdAH"
private const val PREF_KEY_NEVER_SHOW_WELCOME = "welcome_dialog_never_show"
private const val LEGACY_PREF_KEY_1 = "pre_alpha_notice_never_show"
private const val LEGACY_PREF_KEY_2 = "has_dismissed_pre_alpha_notice"

object WelcomeDialogState {
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

@Composable
fun WelcomeDialog() {
    val scope = rememberCoroutineScope()
    val neverShow = remember {
        DesktopDataStore.getKey<Boolean>(PREF_KEY_NEVER_SHOW_WELCOME)
            ?: DesktopDataStore.getKey<Boolean>(LEGACY_PREF_KEY_1)
            ?: DesktopDataStore.getKey<Boolean>(LEGACY_PREF_KEY_2)
            ?: false
    }
    var isVisible by remember { mutableStateOf(!neverShow) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val showDialog = isVisible || WelcomeDialogState.isManuallyOpened

    if (!showDialog) return

    val handleDismiss = {
        if (dontShowAgain) {
            scope.launch(Dispatchers.IO) {
                DesktopDataStore.setKey(PREF_KEY_NEVER_SHOW_WELCOME, true)
            }
        }
        isVisible = false
        WelcomeDialogState.isManuallyOpened = false
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
                    text = "Welcome to CloudStream Desktop",
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
                    text = "Welcome to the CloudStream Desktop client! Core playback and extension compatibility are under continuous active development.\n\nJoin our Discord community to report bugs, get support, request features, and stay up to date with updates!",
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
