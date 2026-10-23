package com.lagradost.cloudstream3.desktop.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxDeviceAuthData
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTokenResult
import com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime
import com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Session
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.Desktop
import java.net.URI

@Composable
fun TorboxAuthDialog(
    auth: TorboxDeviceAuthData,
    onDismiss: () -> Unit,
    onAuthorized: (token: String) -> Unit,
    onError: (message: String) -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(true) }

    // Check if Edge WebView2 is available
    val isWebViewAvailable = remember {
        try {
            WindowsWebView2Runtime.isAvailable
        } catch (_: Throwable) {
            false
        }
    }

    val session = remember(isWebViewAvailable) {
        if (isWebViewAvailable) {
            try {
                WindowsWebView2Session(1280, 720).apply {
                    navigate(auth.verificationUrl)
                }
            } catch (e: Throwable) {
                AppLogger.e("TorboxAuthDialog: Failed to initialize WebView2 session", e)
                null
            }
        } else {
            null
        }
    }

    DisposableEffect(session) {
        onDispose {
            session?.close()
        }
    }

    // Background polling loop
    LaunchedEffect(auth.deviceCode) {
        val intervalMs = (auth.interval.coerceAtLeast(3)) * 1000L
        while (isActive) {
            delay(intervalMs)
            when (val res = TorboxApiClient.redeemDeviceAuthorization(auth.deviceCode)) {
                is TorboxTokenResult.Authorized -> {
                    isCheckingStatus = false
                    onAuthorized(res.token)
                    break
                }
                is TorboxTokenResult.Pending -> {
                    // Still waiting for user authorization
                }
                is TorboxTokenResult.Expired -> {
                    isCheckingStatus = false
                    onError("Device authorization code expired. Please try again.")
                    onDismiss()
                    break
                }
                is TorboxTokenResult.Failed -> {
                    // Non-fatal while polling
                }
            }
        }
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Log In with Torbox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pairing Code:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = auth.userCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(auth.userCode))
                            isCopied = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Code",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isCopied) "Copied!" else "Copy Code")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                    Desktop.getDesktop().browse(URI(auth.verificationUrl))
                                }
                            } catch (e: Exception) {
                                AppLogger.e("TorboxAuthDialog: Failed to open browser", e)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = "Browser",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open in Browser")
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Body: WebView or Fallback Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414)),
                contentAlignment = Alignment.Center,
            ) {
                if (session != null) {
                    SwingPanel(
                        modifier = Modifier.fillMaxSize(),
                        factory = { session.createCanvas() },
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Waiting for Torbox Authorization",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Please open ${auth.verificationUrl} in your browser and enter code ${auth.userCode}.\nThe app will automatically detect your login once approved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().browse(URI(auth.verificationUrl))
                                    }
                                } catch (_: Exception) {}
                            },
                        ) {
                            Text("Open Torbox Device Page")
                        }
                    }
                }
            }
        }
    }
}
