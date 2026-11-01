package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.init.CrashRecoveryManager

/**
 * Startup dialog displayed when an abnormal termination or crash is detected from the prior session.
 * Uses shared CloudstreamAlertDialog wrapper to ensure strict Amoled/Dark consistency.
 */
@Composable
fun CrashRecoveryDialog() {
    if (!CrashRecoveryManager.hasCrashDetected) return

    CloudstreamAlertDialog(
        show = true,
        onDismissRequest = { CrashRecoveryManager.dismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Previous Session Ended Unexpectedly",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        text = {
            Text(
                text = "CloudStream Desktop detected that your last session closed unexpectedly or crashed. You can copy the full diagnostics (crash dump, stream extraction, and application logs) to your clipboard, or open the logs folder in Windows Explorer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        if (openLogsFolder()) {
                            AppToastManager.showInfo("Opened logs folder in Explorer")
                        } else {
                            AppToastManager.showError("Could not open logs folder")
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Open Folder")
                }

                Button(
                    onClick = {
                        if (copyDiagnosticLogsToClipboard()) {
                            AppToastManager.showSuccess("Full diagnostic logs copied to clipboard")
                        } else {
                            AppToastManager.showError("Failed to copy crash logs")
                        }
                        CrashRecoveryManager.dismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Full Logs")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { CrashRecoveryManager.dismiss() },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Dismiss")
            }
        },
    )
}
