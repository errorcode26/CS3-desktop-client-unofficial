package com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract

import com.lagradost.cloudstream3.desktop.repo.SitePlugin
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.LocalPlugin

data class ExtensionsUiState(
    val isFetching: Boolean = false,
    val statusText: String = "Press Sync (sidebar) or Fetch below to load plugins from your repositories.",
    val plugins: List<Pair<String, SitePlugin>> = emptyList(),
    val installedPlugins: List<LocalPlugin> = emptyList(),
    val pluginRequiringBypass: Pair<String, SitePlugin>? = null,
    val pluginRequiringPermission: Triple<String, SitePlugin, String>? = null,
    val inspectedRepoName: String? = null,
) : UiState
