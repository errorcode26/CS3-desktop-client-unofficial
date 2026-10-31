package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

object WebView2SetupState {
    var isDismissedForSession by mutableStateOf(false)
}

/**
 * Non-blocking, real-time onboarding dialog for Microsoft Edge WebView2 Runtime.
 * Allows users to install WebView2 with elevation, check detection status live,
 * or continue into the application freely without being blocked.
 */
@Composable
fun WebView2SetupDialog() {
    if (PlatformPaths.currentOS != PlatformPaths.OS.WINDOWS) return
    if (WebView2SetupState.isDismissedForSession) return

    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var isDetected by remember { mutableStateOf(WindowsWebView2Runtime.isAvailable) }

    // If already detected, do not display
    if (isDetected) return

    fun verifyRuntime() {
        isChecking = true
        scope.launch(Dispatchers.IO) {
            val available = WindowsWebView2Runtime.isAvailable
            withContext(Dispatchers.Main) {
                isDetected = available
                isChecking = false
                if (available) {
                    AppToastManager.showSuccess("Microsoft Edge WebView2 Runtime detected.")
                }
            }
        }
    }

    fun startInstaller() {
        if (isInstalling) return
        isInstalling = true
        installError = null
        scope.launch(Dispatchers.IO) {
            val success = runWebView2Installer()
            val available = WindowsWebView2Runtime.isAvailable
            withContext(Dispatchers.Main) {
                isInstalling = false
                isDetected = available
                if (available) {
                    AppToastManager.showSuccess("WebView2 Runtime installed successfully.")
                } else if (!success) {
                    installError = "Installation was cancelled or requires manual download."
                }
            }
        }
    }

    if (showConfirmation) {
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { showConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Proceed Without WebView2?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "CloudStream will launch, but video player controls, seeking, and subtitle interfaces will be limited.\n\n" +
                        "You can install the official runtime at any time from Microsoft's website.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        WebView2SetupState.isDismissedForSession = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Yes, Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Go Back")
                }
            },
        )
        return
    }

    CloudstreamAlertDialog(
        show = true,
        onDismissRequest = { showConfirmation = true },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("WebView2 Runtime Needed", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "CloudStream Desktop uses Microsoft Edge WebView2 for HTML5 video player controls, subtitles, and streaming interfaces.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Live Status Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDetected) Color(0xFF1B5E20).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDetected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDetected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isDetected) "Runtime Ready" else "Runtime Not Detected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDetected) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                            )
                        }

                        IconButton(
                            onClick = { verifyRuntime() },
                            enabled = !isChecking && !isInstalling,
                            modifier = Modifier.size(28.dp),
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Check Again",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (installError != null) {
                    Text(
                        text = installError ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { startInstaller() },
                enabled = !isInstalling,
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Installing...")
                } else {
                    Text("Install (Recommended)")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        try {
                            Desktop.getDesktop().browse(URI("https://go.microsoft.com/fwlink/p/?LinkId=2124703"))
                        } catch (_: Throwable) {}
                    },
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manual Download")
                }

                TextButton(onClick = { showConfirmation = true }) {
                    Text("Continue Anyway")
                }
            }
        },
    )
}

private fun runWebView2Installer(): Boolean {
    val tempInstaller = File(PlatformPaths.appDataDir, "cache/MicrosoftEdgeWebview2Setup.exe")
    tempInstaller.parentFile?.mkdirs()
    return try {
        AppLogger.i("WebView2Setup: Downloading Microsoft Edge WebView2 bootstrapper...")
        val url = URI("https://go.microsoft.com/fwlink/p/?LinkId=2124703").toURL()
        url.openStream().use { input ->
            tempInstaller.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        AppLogger.i("WebView2Setup: Running installer with administrator privileges...")
        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-WindowStyle", "Normal",
            "-Command",
            "Start-Process \"${tempInstaller.absolutePath.replace("\"", "\\\"")}\" -ArgumentList '/install' -Verb RunAs -Wait",
        ).start()
        val finished = process.waitFor(5, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            return false
        }
        val exitCode = process.exitValue()
        AppLogger.i("WebView2Setup: Installer finished with code $exitCode")
        try { tempInstaller.delete() } catch (_: Throwable) {}
        exitCode == 0 || WindowsWebView2Runtime.isAvailable
    } catch (e: Throwable) {
        AppLogger.e("WebView2Setup: Failed to run installer", e)
        try { tempInstaller.delete() } catch (_: Throwable) {}
        false
    }
}
