package com.lagradost.cloudstream3.desktop.repo

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object DesktopRepositoryManager {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private val redirectClient = OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val reposFile by lazy { File(getExtensionsDir(), "repos.json") }
    private val repoCacheFile by lazy { File(getExtensionsDir(), "repo_cache.json") }
    private val pluginsCacheFile by lazy { File(getExtensionsDir(), "plugins_cache.json") }
    private val repoCache = mutableMapOf<String, Repository>()
    private val pluginsCache = mutableMapOf<String, List<SitePlugin>>()

    private val _savedRepositories = MutableStateFlow<List<RepositoryData>>(emptyList())
    val savedRepositories: StateFlow<List<RepositoryData>> = _savedRepositories.asStateFlow()

    val remotePluginIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val syncGeneration = MutableStateFlow(0)
    val failedIconUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    data class SyncReport(
        val reposRefreshed: Int,
        val catalogPlugins: Int,
        val pluginsUpdated: Int,
        val iconsCached: Int,
        val newPluginsLoaded: Int,
    ) {
        val summary: String
            get() = "Sync done: $reposRefreshed repos, $catalogPlugins plugins listed, $pluginsUpdated updated, $iconsCached icons, $newPluginsLoaded newly loaded."
    }

    init {
        refreshSavedRepositoriesFromDisk()
        loadCachesFromDisk()
    }

    private fun loadCachesFromDisk() {
        try {
            if (repoCacheFile.exists()) {
                val data: Map<String, Repository> = mapper.readValue(
                    repoCacheFile,
                    object : TypeReference<Map<String, Repository>>() {},
                )
                repoCache.putAll(data)
            }
            if (pluginsCacheFile.exists()) {
                val data: Map<String, List<SitePlugin>> = mapper.readValue(
                    pluginsCacheFile,
                    object : TypeReference<Map<String, List<SitePlugin>>>() {},
                )
                pluginsCache.putAll(data)
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to load repository caches from disk", e)
        }
    }

    private fun saveCachesToDisk() {
        try {
            repoCacheFile.parentFile?.mkdirs()
            mapper.writeValue(repoCacheFile, repoCache)
            mapper.writeValue(pluginsCacheFile, pluginsCache)
        } catch (e: Exception) {
            AppLogger.e("Failed to save repository caches to disk", e)
        }
    }

    fun getSavedRepositories(): List<RepositoryData> = _savedRepositories.value

    fun getPluginsJsonUrl(repoUrl: String): String {
        val cached = repoCache[repoUrl]?.pluginLists?.firstOrNull()
        if (!cached.isNullOrBlank()) return cached
        if (repoUrl.endsWith("repo.json", ignoreCase = true)) {
            return repoUrl.replace("repo.json", "builds/plugins.json", ignoreCase = true)
        }
        return repoUrl
    }

    private fun refreshSavedRepositoriesFromDisk() {
        _savedRepositories.value = readRepositoriesFromDisk()
    }

    private fun readRepositoriesFromDisk(): List<RepositoryData> {
        if (!reposFile.exists()) return emptyList()
        return try {
            val root = mapper.readTree(reposFile.readText())
            if (!root.isArray) return emptyList()
            if (root.size() > 0 && root[0].isTextual) {
                val urls = mapper.readValue(root.toString(), object : TypeReference<List<String>>() {})
                val migrated = urls.map { url ->
                    RepositoryData(name = url, url = url)
                }
                writeRepositoriesToDisk(migrated)
                migrated
            } else {
                mapper.readValue(root.toString(), object : TypeReference<List<RepositoryData>>() {})
                    .map { normalizeRepositoryData(it) }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to read repos.json: ${e.message}")
            emptyList()
        }
    }

    @Synchronized
    private fun writeRepositoriesToDisk(repos: List<RepositoryData>) {
        reposFile.parentFile?.mkdirs()
        mapper.writeValue(reposFile, repos)
        _savedRepositories.value = repos
    }

    private fun normalizeRepositoryData(data: RepositoryData): RepositoryData {
        val icon = data.iconUrl?.trim()?.takeIf { it.isNotEmpty() }
        val name = data.name.trim().ifEmpty { data.url }
        return data.copy(iconUrl = icon, name = name)
    }

    @Synchronized
    fun saveRepository(repository: RepositoryData) {
        val incoming = normalizeRepositoryData(repository)
        val current = readRepositoriesFromDisk().toMutableList()
        val index = current.indexOfFirst { it.url == incoming.url }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(
                name = if (incoming.name.isNotBlank() && incoming.name != incoming.url) incoming.name else existing.name,
                iconUrl = incoming.iconUrl ?: existing.iconUrl,
            )
        } else {
            current.add(incoming)
        }
        writeRepositoriesToDisk(current.distinctBy { it.url })
    }

    @Synchronized
    fun removeRepository(url: String) {
        val current = readRepositoriesFromDisk().filter { it.url != url }
        writeRepositoriesToDisk(current)
        val repo = repoCache.remove(url)
        if (repo != null) {
            for (listUrl in repo.pluginLists) {
                pluginsCache.remove(listUrl)
            }
        }
        saveCachesToDisk()
    }

    /**
     * Fetches repo.json from [inputUrl], normalizes the URL, and persists name + icon.
     * If the payload is a JSON array (MegaRepo), it extracts and adds all child repositories.
     * Returns a list of added repositories, or null if loading failed.
     */
    suspend fun addRepositoryFromInput(inputUrl: String): List<Repository>? = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) return@withContext null
        
        // Intercept Vega short codes / GitHub raw urls
        val isVega = trimmed == "vega-org" || trimmed.contains("vega-org") || trimmed.contains("Rowdy-Avinash") || trimmed.contains("vega-providers")
        if (isVega) {
            val added = com.lagradost.cloudstream3.desktop.plugins.vega.VegaRepositoryManager.addRepository(trimmed)
            if (added) {
                // Return an empty list so the UI knows it succeeded but doesn't expect a standard CloudStream .jar repository response
                return@withContext emptyList() 
            }
            return@withContext null
        }
        
        val resolvedUrl = parseRepoUrl(trimmed) ?: trimmed

        // Check if the URL resolves to a Mega Repo (JSON array)
        val request = Request.Builder().url(resolvedUrl).build()
        var body: String? = null
        try {
            redirectClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    body = response.body.string()
                }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch $resolvedUrl: ${e.message}")
        }

        if (body != null && body!!.trimStart().startsWith("[")) {
            // It's a MegaRepo! Parse the JSON array.
            try {
                val nodes = mapper.readTree(body!!)
                val urls = nodes.mapNotNull { it.get("url")?.asText() }

                val addedRepos = mutableListOf<Repository>()
                for (url in urls) {
                    val repo = addSingleRepository(url)
                    if (repo != null) {
                        addedRepos.add(repo)
                    }
                }
                return@withContext addedRepos.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                AppLogger.i("Failed to parse MegaRepo: ${e.message}")
            }
        }

        // Normal single repo flow
        val repo = addSingleRepository(resolvedUrl)
        return@withContext if (repo != null) listOf(repo) else null
    }

    private suspend fun addSingleRepository(url: String): Repository? {
        val resolvedUrl = parseRepoUrl(url) ?: url
        val manifest = fetchRepository(resolvedUrl) ?: return null
        saveRepository(
            RepositoryData(
                iconUrl = manifest.iconUrl,
                name = manifest.name,
                url = resolvedUrl,
            ),
        )
        return manifest
    }

    suspend fun getCachedRepository(url: String): Repository? {
        if (repoCache.containsKey(url)) return repoCache[url]
        val repo = fetchRepository(url)
        if (repo != null) {
            repoCache[url] = repo
            saveCachesToDisk()
        }
        return repo
    }

    suspend fun getCachedPlugins(listUrl: String): List<SitePlugin> {
        if (pluginsCache.containsKey(listUrl)) return pluginsCache[listUrl]!!
        val plugins = fetchPlugins(listUrl).filter { it.status != 0 }
        pluginsCache[listUrl] = plugins
        saveCachesToDisk()

        val newIcons = remotePluginIcons.value.toMutableMap()
        plugins.forEach { remotePlugin ->
            val remoteIcon = remotePlugin.iconUrl
            if (!remoteIcon.isNullOrEmpty()) {
                newIcons[remotePlugin.internalName] = remoteIcon
                newIcons[remotePlugin.name] = remoteIcon
            }
        }
        remotePluginIcons.value = newIcons

        return plugins
    }

    fun readPluginManifest(jarFile: File): Map<String, Any>? {
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return null
                zip.getInputStream(manifestEntry).use { input ->
                    return mapper.readValue(input, object : TypeReference<Map<String, Any>>() {})
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun parseRepoUrl(url: String): String? = withContext(Dispatchers.IO) {
        val fixedUrl = url.trim()
        if (fixedUrl.matches(Regex("^[a-zA-Z0-9!_-]+$"))) {
            val request = Request.Builder().url("https://cutt.ly/$fixedUrl").build()
            client.newCall(request).execute().use { response ->
                val loc = response.header("Location")
                if (loc != null && !loc.startsWith("https://cutt.ly/404")) {
                    return@withContext loc
                }
            }
            return@withContext null
        }
        if (fixedUrl.contains(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"))) {
            return@withContext fixedUrl.replace(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"), "")
                .let { if (!it.startsWith("http")) "https://$it" else it }
        }
        if (!fixedUrl.matches(Regex("^https?://.*"))) {
            return@withContext null
        }
        return@withContext fixedUrl
    }

    suspend fun fetchRepository(url: String): Repository? = withContext(Dispatchers.IO) {
        val finalUrl = parseRepoUrl(url) ?: url.trim().takeIf { it.startsWith("http") } ?: return@withContext null
        val request = Request.Builder().url(finalUrl).build()
        try {
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                if (body.trimStart().startsWith("<")) {
                    AppLogger.i("Failed to load repository from $url: Received HTML instead of JSON. The site may be protected by Cloudflare.")
                    return@withContext null
                }
                return@withContext mapper.readValue(body, Repository::class.java)
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch repository $url: ${e.message}")
            return@withContext null
        }
    }

    suspend fun fetchPlugins(pluginListUrl: String): List<SitePlugin> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(pluginListUrl).build()
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body.string()
                if (body.trimStart().startsWith("<")) {
                    AppLogger.i("Failed to load plugins from $pluginListUrl: Received HTML instead of JSON. The site may be protected by Cloudflare.")
                    return@withContext emptyList()
                }

                return@withContext mapper.readValue(body, object : TypeReference<List<SitePlugin>>() {})
                    .filter { it.status != 0 }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch or parse plugins from $pluginListUrl: ${e.message}")
            emptyList()
        }
    }

    fun getExtensionsDir(): File = PlatformPaths.extensionsDir

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun downloadPlugin(repoName: String, plugin: SitePlugin): File? = withContext(Dispatchers.IO) {
        val repoDir = File(getExtensionsDir(), repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        if (!repoDir.exists()) repoDir.mkdirs()

        val destFile = File(repoDir, "${plugin.internalName}.jar")
        val tempFile = File.createTempFile(destFile.name, ".tmp", getExtensionsDir())

        try {
            val request = Request.Builder().url(plugin.url).build()
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to download plugin")
                val body = response.body
                FileOutputStream(tempFile).use { out ->
                    body.byteStream().copyTo(out)
                }
            }

            if (plugin.fileHash != null) {
                val downloadHash = sha256(tempFile)
                if (plugin.fileHash != downloadHash) {
                    throw IllegalStateException("Extension hash mismatch when validating '${destFile.name}'! Expected: '${plugin.fileHash}', got: '$downloadHash'.")
                }
            }

            try {
                Files.move(
                    tempFile.toPath(),
                    destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            // [PERFORMANCE] If a pre-compiled JVM jar is provided, download it alongside the .cs3 file.
            // ExtensionLoader will detect this -jvm.jar file and completely skip the slow dex2jar conversion step!
            if (!plugin.jarUrl.isNullOrBlank()) {
                val jvmDestFile = File(repoDir, "${plugin.internalName}-jvm.jar")
                val jvmTempFile = File.createTempFile(jvmDestFile.name, ".tmp", getExtensionsDir())
                try {
                    val jvmRequest = Request.Builder().url(plugin.jarUrl).build()
                    redirectClient.newCall(jvmRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            FileOutputStream(jvmTempFile).use { out ->
                                response.body.byteStream().copyTo(out)
                            }
                            try {
                                Files.move(
                                    jvmTempFile.toPath(),
                                    jvmDestFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE,
                                )
                            } catch (_: AtomicMoveNotSupportedException) {
                                Files.move(
                                    jvmTempFile.toPath(),
                                    jvmDestFile.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                )
                            }
                            com.lagradost.common.logging.AppLogger.i("Successfully pre-seeded JVM bytecode for ${plugin.internalName}!")
                        }
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.i("Failed to download pre-compiled JVM jar for ${plugin.internalName}: ${e.message}")
                } finally {
                    jvmTempFile.delete()
                }
            }

            return@withContext destFile
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Failed to download or unzip plugin ${plugin.url}", e)
            return@withContext null
        }
    }

    fun clearCaches() {
        repoCache.clear()
        pluginsCache.clear()
    }

    suspend fun checkAndApplyPluginUpdates() {
        val extensionsDir = getExtensionsDir()
        if (!extensionsDir.exists()) return

        val allRemote = getAllPlugins()
        val localJars = extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .toList()

        var updatedCount = 0
        for (jar in localJars) {
            val manifest = readPluginManifest(jar) ?: continue
            val name = manifest["name"] as? String ?: jar.nameWithoutExtension
            val internalName = manifest["internalName"] as? String ?: name
            val localVersion = manifest["version"]?.toString()?.toIntOrNull() ?: 0

            val remoteMatch = allRemote.find { it.second.internalName == internalName }
            if (remoteMatch != null) {
                val repoName = remoteMatch.first
                val sitePlugin = remoteMatch.second
                if (sitePlugin.version > localVersion) {
                    AppLogger.i("Auto-updating plugin: $internalName from v$localVersion to v${sitePlugin.version}")
                    try {
                        val newJar = downloadPlugin(repoName, sitePlugin)
                        if (newJar != null) {
                            withContext(Dispatchers.IO) {
                                com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(jar.absolutePath)
                                com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(newJar)
                            }
                            updatedCount++
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Failed to auto-update plugin $internalName", e)
                    }
                }
            }
        }
        if (updatedCount > 0) {
            AppLogger.i("Auto-updated $updatedCount plugins successfully.")
            // Trigger a UI refresh if active
            syncGeneration.value = syncGeneration.value + 1
        }
    }

    private fun scanLocalPluginIcons(): Map<String, String> {
        val icons = mutableMapOf<String, String>()
        val extensionsDir = getExtensionsDir()
        if (!extensionsDir.exists()) return icons
        extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .forEach { jar ->
                val manifest = readPluginManifest(jar) ?: return@forEach
                val iconUrl = manifest["iconUrl"] as? String ?: return@forEach
                val internalName = manifest["internalName"] as? String
                val name = manifest["name"] as? String
                if (internalName != null) icons[internalName] = iconUrl
                if (name != null) icons[name] = iconUrl
            }
        return icons
    }

    suspend fun refreshAllRepositoryMetadata(): Int = withContext(Dispatchers.IO) {
        // Fetch all manifests in parallel but collect results without writing to disk yet
        val updates = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String>>()
        coroutineScope {
            getSavedRepositories().map { saved ->
                async {
                    val manifest = fetchRepository(saved.url) ?: return@async
                    repoCache[saved.url] = manifest
                    updates[saved.url] = Pair(manifest.iconUrl, manifest.name)
                    AppLogger.i("Refreshed repo metadata: ${manifest.name}")
                }
            }.awaitAll()
        }
        // Single synchronized batch-write instead of 26 concurrent read-modify-write cycles
        if (updates.isNotEmpty()) {
            synchronized(this@DesktopRepositoryManager) {
                val current = readRepositoriesFromDisk().toMutableList()
                for ((url, pair) in updates) {
                    val (iconUrl, name) = pair
                    val index = current.indexOfFirst { it.url == url }
                    if (index >= 0) {
                        val existing = current[index]
                        current[index] = existing.copy(
                            name = if (name.isNotBlank() && name != url) name else existing.name,
                            iconUrl = iconUrl ?: existing.iconUrl,
                        )
                    }
                }
                writeRepositoriesToDisk(current.distinctBy { it.url })
            }
        }
        updates.size
    }

    suspend fun rebuildRemotePluginCatalog(): Int = withContext(Dispatchers.IO) {
        val iconMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        val total = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            getSavedRepositories().map { saved ->
                async {
                    val repo = fetchRepository(saved.url) ?: return@async
                    repoCache[saved.url] = repo
                    repo.pluginLists.map { listUrl ->
                        async {
                            val plugins = fetchPlugins(listUrl)
                            pluginsCache[listUrl] = plugins
                            AppLogger.i("Fetched ${plugins.size} plugins from $listUrl")
                            plugins.forEach { plugin ->
                                val icon = plugin.iconUrl
                                if (!icon.isNullOrEmpty()) {
                                    iconMap[plugin.internalName] = icon
                                    iconMap[plugin.name] = icon
                                }
                            }
                            total.addAndGet(plugins.size)
                        }
                    }.awaitAll()
                }
            }.awaitAll()
        }

        remotePluginIcons.value = iconMap + scanLocalPluginIcons()
        total.get()
    }

    suspend fun autoUpdatePlugins(): List<com.lagradost.common.storage.PluginUpdateRecord> = withContext(Dispatchers.IO) {
        val updatedList = mutableListOf<com.lagradost.common.storage.PluginUpdateRecord>()
        val savedRepos = getSavedRepositories()
        val extensionsDir = getExtensionsDir()

        savedRepos.forEach { saved ->
            try {
                val repo = fetchRepository(saved.url) ?: return@forEach

                val remotePlugins = coroutineScope {
                    repo.pluginLists.map { listUrl ->
                        async {
                            pluginsCache[listUrl] ?: fetchPlugins(listUrl).also { pluginsCache[listUrl] = it }
                        }
                    }.awaitAll().flatten()
                }.distinctBy { it.internalName }

                val repoDirName = repo.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val repoDir = File(extensionsDir, repoDirName)
                if (!repoDir.exists()) repoDir.mkdirs()

                remotePlugins.forEach { remotePlugin ->
                    val localJar = File(repoDir, "${remotePlugin.internalName}.jar")
                    if (localJar.exists()) {
                        val manifest = readPluginManifest(localJar)
                        val localVersion = manifest?.get("version")?.toString()?.toIntOrNull() ?: 0

                        if (remotePlugin.version > localVersion) {
                            AppLogger.i("Auto-Updater: Updating ${remotePlugin.name} from v$localVersion to v${remotePlugin.version}")

                            val iconUrl = remotePlugin.iconUrl ?: remotePluginIcons.value[remotePlugin.internalName] ?: saved.iconUrl
                            updatedList.add(
                                com.lagradost.common.storage.PluginUpdateRecord(
                                    pluginName = remotePlugin.name,
                                    version = remotePlugin.version,
                                    iconUrl = iconUrl,
                                ),
                            )

                            com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(localJar.absolutePath)

                            localJar.delete()
                            File(repoDir, "${remotePlugin.internalName}-jvm.jar").delete()
                            File(repoDir, "${remotePlugin.internalName}.dex").delete()

                            val newJar = downloadPlugin(repo.name, remotePlugin)

                            if (newJar != null && newJar.exists()) {
                                try {
                                    com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(newJar)
                                    AppLogger.i("Auto-Updater: Successfully hot-reloaded ${remotePlugin.name}")
                                } catch (e: Exception) {
                                    AppLogger.i("Auto-Updater: Failed to hot-reload ${remotePlugin.name}")
                                    AppLogger.e("Auto-Updater", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.i("Auto-Updater: Error checking repository ${saved.url}")
                e.printStackTrace()
            }
        }
        if (updatedList.isNotEmpty()) {
            com.lagradost.common.storage.DesktopDataStore.addUpdateHistory(updatedList)
            com.lagradost.common.storage.DesktopDataStore.setUnreadUpdates(true)
        }
        updatedList
    }

    fun getAllPlugins(): List<Pair<String, SitePlugin>> {
        val list = mutableListOf<Pair<String, SitePlugin>>()
        for (saved in getSavedRepositories()) {
            val repo = repoCache[saved.url] ?: continue
            for (listUrl in repo.pluginLists) {
                pluginsCache[listUrl]?.forEach { list.add(Pair(saved.name, it)) }
            }
        }
        return list.distinctBy { it.second.internalName }
    }

    private val syncMutex = Mutex()

    suspend fun syncAll(): SyncReport = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            AppLogger.i("=== Desktop sync started ===")

            val reposRefreshed = refreshAllRepositoryMetadata()
            val catalogPlugins = rebuildRemotePluginCatalog()
            val pluginsUpdated = autoUpdatePlugins()
            val newPluginsLoaded = com.lagradost.runtime.loader.ExtensionLoader.rescanAndLoadNewPlugins(getExtensionsDir())
            val iconsCached = remotePluginIcons.value.size

            syncGeneration.value = syncGeneration.value + 1
            saveCachesToDisk()
            AppLogger.i("=== Desktop sync finished ===")

            SyncReport(
                reposRefreshed = reposRefreshed,
                catalogPlugins = catalogPlugins,
                pluginsUpdated = pluginsUpdated.size,
                iconsCached = iconsCached,
                newPluginsLoaded = newPluginsLoaded,
            )
        }
    }
}
