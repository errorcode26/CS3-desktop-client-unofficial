package com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract

import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.LocalPlugin

data class ExtensionsUiState(
    val isFetching: Boolean = false,
    val isUninstalling: Boolean = false,
    val statusText: String = "Press Sync (sidebar) or Fetch below to load plugins from your repositories.",
    val plugins: List<Pair<String, SitePlugin>> = emptyList(),
    val installedPlugins: List<LocalPlugin> = emptyList(),
    val pluginRequiringBypass: Triple<String, SitePlugin, String>? = null,
    val pluginRequiringPermission: Triple<String, SitePlugin, String>? = null,
    val inspectedRepoName: String? = null,
    val savedRepositories: List<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData> = emptyList(),
    val remotePluginIcons: Map<String, String?> = emptyMap(),
    val syncGeneration: Int = 0,
    val extensionsDir: java.io.File = java.io.File("."),
    val isSyncing: Boolean = false,
    val installingPlugins: Set<String> = emptySet(),
    val isDialogInstalling: Boolean = false,
) : UiState
