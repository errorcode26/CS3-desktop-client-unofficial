package com.lagradost.cloudstream3.desktop.sandbox.host

import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxAuditReport
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedProviderMetadata
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SerializableCookie
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerResponse
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerLauncher
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsJobObject
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsSandboxAcl
import com.lagradost.common.platform.PlatformPaths
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thrown when loading a plugin JAR in the sandboxed worker fails.
 * [isPermanent] indicates whether the failure is deterministic (corrupted bytecode,
 * class linking, or security violation) vs transient (timeout, IPC glitch).
 */
class PluginLoadFailureException(
    val pluginFile: File,
    val errorMessage: String,
    val isPermanent: Boolean,
    cause: Throwable? = null,
) : Exception("Failed to load plugin '${pluginFile.name}': $errorMessage", cause)

/**
 * Supervisor running in the main Compose app process.
 * Manages the lifecycle of the low-privilege sandboxed JVM worker,
 * multiplexing requests over anonymous pipes with automatic crash recovery.
 */
class SandboxedPluginHost(
    val containerName: String = "CloudStream.PluginWorker.Sandbox.v2",
    val memoryLimitBytes: Long = 3072L * 1024L * 1024L // 3 GB
) : Closeable {

    companion object {
        private val grantedSids = ConcurrentHashMap.newKeySet<String>()

        private val PROTECTED_HOST_KEYS = setOf(
            "trusted_plugins",
            "USER_PROVIDER_API",
            "preferred_player",
            "home_active_providers",
            "preferred_provider_name",
            "last_active_plugin_jar",
            "last_plugin_sync_timestamp",
            "search_history",
            "search_preference_providers",
            "pref_allow_cf_bypass",
            "dns_provider",
            "dns_over_https",
            "pref_dns_mode",
            "user_agent"
        )

        fun isBlockedOrPrivateUrl(rawUrl: String): Boolean {
            val lower = rawUrl.lowercase().trim()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return true

            val host = try {
                java.net.URI(rawUrl).host?.lowercase()?.trim() ?: return true
            } catch (_: Throwable) {
                return true
            }

            if (host == "localhost" || host == "0.0.0.0" || host == "::1" || host == "127.0.0.1") return true
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home") || host.endsWith(".arpa")) return true

            return try {
                val addresses = java.net.InetAddress.getAllByName(host)
                addresses.any { addr ->
                    addr.isLoopbackAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isAnyLocalAddress ||
                    isCarrierGradeNat(addr) ||
                    isIpv6UniqueLocal(addr)
                }
            } catch (_: Throwable) {
                true
            }
        }

        private fun isCarrierGradeNat(addr: java.net.InetAddress): Boolean {
            val bytes = addr.address
            if (bytes.size == 4) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 100 && (b1 in 64..127)) return true
            }
            return false
        }

        private fun isIpv6UniqueLocal(addr: java.net.InetAddress): Boolean {
            val bytes = addr.address
            return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
        }

        fun pruneWorkerClasspath(rawClasspath: String): String {
            if (rawClasspath.isBlank()) return ""
            val entries = rawClasspath.split(File.pathSeparator).filter { it.isNotBlank() }
            val pruned = entries.filter { entry ->
                val lower = entry.lowercase()
                val name = File(entry).name.lowercase()
                val isUiDependency = name.contains("skiko") ||
                    name.contains("compose") ||
                    name.contains("haze") ||
                    name.contains("coil") ||
                    name.contains("decompose") ||
                    name.contains("essenty") ||
                    name.contains("sqldelight") ||
                    name.contains("kdiscordipc") ||
                    name.contains("webview2") ||
                    name.startsWith("runtime-desktop") ||
                    name.startsWith("ui-desktop") ||
                    name.startsWith("ui-graphics-desktop") ||
                    name.startsWith("ui-text-desktop") ||
                    name.startsWith("ui-unit-desktop") ||
                    name.startsWith("foundation-desktop") ||
                    name.startsWith("material-desktop") ||
                    name.startsWith("material3-desktop") ||
                    name.startsWith("material-ripple-desktop") ||
                    name.startsWith("material-icons-") ||
                    lower.contains("org.jetbrains.compose") ||
                    lower.contains("androidx.compose") ||
                    lower.contains("org.jetbrains.skiko") ||
                    lower.contains("dev.chrisbanes.haze") ||
                    lower.contains("io.coil-kt") ||
                    lower.contains("com.arkivanov.decompose") ||
                    lower.contains("com.arkivanov.essenty") ||
                    lower.contains("app.cash.sqldelight")
                !isUiDependency
            }
            return pruned.joinToString(File.pathSeparator)
        }
    }

    private val logger = LoggerFactory.getLogger(SandboxedPluginHost::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mutex = Mutex()
    private val pluginLifecycleMutex = Mutex()
    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<WorkerResponse>>()

    private val loadedPluginPaths = ConcurrentHashMap.newKeySet<String>()

    data class ActiveLinkConsumer(
        val onLink: ((com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit)?,
        val onSubtitle: ((com.lagradost.cloudstream3.SubtitleFile) -> Unit)?,
    )

    private val activeLinkConsumers = ConcurrentHashMap<Long, ActiveLinkConsumer>()
    private val lastSolveAttempts = ConcurrentHashMap<String, Long>()
    private val activeWorkerNetworkRequests = ConcurrentHashMap<Long, Long>()

    private var currentJobObject: WindowsJobObject? = null
    private var currentProcess: WindowsAppContainerLauncher.SandboxedProcess? = null
    private var processWriter: BufferedWriter? = null
    @Volatile
    var isHostAvailable: Boolean = true

    private var repoCatalogSyncJob: kotlinx.coroutines.Job? = null
    private val repoCatalogSyncMutex = Mutex()
    private fun scheduleBridgedCatalogSync() {
        scope.launch(Dispatchers.IO) {
            repoCatalogSyncMutex.withLock {
                repoCatalogSyncJob?.cancel()
                repoCatalogSyncJob = scope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(3500L)
                    logger.info("Triggering debounced catalog sync on host for bridged repositories...")
                    try {
                        com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.rebuildRemotePluginCatalog()
                        com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.incrementSyncGeneration()
                    } catch (e: Throwable) {
                        logger.error("Debounced catalog sync failed on host", e)
                    }
                }
            }
        }
    }

    /**
     * Initializes and starts the sandboxed worker process.
     */
    suspend fun start() = mutex.withLock {
        ensureWorkerStartedLocked()
    }

    private suspend fun ensureWorkerStartedLocked() {
        if (currentProcess?.isAlive() == true) return
        launchWorkerInternal()
        runCatching {
            sendDirectAwaitLocked(
                WorkerRequest(
                    id = nextRequestId.getAndIncrement(),
                    action = WorkerRequest.ACTION_PING
                ),
                15_000L
            )
        }.onFailure {
            logger.warn("Worker ready ping timed out or failed: ${it.message}")
        }
        val rawSettings = com.lagradost.common.storage.DesktopDataStore.rawKeyCache.toMap()
        val allowedExactKeys = setOf(
            com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS,
            com.lagradost.cloudstream3.network.CloudflareKiller.PREF_TLS_BOUND_HOSTS,
            "trusted_plugins_set",
            "dns_provider",
            "dns_over_https",
            "pref_dns_mode",
            "user_agent"
        )
        val schemaStorageKeys = com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values
            .flatMap { it.values }.map { it.storageKey }.toSet()
        val schemaPrefixes = com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.keys

        val safeSettings = rawSettings.filter { (key, _) ->
            allowedExactKeys.contains(key) ||
            schemaStorageKeys.contains(key) ||
            schemaPrefixes.any { prefix -> key.startsWith(prefix) } ||
            key.startsWith("cf_silenced_") ||
            key.startsWith("pref_plugin_")
        }
        if (safeSettings.isNotEmpty()) {
            runCatching {
                sendDirectAwaitLocked(
                    WorkerRequest(
                        id = nextRequestId.getAndIncrement(),
                        action = WorkerRequest.ACTION_SYNC_SETTINGS,
                        settingsMap = safeSettings
                    ),
                    10_000L
                )
            }
        }
        loadedPluginPaths.retainAll { File(it).exists() }
        for (path in loadedPluginPaths) {
            runCatching {
                sendDirectAwaitLocked(WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_LOAD_PLUGIN, pluginPath = path), 20_000L)
            }
        }
    }

    private fun launchWorkerInternal() {
        logger.info("Initializing sandboxed extension worker in container '$containerName' (1 GB Cap)...")

        val sid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            displayName = "CloudStream Extension Sandbox",
            description = "Sandboxed low-privilege runtime for CloudStream plugins",
            capabilities = listOf(
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT
            )
        ) ?: throw IOException("Failed to obtain AppContainer SID for '$containerName'")

        if (grantedSids.add(sid)) {
            // Grant AppContainer permissions to plugin storage and dedicated sandbox directories
            val extensionsDir = PlatformPaths.extensionsDir
            if (!extensionsDir.exists()) extensionsDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(extensionsDir, sid, readOnly = true, allowExecute = true)

            val sandboxDir = PlatformPaths.sandboxDir
            if (!sandboxDir.exists()) sandboxDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxDir, sid, readOnly = false, allowExecute = false)

            // Temporary and cache scratch directories MUST NOT have execute rights
            val sandboxTmp = File(sandboxDir, "tmp")
            if (!sandboxTmp.exists()) sandboxTmp.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxTmp, sid, readOnly = false, allowExecute = false)

            val pluginCacheDir = PlatformPaths.pluginCacheDir
            if (!pluginCacheDir.exists()) pluginCacheDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(pluginCacheDir, sid, readOnly = false, allowExecute = false)

            // Grant read access to JRE and Gradle caches only. project root (user.dir) is intentionally
            // excluded — granting it would expose the entire workspace tree to the sandboxed worker.
            // Classpath entries that fall outside these roots are handled individually below.
            val topRoots = mutableSetOf<File>()

            val gradleCache = File(System.getProperty("user.home"), ".gradle/caches")
            if (gradleCache.exists()) topRoots.add(gradleCache)

            val projectRoot = File(System.getProperty("user.dir"))
            if (projectRoot.exists()) { /* user.dir excluded: see topRoots comment above */ }

            val javaHome = File(System.getProperty("java.home"))
            if (javaHome.exists()) topRoots.add(javaHome)

            for (root in topRoots) {
                try {
                    WindowsSandboxAcl.grantFolderAccess(root, sid, readOnly = true, allowExecute = true)
                } catch (t: Throwable) {
                    logger.warn("Failed granting ACL to root ${root.absolutePath}: ${t.message}")
                }
            }

            // Grant read access to any external classpath entry not already covered by the top roots
            val classpath = pruneWorkerClasspath(System.getProperty("java.class.path") ?: "")
            val classpathEntries = classpath.split(File.pathSeparator).filter { it.isNotBlank() }
            val extraRoots = mutableSetOf<String>()

            for (entry in classpathEntries) {
                val file = File(entry)
                val absPath = file.absolutePath
                val alreadyCovered = topRoots.any { root ->
                    absPath.startsWith(root.absolutePath, ignoreCase = true)
                }
                if (!alreadyCovered) {
                    val parent = (if (file.isDirectory) file else file.parentFile)?.absolutePath ?: continue
                    if (extraRoots.add(parent)) {
                        try {
                            WindowsSandboxAcl.grantFolderAccess(File(parent), sid, readOnly = true, allowExecute = true)
                        } catch (t: Throwable) {
                            logger.debug("Failed granting external classpath ACL to $parent: ${t.message}")
                        }
                    }
                }
            }
        }

        val javaHome = File(System.getProperty("java.home"))
        val candidateFiles = mutableListOf<File>()

        // 1. Primary: Bundled runtime binary locations
        candidateFiles.add(File(javaHome, "bin/CloudStream-Worker.exe"))
        candidateFiles.add(File(javaHome, "CloudStream-Worker.exe"))
        candidateFiles.add(File(javaHome, "bin/javaw.exe"))
        candidateFiles.add(File(javaHome, "javaw.exe"))
        candidateFiles.add(File(javaHome, "bin/java.exe"))
        candidateFiles.add(File(javaHome, "java.exe"))

        // 2. Secondary: Sibling paths from application root
        javaHome.parentFile?.let { appRoot ->
            candidateFiles.add(File(appRoot, "runtime/bin/CloudStream-Worker.exe"))
            candidateFiles.add(File(appRoot, "runtime/bin/javaw.exe"))
            candidateFiles.add(File(appRoot, "runtime/bin/java.exe"))
            candidateFiles.add(File(appRoot, "bin/CloudStream-Worker.exe"))
        }

        // 3. Tertiary: App code source parent
        try {
            val codeSource = SandboxedPluginHost::class.java.protectionDomain?.codeSource?.location
            if (codeSource != null) {
                val loc = File(codeSource.toURI())
                val appBase = if (loc.isDirectory) loc else loc.parentFile
                appBase?.parentFile?.let { installRoot ->
                    candidateFiles.add(File(installRoot, "runtime/bin/CloudStream-Worker.exe"))
                    candidateFiles.add(File(installRoot, "runtime/bin/javaw.exe"))
                    candidateFiles.add(File(installRoot, "runtime/bin/java.exe"))
                }
            }
        } catch (e: Throwable) { logger.debug("CodeSource worker candidate lookup note: ${e.message}") }

        // 4. Fallback: System environment (JAVA_HOME)
        System.getenv("JAVA_HOME")?.let { envHome ->
            candidateFiles.add(File(envHome, "bin/CloudStream-Worker.exe"))
            candidateFiles.add(File(envHome, "bin/javaw.exe"))
            candidateFiles.add(File(envHome, "bin/java.exe"))
        }

        val javaExeFile = candidateFiles.firstOrNull { it.isFile && it.exists() }
        if (javaExeFile == null) {
            isHostAvailable = false
            val checkedPaths = candidateFiles.joinToString("; ") { it.absolutePath }
            val runtimeContents = try {
                File(javaHome, "bin").listFiles()?.joinToString { it.name } ?: "none"
            } catch (e: Throwable) { logger.debug("Runtime bin contents lookup note: ${e.message}"); "unreadable" }
            logger.error("Sandbox worker executable not found. Checked: [$checkedPaths]. runtime/bin contents: [$runtimeContents]")
            throw java.io.FileNotFoundException(
                "Sandbox worker executable not found at ${File(javaHome, "bin/CloudStream-Worker.exe").absolutePath}. " +
                "Checked ${candidateFiles.size} candidate locations."
            )
        }

        // Ensure AppContainer has read and execute permission to the executable's folder and its root
        val exeDir = javaExeFile.parentFile
        if (exeDir != null && exeDir.exists()) {
            try {
                WindowsSandboxAcl.grantFolderAccess(exeDir, sid, readOnly = true, allowExecute = true)
                exeDir.parentFile?.let { exeRoot ->
                    WindowsSandboxAcl.grantFolderAccess(exeRoot, sid, readOnly = true, allowExecute = true)
                }
            } catch (t: Throwable) {
                logger.debug("Granting ACL for worker binary dir ${exeDir.absolutePath}: ${t.message}")
            }
        }
        val javaExe = javaExeFile.absolutePath

        val job = WindowsJobObject(
            jobName = "CS3_PluginWorker_Job_${System.currentTimeMillis()}",
            memoryLimitBytes = memoryLimitBytes
        )
        this.currentJobObject = job

        val sandboxTmp = File(PlatformPaths.sandboxDir, "tmp").apply {
            try {
                if (exists()) {
                    listFiles()?.forEach { file ->
                        try { file.deleteRecursively() } catch (t: Throwable) {
                            logger.debug("Failed cleaning sandbox tmp file ${file.name}: ${t.message}")
                        }
                    }
                } else {
                    mkdirs()
                }
            } catch (t: Throwable) {
                logger.debug("Failed preparing sandbox tmp dir: ${t.message}")
            }
        }
        var classpath = System.getProperty("java.class.path") ?: ""
        if (classpath.isBlank() || !classpath.contains(File.pathSeparator)) {
            try {
                val codeSource = SandboxedPluginHost::class.java.protectionDomain?.codeSource?.location
                if (codeSource != null) {
                    val locationFile = File(codeSource.toURI())
                    val appDir = if (locationFile.isDirectory) locationFile else locationFile.parentFile
                    if (appDir != null && appDir.isDirectory) {
                        val bundledJars = appDir.listFiles { f -> f.extension.equals("jar", ignoreCase = true) }
                        if (!bundledJars.isNullOrEmpty()) {
                            val resolvedCp = bundledJars.joinToString(File.pathSeparator) { it.absolutePath }
                            if (resolvedCp.isNotBlank()) {
                                classpath = resolvedCp
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.warn("Could not dynamically resolve packaged app classpath: ${t.message}")
            }
        }
        val workerClasspath = pruneWorkerClasspath(classpath)
        val commandLine = "\"$javaExe\" " +
            "-Djava.awt.headless=true " +
            "-Dcloudstream.sandbox.worker=true " +
            "-Dcloudstream.data.dir=\"${PlatformPaths.appDataDir.absolutePath}\" " +
            "-Djava.io.tmpdir=\"${sandboxTmp.absolutePath}\" " +
            "-Djna.tmpdir=\"${sandboxTmp.absolutePath}\" " +
            "-Djava.util.prefs.PreferencesFactory=com.lagradost.cloudstream3.desktop.sandbox.worker.NoOpPreferencesFactory " +
            "-XX:+DisableAttachMechanism -Djdk.attach.allowAttachSelf=false " +
            "-XX:TieredStopAtLevel=1 -Xss256k -Xms16m -Xmx256m -XX:MaxMetaspaceSize=80m -XX:ReservedCodeCacheSize=20m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:+ExitOnOutOfMemoryError " +
            "-cp \"$workerClasspath\" " +
            "com.lagradost.cloudstream3.desktop.sandbox.worker.PluginWorkerMain"

        val process = WindowsAppContainerLauncher.launch(
            commandLine = commandLine,
            containerName = containerName,
            workingDir = PlatformPaths.sandboxDir,
            capabilities = listOf(
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT
            ),
            jobObject = job
        )

        this.currentProcess = process
        this.processWriter = BufferedWriter(OutputStreamWriter(process.stdin, StandardCharsets.UTF_8), 65536)

        // Start background stdout reader
        scope.launch {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.stdout, StandardCharsets.UTF_8), 65536)
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                        logger.debug("Ignoring non-JSON line from worker stdout: {}", trimmed)
                        continue
                    }

                    if (trimmed.contains("\"type\":\"CALLBACK_REQUEST\"") || trimmed.contains("\"callbackId\"")) {
                        try {
                            val cbReq = WorkerProtocolSerializer.deserializeCallbackRequest(trimmed)
                            scope.launch {
                                handleWorkerCallback(cbReq)
                            }
                        } catch (e: Throwable) {
                            logger.error("Failed to parse callback request from worker: $trimmed", e)
                        }
                        continue
                    }

                    try {
                        val resp = WorkerProtocolSerializer.deserializeResponse(trimmed)
                        val deferred = pendingRequests.remove(resp.id)
                        deferred?.complete(resp)
                    } catch (e: Throwable) {
                        logger.error("Failed to parse response JSON from worker: $line", e)
                    }
                }
            } catch (e: Throwable) {
                logger.warn("Worker stdout reader stream closed: ${e.message}")
            } finally {
                handleWorkerTermination()
            }
        }

        // Start background stderr logger
        scope.launch {
            val errReader = process.stderr.bufferedReader(StandardCharsets.UTF_8)
            try {
                while (isActive) {
                    val errLine = errReader.readLine() ?: break
                    val trimmed = errLine.trim()
                    if (trimmed.isEmpty()) continue

                    if (trimmed.startsWith("[CS_NET] START|")) {
                        val payload = trimmed.removePrefix("[CS_NET] START|")
                        val parts = payload.split("|", limit = 5)
                        if (parts.size >= 5) {
                            val workerId = parts[0].toLongOrNull() ?: 0L
                            val method = parts[1]
                            val host = parts[2]
                            val path = parts[3]
                            val url = parts[4]
                            val hostId = com.lagradost.common.net.NetworkTrafficBuffer.recordStart(
                                method = method,
                                url = url,
                                host = host,
                                path = path,
                                requestHeaders = emptyMap(),
                                requestBody = null,
                            )
                            activeWorkerNetworkRequests[workerId] = hostId
                            com.lagradost.common.logging.AppLogger.d("Sandbox:HTTP", "-> $method $url")
                        }
                    } else if (trimmed.startsWith("[CS_NET] END|")) {
                        val payload = trimmed.removePrefix("[CS_NET] END|")
                        val parts = payload.split("|", limit = 4)
                        if (parts.size >= 3) {
                            val workerId = parts[0].toLongOrNull() ?: 0L
                            val statusCode = parts[1].toIntOrNull() ?: -1
                            val durationMs = parts[2].toLongOrNull() ?: 0L
                            val error = if (parts.size >= 4 && parts[3].isNotBlank()) parts[3] else null
                            val hostId = activeWorkerNetworkRequests.remove(workerId)
                            if (hostId != null) {
                                com.lagradost.common.net.NetworkTrafficBuffer.recordComplete(
                                    id = hostId,
                                    statusCode = statusCode,
                                    statusMessage = if (statusCode > 0) "HTTP $statusCode" else (error ?: "Failed"),
                                    durationMs = durationMs,
                                    responseHeaders = emptyMap(),
                                    responseBody = null,
                                    responseSize = 0L,
                                    contentType = null,
                                    error = error,
                                )
                            }
                            val statusLabel = if (statusCode > 0) "$statusCode" else (error ?: "FAILED")
                            com.lagradost.common.logging.AppLogger.d("Sandbox:HTTP", "<- $statusLabel (${durationMs}ms)")
                        }
                    } else {
                        when {
                            trimmed.startsWith("[ERROR]") || trimmed.contains(" ERROR ") -> com.lagradost.common.logging.AppLogger.e("Sandbox:Worker", trimmed)
                            trimmed.startsWith("[WARN]") || trimmed.contains(" WARN ") -> com.lagradost.common.logging.AppLogger.w("Sandbox:Worker", trimmed)
                            trimmed.startsWith("[INFO]") || trimmed.contains(" INFO ") -> com.lagradost.common.logging.AppLogger.i("Sandbox:Worker", trimmed)
                            else -> com.lagradost.common.logging.AppLogger.d("Sandbox:Worker", trimmed)
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.debug("Worker stderr reader stopped: ${t.message}")
            }
        }

        logger.info("Sandboxed worker process started successfully (PID: ${process.pid})")
    }

    private fun handleWorkerTermination() {
        val pending = pendingRequests.values.toList()
        pendingRequests.clear()
        activeWorkerNetworkRequests.clear()

        val exit = try { currentProcess?.exitValue() } catch (_: Throwable) { null }
        val exitHex = exit?.let { "0x" + Integer.toHexString(it).uppercase() } ?: "unknown"

        for (p in pending) {
            p.completeExceptionally(IOException("Sandboxed worker process terminated unexpectedly (exit: $exitHex)"))
        }

        currentProcess?.close()
        currentProcess = null
        processWriter = null

        scope.launch(Dispatchers.IO) {
            try {
                File(PlatformPaths.sandboxDir, "tmp").listFiles()?.forEach { file ->
                    try { file.deleteRecursively() } catch (t: Throwable) {
                        logger.debug("Failed cleaning sandbox tmp during termination: ${t.message}")
                    }
                }
            } catch (t: Throwable) {
                logger.debug("Failed accessing sandbox tmp during termination: ${t.message}")
            }
        }
        currentJobObject?.close()
        currentJobObject = null

        logger.warn("Sandboxed worker process terminated. Exit code: $exit ($exitHex). Ready for auto-recovery on next query.")
    }

    private suspend fun sendRequest(request: WorkerRequest, timeoutMs: Long = 30_000L): WorkerResponse {
        val deferred = CompletableDeferred<WorkerResponse>()
        pendingRequests[request.id] = deferred

        try {
            mutex.withLock {
                if (currentProcess?.isAlive() != true) {
                    ensureWorkerStartedLocked()
                }
                sendDirect(request)
            }

            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(request.id)
        }
    }

    private suspend fun sendDirectAwaitLocked(request: WorkerRequest, timeoutMs: Long): WorkerResponse {
        val deferred = CompletableDeferred<WorkerResponse>()
        pendingRequests[request.id] = deferred
        sendDirect(request)
        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(request.id)
        }
    }

    private fun sendDirect(request: WorkerRequest) {
        val writer = processWriter ?: throw IOException("Process stdin writer is not available")
        val json = WorkerProtocolSerializer.serializeRequest(request)
        synchronized(writer) {
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }

    private suspend fun handleWorkerCallback(cbReq: WorkerCallbackRequest) {
        if (cbReq.action == WorkerCallbackRequest.ACTION_EMIT_LINK) {
            val reqId = cbReq.requestId ?: return
            val linkJson = cbReq.linkJson ?: return
            val consumer = activeLinkConsumers[reqId] ?: return
            try {
                val link = WorkerProtocolSerializer.mapper.readValue(linkJson, com.lagradost.cloudstream3.utils.ExtractorLink::class.java)
                if (link != null) {
                    consumer.onLink?.invoke(link)
                }
            } catch (e: Throwable) {
                logger.warn("Failed to deserialize streamed ExtractorLink: ${e.message}")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_ADD_REPOSITORY) {
            val url = cbReq.repoUrl ?: return
            if (isBlockedOrPrivateUrl(url)) {
                logger.warn("Rejected worker repository callback targeting a private or invalid URL: $url")
                return
            }
            val name = cbReq.repoName?.takeIf { it.isNotBlank() } ?: url
            val icon = cbReq.repoIcon
            val normalized = com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(iconUrl = icon, name = name, url = url)
            logger.info("Host handling ACTION_ADD_REPOSITORY from worker for: $name ($url)")
            scope.launch(Dispatchers.IO) {
                try {
                    val isNew = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.saveRepository(normalized)
                    if (isNew) {
                        logger.info("Host saved newly bridged repository: $name. Scheduling debounced sync...")
                        com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.incrementSyncGeneration()
                        scheduleBridgedCatalogSync()
                    }
                } catch (t: Throwable) {
                    logger.warn("Host failed to save bridged repository $name: ${t.message}")
                }
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_EMIT_SUBTITLE) {
            val reqId = cbReq.requestId ?: return
            val subJson = cbReq.subtitleJson ?: return
            val consumer = activeLinkConsumers[reqId] ?: return
            try {
                val sub = WorkerProtocolSerializer.mapper.readValue(subJson, com.lagradost.cloudstream3.SubtitleFile::class.java)
                if (sub != null) {
                    consumer.onSubtitle?.invoke(sub)
                }
            } catch (e: Throwable) {
                logger.warn("Failed to deserialize streamed SubtitleFile: ${e.message}")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_SAVE_SETTING) {
            val key = cbReq.key ?: return
            val valueJson = cbReq.valueJson ?: return
            if (PROTECTED_HOST_KEYS.any { key.equals(it, ignoreCase = true) }) {
                logger.error("Rejected unauthorized worker write to protected host key: $key")
                return
            }

            // Derive host prefixes of all currently loaded plugins
            val validPrefixes = loadedPluginPaths.mapNotNull { path ->
                val f = File(path)
                val m = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.readPluginManifest(f)
                val id = m?.get("internalName")?.toString()?.takeIf { it.isNotBlank() }
                    ?: m?.get("name")?.toString()?.takeIf { it.isNotBlank() }
                    ?: f.nameWithoutExtension
                val clean = id.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
                if (clean.length >= 3) "${clean}_" else null
            }.toSet()

            val isAllowed = (key.startsWith("cf_silenced_") && key.length > "cf_silenced_".length) ||
                validPrefixes.any { p -> key.startsWith(p) && key.length > p.length } ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values.flatMap { it.values }.any { it.storageKey == key }

            if (isAllowed) {
                com.lagradost.common.storage.DesktopDataStore.setRawJson(key, valueJson)
            } else {
                logger.warn("Rejected unauthorized worker setting write to key: $key")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_REMOVE_SETTING) {
            val key = cbReq.key ?: return
            if (PROTECTED_HOST_KEYS.any { key.equals(it, ignoreCase = true) }) {
                logger.error("Rejected unauthorized worker write to protected host key: $key")
                return
            }

            val validPrefixes = loadedPluginPaths.mapNotNull { path ->
                val f = File(path)
                val m = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.readPluginManifest(f)
                val id = m?.get("internalName")?.toString()?.takeIf { it.isNotBlank() }
                    ?: m?.get("name")?.toString()?.takeIf { it.isNotBlank() }
                    ?: f.nameWithoutExtension
                val clean = id.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
                if (clean.length >= 3) "${clean}_" else null
            }.toSet()

            val isAllowed = (key.startsWith("cf_silenced_") && key.length > "cf_silenced_".length) ||
                validPrefixes.any { p -> key.startsWith(p) && key.length > p.length } ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values.flatMap { it.values }.any { it.storageKey == key }

            if (isAllowed) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(key)
            } else {
                logger.warn("Rejected unauthorized worker setting delete for key: $key")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_PROXY_FETCH) {
            val url = cbReq.url ?: return
            val headers = cbReq.headers ?: emptyMap()
            val method = cbReq.method ?: "GET"
            val body = cbReq.body

            if (isBlockedOrPrivateUrl(url)) {
                logger.warn("Rejected unauthorized SSRF/private proxy fetch callback to: $url")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        statusCode = 403,
                        error = "SSRF blocked: Requests to loopback, private LAN, or invalid network destinations are permanently forbidden."
                    )
                )
                return
            }

            logger.info("Handling browser proxy fetch callback from sandboxed worker for $url")
            val (statusCode, contentType, respBody, err) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val initialHost = try { java.net.URI(url).host?.lowercase()?.trim() } catch (_: Throwable) { null }
                    val pinnedAddresses = if (initialHost != null) {
                        try {
                            java.net.InetAddress.getAllByName(initialHost).filter { a ->
                                !a.isLoopbackAddress && !a.isSiteLocalAddress && !a.isLinkLocalAddress && !a.isAnyLocalAddress && !isCarrierGradeNat(a) && !isIpv6UniqueLocal(addr = a)
                            }
                        } catch (_: Throwable) { emptyList() }
                    } else emptyList()

                    if (initialHost != null && pinnedAddresses.isEmpty()) {
                        return@withContext listOf(403, "text/plain", "", "SSRF blocked: resolved addresses are all private or unavailable")
                    }

                    // Strict pinned DNS resolver to prevent DNS rebinding TOCTOU
                    val pinnedDns = okhttp3.Dns { hostname ->
                        if (initialHost != null && hostname.equals(initialHost, ignoreCase = true)) {
                            pinnedAddresses
                        } else {
                            val resolved = java.net.InetAddress.getAllByName(hostname).filter { a ->
                                !a.isLoopbackAddress && !a.isSiteLocalAddress && !a.isLinkLocalAddress && !a.isAnyLocalAddress && !isCarrierGradeNat(a) && !isIpv6UniqueLocal(addr = a)
                            }
                            if (resolved.isEmpty()) {
                                throw java.net.UnknownHostException("SSRF blocked: Host $hostname resolved to private or invalid address")
                            }
                            resolved
                        }
                    }

                    val fetchClient = okhttp3.OkHttpClient.Builder()
                        .dns(pinnedDns)
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    var currentUrl = url
                    var redirectCount = 0
                    var finalCode = 500
                    var finalContentType = "text/plain"
                    var finalBody = ""

                    while (redirectCount <= 5) {
                        if (isBlockedOrPrivateUrl(currentUrl)) {
                            return@withContext listOf(403, "text/plain", "", "SSRF blocked on redirect hop to: $currentUrl")
                        }

                        val reqBuilder = okhttp3.Request.Builder().url(currentUrl)
                        val reqBody = if (redirectCount == 0) body?.toRequestBody(null) else null
                        val reqMethod = if (redirectCount == 0) method else "GET"
                        if (reqMethod.equals("POST", ignoreCase = true) || reqMethod.equals("PUT", ignoreCase = true)) {
                            reqBuilder.method(reqMethod, reqBody ?: ByteArray(0).toRequestBody(null))
                        } else {
                            reqBuilder.method(reqMethod, null)
                        }
                        headers.forEach { (k, v) ->
                            if (!k.equals("content-length", ignoreCase = true) && !k.equals("host", ignoreCase = true)) {
                                reqBuilder.header(k, v)
                            }
                        }
                        val okReq = reqBuilder.build()
                        val response = fetchClient.newCall(okReq).execute()

                        if (response.isRedirect) {
                            val loc = response.header("Location")
                            response.close()
                            if (loc.isNullOrBlank()) {
                                return@withContext listOf(502, "text/plain", "", "Redirect without Location header")
                            }
                            val resolvedLocation = try {
                                java.net.URI(currentUrl).resolve(loc).toASCIIString()
                            } catch (_: Throwable) {
                                return@withContext listOf(400, "text/plain", "", "Invalid redirect location: $loc")
                            }
                            currentUrl = resolvedLocation
                            redirectCount++
                        } else {
                            finalCode = response.code
                            finalContentType = response.header("content-type") ?: "text/html"
                            finalBody = response.body.string()
                            response.close()
                            break
                        }
                    }

                    if (redirectCount > 5) {
                        listOf(508, "text/plain", "", "Too many redirects")
                    } else {
                        listOf(finalCode, finalContentType, finalBody, null)
                    }
                } catch (t: Throwable) {
                    listOf(500, "text/plain", "", t.message)
                }
            }

            val cbResp = WorkerCallbackResponse(
                callbackId = cbReq.callbackId,
                success = (statusCode as Int) in 200..299,
                statusCode = statusCode,
                contentType = contentType as String?,
                body = respBody as String?,
                error = err as String?
            )
            sendCallbackResponse(cbResp)
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_SOLVE_CLOUDFLARE) {
            val url = cbReq.url ?: return
            val host = cbReq.host ?: return
            val headers = cbReq.headers ?: emptyMap()

            if (isBlockedOrPrivateUrl(url) || isBlockedOrPrivateUrl("https://$host")) {
                logger.warn("Rejecting SSRF/private Cloudflare solve callback for host: $host (url: $url)")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "SSRF blocked: Cloudflare solver cannot target loopback, private LAN, or invalid network destinations."
                    )
                )
                return
            }

            val now = System.currentTimeMillis()
            val lastAttempt = lastSolveAttempts[host] ?: 0L
            if (now - lastAttempt < 3_000L) {
                logger.warn("Throttling excessive Cloudflare solve callback requests for host: $host")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Throttled: solve request too frequent"
                    )
                )
                return
            }
            lastSolveAttempts[host] = now

            if (com.lagradost.cloudstream3.desktop.init.PluginWarmupState.progress.value.isWarmingUp) {
                logger.warn("Suppressing interactive Cloudflare verification modal for $host during background warmup.")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Suppressed during background warmup"
                    )
                )
                return
            }

            val isBypassAllowed = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(
                com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS
            ) ?: false
            if (!isBypassAllowed) {
                logger.info("Cloudflare solver skipped for host $host: Disabled in Settings.")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Cloudflare solver is disabled by user in Settings."
                    )
                )
                return
            }

            logger.info("Handling Cloudflare solve callback from sandboxed worker for host: $host")
            val solved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                com.lagradost.cloudstream3.network.CloudflareKiller.solveWithEmbeddedWebView(url, host, headers)
            }

            val clearanceCookies = com.lagradost.cloudstream3.network.CloudflareKiller.getClearance(host)?.cookies.orEmpty()
            val vaultCookies = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getUnexpiredForHost(host)
            val cookies = (clearanceCookies + vaultCookies).distinctBy { "${it.name}|${it.domain}" }

            val serializable = cookies.map { c ->
                SerializableCookie(
                    name = c.name,
                    value = c.value,
                    domain = c.domain,
                    path = c.path,
                    expiresAt = c.expiresAt,
                    secure = c.secure,
                    httpOnly = c.httpOnly
                )
            }

            val userAgent = com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.userAgent
            val cbResp = WorkerCallbackResponse(
                callbackId = cbReq.callbackId,
                success = solved,
                cookies = serializable,
                userAgent = userAgent
            )

            sendCallbackResponse(cbResp)
        }
    }

    private fun sendCallbackResponse(response: WorkerCallbackResponse) {
        val writer = processWriter ?: return
        val json = WorkerProtocolSerializer.serializeCallbackResponse(response)
        synchronized(writer) {
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }

    // High-Level Suspending API

    suspend fun ping(timeoutMs: Long = 15_000L): Boolean {
        val req = WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_PING)
        val resp = sendRequest(req, timeoutMs = timeoutMs)
        return resp.success
    }

    suspend fun syncSettings(settings: Map<String, String>): Boolean {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SYNC_SETTINGS,
            settingsMap = settings
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        return resp.success
    }

    /**
     * Loads a plugin JAR in the sandboxed worker. On success, returns the full list of
     * provider metadata registered by all currently loaded plugins (no second round-trip needed).
     * Returns null if the load failed.
     */
    suspend fun loadPlugin(jarFile: File, forceRetry: Boolean = false): List<SandboxedProviderMetadata>? = pluginLifecycleMutex.withLock {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_PLUGIN,
            forceRetry = forceRetry,
            pluginPath = jarFile.absolutePath
        )
        val resp = try {
            sendRequest(req, timeoutMs = 45_000L)
        } catch (t: Throwable) {
            logger.warn("Sandboxed worker failed during plugin load for ${jarFile.name}: ${t.message}")
            throw PluginLoadFailureException(
                pluginFile = jarFile,
                errorMessage = t.message ?: "IPC request timeout or channel closed",
                isPermanent = false,
                cause = t
            )
        }
        if (!resp.success) {
            val err = resp.error ?: "Extension initialization failed inside sandbox"
            logger.warn("Sandboxed worker rejected plugin ${jarFile.name}: $err")
            val deterministicSignatures = listOf(
                "ClassNotFoundException",
                "NoClassDefFoundError",
                "VerifyError",
                "ClassFormatError",
                "UnsupportedClassVersionError",
                "ZipException",
                "SecurityException",
                "NoSuchMethodError",
                "NoSuchFieldError",
                "IncompatibleClassChangeError",
                "un-transpilable",
                "no valid JAR was produced"
            )
            val isPermanentFailure = deterministicSignatures.any { err.contains(it, ignoreCase = true) }
            throw PluginLoadFailureException(
                pluginFile = jarFile,
                errorMessage = err,
                isPermanent = isPermanentFailure
            )
        }
        loadedPluginPaths.add(jarFile.absolutePath)
        val payload = resp.payloadJson ?: return@withLock emptyList()
        val loadResult = try {
            WorkerProtocolSerializer.mapper.readValue(payload, com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult::class.java)
        } catch (t: Throwable) {
            logger.debug("Parsing SandboxedPluginLoadResult payload fallback: ${t.message}")
            val list = WorkerProtocolSerializer.mapper.readValue<List<SandboxedProviderMetadata>>(
                payload,
                WorkerProtocolSerializer.mapper.typeFactory.constructCollectionType(
                    List::class.java, SandboxedProviderMetadata::class.java
                )
            )
            com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult(providers = list)
        }

        // Derive settings namespace strictly on the host from the plugin's verified file/manifest identity
        val manifest = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.readPluginManifest(jarFile)
        val rawIdentity = manifest?.get("internalName")?.toString()?.takeIf { it.isNotBlank() }
            ?: manifest?.get("name")?.toString()?.takeIf { it.isNotBlank() }
            ?: jarFile.nameWithoutExtension
        val sanitizedIdentity = rawIdentity.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        if (sanitizedIdentity.length < 3) {
            logger.error("Rejecting plugin '${jarFile.name}': Derived identity '$sanitizedIdentity' is too short (< 3 chars) for a secure settings namespace.")
            return@withLock null
        }
        val hostPrefName = "${sanitizedIdentity}_"

        loadResult.schemas.forEach { s ->
            val matchesThisPlugin = s.pluginPrefName.equals(hostPrefName, ignoreCase = true) ||
                s.pluginPrefName.trimEnd('_').equals(sanitizedIdentity, ignoreCase = true) ||
                s.pluginPrefName.trimEnd('_').equals(rawIdentity.trim(), ignoreCase = true)
            if (!matchesThisPlugin) {
                logger.debug("Discarding foreign schema key '${s.key}' (prefName: '${s.pluginPrefName}') during load of '$hostPrefName'")
                return@forEach
            }
            if (s.key.isBlank() || s.key.length < 2) {
                logger.warn("Ignoring invalid or blank schema key from plugin '$hostPrefName'")
                return@forEach
            }
            val sk = "${hostPrefName}${s.key}"
            if (PROTECTED_HOST_KEYS.any { sk.equals(it, ignoreCase = true) || s.key.equals(it, ignoreCase = true) }) {
                logger.warn("Ignoring unauthorized worker schema registration targeting protected key '${s.key}'")
                return@forEach
            }
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                pluginPrefName = hostPrefName,
                key = s.key,
                type = s.type,
                defaultValue = s.defaultValue,
                isGlobal = false,
                options = s.options,
                storageKey = sk,
            )
        }

        loadResult.providers
    }

    suspend fun search(providerName: String, query: String, page: Int = 1): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SEARCH,
            providerName = providerName,
            query = query,
            page = page,
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "Search failed")
        return resp.payloadJson
    }

    suspend fun load(providerName: String, url: String): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD,
            providerName = providerName,
            url = url
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "Load failed")
        return resp.payloadJson
    }

    suspend fun loadLinks(
        providerName: String,
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: ((com.lagradost.cloudstream3.SubtitleFile) -> Unit)? = null,
        callback: ((com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit)? = null
    ): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_LINKS,
            providerName = providerName,
            data = data,
            isCasting = isCasting
        )
        if (callback != null || subtitleCallback != null) {
            activeLinkConsumers[req.id] = ActiveLinkConsumer(
                onLink = callback,
                onSubtitle = subtitleCallback
            )
        }
        return try {
            val resp = sendRequest(req, timeoutMs = 90_000L)
            if (!resp.success) throw IOException(resp.error ?: "LoadLinks failed")
            resp.payloadJson
        } finally {
            activeLinkConsumers.remove(req.id)
        }
    }

    suspend fun getMainPage(
        providerName: String,
        page: Int,
        requestName: String,
        requestData: String,
        horizontalImages: Boolean = false
    ): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_MAIN_PAGE,
            providerName = providerName,
            page = page,
            requestName = requestName,
            requestData = requestData,
            horizontalImages = horizontalImages
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "MainPage failed")
        return resp.payloadJson
    }

    suspend fun getLoadedProviders(): List<SandboxedProviderMetadata> {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_GET_LOADED_PROVIDERS
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        if (!resp.success || resp.payloadJson == null) return emptyList()
        return try {
            val loadResult = WorkerProtocolSerializer.mapper.readValue(
                resp.payloadJson,
                com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult::class.java
            )
            loadResult.providers
        } catch (t: Throwable) {
            logger.debug("Parsing getLoadedProviders payload fallback: ${t.message}")
            WorkerProtocolSerializer.mapper.readValue(
                resp.payloadJson,
                WorkerProtocolSerializer.mapper.typeFactory.constructCollectionType(List::class.java, SandboxedProviderMetadata::class.java)
            )
        }
    }

    suspend fun unloadPlugin(jarFile: File): Boolean = pluginLifecycleMutex.withLock {
        val canonical = try { jarFile.canonicalPath } catch (t: Throwable) {
            logger.debug("Failed canonicalPath resolution for ${jarFile.name}: ${t.message}")
            jarFile.absolutePath
        }
        loadedPluginPaths.remove(jarFile.absolutePath)
        loadedPluginPaths.remove(canonical)

        if (currentProcess?.isAlive() != true) return@withLock true

        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_UNLOAD_PLUGIN,
            pluginPath = jarFile.absolutePath
        )
        try {
            val resp = sendRequest(req, timeoutMs = 5_000L)
            if (!resp.success) {
                logger.warn("Worker unload rejected for ${jarFile.name}: ${resp.error}; recycling worker to release locks.")
                recycleWorker()
            }
        } catch (t: Throwable) {
            logger.warn("Worker unload timed out or failed for ${jarFile.name} (${t.message}); recycling worker to release locks.")
            recycleWorker()
        }
        return@withLock true
    }

    suspend fun runSecurityAudit(): SandboxAuditReport {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SECURITY_AUDIT
        )
        val resp = sendRequest(req, timeoutMs = 15_000L)
        if (!resp.success || resp.payloadJson == null) {
            throw IOException("Security audit failed: ${resp.error ?: "Unknown error"}")
        }
        return WorkerProtocolSerializer.mapper.readValue(
            resp.payloadJson,
            SandboxAuditReport::class.java
        )
    }

    fun recycleWorker() {
        try {
            currentProcess?.destroy()
        } catch (t: Throwable) {
            logger.debug("Worker process destroy: ${t.message}")
        }
        currentProcess = null
        currentJobObject?.close()
        currentJobObject = null
    }

    override fun close() {
        recycleWorker()
    }
}
