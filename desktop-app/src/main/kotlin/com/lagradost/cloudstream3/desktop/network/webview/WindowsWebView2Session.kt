package com.lagradost.cloudstream3.desktop.network.webview

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.awt.Canvas
import java.awt.Color
import java.awt.Graphics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Manages an individual Microsoft Edge WebView2 session.
 * Exposes an AWT heavyweight [Canvas] that reparents the WebView's Win32 HWND into the Compose UI,
 * and safely parks it back into the hidden host when detached.
 */
class WindowsWebView2Session(
    private val initialWidth: Int = 1280,
    private val initialHeight: Int = 720,
) {
    companion object {
        private const val TAG = "WindowsWebView2Session"

        val IID_CONTROLLER_DONE = WindowsCom.iid("6c4819f3-c9b7-4260-8127-c9f5bde7f68c")
        val IID_EXECUTE_SCRIPT_DONE = WindowsCom.iid("49511172-cc67-4bca-9923-137112f4c4cc")
        val IID_DEVTOOLS_DONE = WindowsCom.iid("5c4889f0-5ef6-4c5a-952c-d8f1b92d0574")
        val IID_SOURCE_CHANGED = WindowsCom.iid("3c067f9f-5388-4772-8b48-79f7ef1ab37c")
        val IID_NAVIGATION_COMPLETED = WindowsCom.iid("d33a35bf-1c49-4f98-93ab-006e0533fe1c")
        val IID_SCRIPT_DIALOG = WindowsCom.iid("ef381bf9-afa8-4e37-91c4-8ac48524bdfb")
        val IID_NEW_WINDOW = WindowsCom.iid("d4c185fe-c81c-4989-97af-2d3fa7ab5651")
        val IID_CORE2 = WindowsCom.iid("9E8F0CF8-E670-4B5E-B2BC-73E061E3184C")
    }

    private val rt = WindowsWebView2Runtime
    @Volatile private var host: WinDef.HWND? = null
    private var controller: Pointer? = null
    private var core: Pointer? = null
    private var core2: Pointer? = null

    enum class State { NONE, CREATING, READY, CLOSED }
    @Volatile var state = State.NONE
        private set

    val isReady: Boolean get() = state == State.READY

    private val pending = ArrayList<(Pointer) -> Unit>()
    @Volatile var currentUrl: String? = null
        private set

    @Volatile private var screenParent: WinDef.HWND? = null
    private var activeCanvas: Canvas? = null

    var onNavigationCompleted: ((url: String) -> Unit)? = null

    init {
        rt.register(this)
    }

    /** Schedules [action] on the WebView2 STA thread as soon as the core is ready */
    fun withCore(action: (Pointer) -> Unit) {
        rt.post {
            when (state) {
                State.READY -> core?.let(action)
                State.CLOSED -> {}
                State.CREATING -> pending.add(action)
                State.NONE -> {
                    pending.add(action)
                    create()
                }
            }
        }
    }

    private fun create() {
        state = State.CREATING
        rt.withEnvironment { env ->
            if (state == State.CLOSED) return@withEnvironment
            if (env == null) {
                AppLogger.e("$TAG: Cannot create session: null WebView2 environment.")
                state = State.NONE
                pending.clear()
                return@withEnvironment
            }

            val window = rt.createHost(this, initialWidth, initialHeight)
            host = window

            val handler = ComObject.completed(IID_CONTROLLER_DONE) { hr, c ->
                if (!WindowsCom.ok(hr) || c == null) {
                    AppLogger.e("$TAG: Controller creation failed: HRESULT 0x${Integer.toHexString(hr)}")
                    state = State.NONE
                    pending.clear()
                    return@completed
                }
                if (state == State.CLOSED) {
                    WindowsCom.call(c, 24) // Close
                    return@completed
                }

                controller = WindowsCom.addRef(c)
                val cw = WindowsCom.getPtr(c, 25) // get_CoreWebView2
                core = cw
                core2 = cw?.let { WindowsCom.query(it, IID_CORE2) }

                WindowsCom.putBool(c, 4, true) // put_IsVisible = TRUE
                onHostResized()
                rt.onCoreCreated(core2)

                if (cw != null) {
                    setupCore(cw)
                }

                state = State.READY
                val queued = pending.toList()
                pending.clear()
                if (cw != null) {
                    queued.forEach { action -> runCatching { action(cw) } }
                }
            }

            val hr = WindowsCom.call(env, 3, window.pointer, handler.pointer)
            handler.releaseOwn()
            if (hr < 0) {
                AppLogger.e("$TAG: Failed to dispatch controller creation: HRESULT 0x${Integer.toHexString(hr)}")
                state = State.NONE
                pending.clear()
            }
        }
    }

    private fun setupCore(c: Pointer) {
        // Prevent dialogs from hanging the background solver indefinitely
        val scriptDialogHandler = ComObject.event(IID_SCRIPT_DIALOG) { _, args ->
            if (args != null) {
                val kind = WindowsCom.getInt(args, 4) ?: 0
                if (kind == 0 || kind == 3) { // alert or beforeunload
                    WindowsCom.call(args, 6) // Accept
                }
            }
        }
        WindowsCom.addHandler(c, 21, scriptDialogHandler)
        scriptDialogHandler.releaseOwn()

        // Block pop-up window spam
        val newWindowHandler = ComObject.event(IID_NEW_WINDOW) { _, args ->
            if (args != null) WindowsCom.putBool(args, 6, true) // put_Handled(TRUE)
        }
        WindowsCom.addHandler(c, 44, newWindowHandler)
        newWindowHandler.releaseOwn()

        // Track source and navigation completion
        val sourceHandler = ComObject.event(IID_SOURCE_CHANGED) { _, _ ->
            currentUrl = WindowsCom.getString(c, 4)
        }
        WindowsCom.addHandler(c, 11, sourceHandler)
        sourceHandler.releaseOwn()

        val navHandler = ComObject.event(IID_NAVIGATION_COMPLETED) { _, _ ->
            val u = WindowsCom.getString(c, 4)
            if (u != null && u != "about:blank") {
                currentUrl = u
                onNavigationCompleted?.invoke(u)
                rt.requestSyncCookies()
            }
        }
        WindowsCom.addHandler(c, 15, navHandler)
        navHandler.releaseOwn()
    }

    fun navigate(url: String, headers: Map<String, String> = emptyMap()) {
        withCore { c ->
            val env2 = rt.environment2
            val c2 = core2
            if (headers.isEmpty() || env2 == null || c2 == null) {
                WindowsCom.call(c, 5, WString(url))
                return@withCore
            }

            val headerText = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
            val request = WindowsCom.getPtr(env2, 8, WString(url), WString("GET"), null, WString(headerText))
            if (request == null) {
                WindowsCom.call(c, 5, WString(url))
                return@withCore
            }
            WindowsCom.call(c2, 63, request)
            WindowsCom.release(request)
        }
    }

    fun reload() {
        withCore { c ->
            WindowsCom.call(c, 27) // Reload
        }
    }

    fun executeScript(script: String, callback: ((String) -> Unit)? = null) {
        withCore { c ->
            val handler = ComObject.completed(IID_EXECUTE_SCRIPT_DONE) { hr, result ->
                val json = if (WindowsCom.ok(hr)) result?.getWideString(0) else null
                callback?.invoke(json ?: "null")
            }
            if (WindowsCom.call(c, 29, WString(script), handler.pointer) < 0) {
                callback?.invoke("null")
            }
            handler.releaseOwn()
        }
    }

    fun devTools(method: String, params: String = "{}", callback: ((String?) -> Unit)? = null) {
        withCore { c ->
            val handler = ComObject.completed(IID_DEVTOOLS_DONE) { hr, result ->
                val json = if (WindowsCom.ok(hr)) result?.getWideString(0) else null
                callback?.invoke(json)
            }
            if (WindowsCom.call(c, 36, WString(method), WString(params), handler.pointer) < 0) {
                callback?.invoke(null)
            }
            handler.releaseOwn()
        }
    }

    suspend fun evaluateJs(expression: String, timeoutMs: Long = 30_000L): String? {
        val deferred = CompletableDeferred<String?>()
        val mapper = jacksonObjectMapper()
        val params = mapper.writeValueAsString(
            mapOf(
                "expression" to expression,
                "awaitPromise" to true,
                "returnByValue" to true,
            )
        )
        devTools("Runtime.evaluate", params) { json ->
            deferred.complete(json)
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult? {
        val mapper = jacksonObjectMapper()
        val forbiddenHeaders = setOf(
            "user-agent", "cookie", "host", "content-length", "transfer-encoding",
            "connection", "accept-encoding", "referer", "origin",
            "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
            "sec-fetch-site", "sec-fetch-mode", "sec-fetch-user", "sec-fetch-dest"
        )
        val filteredHeaders = headers.filterKeys { it.lowercase() !in forbiddenHeaders }
        val reqPayload = mapOf(
            "url" to url,
            "method" to method,
            "headers" to filteredHeaders,
            "body" to body,
        )
        val jsonPayload = mapper.writeValueAsString(reqPayload)
        val b64 = java.util.Base64.getEncoder().encodeToString(jsonPayload.toByteArray(Charsets.UTF_8))
        val expression = """(async()=>{try{const req=JSON.parse(atob("$b64"));const opts={method:req.method,headers:req.headers||{},credentials:"include"};if(req.body)opts.body=req.body;const r=await fetch(req.url,opts);const t=await r.text();return JSON.stringify({s:r.status,ct:r.headers.get("content-type")||"",b:t})}catch(e){return JSON.stringify({s:0,ct:"",b:(e&&e.stack)?e.stack:""+e})}})()"""

        val cdpResponse = evaluateJs(expression) ?: return null
        val root = mapper.readTree(cdpResponse)
        val resultJson = root.get("result")?.get("value")?.asText() ?: return null
        val resultTree = mapper.readTree(resultJson)
        val statusCode = resultTree.get("s")?.asInt() ?: 0
        val contentType = resultTree.get("ct")?.asText()?.ifBlank { null }
        if (statusCode == 0) {
            val err = resultTree.get("b")?.asText().orEmpty()
            AppLogger.e("$TAG: fetch error for $url: $err")
            return null
        }
        val bodyText = resultTree.get("b")?.asText() ?: ""
        return com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult(
            statusCode = statusCode,
            contentType = contentType,
            body = bodyText,
            bodyBytes = null,
        )
    }

    /**
     * Ensures the WebView is on the same origin as the target URL before executing a JS fetch.
     * Direct browser navigation to API endpoints often triggers redirects (e.g., to the homepage).
     * By navigating to the root origin and fetching from within that context, we avoid CORS
     * and ensure cookies are sent.
     */
    suspend fun navigateAndCapture(
        url: String,
        timeoutMs: Long = 15_000L
    ): com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult? {
        val deferred = CompletableDeferred<com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult?>()
        var captured = false

        val prevHandler = onNavigationCompleted
        onNavigationCompleted = { navUrl ->
            if (navUrl == url || navUrl.startsWith(url)) {
                captured = true
                kotlin.concurrent.thread {
                    Thread.sleep(500)
                    kotlinx.coroutines.runBlocking {
                        val rawCdp = evaluateJs("document.documentElement.outerHTML", 2000L) ?: ""
                        val html = try {
                            jacksonObjectMapper().readTree(rawCdp).get("result")?.get("value")?.asText() ?: ""
                        } catch (_: Exception) { "" }
                        deferred.complete(
                            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult(
                                statusCode = 200,
                                contentType = "text/html",
                                body = html
                            )
                        )
                    }
                }
            }
        }

        navigate(url)

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            null
        } finally {
            onNavigationCompleted = prevHandler
        }
    }

    suspend fun awaitOriginAndFetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Long = 10_000L
    ): com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.CdpFetchResult? {
        val targetUrl = try { java.net.URI(url) } catch (_: Exception) { null } ?: return null
        val targetOrigin = "${targetUrl.scheme}://${targetUrl.host}/"
        
        var currentOrigin = evaluateJs("window.location.origin", 1000L)?.replace("\"", "") ?: ""
        if (!currentOrigin.startsWith(targetOrigin.removeSuffix("/"))) {
            val deferred = CompletableDeferred<Boolean>()
            val prevHandler = onNavigationCompleted
            onNavigationCompleted = { navUrl ->
                if (navUrl.startsWith(targetOrigin)) {
                    onNavigationCompleted = prevHandler
                    deferred.complete(true)
                }
            }
            navigate(targetOrigin)
            try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (_: Exception) {
                onNavigationCompleted = prevHandler
            }
        }
        
        return fetch(url, method, headers, body)
    }

    internal fun onHostResized() {
        val c = controller ?: return
        val window = host ?: return
        val rect = WinDef.RECT()
        User32.INSTANCE.GetClientRect(window, rect)
        WindowsCom.call(c, 6, RectByValue(0, 0, rect.right - rect.left, rect.bottom - rect.top))
    }

    private fun fitInto(parent: WinDef.HWND) {
        val window = host ?: return
        val rect = WinDef.RECT()
        User32.INSTANCE.GetClientRect(parent, rect)
        User32.INSTANCE.MoveWindow(window, 0, 0, max(1, rect.right - rect.left), max(1, rect.bottom - rect.top), true)
        controller?.let { WindowsCom.call(it, 23) } // NotifyParentWindowPositionChanged
    }

    /** Creates or returns an AWT Canvas that mounts this session into Compose Desktop */
    fun createCanvas(): Canvas {
        return activeCanvas ?: SessionCanvas().also { activeCanvas = it }
    }

    private inner class SessionCanvas : Canvas() {
        init {
            background = Color(0x18, 0x18, 0x18)
        }

        override fun addNotify() {
            super.addNotify()
            val parent = WinDef.HWND(Native.getComponentPointer(this) ?: return)
            screenParent = parent
            withCore { _ ->
                val window = host ?: return@withCore
                if (screenParent != parent) return@withCore
                User32.INSTANCE.SetParent(window, parent)
                fitInto(parent)
            }
        }

        override fun removeNotify() {
            screenParent = null
            host?.let { rt.park(it) }
            super.removeNotify()
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun reshape(x: Int, y: Int, width: Int, height: Int) {
            super.reshape(x, y, width, height)
            val parent = screenParent ?: return
            rt.post { if (screenParent == parent) fitInto(parent) }
        }

        override fun paint(g: Graphics?) {}
        override fun update(g: Graphics?) {}
    }

    fun close() {
        rt.post {
            if (state == State.CLOSED) return@post
            state = State.CLOSED
            pending.clear()
            screenParent = null
            activeCanvas = null

            val c = controller
            controller = null
            if (c != null) runCatching { WindowsCom.call(c, 24) } // Close

            rt.onCoreRemoved(core2)
            WindowsCom.release(core2)
            WindowsCom.release(core)
            WindowsCom.release(c)
            core2 = null
            core = null

            host?.let { rt.destroyHost(it) }
            host = null
            rt.unregister(this)
        }
    }
}
