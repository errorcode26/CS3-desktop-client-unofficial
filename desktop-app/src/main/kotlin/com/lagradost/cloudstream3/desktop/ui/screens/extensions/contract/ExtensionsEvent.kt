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
    data class OnInstallPlugin(val repoName: String, val plugin: SitePlugin, val onResult: (String) -> Unit) : ExtensionsUiEvent
    data class OnUninstallPlugins(val plugins: List<LocalPlugin>) : ExtensionsUiEvent
    data class OnUninstallByInternalName(val internalName: String) : ExtensionsUiEvent
    data class OnLoadLocalPlugin(val file: File) : ExtensionsUiEvent
    data class OnRemoveRepository(val url: String) : ExtensionsUiEvent
    data object OnClearBypass : ExtensionsUiEvent
    data class OnBypassSecurityAndInstall(val repoName: String, val plugin: SitePlugin) : ExtensionsUiEvent
    data object OnClearPermissionRequest : ExtensionsUiEvent
    data class OnGrantPermissionAndInstall(val repoName: String, val plugin: SitePlugin, val permissionName: String) : ExtensionsUiEvent
}
