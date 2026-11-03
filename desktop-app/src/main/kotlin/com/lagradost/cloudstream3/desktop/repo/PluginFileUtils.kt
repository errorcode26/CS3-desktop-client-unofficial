package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Internal file utility for plugin downloads, hashing, and cache directories.
 * Not part of the public API — consumed only by [DesktopRepositoryManager].
 */
internal object PluginFileUtils {

    fun getExtensionsDir(): File = PlatformPaths.extensionsDir

    fun normalizeHash(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().lowercase()
            .removePrefix("sha256-")
            .removePrefix("sha256:")
            .removePrefix("sha-256:")
    }

    fun rawSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = "sha256-" + rawSha256(file)

    fun validatePluginArchive(file: File, expectedInternalName: String = ""): Boolean {
        if (!file.exists() || file.length() < 100L) return false
        return try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return false
                var nameMatches = false
                zip.getInputStream(manifestEntry).use { input ->
                    val manifestNode = PluginNetworkClient.mapper.readTree(input)
                    val internalName = manifestNode.get("internalName")?.asText().orEmpty()
                    val manifestName = manifestNode.get("name")?.asText().orEmpty()
                    if (expectedInternalName.isBlank() ||
                        internalName.equals(expectedInternalName, ignoreCase = true) ||
                        manifestName.equals(expectedInternalName, ignoreCase = true)
                    ) {
                        nameMatches = true
                    }
                }
                if (!nameMatches) return false

                val hasDex = zip.getEntry("classes.dex") != null
                val hasClasses = zip.entries().asSequence().any { it.name.endsWith(".class") }
                hasDex || hasClasses
            }
        } catch (_: Throwable) {
            false
        }
    }

    private val timestampedSecureJarRegex = Regex(""".+_\d{13}-secure\.jar$""")

    fun cleanStaleTempFiles() {
        try {
            val now = System.currentTimeMillis()
            val cutoff = now - (5 * 60 * 1000L)
            val artifactCutoff = now - (10 * 60 * 1000L)
            val dirs = listOf(getExtensionsDir(), PlatformPaths.pluginCacheDir)
            dirs.forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown()
                        .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".bak")) }
                        .filter { it.lastModified() < cutoff }
                        .forEach { f ->
                            try { f.delete() } catch (_: Throwable) {}
                        }
                }
            }

            // Reclaim bytecode cache artifacts that can never be used again:
            //  - timestamped fallback jars created by the old locked-cache fallback path
            //  - artifacts whose source plugin no longer exists in any repository directory
            val cacheDir = PlatformPaths.pluginCacheDir
            if (!cacheDir.exists()) return
            val extensionsRoot = getExtensionsDir()
            val pluginStems = if (extensionsRoot.exists()) {
                extensionsRoot.walkTopDown()
                    .filter {
                        it.isFile &&
                            (it.extension.equals("jar", ignoreCase = true) || it.extension.equals("cs3", ignoreCase = true))
                    }
                    .map { it.nameWithoutExtension.substringBefore("-jvm").substringBefore("-secure") }
                    .toSet()
            } else {
                emptySet()
            }

            cacheDir.listFiles()?.forEach { f ->
                if (!f.isFile || f.lastModified() >= artifactCutoff) return@forEach
                if (timestampedSecureJarRegex.matches(f.name)) {
                    try { f.delete() } catch (_: Throwable) {}
                    return@forEach
                }
                val name = f.name
                val stem = name
                    .removeSuffix(".d2j_failed")
                    .removeSuffix(".load_failed")
                    .removeSuffix(".tmp")
                    .removeSuffix(".dex")
                    .removeSuffix("-jvm.jar")
                    .removeSuffix("-secure.jar")
                if (stem == name) return@forEach
                val stillReferenced = pluginStems.any { it.equals(stem, ignoreCase = true) }
                if (!stillReferenced) {
                    try { f.delete() } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    fun replaceFileWithRetry(source: File, destination: File, maxRetries: Int = 3) {
        var lastException: Throwable? = null
        for (attempt in 1..maxRetries) {
            try {
                try {
                    Files.move(
                        source.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        source.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                return
            } catch (t: Throwable) {
                lastException = t
                if (attempt < maxRetries) {
                    @Suppress("ExplicitGarbageCollectionCall")
                    System.gc()
                    Thread.sleep(50L * attempt)
                }
            }
        }
        if (lastException != null) throw lastException
    }

    /**
     * Returns every cache artifact that belongs exactly to [stem] (case-insensitive).
     * Deliberately does not prefix-match, so uninstalling one plugin never touches
     * cache files belonging to a plugin whose name starts with the same stem.
     */
    fun cacheArtifactsForStem(stem: String): List<File> {
        val cacheDir = PlatformPaths.pluginCacheDir
        val exactNames = listOf(
            "$stem-jvm.jar",
            "$stem-secure.jar",
            "$stem.dex",
            "$stem.d2j_failed",
            "$stem.load_failed",
        ).map { it.lowercase() }.toSet()
        val timestamped = Regex("^${Regex.escape(stem)}_\\d{13}-secure\\.jar$", RegexOption.IGNORE_CASE)
        return cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.lowercase() in exactNames || timestamped.matches(it.name)) }
            .orEmpty()
    }

    /**
     * Downloads a `.jar` or `.cs3` plugin to the correct repository directory.
     * Optionally fetches a pre-compiled JVM bytecode jar to bypass Dex2Jar.
     */
    suspend fun downloadPlugin(repoName: String, plugin: SitePlugin): File? = withContext(Dispatchers.IO) {
        val safeRepoName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val repoDir = File(getExtensionsDir(), safeRepoName)
        if (!repoDir.exists()) repoDir.mkdirs()

        val safeInternalName = plugin.internalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(repoDir, "$safeInternalName.jar")
        if (!destFile.canonicalFile.startsWith(repoDir.canonicalFile)) {
            AppLogger.e("Security Alert: Path traversal attempt detected in plugin internalName: '${plugin.internalName}'")
            return@withContext null
        }
        val tempFile = File.createTempFile(destFile.name, ".tmp", getExtensionsDir())

        try {
            // Primary download URL first, followed by verified alternate repository mirrors for this plugin
            val candidateUrls = mutableListOf(plugin.url)
            DesktopRepositoryManager.getAllPlugins()
                .filter { (rName, other) ->
                    (rName == repoName || (plugin.fileHash != null && other.fileHash == plugin.fileHash)) &&
                        other.internalName == plugin.internalName &&
                        other.url != plugin.url &&
                        other.url.startsWith("http")
                }
                .forEach { candidateUrls.add(it.second.url) }

            var downloadSuccess = false
            for (candidateUrl in candidateUrls) {
                try {
                    val request = Request.Builder().url(candidateUrl).build()
                    PluginNetworkClient.redirectClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code} downloading from $candidateUrl")
                        val body = response.body
                        val contentLength = body.contentLength()
                        if (contentLength > 64 * 1024 * 1024L) {
                            throw IllegalStateException("Plugin package exceeded 64 MB maximum buffer limit")
                        }
                        FileOutputStream(tempFile).use { out ->
                            val buffer = ByteArray(8192)
                            var totalBytes = 0L
                            val input = body.byteStream()
                            var read = input.read(buffer)
                            while (read != -1) {
                                totalBytes += read
                                if (totalBytes > 64 * 1024 * 1024L) {
                                    throw IllegalStateException("Plugin package exceeded 64 MB maximum buffer limit")
                                }
                                out.write(buffer, 0, read)
                                read = input.read(buffer)
                            }
                        }
                    }

                    if (plugin.fileHash != null) {
                        val downloadHash = rawSha256(tempFile)
                        val expectedNorm = normalizeHash(plugin.fileHash)
                        val actualNorm = normalizeHash(downloadHash)
                        if (expectedNorm != null && expectedNorm != actualNorm) {
                            var hashValidAfterRefresh = false
                            try {
                                val savedRepo = DesktopRepositoryManager.getSavedRepositories().find { it.name == repoName }
                                val listUrl = savedRepo?.let { DesktopRepositoryManager.getPluginsJsonUrl(it.url) }
                                if (listUrl != null) {
                                    val freshPlugins = PluginNetworkClient.fetchPlugins(listUrl)
                                    val freshMatch = freshPlugins.find { it.internalName == plugin.internalName }
                                    if (freshMatch != null && normalizeHash(freshMatch.fileHash) == actualNorm) {
                                        AppLogger.i("Plugin hash was updated remotely for ${plugin.internalName} (cached: ${plugin.fileHash}, remote: ${freshMatch.fileHash}). Updating cache...")
                                        DesktopRepositoryManager.updateCachedPlugin(listUrl, freshMatch)
                                        hashValidAfterRefresh = true
                                    }
                                }
                            } catch (refreshErr: Exception) {
                                AppLogger.w("Failed to refresh repository metadata during hash mismatch: ${refreshErr.message}")
                            }

                            if (!hashValidAfterRefresh) {
                                val hasMoreCandidates = candidateUrls.indexOf(candidateUrl) < candidateUrls.size - 1
                                if (hasMoreCandidates) {
                                    throw IllegalStateException("Hash mismatch for '$candidateUrl'. Trying next mirror...")
                                }
                                val isArchiveValid = validatePluginArchive(tempFile, plugin.internalName)
                                if (!isArchiveValid) {
                                    throw IllegalStateException("Extension hash mismatch and invalid archive for '${destFile.name}'! Expected: '${plugin.fileHash}', got: '$downloadHash'.")
                                } else {
                                    AppLogger.w("Hash discrepancy for '${destFile.name}' (expected '$expectedNorm', got '$actualNorm'), but archive integrity verified. Proceeding.")
                                }
                            }
                        }
                    }

                    if (!validatePluginArchive(tempFile, plugin.internalName)) {
                        throw IllegalStateException("Downloaded file for '${plugin.internalName}' failed archive integrity verification.")
                    }

                    downloadSuccess = true
                    break
                } catch (e: Exception) {
                    AppLogger.w("Failed to download '${plugin.internalName}' from $candidateUrl: ${e.message}. Trying next mirror if available...")
                }
            }

            if (!downloadSuccess) {
                return@withContext null
            }

            replaceFileWithRetry(tempFile, destFile)

            // Remove legacy artifacts of the same plugin that differ only by extension
            // (e.g. an older .cs3 superseded by the canonical .jar). Exact stem match only.
            repoDir.listFiles { f ->
                f.isFile &&
                    f != destFile &&
                    f.nameWithoutExtension.equals(safeInternalName, ignoreCase = true) &&
                    (f.extension.equals("cs3", ignoreCase = true) || f.extension.equals("jar", ignoreCase = true))
            }?.forEach { legacy ->
                try { legacy.delete() } catch (_: Throwable) {}
            }

            // Clear any previously recorded failure marker for this extension now that fresh files are written
            com.lagradost.runtime.loader.ExtensionLoader.clearFailedMarker(destFile)

            // [PERFORMANCE] If a pre-compiled JVM jar is provided, download it alongside the .cs3 file.
            // ExtensionLoader will detect this -jvm.jar file and completely skip the slow dex2jar conversion step!
            if (!plugin.jarUrl.isNullOrBlank() && !plugin.jarHash.isNullOrBlank()) {
                val jvmDestFile = File(PlatformPaths.pluginCacheDir, "$safeInternalName-jvm.jar")
                if (!jvmDestFile.canonicalFile.startsWith(PlatformPaths.pluginCacheDir.canonicalFile)) {
                    AppLogger.e("Security Alert: Path traversal attempt detected in jarUrl destination: '${plugin.internalName}'")
                    return@withContext destFile
                }
                val jvmTempFile = File.createTempFile(jvmDestFile.name, ".tmp", PlatformPaths.pluginCacheDir)
                try {
                    val jvmRequest = Request.Builder().url(plugin.jarUrl).build()
                    PluginNetworkClient.redirectClient.newCall(jvmRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body.contentLength() > 64 * 1024 * 1024L) {
                                throw IllegalStateException("JVM plugin exceeded 64 MB maximum buffer limit")
                            }
                            FileOutputStream(jvmTempFile).use { out ->
                                val buffer = ByteArray(8192)
                                var totalBytes = 0L
                                val input = body.byteStream()
                                var read = input.read(buffer)
                                while (read != -1) {
                                    totalBytes += read
                                    if (totalBytes > 64 * 1024 * 1024L) {
                                        throw IllegalStateException("JVM plugin exceeded 64 MB maximum buffer limit")
                                    }
                                    out.write(buffer, 0, read)
                                    read = input.read(buffer)
                                }
                            }

                            val downloadHash = rawSha256(jvmTempFile)
                            val expectedJvmNorm = normalizeHash(plugin.jarHash)
                            val actualJvmNorm = normalizeHash(downloadHash)
                            if (expectedJvmNorm != null && expectedJvmNorm != actualJvmNorm) {
                                throw IllegalStateException("JVM Extension hash mismatch when validating '${jvmDestFile.name}'! Expected: '${plugin.jarHash}', got: '$downloadHash'.")
                            }

                            val isValidJvm = try {
                                ZipFile(jvmTempFile).use { z -> z.entries().asSequence().any { it.name.endsWith(".class") } }
                            } catch (_: Throwable) { false }
                            if (!isValidJvm) {
                                throw IllegalStateException("Corrupt pre-compiled JVM jar downloaded for '${plugin.internalName}'")
                            }

                            replaceFileWithRetry(jvmTempFile, jvmDestFile)
                            AppLogger.i("Successfully pre-seeded JVM bytecode for ${plugin.internalName}!")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.i("Failed to download pre-compiled JVM jar for ${plugin.internalName}: ${e.message}")
                } finally {
                    jvmTempFile.delete()
                }
            }

            return@withContext destFile
        } catch (e: Exception) {
            AppLogger.e("Failed to download or unzip plugin ${plugin.url}", e)
            return@withContext null
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /** Unloads jars and physically deletes the repo directory. */
    fun deleteRepositoryDirectory(repoName: String) {
        val safeRepoName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val repoDir = File(getExtensionsDir(), safeRepoName)
        if (repoDir.exists()) {
            // Must explicitly unload all plugins from this repo first to release Windows file locks
            val jars = repoDir.listFiles { f -> f.isFile && (f.extension == "jar" || f.extension == "cs3") }
            jars?.forEach { jar ->
                com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(jar.absolutePath)
                val stem = jar.nameWithoutExtension.substringBefore("-jvm").substringBefore("-secure")
                cacheArtifactsForStem(stem).forEach { cf -> if (cf.exists()) cf.delete() }
            }

            // GC and finalize to release locks (same as individual plugin uninstall)
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            Thread.sleep(150)
            @Suppress("deprecation")
            System.runFinalization()

            val deleted = repoDir.deleteRecursively()
            AppLogger.i("Deleted physical repository directory '${repoDir.name}': ok=$deleted")
        }
    }
}
