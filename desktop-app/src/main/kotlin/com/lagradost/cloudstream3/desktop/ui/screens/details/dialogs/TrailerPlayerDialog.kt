package com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData
import java.awt.Canvas
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities

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
            .fillMaxWidth(0.85f)
            .fillMaxHeight(0.85f),
        containerColor = Color.Black,
        shape = RoundedCornerShape(16.dp),
    ) {
        if (trailer == null) return@CloudstreamCustomDialog

        val embedUrl = remember(trailer) { resolveTrailerEmbedUrl(trailer) }
        LaunchedEffect(Unit) {
            NativePlayerBridge.syncUiScale(1.0f)
        }

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
                private var isSyncing = false

                fun syncPhysicalBounds() {
                    if (isSyncing || !isDisplayable || width <= 0 || height <= 0) return
                    isSyncing = true
                    try {
                        NativePlayerBridge.resizeWebViewForComponent(this)


                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("TrailerDialog: Failed to sync physical bounds", e)
                    } finally {
                        isSyncing = false
                    }
                }

                init {
                    background = java.awt.Color.BLACK
                }

                override fun paint(g: java.awt.Graphics?) {
                    g?.color = java.awt.Color.BLACK
                    g?.fillRect(0, 0, Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
                }

                override fun update(g: java.awt.Graphics?) {
                    paint(g)
                }

                override fun getPreferredSize(): java.awt.Dimension {
                    val p = parent
                    return if (p != null && p.width > 0 && p.height > 0) {
                        java.awt.Dimension(p.width, p.height)
                    } else {
                        java.awt.Dimension(1, 1)
                    }
                }

                override fun addNotify() {
                    super.addNotify()
                    try {
                        val root = SwingUtilities.getRootPane(this)
                        root?.background = java.awt.Color.BLACK
                        root?.layeredPane?.background = java.awt.Color.BLACK
                        val window = SwingUtilities.getWindowAncestor(this)
                        if (window != null) {
                            window.background = java.awt.Color.BLACK
                            if (window is javax.swing.JDialog) {
                                window.contentPane?.background = java.awt.Color.BLACK
                            }
                            val windowHwnd = com.sun.jna.Native.getComponentID(window)
                            if (windowHwnd != 0L) {
                                NativePlayerBridge.applyWindowChrome(windowHwnd, true, 0x000000, 0x000000, 0xFFFFFF)
                            }
                        }
                        this.parent?.background = java.awt.Color.BLACK

                        val wid = com.sun.jna.Native.getComponentID(this)
                        val (physW, physH) = NativePlayerBridge.getPhysicalDimensions(this)
                        val w = if (physW > 0) physW else width.coerceAtLeast(1)
                        val h = if (physH > 0) physH else height.coerceAtLeast(1)
                        NativePlayerBridge.syncUiScale(1.0f)

                        NativePlayerBridge.initWebView(wid, w, h)
                        NativePlayerBridge.loadUrl(embedUrl)

                        SwingUtilities.invokeLater { syncPhysicalBounds() }
                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("TrailerDialog: Failed to init WebView", e)
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun reshape(x: Int, y: Int, width: Int, height: Int) {
                    super.reshape(x, y, width, height)
                    syncPhysicalBounds()
                }
            }.apply {
                addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent?) {
                        syncPhysicalBounds()
                    }

                    override fun componentMoved(e: ComponentEvent?) {
                        syncPhysicalBounds()
                    }
                })
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val isHeightConstrained = (maxWidth / maxHeight) > (16f / 9f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(16f / 9f, matchHeightConstraintsFirst = isHeightConstrained)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SwingPanel(
                    background = Color.Black,
                    factory = { canvas },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.syncPhysicalBounds() },
                )
            }
        }
    }
}
