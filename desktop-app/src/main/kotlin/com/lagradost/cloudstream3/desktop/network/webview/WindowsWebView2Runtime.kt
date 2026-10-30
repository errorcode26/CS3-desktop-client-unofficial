package com.lagradost.cloudstream3.desktop.network.webview

import com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import okhttp3.Cookie
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Microsoft Edge WebView2 runtime manager for Windows.
 * Runs on a dedicated STA (Single-Threaded Apartment) worker thread with an off-screen Win32 message loop window.
 * Keeps challenges active at full speed without throttling while syncing resolved cookies into the secure vault.
 */
object WindowsWebView2Runtime {
    private const val TAG = "WindowsWebView2"
    private const val WM_RUN = 0x8000 + 0x42

    private val IID_ENV_DONE = WindowsCom.iid("4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d")
    private val IID_ENV2 = WindowsCom.iid("41f3632b-5ef4-404f-ad82-2d606c5a9a21")
    private val IID_GET_COOKIES_DONE = WindowsCom.iid("5a4f5069-5c15-47c3-8646-f4de1c116670")

    private interface Loader : Library {
        fun CreateCoreWebView2EnvironmentWithOptions(
            browserFolder: WString?,
            userDataFolder: WString?,
            options: Pointer?,
            handler: Pointer?,
        ): Int

        fun GetAvailableCoreWebView2BrowserVersionString(
            browserFolder: WString?,
            versionInfo: PointerByReference,
        ): Int
    }

    private val loader: Loader? by lazy {
        locateLoaderDll()
    }

    private fun locateLoaderDll(): Loader? {
        val javaLibPaths = System.getProperty("java.library.path")?.split(File.pathSeparator)?.map { File(it, "WebView2Loader.dll") } ?: emptyList()
        val candidatePaths = listOfNotNull(
            *javaLibPaths.toTypedArray(),
            System.getProperty("compose.application.resources.dir")?.let { File(it, "jni/WebView2Loader.dll") },
            File("appResources/windows/jni/WebView2Loader.dll"),
            File("desktop-app/appResources/windows/jni/WebView2Loader.dll"),
            File("app/resources/jni/WebView2Loader.dll"),
            File("resources/jni/WebView2Loader.dll"),
            File(System.getProperty("user.dir"), "app/resources/jni/WebView2Loader.dll"),
            File(System.getProperty("user.dir"), "resources/jni/WebView2Loader.dll"),
            File(System.getProperty("user.home"), "AppData/Local/CloudStream/app/resources/jni/WebView2Loader.dll"),
            System.getProperty("compose.application.resources.dir")?.let { File(it, "WebView2Loader.dll") },
            File("appResources/windows/mpv/WebView2Loader.dll"),
            File("desktop-app/appResources/windows/mpv/WebView2Loader.dll"),
            File("appResources/windows/WebView2Loader.dll"),
            File(System.getProperty("user.home"), "AppData/Local/CloudStream/app/resources/WebView2Loader.dll"),
            File(System.getProperty("user.home"), "AppData/Local/Microsoft/TeamsMeetingAdd-in/1.24.14501/x64/runtimes/win-x64/native/WebView2Loader.dll"),
        )

        for (candidate in candidatePaths) {
            if (candidate.isFile) {
                try {
                    val loaded = Native.load(candidate.absolutePath, Loader::class.java)
                    AppLogger.i("$TAG: Successfully loaded native loader from ${candidate.absolutePath}")
                    return loaded
                } catch (t: Throwable) {
                    AppLogger.w("$TAG: Candidate loader failed at ${candidate.absolutePath}: ${t.message}")
                }
            }
        }

        return try {
            val loaded = Native.load("WebView2Loader", Loader::class.java)
            AppLogger.i("$TAG: Loaded WebView2Loader from system library path.")
            loaded
        } catch (t: Throwable) {
            AppLogger.w("$TAG: Native WebView2Loader not found: ${t.message}")
            null
        }
    }

    /** The installed Microsoft Edge WebView2 runtime version (e.g. "154.0.4258.53") */
    val version: String? by lazy {
        val l = loader ?: return@lazy null
        try {
            val ref = PointerByReference()
            if (l.GetAvailableCoreWebView2BrowserVersionString(null, ref) < 0) return@lazy null
            val p = ref.value ?: return@lazy null
            try {
                p.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(p)
            }
        } catch (t: Throwable) {
            AppLogger.w("$TAG: Failed to query runtime version: ${t.message}")
            null
        }.also {
            AppLogger.i("$TAG: WebView2 Runtime version: ${it ?: "unavailable"}")
        }
    }

