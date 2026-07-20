package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiState
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LocalPlugin(
    val file: File,
    val name: String,
    val internalName: String,
    val version: Int,
    val iconUrl: String?,
    val repoName: String,
    val language: String?,
    val tvTypes: List<String>?,
    val description: String? = null,
    val fileSize: Long = 0L,
)

class ExtensionsViewModel : BaseMviViewModel<ExtensionsUiState, ExtensionsUiEvent, ExtensionsUiEffect>(
    initialState = ExtensionsUiState()
) {
    val savedRepositories = DesktopRepositoryManager.savedRepositories
    val remotePluginIcons = DesktopRepositoryManager.remotePluginIcons
    val syncGeneration = DesktopRepositoryManager.syncGeneration

    override fun handleEvent(event: ExtensionsUiEvent) {
        when (event) {
            is ExtensionsUiEvent.OnFetchPlugins -> fetchPlugins()
            is ExtensionsUiEvent.OnLoadPluginsFromManager -> loadPluginsFromManager()
            is ExtensionsUiEvent.OnRefreshInstalled -> refreshInstalled()
            is ExtensionsUiEvent.OnInspectRepository -> inspectRepository(event.repoName)
            is ExtensionsUiEvent.OnInstallPlugin -> installPlugin(event.repoName, event.plugin, event.onResult)
            is ExtensionsUiEvent.OnUninstallPlugins -> uninstallPlugins(event.plugins)
            is ExtensionsUiEvent.OnUninstallByInternalName -> uninstallByInternalName(event.internalName)
            is ExtensionsUiEvent.OnLoadLocalPlugin -> loadLocalPlugin(event.file)
            is ExtensionsUiEvent.OnRemoveRepository -> removeRepository(event.url)
            is ExtensionsUiEvent.OnClearBypass -> clearBypass()
            is ExtensionsUiEvent.OnBypassSecurityAndInstall -> bypassSecurityAndInstall(event.repoName, event.plugin)
            is ExtensionsUiEvent.OnClearPermissionRequest -> clearPermissionRequest()
            is ExtensionsUiEvent.OnGrantPermissionAndInstall -> grantPermissionAndInstall(event.repoName, event.plugin, event.permissionName)
        }
    }

    suspend fun addRepositoryFromInput(input: String): List<com.lagradost.cloudstream3.desktop.repo.Repository>? = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.addRepositoryFromInput(input)
    }

    private fun removeRepository(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopRepositoryManager.removeRepository(url)
        }
    }

    suspend fun syncAllRepos() = withContext(Dispatchers.IO) {
        DesktopRepositoryManager.syncAll()
    }

    fun getPluginsJsonUrl(url: String): String = DesktopRepositoryManager.getPluginsJsonUrl(url)

    fun getExtensionsDir(): File = DesktopRepositoryManager.getExtensionsDir()

    fun isIconFailed(url: String): Boolean = DesktopRepositoryManager.isIconFailed(url)

    fun markIconFailed(url: String) = DesktopRepositoryManager.markIconFailed(url)

    private fun inspectRepository(repoName: String?) {
        updateState { copy(inspectedRepoName = repoName) }
    }

    private fun fetchPlugins() {
        updateState { copy(isFetching = true, statusText = "Fetching plugins from repositories...") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.syncAll()
                }
                val allPlugins = DesktopRepositoryManager.getAllPlugins()
                val text = "Fetched ${allPlugins.size} plugins from ${DesktopRepositoryManager.getSavedRepositories().size} repositories."
                updateState { copy(plugins = allPlugins, statusText = text) }
            } catch (e: Throwable) {
                updateState { copy(statusText = "Error: ${e.message}") }
            } finally {
                updateState { copy(isFetching = false) }
            }
        }
    }

    private fun loadPluginsFromManager() {
        val allPlugins = DesktopRepositoryManager.getAllPlugins()
        val text = "Showing ${allPlugins.size} plugins from ${DesktopRepositoryManager.getSavedRepositories().size} repositories."
        updateState { copy(plugins = allPlugins, statusText = text) }
    }

    private fun refreshInstalled() {
        val list = mutableListOf<LocalPlugin>()
        val extensionsDir = DesktopRepositoryManager.getExtensionsDir()
        val allRemote = DesktopRepositoryManager.getAllPlugins()
        if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
                .filter { !it.name.endsWith("-jvm.jar") }
                .forEach { jar ->
                    val manifest = DesktopRepositoryManager.readPluginManifest(jar)
                    val name = manifest?.get("name") as? String ?: jar.nameWithoutExtension
                    val internalName = manifest?.get("internalName") as? String ?: name
                    val version = manifest?.get("version")?.toString()?.toIntOrNull() ?: 0
                    val iconUrl = manifest?.get("iconUrl") as? String

                    val remoteMatch = allRemote.find { it.second.internalName == internalName }
                    val repoName = remoteMatch?.first ?: jar.parentFile.name.replace("_", " ")

                    val rawTvTypes = manifest?.get("tvTypes")
                    val tvTypes = when (rawTvTypes) {
                        is List<*> -> rawTvTypes.filterIsInstance<String>()
                        is String -> listOf(rawTvTypes)
                        else -> remoteMatch?.second?.tvTypes ?: emptyList()
                    }
                    val language = manifest?.get("language") as? String ?: remoteMatch?.second?.language

                    val description = manifest?.get("description") as? String ?: remoteMatch?.second?.description
                    list.add(LocalPlugin(jar, name, internalName, version, iconUrl, repoName, language, tvTypes, description, jar.length()))
                }
        }
        updateState { copy(installedPlugins = list) }
    }

    private fun installPlugin(repoName: String, plugin: SitePlugin, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jarFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (jarFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                        ExtensionLoader.loadAndInit(jarFile)
                    }
                    onResult("Installed")
                    refreshInstalled()
                } else {
                    onResult("Failed")
                }
            } catch (e: com.lagradost.runtime.security.RequiresPermissionException) {
                com.lagradost.common.logging.AppLogger.e("Permission required for plugin", e)
                updateState { copy(pluginRequiringPermission = Triple(repoName, plugin, e.permissionName)) }
                onResult("Requires Permission")
            } catch (e: java.lang.SecurityException) {
                com.lagradost.common.logging.AppLogger.e("Security exception removing plugin", e)
                updateState { copy(pluginRequiringBypass = Pair(repoName, plugin)) }
                onResult("Blocked (Security)")
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                try {
                    val jarFile = java.io.File(DesktopRepositoryManager.getExtensionsDir(), "${repoName.replace(" ", "_")}/${plugin.internalName}.jar")
                    if (jarFile.exists()) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                    }
                } catch (_: Throwable) {}
                onResult("Error")
            }
        }
    }

    private fun bypassSecurityAndInstall(repoName: String, plugin: SitePlugin) {
        updateState { copy(pluginRequiringBypass = null) }
        viewModelScope.launch {
            try {
                val jarFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (jarFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                        ExtensionLoader.loadAndInit(jarFile, forceBypassSecurity = true)
                    }
                    refreshInstalled()
                    DesktopRepositoryManager.incrementSyncGeneration()
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                try {
                    val jarFile = java.io.File(DesktopRepositoryManager.getExtensionsDir(), "${repoName.replace(" ", "_")}/${plugin.internalName}.jar")
                    if (jarFile.exists()) {
                        ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun clearBypass() {
        updateState { copy(pluginRequiringBypass = null) }
    }

    private fun grantPermissionAndInstall(repoName: String, plugin: SitePlugin, permissionName: String) {
        updateState { copy(pluginRequiringPermission = null) }
        com.lagradost.runtime.permission.PluginPermissionAPI.grantPermission(plugin.internalName, permissionName)
        installPlugin(repoName, plugin) {}
    }

    private fun clearPermissionRequest() {
        updateState { copy(pluginRequiringPermission = null) }
    }

    private fun uninstallPlugins(plugins: List<LocalPlugin>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (plugin in plugins) {
                try {
                    ExtensionLoader.unloadPlugin(plugin.file.absolutePath)

                    System.gc()
                    kotlinx.coroutines.delay(100)

                    val parentDir = plugin.file.parentFile
                    val jvmFile = java.io.File(parentDir, plugin.file.nameWithoutExtension + "-jvm.jar")
                    val dexFile = java.io.File(parentDir, plugin.file.nameWithoutExtension + ".dex")

                    val cs3Deleted = plugin.file.delete()
                    if (!cs3Deleted) plugin.file.deleteOnExit()

                    val jvmDeleted = jvmFile.delete()
                    if (!jvmDeleted && jvmFile.exists()) jvmFile.deleteOnExit()

                    val dexDeleted = dexFile.delete()
                    if (!dexDeleted && dexFile.exists()) dexFile.deleteOnExit()

                    com.lagradost.common.logging.AppLogger.i("Uninstalled ${plugin.name}: cs3=$cs3Deleted, jvm=$jvmDeleted, dex=$dexDeleted")

                    if (parentDir != null) {
                        if (parentDir.listFiles()?.isEmpty() == true) {
                            parentDir.delete()
                        } else {
                            parentDir.deleteOnExit()
                        }
                    }
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Error uninstalling plugin", e)
                }
            }
            refreshInstalled()
        }
    }

    private fun uninstallByInternalName(internalName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val installedMatch = uiState.value.installedPlugins.find { it.internalName == internalName }
            if (installedMatch != null) {
                uninstallPlugins(listOf(installedMatch))
            }
        }
    }

    private fun loadLocalPlugin(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetDir = File(DesktopRepositoryManager.getExtensionsDir(), "Local_Sandbox")
            targetDir.mkdirs()
            val targetFile = File(targetDir, file.name)
            file.copyTo(targetFile, overwrite = true)
            try {
                ExtensionLoader.loadAndInit(targetFile)
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Error loading local plugin", e)
            }
            refreshInstalled()
        }
    }
}
