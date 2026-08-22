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

/**
 * Internal file utility for plugin downloads, hashing, and cache directories.
 * Not part of the public API — consumed only by [DesktopRepositoryManager].
 */
internal object PluginFileUtils {

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

    /**
     * Downloads a `.jar` or `.cs3` plugin to the correct repository directory.
     * Optionally fetches a pre-compiled JVM bytecode jar to bypass Dex2Jar.
     */
    suspend fun downloadPlugin(repoName: String, plugin: SitePlugin): File? = withContext(Dispatchers.IO) {
        val repoDir = File(getExtensionsDir(), repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        if (!repoDir.exists()) repoDir.mkdirs()

        val destFile = File(repoDir, "${plugin.internalName}.jar")
        val tempFile = File.createTempFile(destFile.name, ".tmp", getExtensionsDir())

        try {
            // Primary download URL first, followed by any alternate repository mirrors for this plugin
            val candidateUrls = mutableListOf(plugin.url)
            DesktopRepositoryManager.getAllPlugins()
                .filter { it.second.internalName == plugin.internalName && it.second.url != plugin.url && it.second.url.startsWith("http") }
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

                    if (plugin.fileHash != null && candidateUrl == plugin.url) {
                        val downloadHash = sha256(tempFile)
                        if (plugin.fileHash != downloadHash) {
                            throw IllegalStateException("Extension hash mismatch when validating '${destFile.name}'! Expected: '${plugin.fileHash}', got: '$downloadHash'.")
                        }
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
            if (!plugin.jarUrl.isNullOrBlank() && !plugin.jarHash.isNullOrBlank()) {
                val jvmDestFile = File(repoDir, "${plugin.internalName}-jvm.jar")
                val jvmTempFile = File.createTempFile(jvmDestFile.name, ".tmp", getExtensionsDir())
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

                            val downloadHash = sha256(jvmTempFile)
                            if (plugin.jarHash != downloadHash) {
                                throw IllegalStateException("JVM Extension hash mismatch when validating '${jvmDestFile.name}'! Expected: '${plugin.jarHash}', got: '$downloadHash'.")
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
        val repoDir = File(getExtensionsDir(), repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
        if (repoDir.exists()) {
            // Must explicitly unload all plugins from this repo first to release Windows file locks
            val jars = repoDir.listFiles { f -> f.isFile && (f.extension == "jar" || f.extension == "cs3") }
            jars?.forEach { jar ->
                com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(jar.absolutePath)
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
