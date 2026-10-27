package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import java.io.File

class UninstallPlugin(
    private val repository: PluginRepository,
) {
    suspend fun await(file: File, internalName: String, name: String): Boolean {
        try {
            SandboxedPluginManager.unloadPlugin(file)
            ExtensionLoader.unloadPlugin(file.absolutePath)
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            Thread.sleep(100)
            @Suppress("deprecation")
            System.runFinalization()

            val stem = file.nameWithoutExtension
            val parentDir = file.parentFile
            val cacheDir = PlatformPaths.pluginCacheDir
            val toDelete = mutableSetOf<File>()
            toDelete.add(file)
            parentDir?.listFiles { f ->
                f.isFile && f.name.startsWith(stem, ignoreCase = true) && (
                    f.name.endsWith(".tmp", ignoreCase = true) ||
                    f.name.contains(".pending_delete", ignoreCase = true) ||
                    f.name.endsWith("-jvm.jar", ignoreCase = true) ||
                    f.name.endsWith("-secure.jar", ignoreCase = true) ||
                    f.name.endsWith(".dex", ignoreCase = true) ||
                    f.name.endsWith(".d2j_failed", ignoreCase = true)
                )
            }?.let { toDelete.addAll(it) }
            cacheDir.listFiles { f ->
                f.isFile && f.name.startsWith(stem, ignoreCase = true)
            }?.let { toDelete.addAll(it) }
            val filesToDelete = toDelete.toList()
            /*

                File(cacheDir, "$stem-jvm.jar"),
                File(cacheDir, "$stem-jvm.jar.tmp"),
                File(cacheDir, "$stem.dex"),
                File(cacheDir, "$stem-secure.jar"),
                File(cacheDir, "$stem.d2j_failed"),
                parentDir?.let { File(it, "$stem-jvm.jar") },
                parentDir?.let { File(it, "$stem.dex") },
                parentDir?.let { File(it, "$stem-secure.jar") },
            */
            for (f in filesToDelete) {
                if (f.exists()) {
                    var ok = false
                    for (attempt in 1..3) {
                        try {
                            java.nio.file.Files.deleteIfExists(f.toPath())
                            ok = true
                            break
                        } catch (_: Throwable) {
                            @Suppress("ExplicitGarbageCollectionCall")
                            System.gc()
                            @Suppress("deprecation")
                            System.runFinalization()
                            Thread.sleep(50L * attempt)
                        }
                    }
                    if (!ok) {
                        ok = f.delete()
                    }
                    if (!ok) {
                        val pendingFile = File(f.parentFile, "${f.name}.${System.currentTimeMillis()}.pending_delete")
                        val renamed = f.renameTo(pendingFile)
                        if (renamed) {
                            pendingFile.deleteOnExit()
                        } else {
                            f.deleteOnExit()
                        }
                        if (f == file) {
                            try {
                                File(f.parentFile, "${f.name}.uninstalled").createNewFile()
                            } catch (_: Throwable) {}
                        }
                    }

                }
            }
            ExtensionLoader.removeTrusted(file, internalName, manifestName = name)
            repository.incrementSyncGeneration()
            return true
        } catch (t: Throwable) {
            com.lagradost.common.logging.AppLogger.e("UninstallPlugin: Failed to uninstall plugin ${file.name}", t)
            return false
        }
    }
}
