package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.WatchHistory

data class DetailsUiState(
    val response: LoadResponse? = null,
    val fakeData: LoadResponse? = null,
    val isLoading: Boolean = true,
    val fetchFailed: Boolean = false,
    val errorMessage: String? = null,
    val watchHistory: Map<String, WatchHistory> = emptyMap(),
    val activeLinkData: Triple<MainAPI, String, WatchHistory>? = null,
    val isPanelOpen: Boolean = false,
    val screenshots: List<String>? = null,
    val enrichmentTrigger: Int = 0,
    val enrichedLogoUrl: String? = null,
    val enrichedBackdropUrl: String? = null,
    val enrichedTagline: String? = null,
    val enrichedStatus: String? = null,
    val enrichedStudios: List<String> = emptyList(),
    val enrichedCollectionName: String? = null,
    val enrichedCollectionBackdrop: String? = null,
    val enrichedSeasonsCount: Int? = null,
    val enrichedEpisodesCount: Int? = null,
    val enrichedOriginalLanguage: String? = null,
    val enrichedReleaseDate: String? = null,
    val enrichedCountry: String? = null,
    val enrichedCollectionItems: List<SearchResponse> = emptyList(),
    val heroColor: Color? = null,
    val isEnriching: Boolean = false,
    val error: String? = null,
    val enrichedBudget: Long? = null,
    val enrichedRevenue: Long? = null,
    val enrichedNetworks: List<String> = emptyList(),
    val bookmarks: Map<String, DesktopBookmark> = emptyMap(),
    val autoPlayEnabled: Boolean = true
) : UiState
