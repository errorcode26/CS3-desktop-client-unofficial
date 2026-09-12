package com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract

import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.LocalPlugin
import java.io.File

sealed interface ExtensionsUiEvent : UiEvent {
    data object OnFetchPlugins : ExtensionsUiEvent
    data object OnLoadPluginsFromManager : ExtensionsUiEvent
    data object OnRefreshInstalled : ExtensionsUiEvent
    data class OnAddRepositoryFromInput(val input: String) : ExtensionsUiEvent
    data object OnSyncAllRepos : ExtensionsUiEvent
    data class OnInspectRepository(val repoName: String?) : ExtensionsUiEvent
    data class OnInstallPlugin(val repoName: String, val plugin: SitePlugin) : ExtensionsUiEvent
    data class OnUninstallPlugins(val plugins: List<LocalPlugin>) : ExtensionsUiEvent
    data class OnUninstallByInternalName(val internalName: String) : ExtensionsUiEvent
    data class OnUninstallPlugin(val repoName: String, val internalName: String) : ExtensionsUiEvent
    data class OnLoadLocalPlugin(val file: File) : ExtensionsUiEvent
    data class OnRemoveRepository(val url: String) : ExtensionsUiEvent
    data object OnClearBypass : ExtensionsUiEvent
    data class OnBypassSecurityAndInstall(val repoName: String, val plugin: SitePlugin) : ExtensionsUiEvent
    data object OnClearPermissionRequest : ExtensionsUiEvent
    data class OnGrantPermissionAndInstall(val repoName: String, val plugin: SitePlugin, val permissionName: String) : ExtensionsUiEvent
    data object OnClearUpdateHistory : ExtensionsUiEvent
    data class OnReloadPluginAfterSettings(val file: File, val pluginName: String) : ExtensionsUiEvent
    data class OnAddStremioAddon(val url: String, val onResult: (Result<com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon>) -> Unit) : ExtensionsUiEvent
    data class OnRemoveStremioAddon(val manifestUrl: String) : ExtensionsUiEvent
    data class OnSetStremioAddonEnabled(val manifestUrl: String, val enabled: Boolean) : ExtensionsUiEvent
    data class OnRefreshStremioAddon(val manifestUrl: String) : ExtensionsUiEvent
    data class OnMoveStremioAddon(val fromIndex: Int, val toIndex: Int) : ExtensionsUiEvent
}
