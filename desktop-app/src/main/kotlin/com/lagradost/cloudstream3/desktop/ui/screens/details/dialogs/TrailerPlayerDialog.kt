package com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData
import java.awt.Canvas
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Clean embed URL resolver for YouTube trailers and direct video streams.
 * Wraps embeds in a local HTML container with standard iframe permissions to prevent YouTube Error 153.
 */
fun resolveTrailerEmbedUrl(trailer: TrailerData): String {
    val rawId = trailer.rawKey ?: extractYouTubeId(trailer.url) ?: trailer.id
    val isYouTube = trailer.site.equals("youtube", ignoreCase = true) ||
            trailer.url.contains("youtube.com", ignoreCase = true) ||
            trailer.url.contains("youtu.be", ignoreCase = true)

    val port = com.lagradost.player.impl.proxy.LocalStreamProxy.port
    return if (isYouTube && rawId.isNotBlank()) {
        "http://127.0.0.1:$port/trailer?id=$rawId"
    } else {
        val encodedUrl = java.net.URLEncoder.encode(trailer.url, "UTF-8")
        "http://127.0.0.1:$port/trailer?u=$encodedUrl"
    }
}

private fun extractYouTubeId(url: String): String? {
    val patterns = listOf(
        "(?:v=|\\/v\\/|youtu\\.be\\/|\\/embed\\/)([a-zA-Z0-9_-]{11})".toRegex(),
        "^([a-zA-Z0-9_-]{11})$".toRegex()
    )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1]
        }
    }
    return null
}

/**
 * 16:9 In-App Trailer Player Modal Dialog.
 * Uses [CloudstreamCustomDialog] with WebView2 embedding.
 */
@Composable
fun TrailerPlayerDialog(
    trailer: TrailerData?,
    onDismissRequest: () -> Unit,
) {
    val show = trailer != null

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.70f)
            .widthIn(min = 640.dp, max = 1100.dp)
            .wrapContentHeight(),
        containerColor = Color.Transparent,
    ) {
        if (trailer == null) return@CloudstreamCustomDialog

        val embedUrl = remember(trailer) { resolveTrailerEmbedUrl(trailer) }

        DisposableEffect(trailer) {
            NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
                override fun onPlayerEvent(type: String, value: String) {
                    if (type == "close" || value.contains("\"close\"")) {
                        onDismissRequest()
                    }
                }
            })

            onDispose {
                NativePlayerBridge.setEventListener(null)
                Thread({
                    NativePlayerBridge.destroyWebView()
                }, "cs3-trailer-dispose").apply {
                    isDaemon = true
                    start()
                }
            }
        }

        val canvas = remember(trailer) {
            object : Canvas() {
                init {
                    background = java.awt.Color.BLACK
                }

                override fun paint(g: java.awt.Graphics?) {
                    g?.color = java.awt.Color.BLACK
                    g?.fillRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
                }

                override fun update(g: java.awt.Graphics?) {
                    paint(g)
                }

                override fun addNotify() {
                    super.addNotify()
                    try {
                        val wid = com.sun.jna.Native.getComponentID(this)
                        val w = width.coerceAtLeast(1)
                        val h = height.coerceAtLeast(1)
                        NativePlayerBridge.initWebView(wid, w, h)
                        NativePlayerBridge.loadUrl(embedUrl)
                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("TrailerDialog: Failed to init WebView", e)
                    }
                }
            }.apply {
                addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent?) {
                        if (isDisplayable) {
                            NativePlayerBridge.resizeWebView(width, height)
                        }
                    }
                })
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Floating Top Header Bar (Trailer Title Pill on Left, Floating Close Button on Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        text = trailer.name.ifBlank { "Official Trailer" },
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E).copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onDismissRequest() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Trailer",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // 16:9 Borderless Video Card with Clean Cinema Border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.15f)),
            ) {
                SwingPanel(
                    background = Color.Black,
                    factory = { canvas },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Floating Helper Hint Below
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Press",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "Esc",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = "or click outside to close",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
