package com.lagradost.cloudstream3.desktop.ui.screens.home.contract

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory

data class HomeUiState(
    val providers: List<MainAPI> = emptyList(),
    val activeProviders: List<String> = emptyList(),

    val errorSnapshot: String = DesktopErrorReporter.getSnapshot(),
    val historyList: List<WatchHistory> = emptyList(),
    val mergedPluginIcons: Map<String, String> = emptyMap(),
    val heroMetaMap: Map<String, HeroMeta> = emptyMap(),
    val bookmarks: Map<String, DesktopBookmark> = emptyMap(),
    val disabledCatalogs: Map<String, Set<String>> = emptyMap(),
    val showHomeManagement: Boolean = false,
) : UiState {
    val activeProviderApis: List<MainAPI>
        get() = activeProviders.mapNotNull { name -> providers.firstOrNull { it.name == name } }
}
