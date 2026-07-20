package com.lagradost.cloudstream3.desktop.ui.screens.home.contract

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory

data class HomeUiState(
    val providers: List<MainAPI> = emptyList(),
    val selectedProviderName: String? = null,
    val searchResultsGrouped: List<Pair<MainAPI, List<SearchResponse>>>? = null,
    val isLoadingSearch: Boolean = false,
    val searchQuery: String = "",
    val isGlobalSearchEnabled: Boolean = false,
    val errorSnapshot: String = DesktopErrorReporter.getSnapshot(),
    val historyList: List<WatchHistory> = emptyList(),
    val mergedPluginIcons: Map<String, String> = emptyMap(),
    val heroMetaMap: Map<String, HeroMeta> = emptyMap(),
    val heroExtractedColor: Color? = null,
    val heroColorMap: Map<String, Color> = emptyMap(),
    val bookmarks: Map<String, DesktopBookmark> = emptyMap(),
) : UiState {
    val selectedProvider: MainAPI?
        get() = providers.firstOrNull { it.name == selectedProviderName }
}
