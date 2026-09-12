package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.plugins.interactor.*
import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiState
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class ExtensionsViewModel(
    private val pluginRepo: PluginRepository = AppContainerHolder.container.pluginRepository,
    private val getPluginRepositories: GetPluginRepositories = GetPluginRepositories(pluginRepo),
    private val addPluginRepository: AddPluginRepository = AddPluginRepository(pluginRepo),
    private val removePluginRepository: RemovePluginRepository = RemovePluginRepository(pluginRepo),
    private val getAvailablePlugins: GetAvailablePlugins = GetAvailablePlugins(pluginRepo),
    private val installPluginUseCase: InstallPlugin = InstallPlugin(pluginRepo),
    private val syncPluginRepositories: SyncPluginRepositories = SyncPluginRepositories(pluginRepo),
    private val getPluginIconUseCase: GetPluginIcon = GetPluginIcon(pluginRepo),
) : BaseMviViewModel<ExtensionsUiState, ExtensionsUiEvent, ExtensionsUiEffect>(
    initialState = ExtensionsUiState(),
) {
    init {
        viewModelScope.launch {
            getPluginRepositories.subscribe().collect { repos ->
                updateState { copy(savedRepositories = repos) }
            }
        }
        viewModelScope.launch {
            getPluginIconUseCase.subscribeIcons().collect { icons ->
                updateState { copy(remotePluginIcons = icons) }
            }
        }
        viewModelScope.launch {
            pluginRepo.syncGeneration.collect { gen ->
                val allPlugins = getAvailablePlugins.get()
                updateState { copy(syncGeneration = gen, plugins = allPlugins) }
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
            is ExtensionsUiEvent.OnInstallPlugin -> installPlugin(event.repoName, event.plugin)
            is ExtensionsUiEvent.OnUninstallPlugins -> uninstallPlugins(event.plugins)
            is ExtensionsUiEvent.OnUninstallByInternalName -> uninstallByInternalName(event.internalName)
            is ExtensionsUiEvent.OnUninstallPlugin -> uninstallPlugin(event.repoName, event.internalName)
            is ExtensionsUiEvent.OnLoadLocalPlugin -> loadLocalPlugin(event.file)
            is ExtensionsUiEvent.OnRemoveRepository -> removeRepository(event.url)
            is ExtensionsUiEvent.OnClearBypass -> clearBypass()
            is ExtensionsUiEvent.OnBypassSecurityAndInstall -> bypassSecurityAndInstall(event.repoName, event.plugin)
            is ExtensionsUiEvent.OnClearPermissionRequest -> clearPermissionRequest()
            is ExtensionsUiEvent.OnGrantPermissionAndInstall -> grantPermissionAndInstall(event.repoName, event.plugin, event.permissionName)
            is ExtensionsUiEvent.OnAddRepositoryFromInput -> addRepositoryFromInput(event.input)
            is ExtensionsUiEvent.OnSyncAllRepos -> syncAllRepos()
            is ExtensionsUiEvent.OnClearUpdateHistory -> clearUpdateHistory()
            is ExtensionsUiEvent.OnAddStremioAddon -> addStremioAddon(event.url, event.onResult)
            is ExtensionsUiEvent.OnRemoveStremioAddon -> removeStremioAddon(event.manifestUrl)
            is ExtensionsUiEvent.OnSetStremioAddonEnabled -> setStremioAddonEnabled(event.manifestUrl, event.enabled)
            is ExtensionsUiEvent.OnRefreshStremioAddon -> refreshStremioAddon(event.manifestUrl)
            is ExtensionsUiEvent.OnMoveStremioAddon -> moveStremioAddon(event.fromIndex, event.toIndex)
        }
    }

    private fun addRepositoryFromInput(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val addedRepos = addPluginRepository.await(input)
                if (!addedRepos.isNullOrEmpty()) {
                    val repoNames = addedRepos.take(2).joinToString { it.name } + if (addedRepos.size > 2) " and ${addedRepos.size - 2} more" else ""
                    val allPlugins = getAvailablePlugins.get()
                    updateState { copy(plugins = allPlugins, statusText = "Added ${addedRepos.size} repository(s): $repoNames.") }
                    refreshInstalled()
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
            removePluginRepository.await(url)
            pluginRepo.incrementSyncGeneration()
        }
    }

    private fun syncAllRepos() {
        viewModelScope.launch {
            updateState { copy(isSyncing = true, isFetching = true, statusText = "Syncing repositories...") }
            try {
                withContext(Dispatchers.IO) {
                    syncPluginRepositories.await { done, total ->
                        val currentPlugins = getAvailablePlugins.get()
                        updateState {
                            copy(
                                plugins = currentPlugins,
                                statusText = "Syncing repositories ($done/$total)...",
                            )
                        }
                    }
                }
                val allPlugins = getAvailablePlugins.get()
                updateState { copy(plugins = allPlugins, statusText = "Sync completed successfully.") }
            } catch (e: Throwable) {
                updateState { copy(statusText = "Error syncing: ${e.message}") }
            } finally {
                updateState { copy(isSyncing = false, isFetching = false) }
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
                    syncPluginRepositories.await { done, total ->
                        val currentPlugins = getAvailablePlugins.get()
                        updateState {
                            copy(
                                plugins = currentPlugins,
                                statusText = "Fetching repositories ($done/$total)...",
                            )
                        }
                    }
                }
                val allPlugins = getAvailablePlugins.get()
                val text = "Fetched ${allPlugins.size} plugins from ${getPluginRepositories.get().size} repositories."
                updateState { copy(plugins = allPlugins, statusText = text) }
            } catch (e: Throwable) {
                updateState { copy(statusText = "Error: ${e.message}") }
            } finally {
                updateState { copy(isFetching = false) }
            }
        }
    }

    private fun loadPluginsFromManager() {
        val allPlugins = getAvailablePlugins.get()
        val text = "Showing ${allPlugins.size} plugins from ${getPluginRepositories.get().size} repositories."
        updateState { copy(plugins = allPlugins, statusText = text) }
    }

    private fun refreshInstalled() {
        val list = mutableListOf<LocalPlugin>()
        val extensionsDir = pluginRepo.getExtensionsDir()
        val allRemote = getAvailablePlugins.get()
        val savedRepos = getPluginRepositories.get()
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
                    val manifest = pluginRepo.readPluginManifest(jar)
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

    private fun installPlugin(repoName: String, plugin: SitePlugin) {
        viewModelScope.launch {
            updateState { copy(installingPlugins = installingPlugins + plugin.internalName) }
            val repoCleanName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetDir = File(pluginRepo.getExtensionsDir(), repoCleanName)
            val jarFile = File(targetDir, "${plugin.internalName}.jar")
            val jvmJarFile = File(targetDir, "${plugin.internalName}-jvm.jar")
            val dexFile = File(targetDir, "${plugin.internalName}.dex")

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
                    installPluginUseCase.await(repoName, plugin)
                }
                if (downloadedFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(downloadedFile.absolutePath)
                        ExtensionLoader.loadAndInit(downloadedFile)
                    }
                    refreshInstalled()
                    pluginRepo.incrementSyncGeneration()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                        "Installed '${plugin.name}' (v${plugin.version})"
                    )
                } else {
                    cleanupFailedArtifacts()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Failed to download '${plugin.name}': Network or server error"
                    )
                }
            } catch (e: com.lagradost.runtime.security.RequiresPermissionException) {
                com.lagradost.common.logging.AppLogger.e("Permission required for plugin", e)
                updateState { copy(pluginRequiringPermission = Triple(repoName, plugin, e.permissionName)) }
            } catch (e: java.lang.SecurityException) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Security notice installing plugin", e)
                val reason = e.message ?: "Suspicious bytecode or unverified class access detected."
                updateState { copy(pluginRequiringBypass = Triple(repoName, plugin, reason)) }
            } catch (e: Throwable) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                    "Failed to install '${plugin.name}': ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                updateState { copy(installingPlugins = installingPlugins - plugin.internalName) }
            }
        }
    }

    private fun bypassSecurityAndInstall(repoName: String, plugin: SitePlugin) {
        viewModelScope.launch {
            updateState { copy(isDialogInstalling = true, installingPlugins = installingPlugins + plugin.internalName) }
            val repoCleanName = repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetDir = File(pluginRepo.getExtensionsDir(), repoCleanName)
            val jarFile = File(targetDir, "${plugin.internalName}.jar")
            val jvmJarFile = File(targetDir, "${plugin.internalName}-jvm.jar")
            val dexFile = File(targetDir, "${plugin.internalName}.dex")

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
                    installPluginUseCase.await(repoName, plugin)
                }
                if (downloadedFile != null) {
                    withContext(Dispatchers.IO) {
                        ExtensionLoader.unloadPlugin(downloadedFile.absolutePath)
                        ExtensionLoader.loadAndInit(downloadedFile, forceBypassSecurity = true)
                    }
                    refreshInstalled()
                    pluginRepo.incrementSyncGeneration()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                        "Trusted and installed '${plugin.name}'"
                    )
                } else {
                    cleanupFailedArtifacts()
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                        "Failed to download '${plugin.name}'"
                    )
                }
            } catch (e: Throwable) {
                cleanupFailedArtifacts()
                com.lagradost.common.logging.AppLogger.e("Error loading plugin", e)
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError(
                    "Failed to load '${plugin.name}': ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                updateState { copy(pluginRequiringBypass = null, isDialogInstalling = false, installingPlugins = installingPlugins - plugin.internalName) }
            }
        }
    }

    private fun clearBypass() {
        updateState { copy(pluginRequiringBypass = null, isDialogInstalling = false) }
    }

    private fun grantPermissionAndInstall(repoName: String, plugin: SitePlugin, permissionName: String) {
        com.lagradost.runtime.permission.PluginPermissionAPI.grantPermission(plugin.internalName, permissionName)
        updateState { copy(pluginRequiringPermission = null) }
        installPlugin(repoName, plugin)
    }

    private fun clearPermissionRequest() {
        updateState { copy(pluginRequiringPermission = null, isDialogInstalling = false) }
    }

    private fun uninstallPlugins(plugins: List<LocalPlugin>) {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isUninstalling = true) }
            for (plugin in plugins) {
                try {
                    // Step 1: Gracefully unload (calls beforeUnload, removes providers from APIHolder).
                    ExtensionLoader.unloadPlugin(plugin.file.absolutePath)

                    // Step 2: Force JVM to release native Windows file handles.
                    @Suppress("ExplicitGarbageCollectionCall")
                    System.gc()
                    delay(150)
                    @Suppress("deprecation")
                    System.runFinalization()

                    // Step 3: Check if this plugin owned the active provider.
                    val pluginProviders = com.lagradost.cloudstream3.APIHolder.allProviders
                        .filter { it.sourcePlugin == plugin.file.absolutePath }
                        .map { it.name }
                    val activeKeys = com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.activeProviderKeys.value
                    val updatedKeys = activeKeys.filter { key -> !pluginProviders.any { p -> key.contains(p) } }
                    if (updatedKeys.size != activeKeys.size) {
                        com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.setActiveProviders(updatedKeys)
                    }

                    // Step 4: Delete ONLY this plugin's own files.
                    val stem = plugin.file.nameWithoutExtension
                    val parentDir = plugin.file.parentFile
                    val filesToDelete = listOfNotNull(
                        plugin.file,
                        parentDir?.let { File(it, "$stem-jvm.jar") },
                        parentDir?.let { File(it, "$stem.dex") },
                        parentDir?.let { File(it, "$stem-secure.jar") },
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
            pluginRepo.incrementSyncGeneration()
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
            val targetDir = File(pluginRepo.getExtensionsDir(), "Local_Sandbox")
            targetDir.mkdirs()
            val targetFile = File(targetDir, file.name)
            file.copyTo(targetFile, overwrite = true)
            try {
                ExtensionLoader.loadAndInit(targetFile)
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Error loading local plugin", e)
            }
            refreshInstalled()
            pluginRepo.incrementSyncGeneration()
        }
    }

    private fun clearUpdateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.clearUpdatesHistory()
        }
    }

    private fun addStremioAddon(url: String, onResult: (Result<com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.addAddon(url)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    private fun removeStremioAddon(manifestUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.removeAddon(manifestUrl)
        }
    }

    private fun setStremioAddonEnabled(manifestUrl: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.setAddonEnabled(manifestUrl, enabled)
        }
    }

    private fun refreshStremioAddon(manifestUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.refreshAddon(manifestUrl)
        }
    }

    private fun moveStremioAddon(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager.moveAddon(fromIndex, toIndex)
        }
    }
}
