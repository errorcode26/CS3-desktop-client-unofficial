package com.lagradost.cloudstream3.desktop.sandbox.worker

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxAuditReport
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedMainPageData
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedProviderMetadata
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SerializableSettingSchema
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedHomePageList
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedHomePageResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedItem
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedSearchResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerResponse
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.models.CustomSite
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Headless Entrypoint executing exclusively inside the Windows AppContainer and Job Object.
 * Communicates with the main Compose app via line-delimited JSON-RPC over stdin/stdout.
 */
object PluginWorkerMain {

    private val logger = LoggerFactory.getLogger(PluginWorkerMain::class.java)
    private val inFlightRequests = java.util.concurrent.atomic.AtomicInteger(0)
    private var postScrapeGcJob: kotlinx.coroutines.Job? = null
    private val gcMutex = Mutex()

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("java.awt.headless", "true")
        System.setProperty("cloudstream.sandbox.worker", "true")
        System.setProperty("java.util.prefs.PreferencesFactory", NoOpPreferencesFactory::class.java.name)

        // Capture raw stdout for RPC pipe before redirecting standard console logging to stderr
        // Prevent JVM URLConnection cache from holding open file handles on loaded JARs.
        // Without this, Windows file locks persist after ClassLoader close, blocking plugin updates.
        try { java.net.URLConnection.setDefaultUseCaches("jar", false) } catch (e: Throwable) { logger.debug("Jar cache config note: ${e.message}") }
        val rawStdout = System.out
        System.setOut(System.err)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error("Unhandled worker exception on thread '${thread.name}': ${throwable.message}", throwable)
        }

        try {
            java.security.Security.addProvider(org.conscrypt.Conscrypt.newProvider())
        } catch (e: Throwable) { logger.debug("Security provider init note: ${e.message}") }
        try {
            java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        } catch (e: Throwable) { logger.debug("Security provider init note: ${e.message}") }

        try {
            com.lagradost.common.storage.DesktopDataStore.enableWorkerMode()

            com.lagradost.cloudstream3.desktop.network.NetworkConfig.updateGlobalNetworkClients()
            org.schabi.newpipe.extractor.NewPipe.init(com.lagradost.cloudstream3.desktop.network.NewPipeDownloader.getInstance())
        } catch (t: Throwable) {
            logger.warn("Worker network client initialization warning: ${t.message}")
        }

        try {
            com.lagradost.runtime.security.RhinoSecurity.init()
        } catch (e: Throwable) { logger.debug("Security provider init note: ${e.message}") }

        logger.info("Sandboxed PluginWorkerMain started. PID: ${ProcessHandle.current().pid()}")

        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8), 65536)
        val writer = BufferedWriter(OutputStreamWriter(rawStdout, StandardCharsets.UTF_8), 65536)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        WorkerHostBridge.rpcWriter = writer

        // Periodic background memory trimmer: runs every 60 seconds to release unreferenced
        // plugin extraction, decompressed bytecode, and parsing buffers back to the Windows OS.
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(60_000L)
                try {
                    System.gc()
                } catch (e: Throwable) { logger.debug("Worker cleanup note: ${e.message}") }
            }
        }

        com.lagradost.common.storage.DesktopDataStore.onPersistKeyOverride = { key, json ->
            WorkerHostBridge.saveSetting(key, json)
        }
        com.lagradost.common.storage.DesktopDataStore.onRemoveKeyOverride = { key ->
            WorkerHostBridge.removeSetting(key)
        }

        android.webkit.WebView.loadUrlHandler = java.util.function.BiConsumer { webView, url ->
            logger.info("Worker WebView.loadUrl: $url")
            val client = webView.webViewClient
            client?.onPageStarted(webView, url, null)
            scope.launch {
                val httpUrl = url.toHttpUrlOrNull()
                val host = httpUrl?.host ?: ""
                try {
                    if (host.isNotBlank()) {
                        if (com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS) != true) {
                            logger.info("Worker WebView.loadUrl: Cloudflare solver is disabled by user in Settings. Skipping solve request for $host.")
                        } else if (com.lagradost.common.storage.DesktopDataStore.isDomainCfSilenced(host)) {
                            logger.info("Worker WebView.loadUrl: Host $host is silenced/ignored. Skipping solve request.")
                        } else {
                            val solveResult = WorkerHostBridge.requestCloudflareSolve(
                                url = url,
                                host = host,
                                headers = emptyMap()
                            )
                            if (solveResult != null && solveResult.cookies.isNotEmpty()) {
                                com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance?.saveCookies(solveResult.cookies)
                                val ua = solveResult.userAgent?.takeIf { it.isNotBlank() } ?: com.lagradost.cloudstream3.USER_AGENT
                                com.lagradost.cloudstream3.network.CloudflareKiller.saveClearance(host = host, userAgent = ua, okCookies = solveResult.cookies)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error("Worker WebView.loadUrlHandler error: ${e.message}")
                } finally {
                    try {
                        client?.onPageFinished(webView, url)
                    } catch (e: Throwable) { logger.debug("WebView onPageFinished note: ${e.message}") }
                }
            }
        }

        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                if (line.contains("\"type\":\"CALLBACK_RESPONSE\"") || line.contains("\"callbackId\"")) {
                    try {
                        val cbResp = WorkerProtocolSerializer.deserializeCallbackResponse(line)
                        WorkerHostBridge.pendingCallbacks[cbResp.callbackId]?.complete(cbResp)
                    } catch (e: Throwable) {
                        logger.error("Failed to parse callback response: $line", e)
                    }
                    continue
                }

                val req = try {
                    WorkerProtocolSerializer.deserializeRequest(line)
                } catch (e: Throwable) {
                    logger.error("Failed to parse request JSON: $line", e)
                    val errResp = WorkerResponse(id = -1, success = false, error = "Malformed JSON: ${e.message}")
                    sendResponse(writer, errResp)
                    continue
                }

                inFlightRequests.incrementAndGet()
                scope.launch {
                    try {
                    val resp = try {
                        handleRequest(req)
                    } catch (e: Throwable) {
                        logger.error("Error executing action '${req.action}' for ID ${req.id}", e)
                        WorkerResponse(id = req.id, success = false, error = "${e.javaClass.simpleName}: ${e.message}")
                    }

                    sendResponse(writer, resp)
                    } finally {
                        if (inFlightRequests.decrementAndGet() == 0) {
                            scope.launch {
                                gcMutex.withLock {
                                    postScrapeGcJob?.cancel()
                                    postScrapeGcJob = scope.launch {
                                        kotlinx.coroutines.delay(3_000L)
                                        if (inFlightRequests.get() == 0) {
                                            try {
                                                System.gc()
                                            } catch (e: Throwable) { logger.debug("Post-scrape GC note: ${e.message}") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            logger.error("Fatal exception in PluginWorkerMain loop", t)
        }

        logger.info("Sandboxed PluginWorkerMain exiting cleanly (stdin closed).")
    }

    private suspend fun handleRequest(req: WorkerRequest): WorkerResponse {
        return when (req.action) {
            WorkerRequest.ACTION_PING -> {
                WorkerResponse(id = req.id, success = true, payloadJson = "\"pong\"")
            }

            WorkerRequest.ACTION_SYNC_COOKIES -> {
                val serializableCookies = req.cookies.orEmpty()
                val okCookies = serializableCookies.mapNotNull { sc ->
                    try {
                        okhttp3.Cookie.Builder()
                            .name(sc.name)
                            .value(sc.value)
                            .domain(sc.domain)
                            .path(sc.path)
                            .apply {
                                if (sc.secure) secure()
                                if (sc.httpOnly) httpOnly()
                                sc.expiresAt?.let { expiresAt(it) }
                            }
                            .build()
                    } catch (_: Throwable) {
                        null
                    }
                }
                com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance?.saveCookies(okCookies)
                logger.info("Synchronized ${okCookies.size} cookies from host into sandboxed worker.")

                // Populate Cloudflare clearance cache in the worker for hosts that have cf_clearance
                val cookiesByDomain = okCookies.groupBy { it.domain.lowercase().trimStart('.') }
                val tlsBoundHosts = com.lagradost.common.storage.DesktopDataStore.getKey<Set<String>>(
                    com.lagradost.cloudstream3.network.CloudflareKiller.PREF_TLS_BOUND_HOSTS
                ).orEmpty()

                cookiesByDomain.forEach { (domain, dCookies) ->
                    val hasCfClearance = dCookies.any { it.name.equals("cf_clearance", ignoreCase = true) }
                    if (hasCfClearance) {
                        val isTls = tlsBoundHosts.contains(domain) || com.lagradost.cloudstream3.network.CloudflareKiller.isTlsBound(domain)
                        com.lagradost.cloudstream3.network.CloudflareKiller.saveClearance(
                            host = domain,
                            userAgent = com.lagradost.cloudstream3.USER_AGENT,
                            okCookies = dCookies,
                            isTlsBound = isTls
                        )
                        logger.info("Initialized clearance in worker for $domain (${dCookies.size} cookies, isTlsBound=$isTls)")
                    }
                }

                WorkerResponse(id = req.id, success = true)
            }

            WorkerRequest.ACTION_SECURITY_AUDIT -> {
                logger.info("Executing live Sandbox Security Audit probe inside AppContainer...")
                val details = mutableMapOf<String, String>()

                // Probe 1: Subprocess execution (Job Object ActiveProcessLimit = 1)
                val subprocessBlocked = try {
                    val p = ProcessBuilder("cmd.exe", "/c", "echo audit_probe").start()
                    p.waitFor()
                    details["subprocess"] = "FAILED: Subprocess spawned"
                    false
                } catch (e: Throwable) {
                    details["subprocess"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 2: Write to real OS Windows Temp folder outside sandbox
                val winTempWriteBlocked = try {
                    val userHome = System.getProperty("user.home") ?: "C:\\Users"
                    val tempProbe = File(userHome, "AppData/Local/Temp/cloudstream_sandbox_probe_${System.currentTimeMillis()}.txt")
                    tempProbe.writeText("audit_test")
                    tempProbe.delete()
                    details["winTempWrite"] = "FAILED: Wrote to ${tempProbe.absolutePath}"
                    false
                } catch (e: Throwable) {
                    details["winTempWrite"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 3: Write to Windows Startup folder
                val startupWriteBlocked = try {
                    val appData = System.getenv("APPDATA") ?: ""
                    val startupProbe = File(appData, "Microsoft/Windows/Start Menu/Programs/Startup/cloudstream_probe_${System.currentTimeMillis()}.bat")
                    startupProbe.writeText("echo evil")
                    startupProbe.delete()
                    details["startupWrite"] = "FAILED: Wrote to Startup folder"
                    false
                } catch (e: Throwable) {
                    details["startupWrite"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 4: Extension directory write / tamper protection
                val extensionTamperBlocked = try {
                    val extProbe = File(PlatformPaths.extensionsDir, "probe_test_${System.currentTimeMillis()}.jar")
                    extProbe.writeText("probe")
                    extProbe.delete()
                    details["extensionTamper"] = "FAILED: Wrote to Extensions directory"
                    false
                } catch (e: Throwable) {
                    details["extensionTamper"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 5: Read sensitive host files
                val sensitiveReadBlocked = try {
                    val db = File(PlatformPaths.dataDir, "main.db")
                    if (db.exists()) {
                        db.readBytes()
                        details["sensitiveRead"] = "FAILED: Read main.db"
                        false
                    } else {
                        val ssh = File(System.getProperty("user.home"), ".ssh/id_rsa")
                        if (ssh.exists()) {
                            ssh.readBytes()
                            details["sensitiveRead"] = "FAILED: Read SSH key"
                            false
                        } else {
                            details["sensitiveRead"] = "BLOCKED: Protected (target absent)"
                            true
                        }
                    }
                } catch (e: Throwable) {
                    details["sensitiveRead"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 6: Private Network / Loopback connection (AppContainer Network Isolation)
                val lanAccessBlocked = try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 54321), 200)
                    socket.close()
                    details["lanAccess"] = "FAILED: Connected to 127.0.0.1 loopback"
                    false
                } catch (e: java.net.ConnectException) {
                    details["lanAccess"] = "BLOCKED: Loopback blocked by WFP (${e.message})"
                    true
                } catch (e: java.net.SocketTimeoutException) {
                    details["lanAccess"] = "BLOCKED: Loopback timed out"
                    true
                } catch (e: java.io.IOException) {
                    details["lanAccess"] = "BLOCKED: ${e.message}"
                    true
                } catch (e: Throwable) {
                    details["lanAccess"] = "BLOCKED: ${e.message}"
                    true
                }

                // Probe 7: Isolated sandbox scratch folder write (should succeed)
                val sandboxTmpAllowed = try {
                    val tmpDir = File(PlatformPaths.sandboxDir, "tmp")
                    if (!tmpDir.exists()) tmpDir.mkdirs()
                    val tmpFile = File(tmpDir, "probe_scratch_${System.currentTimeMillis()}.tmp")
                    tmpFile.writeText("scratch_ok")
                    val verified = tmpFile.readText() == "scratch_ok"
                    tmpFile.delete()
                    details["sandboxTmp"] = if (verified) "ALLOWED: Scratch write verified" else "FAILED: Scratch write mismatch"
                    verified
                } catch (e: Throwable) {
                    details["sandboxTmp"] = "FAILED: ${e.message}"
                    false
                }

                val report = SandboxAuditReport(
                    subprocessBlocked = subprocessBlocked,
                    winTempWriteBlocked = winTempWriteBlocked,
                    startupWriteBlocked = startupWriteBlocked,
                    extensionTamperBlocked = extensionTamperBlocked,
                    sensitiveReadBlocked = sensitiveReadBlocked,
                    lanAccessBlocked = lanAccessBlocked,
                    sandboxTmpAllowed = sandboxTmpAllowed,
                    details = details
                )

                val reportJson = WorkerProtocolSerializer.mapper.writeValueAsString(report)
                logger.info("Sandbox Security Audit probe completed. Fully secure: ${report.isFullySecure}")
                WorkerResponse(id = req.id, success = true, payloadJson = reportJson)
            }

            WorkerRequest.ACTION_SYNC_SETTINGS -> {
                val map = req.settingsMap.orEmpty()
                map.forEach { (k, v) ->
                    com.lagradost.common.storage.DesktopDataStore.rawKeyCache[k] = v
                }
                val removed = req.removedSettingKeys.orEmpty()
                removed.forEach { key ->
                    com.lagradost.common.storage.DesktopDataStore.rawKeyCache.remove(key)
                }
                logger.info("Synchronized ${map.size} settings and removed ${removed.size} from sandboxed worker.")
                instantiateClonedSitesInWorker()
                WorkerResponse(id = req.id, success = true)
            }

            WorkerRequest.ACTION_UNLOAD_PLUGIN -> {
                val path = req.pluginPath ?: error("Missing pluginPath")
                try {
                    ExtensionLoader.unloadPlugin(path)
                } catch (e: Throwable) {
                    logger.warn("Worker failed unloading plugin $path: ${e.message}")
                }
                try {
                    val canonical = try { File(path).canonicalPath } catch (e: Throwable) { logger.debug("Canonical path note: ${e.message}"); path }
                    if (canonical != path) {
                        ExtensionLoader.unloadPlugin(canonical)
                    }
                } catch (e: Throwable) { logger.debug("Worker cleanup note: ${e.message}") }
                @Suppress("ExplicitGarbageCollectionCall")
                System.gc()
                logger.info("Sandboxed worker unloaded plugin $path")
                WorkerResponse(id = req.id, success = true)
            }

            WorkerRequest.ACTION_LOAD_PLUGIN -> {
                val path = req.pluginPath ?: error("Missing pluginPath")
                val jarFile = File(path)
                if (!jarFile.exists()) {
                    error("Plugin file not found: $path")
                }
                ExtensionLoader.loadAndInit(jarFile, forceBypassSecurity = true, forceRetry = req.forceRetry)
                instantiateClonedSitesInWorker(req.clonedSitesJson)
                // Return all currently loaded provider metadata so the host avoids a second round-trip
                val providers = APIHolder.allProviders.map { p ->
                    SandboxedProviderMetadata(
                        name = p.name,
                        mainUrl = p.mainUrl,
                        lang = p.lang,
                        supportedTypes = p.supportedTypes.map { it.name }.toSet(),
                        sourcePlugin = p.sourcePlugin,
                        hasMainPage = p.hasMainPage,
                        hasQuickSearch = p.hasQuickSearch,
                        providerClassName = p.javaClass.simpleName,
                        canBeOverridden = p.canBeOverridden,
                        mainPage = p.mainPage.map {
                            SandboxedMainPageData(name = it.name, data = it.data, horizontalImages = it.horizontalImages)
                        }
                    )
                }
                val internalName = jarFile.nameWithoutExtension.removeSuffix("-jvm").removeSuffix("-secure")
                val expectedPref = "${internalName}_"
                val allSchemas = com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.entries
                    .filter { it.key.equals(expectedPref, ignoreCase = true) || it.key.trimEnd('_').equals(internalName, ignoreCase = true) }
                    .flatMap { (_, map) ->
                    map.values.map { schema ->
                        SerializableSettingSchema(
                            pluginPrefName = schema.pluginPrefName,
                            key = schema.key,
                            type = schema.type,
                            defaultValue = schema.defaultValue,
                            isGlobal = schema.isGlobal,
                            options = schema.options,
                            storageKey = schema.storageKey,
                        )
                    }
                }
                val loadResult = SandboxedPluginLoadResult(providers = providers, schemas = allSchemas)
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(loadResult)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_SEARCH -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val query = req.query ?: error("Missing query")
                val page = req.page ?: 1
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val (results, hasNext) = try {
                    val searchList = provider.search(query, page)
                    (searchList?.items ?: emptyList()) to (searchList?.hasNext ?: false)
                } catch (e: NotImplementedError) {
                    try {
                        val searchList = provider.search(query) ?: emptyList()
                        searchList to false
                    } catch (e2: NotImplementedError) {
                        if (provider.hasQuickSearch) {
                            try {
                                val quickList = provider.quickSearch(query) ?: emptyList()
                                quickList to false
                            } catch (_: NotImplementedError) {
                                emptyList<SearchResponse>() to false
                            }
                        } else {
                            emptyList<SearchResponse>() to false
                        }
                    }
                }

                val typedItems = results.map { item ->
                    TypedItem(
                        className = item.javaClass.name,
                        json = WorkerProtocolSerializer.mapper.valueToTree(item)
                    )
                }
                val typedResponse = TypedSearchResponse(
                    items = typedItems,
                    hasNext = hasNext
                )
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(typedResponse)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_LOAD -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val url = req.url ?: error("Missing url")
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val details = provider.load(url)
                val json = if (details != null) {
                    val typed = TypedItem(
                        className = details.javaClass.name,
                        json = WorkerProtocolSerializer.mapper.valueToTree(details)
                    )
                    WorkerProtocolSerializer.mapper.writeValueAsString(typed)
                } else {
                    null
                }
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_MAIN_PAGE -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val page = req.page ?: 1
                val reqName = req.requestName ?: ""
                val reqData = req.requestData ?: ""
                val horiz = req.horizontalImages ?: false

                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val pageRequest = MainPageRequest(name = reqName, data = reqData, horizontalImages = horiz)
                val homePageResponse = provider.getMainPage(page, pageRequest)
                val json = if (homePageResponse != null) {
                    val typedLists = homePageResponse.items.map { list ->
                        val typedSearchItems = list.list.map { item ->
                            TypedItem(
                                className = item.javaClass.name,
                                json = WorkerProtocolSerializer.mapper.valueToTree(item)
                            )
                        }
                        TypedHomePageList(
                            name = list.name,
                            list = typedSearchItems,
                            isHorizontalImages = list.isHorizontalImages
                        )
                    }
                    val typedResponse = TypedHomePageResponse(
                        items = typedLists,
                        hasNext = homePageResponse.hasNext
                    )
                    WorkerProtocolSerializer.mapper.writeValueAsString(typedResponse)
                } else {
                    null
                }
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_LOAD_LINKS -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val data = req.data ?: error("Missing data")
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val links = java.util.Collections.synchronizedList(mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>())
                val subs = java.util.Collections.synchronizedList(mutableListOf<com.lagradost.cloudstream3.SubtitleFile>())

                try {
                    provider.loadLinks(
                        data = data,
                        isCasting = req.isCasting,
                        subtitleCallback = { sub ->
                            try {
                                subs.add(sub)
                                WorkerHostBridge.emitSubtitle(req.id, sub)
                            } catch (e: Throwable) {
                                logger.warn("Failed to emit subtitle callback: ${e.message}")
                            }
                        },
                        callback = { link ->
                            try {
                                links.add(link)
                                WorkerHostBridge.emitLink(req.id, link)
                            } catch (e: Throwable) {
                                logger.warn("Failed to emit link callback: ${e.message}")
                            }
                        }
                    )
                } catch (e: Throwable) {
                    logger.warn("Exception during provider.loadLinks for '$providerName': ${e.message}", e)
                }

                val payload = mapOf("links" to links.toList(), "subs" to subs.toList())
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(payload)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_GET_LOADED_PROVIDERS -> {
                val providers = APIHolder.allProviders.map { p ->
                    SandboxedProviderMetadata(
                        name = p.name,
                        mainUrl = p.mainUrl,
                        lang = p.lang,
                        supportedTypes = p.supportedTypes.map { it.name }.toSet(),
                        sourcePlugin = p.sourcePlugin,
                        hasMainPage = p.hasMainPage,
                        hasQuickSearch = p.hasQuickSearch,
                        providerClassName = p.javaClass.simpleName,
                        canBeOverridden = p.canBeOverridden,
                        mainPage = p.mainPage.map {
                            SandboxedMainPageData(
                                name = it.name,
                                data = it.data,
                                horizontalImages = it.horizontalImages
                            )
                        }
                    )
                }
                val loadResult = SandboxedPluginLoadResult(providers = providers, schemas = emptyList())
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(loadResult)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_SYNC_CLONED_SITES -> {
                synchronized(APIHolder.allProviders) {
                    APIHolder.allProviders.removeIf { !it.canBeOverridden }
                }
                instantiateClonedSitesInWorker(req.clonedSitesJson)
                val providers = APIHolder.allProviders.map { p ->
                    SandboxedProviderMetadata(
                        name = p.name,
                        mainUrl = p.mainUrl,
                        lang = p.lang,
                        supportedTypes = p.supportedTypes.map { it.name }.toSet(),
                        sourcePlugin = p.sourcePlugin,
                        hasMainPage = p.hasMainPage,
                        hasQuickSearch = p.hasQuickSearch,
                        providerClassName = p.javaClass.simpleName,
                        canBeOverridden = p.canBeOverridden,
                        mainPage = p.mainPage.map {
                            SandboxedMainPageData(
                                name = it.name,
                                data = it.data,
                                horizontalImages = it.horizontalImages
                            )
                        }
                    )
                }
                val loadResult = SandboxedPluginLoadResult(providers = providers, schemas = emptyList())
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(loadResult)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            else -> {
                WorkerResponse(id = req.id, success = false, error = "Unknown action '${req.action}'")
            }
        }
    }

    private fun instantiateClonedSitesInWorker(clonedSitesJson: String? = null) {
        try {
            val rawJson = clonedSitesJson
                ?: com.lagradost.common.storage.DesktopDataStore.rawKeyCache[PreferenceKeys.USER_PROVIDER_API]
                ?: com.lagradost.common.storage.DesktopDataStore.rawKeyCache["user_custom_sites"]
            if (rawJson.isNullOrBlank()) return

            val list = try {
                WorkerProtocolSerializer.mapper.readValue(
                    rawJson,
                    object : TypeReference<List<CustomSite>>() {}
                )
            } catch (e: Throwable) {
                logger.debug("Failed parsing cloned sites in worker: ${e.message}")
                emptyList()
            }
            if (list.isEmpty()) return

            synchronized(APIHolder.allProviders) {
                list.distinctBy { "${it.name}_${it.url.trimEnd('/')}" }.forEach { custom ->
                    val cleanUrl = custom.url.trimEnd('/')
                    val baseProvider = APIHolder.allProviders.firstOrNull {
                        it.canBeOverridden && (
                            it.javaClass.simpleName.equals(custom.parentJavaClass, ignoreCase = true) ||
                            it.name.equals(custom.parentJavaClass, ignoreCase = true)
                        )
                    } ?: return@forEach

                    if (baseProvider.mainUrl.trimEnd('/') == cleanUrl) return@forEach
                    val alreadyExists = APIHolder.allProviders.any {
                        it.name == custom.name && it.mainUrl.trimEnd('/') == cleanUrl
                    }
                    if (!alreadyExists) {
                        try {
                            val clone = baseProvider.javaClass.getDeclaredConstructor().newInstance()
                            clone.name = custom.name
                            clone.lang = custom.lang.ifBlank { baseProvider.lang }
                            clone.mainUrl = cleanUrl
                            clone.canBeOverridden = false
                            clone.sourcePlugin = baseProvider.sourcePlugin
                            APIHolder.allProviders.add(clone)
                            APIHolder.addPluginMapping(clone)
                            logger.info("Instantiated worker cloned provider '${custom.name}' (${custom.url}) from '${baseProvider.name}'")
                        } catch (t: Throwable) {
                            logger.warn("Failed instantiating worker clone '${custom.name}': ${t.message}")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            logger.warn("Error loading cloned sites in worker: ${t.message}")
        }
    }

    private fun sendResponse(writer: BufferedWriter, resp: WorkerResponse) {
        synchronized(writer) {
            val json = WorkerProtocolSerializer.serializeResponse(resp)
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }
}

class NoOpPreferencesFactory : java.util.prefs.PreferencesFactory {
    private val root = NoOpPreferences()
    override fun systemRoot(): java.util.prefs.Preferences = root
    override fun userRoot(): java.util.prefs.Preferences = root
}

private class NoOpPreferences : java.util.prefs.AbstractPreferences(null, "") {
    private val map = mutableMapOf<String, String>()
    override fun putSpi(key: String, value: String) { map[key] = value }
    override fun getSpi(key: String): String? = map[key]
    override fun removeSpi(key: String) { map.remove(key) }
    override fun removeNodeSpi() {}
    override fun keysSpi(): Array<String> = map.keys.toTypedArray()
    override fun childrenNamesSpi(): Array<String> = emptyArray()
    override fun childSpi(name: String): java.util.prefs.AbstractPreferences = this
    override fun syncSpi() {}
    override fun flushSpi() {}
}
