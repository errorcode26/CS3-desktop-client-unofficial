package com.lagradost.cloudstream3.desktop.domain.plugins.interactor

import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.runtime.loader.ExtensionLoader
import java.io.File

class UninstallPlugin(
    private val repository: PluginRepository,
) {
    suspend fun await(file: File, internalName: String, name: String): Boolean {
        try {
            ExtensionLoader.unloadPlugin(file.absolutePath)
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            Thread.sleep(100)
            @Suppress("deprecation")
            System.runFinalization()

            val stem = file.nameWithoutExtension
            val parentDir = file.parentFile
            val filesToDelete = listOfNotNull(
                file,
                parentDir?.let { File(it, "$stem-jvm.jar") },
                parentDir?.let { File(it, "$stem.dex") },
                parentDir?.let { File(it, "$stem-secure.jar") },
            )
            for (f in filesToDelete) {
                if (f.exists()) {
                    val ok = f.delete()
                    if (!ok) f.deleteOnExit()
                }
            }
            ExtensionLoader.removeTrusted(file, internalName, manifestName = name)
            repository.incrementSyncGeneration()
            return true
        } catch (_: Throwable) {
            return false
        }
    }
}
