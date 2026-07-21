package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiState
import com.lagradost.cloudstream3.desktop.ui.screens.home.PREF_SELECTED_PROVIDER
import com.lagradost.common.storage.DesktopDataStore
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
    initialState = ExtensionsUiState(),
) {
    init {
        viewModelScope.launch {
            DesktopRepositoryManager.savedRepositories.collect { repos ->
                updateState { copy(savedRepositories = repos) }
            }
        }
        viewModelScope.launch {
            DesktopRepositoryManager.remotePluginIcons.collect { icons ->
                updateState { copy(remotePluginIcons = icons) }
            }
        }
        viewModelScope.launch {
            DesktopRepositoryManager.syncGeneration.collect { gen ->
                updateState { copy(syncGeneration = gen) }
            }
        }
        // Immediately populate the Installed tab on ViewModel creation
        viewModelScope.launch(Dispatchers.IO) {
            refreshInstalled()
        }
    }

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
            is ExtensionsUiEvent.OnClearPermissionRequest -> clearPermissionRequest()
            is ExtensionsUiEvent.OnGrantPermissionAndInstall -> grantPermissionAndInstall(event.repoName, event.plugin, event.permissionName)
            is ExtensionsUiEvent.OnAddRepositoryFromInput -> addRepositoryFromInput(event.input)
            is ExtensionsUiEvent.OnSyncAllRepos -> syncAllRepos()
        }
    }

    private fun addRepositoryFromInput(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val addedRepos = DesktopRepositoryManager.addRepositoryFromInput(input)
                if (addedRepos != null && addedRepos.isNotEmpty()) {
                    val repoNames = addedRepos.take(2).joinToString { it.name } + if (addedRepos.size > 2) " and ${addedRepos.size - 2} more" else ""
                    updateState { copy(statusText = "Added ${addedRepos.size} repository(s): $repoNames. Syncing...") }
                    DesktopRepositoryManager.syncAll()
                    val allPlugins = DesktopRepositoryManager.getAllPlugins()
                    updateState { copy(plugins = allPlugins, statusText = "Repositories added and synced successfully.") }
                } else {
                    updateState { copy(statusText = "Failed to load repository. Check the URL and try again.") }
                }
            } catch (e: Throwable) {
                updateState { copy(statusText = "Error: ${e.message}") }
            }
        }
    }

    private fun removeRepository(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopRepositoryManager.removeRepository(url)
        }
    }

    private fun syncAllRepos() {
        viewModelScope.launch {
            updateState { copy(isFetching = true, statusText = "Syncing repositories...") }
            try {
                withContext(Dispatchers.IO) { DesktopRepositoryManager.syncAll() }
                val allPlugins = DesktopRepositoryManager.getAllPlugins()
                updateState { copy(plugins = allPlugins, statusText = "Sync completed successfully.") }
            } catch (e: Throwable) {
                updateState { copy(statusText = "Error syncing: ${e.message}") }
            } finally {
                updateState { copy(isFetching = false) }
            }
        }
    }

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
                    DesktopRepositoryManager.incrementSyncGeneration()
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
            updateState { copy(isUninstalling = true) }
            for (plugin in plugins) {
                try {
                    // Step 1: Gracefully unload (calls beforeUnload, removes providers from APIHolder).
                    ExtensionLoader.unloadPlugin(plugin.file.absolutePath)

                    // Step 2: Force JVM to release native Windows file handles.
                    // URLClassLoader holds sun.misc.URLClassPath file handles that are only
                    // released after the GC sweeps unreferenced class loaders. We force this
                    // explicitly before attempting file deletion to prevent Windows ACCESS_DENIED.
                    @Suppress("ExplicitGarbageCollectionCall")
                    System.gc()
                    Thread.sleep(150)
                    @Suppress("deprecation")
                    System.runFinalization()

                    // Step 3: Check if this plugin owned the active provider.
                    // Instead of writing to DataStore directly (which would break MVI boundaries),
                    // we fire a ClearActiveProvider effect. The UI layer (ExtensionsScreen) handles
                    // the actual DataStore write, keeping this ViewModel pure.
                    val pluginProviders = com.lagradost.cloudstream3.APIHolder.allProviders
                        .filter { it.sourcePlugin == plugin.file.absolutePath }
                        .map { it.name }
                    val activeProvider = DesktopDataStore.getKey<String>(PREF_SELECTED_PROVIDER)
                    if (activeProvider != null && pluginProviders.contains(activeProvider)) {
                        sendEffect(ExtensionsUiEffect.ClearActiveProvider(activeProvider))
                    }

                    // Step 4: Delete ONLY this plugin's own files.
                    // The extension pack folder (parent dir) belongs to the repo and is NEVER
                    // deleted here — that is the responsibility of extension pack removal only.
                    val stem = plugin.file.nameWithoutExtension
                    val parentDir = plugin.file.parentFile
                    val filesToDelete = listOfNotNull(
                        plugin.file,
                        parentDir?.let { java.io.File(it, "$stem-jvm.jar") },
                        parentDir?.let { java.io.File(it, "$stem.dex") },
                        parentDir?.let { java.io.File(it, "$stem-secure.jar") },
                    )
                    for (f in filesToDelete) {
                        if (f.exists()) {
                            val ok = f.delete()
                            if (!ok) f.deleteOnExit()
                            com.lagradost.common.logging.AppLogger.i("Delete '${f.name}': ok=$ok")
                        }
                    }

                    com.lagradost.common.logging.AppLogger.i("Uninstalled plugin '${plugin.name}' successfully.")
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.e("Error uninstalling plugin '${plugin.name}'", e)
                }
            }
            refreshInstalled()
            DesktopRepositoryManager.incrementSyncGeneration()
            updateState { copy(isUninstalling = false) }
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
            DesktopRepositoryManager.incrementSyncGeneration()
        }
    }
}
