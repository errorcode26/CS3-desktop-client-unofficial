package com.lagradost.cloudstream3.desktop.sync

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI

object OAuthLocalServer {

    /**
     * TODO: External Tracking (AniList/Simkl) is currently PAUSED.
     * This object is boilerplate for the future native desktop tracking implementation.
     * Do not wire this up to the UI until it's ready.
     *
     * Spins up a temporary localhost server, opens the user's browser to the [authUrl],
     * and waits for the OAuth redirect.
     *
     * Handles both query string tokens (e.g., ?code=...) and fragment tokens (e.g., #access_token=...)
     * by injecting a Javascript helper page.
     *
     * @return The raw token string or fragment (e.g., "#access_token=123&expires_in=..."), or null if failed/timed out.
     */
    suspend fun authenticate(authUrl: String, port: Int = 8080): String? = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(port)
            serverSocket.soTimeout = 60_000 // 60 second timeout

            // Open the browser
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(authUrl))
            } else {
                AppLogger.e("OAuthLocalServer: Desktop browsing is not supported.")
                return@withContext null
            }

            var tokenResult: String? = null
            var keepRunning = true

            while (keepRunning) {
                try {
                    val client = serverSocket.accept()
                    client.use {
                        val reader = client.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use

                        // Parse the requested path
                        val parts = requestLine.split(" ")
                        if (parts.size >= 2) {
                            val path = parts[1]

                            if (path.startsWith("/token?data=")) {
                                // This is the Javascript AJAX callback with the fragment
                                tokenResult = path.substringAfter("/token?data=")
                                val out = client.getOutputStream()
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK".toByteArray())
                                out.flush()
                                keepRunning = false
                            } else if (path.startsWith("/callback")) {
                                // If it has query parameters directly (Authorization Code Grant)
                                if (path.contains("?") && !path.endsWith("?")) {
                                    tokenResult = path.substringAfter("?")
                                    val out = client.getOutputStream()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body><h2>Success! You can close this tab.</h2></body></html>".toByteArray())
                                    out.flush()
                                    keepRunning = false
                                } else {
                                    // Implicit Grant (token in fragment). Send JS to capture it.
                                    val html = """
                                        <html>
                                        <head><title>Cloudstream Authentication</title></head>
                                        <body style="font-family: sans-serif; text-align: center; margin-top: 50px;">
                                            <h2 id="msg">Authenticating... Please wait.</h2>
                                            <script>
                                                if (window.location.hash) {
                                                    fetch('/token?data=' + encodeURIComponent(window.location.hash))
                                                        .then(response => {
                                                            document.getElementById('msg').innerText = "Success! You can safely close this tab.";
                                                        })
                                                        .catch(err => {
                                                            document.getElementById('msg').innerText = "Failed to send token to app.";
                                                        });
                                                } else {
                                                    document.getElementById('msg').innerText = "Error: No token found in URL.";
                                                }
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    val out = client.getOutputStream()
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n$html".toByteArray())
                                    out.flush()
                                }
                            } else {
                                // Ignore favicon or other requests
                                val out = client.getOutputStream()
                                out.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                                out.flush()
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    AppLogger.w("OAuthLocalServer: Timed out waiting for browser redirect.")
                    keepRunning = false
                }
            }
            return@withContext tokenResult?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        } catch (e: Exception) {
            AppLogger.e("OAuthLocalServer failed", e)
            return@withContext null
        } finally {
            serverSocket?.close()
        }
    }
}