    val isAvailable: Boolean get() = version != null

    /** Genuine Edge User-Agent matching the host's actual WebView2 version */
    val userAgent: String
        get() {
            val major = version?.substringBefore('.') ?: "154"
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36 Edg/$major.0.0.0"
        }

    // ------------------------------------------------------------------ Worker STA Thread

    private val queue = ConcurrentLinkedQueue<() -> Unit>()

    @Volatile private var thread: Thread? = null
    private var parking: WinDef.HWND? = null
    private val started = CountDownLatch(1)
    private val hosts = ConcurrentHashMap<Pointer, WindowsWebView2Session>()
    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "windows-webview2-timer").apply { isDaemon = true }
    }

    private val windowProc = object : WinUser.WindowProc {
        override fun callback(hwnd: WinDef.HWND?, uMsg: Int, wParam: WinDef.WPARAM?, lParam: WinDef.LPARAM?): WinDef.LRESULT {
            when (uMsg) {
                WM_RUN -> {
                    drain()
                    return WinDef.LRESULT(0)
                }
                WinUser.WM_SIZE -> hwnd?.pointer?.let { hosts[it] }?.let { session ->
                    runCatching { session.onHostResized() }
                }
            }
            return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam)
        }
    }

    private fun drain() {
        while (true) {
            val task = queue.poll() ?: break
            try {
                task()
            } catch (t: Throwable) {
                AppLogger.e("$TAG: Task execution error: ${t.message}")
            }
        }
    }

    private fun ensureThread() {
        if (thread != null) return
        synchronized(this) {
            if (thread != null) return
            thread = Thread({ loop() }, "windows-webview2").apply {
                isDaemon = true
                start()
            }
        }
        startTimers()
    }

    private fun loop() {
        runCatching {
            val fn = com.sun.jna.Function.getFunction("user32", "SetThreadDpiAwarenessContext")
            fn.invoke(Pointer::class.java, arrayOf(Pointer.createConstant(-4)))
        }
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        val instance = Kernel32.INSTANCE.GetModuleHandle(null)
        val cls = WinUser.WNDCLASSEX().apply {
            hInstance = instance
            lpfnWndProc = windowProc
            lpszClassName = "CloudStreamWebView2Parking"
        }
        User32.INSTANCE.RegisterClassEx(cls)

        // Off-screen parking window at (-32000, -32000) keeps non-interactive challenges alive
        // without background timer throttling.
        parking = User32.INSTANCE.CreateWindowEx(
            0x80 or 0x08000000,
            "CloudStreamWebView2Parking",
            "CloudStream Hidden Web Host",
            0x80000000.toInt() or 0x02000000,
            -32000,
            -32000,
            1280,
            720,
            null,
            null,
            instance,
            null,
        )
        User32.INSTANCE.ShowWindow(parking, 0) // SW_HIDE
        started.countDown()

        val msg = WinUser.MSG()
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
    }

    /** Schedules [block] to run safely on the dedicated WebView2 STA thread */
    fun post(block: () -> Unit) {
        ensureThread()
        queue.add(block)
        started.await(10, TimeUnit.SECONDS)
        User32.INSTANCE.PostMessage(parking, WM_RUN, null, null)
    }

    val isWebViewThread: Boolean get() = Thread.currentThread() === thread

    internal fun createHost(session: WindowsWebView2Session, width: Int, height: Int): WinDef.HWND {
        val hwnd = User32.INSTANCE.CreateWindowEx(
            0,
            "CloudStreamWebView2Parking",
            null,
            0x40000000 or 0x10000000 or 0x02000000,
            0,
            0,
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            parking,
            null,
            Kernel32.INSTANCE.GetModuleHandle(null),
            null,
        )
        hosts[hwnd.pointer] = session
        return hwnd
    }

    internal fun destroyHost(hwnd: WinDef.HWND) {
        hosts.remove(hwnd.pointer)
        User32.INSTANCE.DestroyWindow(hwnd)
    }

    /** Reparents a session's HWND back into the hidden parking window */
    internal fun park(hwnd: WinDef.HWND) {
        parking?.let { User32.INSTANCE.SetParent(hwnd, it) }
    }

    // ------------------------------------------------------------------ Environment

    private var env: Pointer? = null
    private var env2: Pointer? = null
    private var creatingEnv = false
    private val envWaiters = ArrayList<(Pointer?) -> Unit>()
    private val activeSessions = ConcurrentHashMap.newKeySet<WindowsWebView2Session>()
    private val lastSyncedClearanceSig = ConcurrentHashMap<String, String>()

    internal val environment2: Pointer? get() = env2
    internal val environment: Pointer? get() = env

    internal fun withEnvironment(block: (Pointer?) -> Unit) {
        env?.let {
            block(it)
            return
        }
        envWaiters.add(block)
        if (creatingEnv) return
        creatingEnv = true

        val l = loader
        if (l == null) {
            finishEnv(null)
            return
        }

        // Configure anti-throttling flags so background Turnstile challenges resolve at full speed
        Kernel32.INSTANCE.SetEnvironmentVariable(
            "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS",
            listOf(
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--disable-backgrounding-occluded-windows",
                "--force-dark-mode",
                "--enable-features=WebContentsForceDark",
                "--disable-features=CalculateNativeWinOcclusion,msSmartScreenProtection,msEdgeShoppingAssistant,msWebOOUI,msPdfOOUI,HardwareMediaKeyHandling,MediaSessionService",
                "--autoplay-policy=no-user-gesture-required",
                "--disable-blink-features=AutomationControlled",
                "--renderer-process-limit=4",
                "--no-first-run",
                "--disable-component-update",
            ).joinToString(" "),
        )

        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dataDir = File(appData, "CloudStreamDesktop/webview2_runtime").also { it.mkdirs() }
        val startTime = System.currentTimeMillis()

        val handler = ComObject.completed(IID_ENV_DONE) { hr, e ->
            if (WindowsCom.ok(hr) && e != null) {
                AppLogger.i("$TAG: Edge WebView2 $version initialized in ${System.currentTimeMillis() - startTime}ms")
                finishEnv(WindowsCom.addRef(e))
            } else {
                AppLogger.e("$TAG: Failed to start WebView2 environment: HRESULT 0x${Integer.toHexString(hr)}")
                finishEnv(null)
            }
        }

        val hr = l.CreateCoreWebView2EnvironmentWithOptions(null, WString(dataDir.absolutePath), null, handler.pointer)
        handler.releaseOwn()
        if (hr < 0) {
            AppLogger.e("$TAG: CreateCoreWebView2EnvironmentWithOptions error: HRESULT 0x${Integer.toHexString(hr)}")
            finishEnv(null)
        }
    }

    private fun finishEnv(e: Pointer?) {
        creatingEnv = false
        env = e
        env2 = e?.let { WindowsCom.query(it, IID_ENV2) }
        val waiting = envWaiters.toList()
        envWaiters.clear()
        waiting.forEach { runCatching { it(e) } }
    }

    internal fun register(session: WindowsWebView2Session) {
        activeSessions.add(session)
    }

    internal fun unregister(session: WindowsWebView2Session) {
        activeSessions.remove(session)
    }

    internal fun isBloatCookie(name: String): Boolean = com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.isBloatCookie(name)

    // ------------------------------------------------------------------ Continuous Cookie Sync

    private val cores = LinkedHashSet<Pointer>()

    private fun cookieManager(): Pointer? = cores.firstOrNull()?.let { WindowsCom.getPtr(it, 66) }

    internal fun onCoreCreated(core2: Pointer?) {
        if (core2 != null) cores.add(core2)
    }

    internal fun onCoreRemoved(core2: Pointer?) {
        if (core2 != null) cores.remove(core2)
    }

    internal fun requestSyncCookies() {
        post { syncCookies() }
    }

    fun clearBrowserCookies(domain: String? = null) {
        post {
            activeSessions.forEach { session ->
                session.devTools("Network.clearBrowserCookies")
            }
            val cm = cookieManager()
            if (cm != null) {
                try {
                    if (domain == null) {
                        WindowsCom.call(cm, 8) // DeleteAllCookies
                        lastSyncedClearanceSig.clear()
                        AppLogger.i("$TAG: Cleared all cookies from WebView2 engine.")
                    } else {
                        val clean = domain.lowercase().trim().trimStart('.')
                        lastSyncedClearanceSig.remove(clean)
                        // Slot 5: GetCookies(uri, handler). Enumerate and delete matching cookies via slot 7 (DeleteCookie)
                        val handler = ComObject.completed(IID_GET_COOKIES_DONE) { hr, list ->
                            if (WindowsCom.ok(hr) && list != null) {
                                val count = IntByReference()
                                WindowsCom.call(list, 3, count)
                                val total = count.value
                                val delCm = cookieManager()
                                if (delCm != null) {
                                    try {
                                        for (i in 0 until total) {
                                            val c = WindowsCom.getPtr(list, 4, i) ?: continue
                                            try {
                                                val d = WindowsCom.getString(c, 6)?.trimStart('.')?.lowercase()
                                                if (d == clean || d?.endsWith(".$clean") == true || clean.endsWith(".$d")) {
                                                    WindowsCom.call(delCm, 7, c) // DeleteCookie
                                                }
                                            } finally {
                                                WindowsCom.release(c)
                                            }
                                        }
                                    } finally {
                                        WindowsCom.release(delCm)
                                    }
                                }
                            }
                        }
                        WindowsCom.call(cm, 5, WString(""), handler.pointer)
                        handler.releaseOwn()
                        AppLogger.i("$TAG: Cleared cookies for $clean from WebView2 engine.")
                    }
                } finally {
                    WindowsCom.release(cm)
                }
            } else {
                if (domain == null) {
                    lastSyncedClearanceSig.clear()
                } else {
                    lastSyncedClearanceSig.remove(domain.lowercase().trim().trimStart('.'))
                }
            }
        }
    }

    private fun startTimers() {
        // Poll cookies every 1.5s when any browser session is ready
        timer.scheduleWithFixedDelay({
            if (activeSessions.any { it.isReady }) {
                post { syncCookies() }
            }
        }, 1500, 1500, TimeUnit.MILLISECONDS)
    }

    private fun syncCookies() {
        val cm = cookieManager() ?: return
        val handler = ComObject.completed(IID_GET_COOKIES_DONE) { hr, list ->
            if (WindowsCom.ok(hr) && list != null) {
                val count = IntByReference()
                WindowsCom.call(list, 3, count)
                val total = count.value

                val allCookies = ArrayList<Cookie>(total)
                val cookiesByDomain = LinkedHashMap<String, MutableList<Cookie>>()

                for (i in 0 until total) {
                    val c = WindowsCom.getPtr(list, 4, i) ?: continue
                    try {
                        val name = WindowsCom.getString(c, 3) ?: continue
                        if (isBloatCookie(name)) continue
                        val value = WindowsCom.getString(c, 4) ?: ""
                        val domain = WindowsCom.getString(c, 6) ?: continue
                        val path = WindowsCom.getString(c, 7) ?: "/"
                        val isSession = WindowsCom.getBool(c, 16)
                        val expires = if (isSession) null else WindowsCom.getDouble(c, 8)?.let { (it * 1000).toLong() }
                        val isSecure = WindowsCom.getBool(c, 14)
                        val isHttpOnly = WindowsCom.getBool(c, 10)

                        val cleanHost = domain.trimStart('.')
                        val okCookie = try {
                            Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(cleanHost)
                                .path(path)
                                .apply {
                                    if (isSecure) secure()
                                    if (isHttpOnly) httpOnly()
                                    expires?.let { expiresAt(it) }
                                }
                                .build()
                        } catch (_: Throwable) {
                            null
                        }

                        if (okCookie != null) {
                            allCookies.add(okCookie)
                            cookiesByDomain.getOrPut(cleanHost) { ArrayList() }.add(okCookie)
                        }
                    } finally {
                        WindowsCom.release(c)
                    }
                }

                if (allCookies.isNotEmpty()) {
                    // Batch save into DPAPI Vault
                    WindowsCookieVault.saveCookies(allCookies)

                    // Find any domain with cf_clearance and register clearance with ALL domain cookies
                    for ((domain, domainCookies) in cookiesByDomain) {
                        val clearanceCookie = domainCookies.firstOrNull { it.name == "cf_clearance" }
                        if (clearanceCookie != null) {
                            val apex = CloudflareKiller.getApexDomain(domain)
                            val combinedCookies = LinkedHashMap<String, Cookie>()
                            if (apex.isNotBlank() && apex != domain) {
                                cookiesByDomain[apex]?.forEach { combinedCookies[it.name] = it }
                            }
                            domainCookies.forEach { combinedCookies[it.name] = it }

                            val sig = "${clearanceCookie.value}_${combinedCookies.size}"
                            val prevSig = lastSyncedClearanceSig[domain]
                            if (sig != prevSig) {
                                lastSyncedClearanceSig[domain] = sig
                                CloudflareKiller.saveClearance(
                                    host = domain,
                                    userAgent = userAgent,
                                    okCookies = combinedCookies.values.toList(),
                                )
                                AppLogger.i("$TAG: Clearance synchronized for $domain (${combinedCookies.size} cookies).")
                            }
                        }
                    }
                }
            }
        }

        val hr = WindowsCom.call(cm, 5, WString(""), handler.pointer)
        WindowsCom.release(cm)
        handler.releaseOwn()
    }
}
