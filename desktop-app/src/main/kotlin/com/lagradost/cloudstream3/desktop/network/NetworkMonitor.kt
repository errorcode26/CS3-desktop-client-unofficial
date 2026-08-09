package com.lagradost.cloudstream3.desktop.network

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

object NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize() {
        scope.launch {
            checkConnectivity()
            // Periodic background monitor every 10 seconds
            while (isActive) {
                delay(10000)
                checkConnectivity()
            }
        }
    }

    suspend fun checkConnectivity(): Boolean = withContext(Dispatchers.IO) {
        _isChecking.value = true
        val online = try {
            testSocket("1.1.1.1", 53) || testSocket("8.8.8.8", 53) || testHttp()
        } catch (e: Exception) {
            false
        } finally {
            _isChecking.value = false
        }

        if (_isOnline.value != online) {
            _isOnline.value = online
            AppLogger.i("Network state changed: isOnline = $online")
        }
        online
    }

    private fun testSocket(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun testHttp(): Boolean {
        return try {
            val url = java.net.URI("https://www.cloudflare.com/cdn-cgi/trace").toURL()
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "HEAD"
                instanceFollowRedirects = true
            }
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
    }
}
