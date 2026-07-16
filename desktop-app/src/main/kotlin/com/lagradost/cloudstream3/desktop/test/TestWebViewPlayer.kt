package com.lagradost.cloudstream3.desktop.test

import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.desktop.player.MpvLibrary
import com.sun.jna.Native
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")

    SwingUtilities.invokeLater {
        val window = JFrame("WebView2 Test Player")
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        window.setSize(800, 600)
        window.contentPane.background = Color.BLACK
        window.background = Color.BLACK

        val layeredPane = JLayeredPane()
        layeredPane.preferredSize = Dimension(800, 600)
        window.contentPane = layeredPane

        val canvas = object : Canvas() {
            override fun paint(g: java.awt.Graphics?) {}
            override fun update(g: java.awt.Graphics?) {}
        }
        canvas.background = Color.BLACK
        canvas.setBounds(0, 0, 800, 600)
        layeredPane.add(canvas, Integer.valueOf(0))

        window.setLocationRelativeTo(null) // center on screen
        window.isVisible = true

        val hwnd = Native.getComponentID(canvas)
        println("AWT Canvas HWND: $hwnd")

        var mpvHandle: com.sun.jna.Pointer? = null
        var isFullscreen = false

        window.contentPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val w = window.contentPane.width
                val h = window.contentPane.height
                layeredPane.setBounds(0, 0, w, h)
                canvas.setBounds(0, 0, w, h)
            }
        })

        canvas.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val size = e.component.size
                println("Canvas resized to ${size.width}x${size.height}")
                NativePlayerBridge.resizeWebView(size.width, size.height)
            }
        })

        NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
            private fun extractJsonString(json: String, key: String): String {
                val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
            }

            private fun extractJsonValue(json: String, key: String): String {
                val strResult = extractJsonString(json, key)
                if (strResult.isNotEmpty()) return strResult
                val numRegex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*([0-9.eE+\\-]+|true|false|null)")
                return numRegex.find(json)?.groupValues?.getOrNull(1) ?: ""
            }

            override fun onPlayerEvent(eventJson: String, value: String) {
                val type = extractJsonString(value, "type")
                val arg = extractJsonValue(value, "value")
                
                val handle = mpvHandle
                if (handle != null) {
                    when (type) {
                        "togglePlay" -> {
                            val pauseStr = MpvLibrary.getPropertyString(handle, "pause") ?: "yes"
                            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", if (pauseStr == "yes") "no" else "yes")
                        }
                        "seek" -> { // Relative seek
                            val seekMs = arg.toLongOrNull() ?: 0L
                            MpvLibrary.INSTANCE.mpv_command_string(handle, "seek ${seekMs / 1000.0}")
                        }
                        "seekBy" -> { // Relative seek
                            val seekMs = arg.toLongOrNull() ?: 0L
                            MpvLibrary.INSTANCE.mpv_command_string(handle, "seek ${seekMs / 1000.0}")
                        }
                        "seekTo" -> { // Absolute seek
                            val seekMs = arg.toLongOrNull() ?: 0L
                            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "time-pos", (seekMs / 1000.0).toString())
                        }
                        "setVolume" -> {
                            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "volume", arg)
                        }
                        "toggleMute" -> {
                            val muteStr = MpvLibrary.getPropertyString(handle, "mute") ?: "no"
                            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "mute", if (muteStr == "yes") "no" else "yes")
                        }
                        "toggleFullscreen" -> {
                            val gc = window.graphicsConfiguration ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
                            val bounds = gc.bounds
                            val scaleX = gc.defaultTransform.scaleX
                            val scaleY = gc.defaultTransform.scaleY
                            
                            val ptr = com.sun.jna.Native.getComponentPointer(window)
                            val hwnd = com.sun.jna.Pointer.nativeValue(ptr)
                            isFullscreen = !isFullscreen
                            
                            NativePlayerBridge.setFullscreen(
                                hwnd,
                                isFullscreen,
                                (bounds.x * scaleX).toInt(),
                                (bounds.y * scaleY).toInt(),
                                (bounds.width * scaleX).toInt(),
                                (bounds.height * scaleY).toInt()
                            )
                            if (isFullscreen) {
                                window.toFront()
                                window.requestFocus()
                            }
                        }
                        "exitPlayer" -> {
                            System.exit(0)
                        }
                        "setMpvProperty" -> {
                            val parts = arg.split(":", limit = 2)
                            if (parts.size == 2) {
                                MpvLibrary.INSTANCE.mpv_set_property_string(handle, parts[0], parts[1])
                            }
                        }
                        "toggleStats" -> {
                            MpvLibrary.INSTANCE.mpv_command_string(handle, "script-binding stats/display-stats-toggle")
                        }
                    }
                }
            }
        })

        // initWebView now spawns a separate thread and returns quickly.
        // The WebView2 popup window will appear over the host window.
        println("Calling initWebView...")
        val containerHwnd = NativePlayerBridge.initWebView(hwnd, 800, 600)
        println("initWebView returned, containerHwnd=$containerHwnd")

        // After 3 seconds, navigate to our controls HTML and open devtools
        // Start a local HTTP server to serve the UI files
        // This avoids WebView2's strict file:// cross-origin restrictions
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("localhost", 8080), 0)
        server.createContext("/") { exchange ->
            val reqPath = exchange.requestURI.path.takeIf { it != "/" } ?: "/controls.html"
            val relativePath = reqPath.removePrefix("/")
            val file = File("src/main/resources/player-ui", relativePath)
            
            if (file.exists()) {
                val bytes = file.readBytes()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } else {
                println("HTTP 404: ${file.absolutePath} not found")
                exchange.sendResponseHeaders(404, 0)
            }
            exchange.responseBody.close()
        }
        server.start()
        println("Local HTTP server running on http://localhost:8080")

        Timer(3000) {
            val url = "http://localhost:8080/controls.html"
            println("Loading URL: $url")
            NativePlayerBridge.loadUrl(url)

            Timer(1000) {
                println("Opening DevTools...")
                NativePlayerBridge.openDevTools()
            }.also { it.isRepeats = false; it.start() }
        }.also { it.isRepeats = false; it.start() }

        // Periodically push real player state and enforce resize
        Timer(100) {
            val handle = mpvHandle
            if (handle != null) {
                val posStr = MpvLibrary.getPropertyString(handle, "time-pos") ?: "0.0"
                val durationStr = MpvLibrary.getPropertyString(handle, "duration") ?: "0.0"
                val pauseStr = MpvLibrary.getPropertyString(handle, "pause") ?: "yes"
                val volStr = MpvLibrary.getPropertyString(handle, "volume") ?: "100.0"
                val muteStr = MpvLibrary.getPropertyString(handle, "mute") ?: "no"
                
                val posMs = ((posStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                val durMs = ((durationStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                val isPlaying = pauseStr != "yes"
                val volume = volStr.toDoubleOrNull() ?: 100.0
                val isMuted = muteStr == "yes"
                
                NativePlayerBridge.executeScript(
                    "if(window.playerUpdate) window.playerUpdate('{\"type\":\"state_update\", \"positionMs\": $posMs, \"durationMs\": $durMs, \"isPlaying\": $isPlaying, \"volume\": $volume, \"isMuted\": $isMuted}')"
                )
            }
            // Enforce resize periodically
            NativePlayerBridge.resizeWebView(canvas.width, canvas.height)
        }.start()

        // Initialize MPV underneath!
        val mpvDir = File("appResources/windows/mpv").absoluteFile
        println("Setting jna.library.path to: ${mpvDir.absolutePath} (exists=${mpvDir.exists()})")
        System.setProperty("jna.library.path", mpvDir.absolutePath)
        val lib = MpvLibrary.INSTANCE
        mpvHandle = lib.mpv_create()
        if (mpvHandle != null) {
            lib.mpv_set_option_string(mpvHandle!!, "wid", containerHwnd.toString())
            lib.mpv_set_option_string(mpvHandle!!, "vo", "gpu-next")
            lib.mpv_set_option_string(mpvHandle!!, "gpu-api", "d3d11")
            lib.mpv_set_option_string(mpvHandle!!, "hwdec", "auto")
            lib.mpv_set_option_string(mpvHandle!!, "keep-open", "yes")
            lib.mpv_set_option_string(mpvHandle!!, "pause", "no")
            lib.mpv_set_option_string(mpvHandle!!, "loop-file", "inf")
            lib.mpv_initialize(mpvHandle!!)
            lib.mpv_command_string(mpvHandle!!, "loadfile \"https://media.w3.org/2010/05/sintel/trailer.mp4\"")
            println("MPV initialized and loading Sintel trailer!")
        } else {
            println("Failed to initialize MPV.")
        }
    }
}
