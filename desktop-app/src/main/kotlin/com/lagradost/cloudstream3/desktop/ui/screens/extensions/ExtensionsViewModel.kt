package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiState
import com.lagradost.cloudstream3.desktop.ui.screens.home.PREF_ACTIVE_PROVIDERS
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
        // Immediately populate both the Catalog and Installed tabs from local cache (0ms instant render)
        viewModelScope.launch(Dispatchers.IO) {
            loadPluginsFromManager()
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
            is ExtensionsUiEvent.OnUninstallPlugin -> uninstallPlugin(event.repoName, event.internalName)
            is ExtensionsUiEvent.OnLoadLocalPlugin -> loadLocalPlugin(event.file)
            is ExtensionsUiEvent.OnRemoveRepository -> removeRepository(event.url)
            is ExtensionsUiEvent.OnClearBypass -> clearBypass()
            is ExtensionsUiEvent.OnBypassSecurityAndInstall -> bypassSecurityAndInstall(event.repoName, event.plugin, event.onResult)
            is ExtensionsUiEvent.OnClearPermissionRequest -> clearPermissionRequest()
            is ExtensionsUiEvent.OnGrantPermissionAndInstall -> grantPermissionAndInstall(event.repoName, event.plugin, event.permissionName, event.onResult)
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
                    val allPlugins = DesktopRepositoryManager.getAllPlugins()
                    updateState { copy(plugins = allPlugins, statusText = "Added ${addedRepos.size} repository(s): $repoNames.") }
                    refreshInstalled()
                    DesktopRepositoryManager.incrementSyncGeneration()
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
                withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.syncAll { done, total ->
                        val currentPlugins = DesktopRepositoryManager.getAllPlugins()
                        updateState {
                            copy(
                                plugins = currentPlugins,
                                statusText = "Syncing repositories ($done/$total)...",
                            )
                        }
                    }
                }
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
                    DesktopRepositoryManager.syncAll { done, total ->
                        val currentPlugins = DesktopRepositoryManager.getAllPlugins()
                        updateState {
                            copy(
                                plugins = currentPlugins,
                                statusText = "Fetching repositories ($done/$total)...",
                            )
                        }
                    }
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
        val savedRepos = DesktopRepositoryManager.getSavedRepositories()
        if (extensionsDir.exists()) {
            extensionsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
                .filter {
                    !it.name.endsWith("-jvm.jar") &&
                    !it.name.contains("-secure") &&
                    !it.name.contains("-jvm") &&
                    !it.name.endsWith(".dex")
                }
                .distinctBy { it.nameWithoutExtension.substringBefore("-jvm").substringBefore("-secure") }
                .forEach { jar ->
                    val manifest = DesktopRepositoryManager.readPluginManifest(jar)
                    val name = manifest?.get("name") as? String ?: jar.nameWithoutExtension
                    val internalName = manifest?.get("internalName") as? String ?: name
                    val version = manifest?.get("version")?.toString()?.toIntOrNull() ?: 0
                    val iconUrl = manifest?.get("iconUrl") as? String

                    // Exact repository matching based on folder structure on disk
                    val folderName = jar.parentFile?.name ?: ""
                    val matchingSavedRepo = savedRepos.find {
                        val cleanName = it.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        cleanName.equals(folderName, ignoreCase = true)
                    }
                    val remoteMatch = allRemote.find { (rName, p) ->
                        val cleanRName = rName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        p.internalName == internalName && cleanRName.equals(folderName, ignoreCase = true)
                    }

                    val repoName = matchingSavedRepo?.name
                        ?: remoteMatch?.first
                        ?: folderName.replace("_", " ").ifBlank { "Local" }

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
            val repoCleanName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetDir = java.io.File(DesktopRepositoryManager.getExtensionsDir(), repoCleanName)
            val jarFile = java.io.File(targetDir, "${plugin.internalName}.jar")
            val jvmJarFile = java.io.File(targetDir, "${plugin.internalName}-jvm.jar")
            val dexFile = java.io.File(targetDir, "${plugin.internalName}.dex")

            val cleanupFailedArtifacts = {
                try {
                    ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                    if (jarFile.exists()) jarFile.delete()
                    if (jvmJarFile.exists()) jvmJarFile.delete()
                    if (dexFile.exists()) dexFile.delete()
                } catch (_: Throwable) {}
            }

            try {
                val downloadedFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (downloadedFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(downloadedFile.absolutePath)
                        ExtensionLoader.loadAndInit(downloadedFile)
                    }
                    onResult("Installed")
                    refreshInstalled()
                    DesktopRepositoryManager.incrementSyncGeneration()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                        "Installed '${plugin.name}' (v${plugin.version})"
                    )
                } else {
                    cleanupFailedArtifacts()
                    onResult("Failed")
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Failed to download '${plugin.name}': Network or server error"
                    )
                }
            } catch (e: com.lagradost.runtime.security.RequiresPermissionException) {
                com.lagradost.common.logging.AppLogger.e("Permission required for plugin", e)
                updateState { copy(pluginRequiringPermission = Triple(repoName, plugin, e.permissionName)) }
                onResult("Requires Permission")
            } catch (e: java.lang.SecurityException) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Security notice installing plugin", e)
                val reason = e.message ?: "Suspicious bytecode or unverified class access detected."
                updateState { copy(pluginRequiringBypass = Triple(repoName, plugin, reason)) }
                onResult("")
            } catch (e: Throwable) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                onResult("Error")
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                    "Failed to install '${plugin.name}': ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private fun bypassSecurityAndInstall(repoName: String, plugin: SitePlugin, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val repoCleanName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetDir = java.io.File(DesktopRepositoryManager.getExtensionsDir(), repoCleanName)
            val jarFile = java.io.File(targetDir, "${plugin.internalName}.jar")
            val jvmJarFile = java.io.File(targetDir, "${plugin.internalName}-jvm.jar")
            val dexFile = java.io.File(targetDir, "${plugin.internalName}.dex")

            val cleanupFailedArtifacts = {
                try {
                    ExtensionLoader.unloadPlugin(jarFile.absolutePath)
                    if (jarFile.exists()) jarFile.delete()
                    if (jvmJarFile.exists()) jvmJarFile.delete()
                    if (dexFile.exists()) dexFile.delete()
                } catch (_: Throwable) {}
            }

            try {
                // Persist trust with repository namespacing and all alias variants
                ExtensionLoader.addTrusted(jarFile, plugin.internalName, manifestName = plugin.name)

                val downloadedFile = withContext(Dispatchers.IO) {
                    DesktopRepositoryManager.downloadPlugin(repoName, plugin)
                }
                if (downloadedFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(downloadedFile.absolutePath)
                        ExtensionLoader.loadAndInit(downloadedFile, forceBypassSecurity = true)
                    }
                    onResult("Installed")
                    refreshInstalled()
                    DesktopRepositoryManager.incrementSyncGeneration()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                        "Trusted and installed '${plugin.name}'"
                    )
                } else {
                    cleanupFailedArtifacts()
                    onResult("Failed")
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Failed to download '${plugin.name}'"
                    )
                }
            } catch (e: Throwable) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                onResult("Error")
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                    "Failed to load '${plugin.name}': ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                updateState { copy(pluginRequiringBypass = null) }
            }
        }
    }

    private fun clearBypass() {
        updateState { copy(pluginRequiringBypass = null) }
    }

    private fun grantPermissionAndInstall(repoName: String, plugin: SitePlugin, permissionName: String, onResult: (String) -> Unit) {
        com.lagradost.runtime.permission.PluginPermissionAPI.grantPermission(plugin.internalName, permissionName)
        installPlugin(repoName, plugin) { result ->
            onResult(result)
            updateState { copy(pluginRequiringPermission = null) }
        }
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
                    val activeProviders = DesktopDataStore.getKey<List<String>>(PREF_ACTIVE_PROVIDERS)
                    val activeProvider = activeProviders?.firstOrNull()
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

                    // Step 5: Revoke persistent trust so future fresh re-installs require re-verification
                    ExtensionLoader.removeTrusted(plugin.file, plugin.internalName, manifestName = plugin.name)

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

    private fun uninstallPlugin(repoName: String, internalName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanRepo = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val installedMatch = uiState.value.installedPlugins.find {
                it.internalName == internalName && (
                    it.file.parentFile?.name?.equals(cleanRepo, ignoreCase = true) == true ||
                    it.repoName.equals(repoName, ignoreCase = true)
                )
            }
            if (installedMatch != null) {
                uninstallPlugins(listOf(installedMatch))
            }
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
