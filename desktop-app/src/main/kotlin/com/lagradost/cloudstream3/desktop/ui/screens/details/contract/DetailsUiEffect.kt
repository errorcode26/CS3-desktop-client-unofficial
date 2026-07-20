package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect

sealed interface DetailsUiEffect : UiEffect {
    data class ShowToast(val message: String) : DetailsUiEffect
    data class NavigateToPlayer(val launchData: com.lagradost.cloudstream3.desktop.ui.VideoLaunchData) : DetailsUiEffect
    data class ShowErrorDialog(val message: String) : DetailsUiEffect
}
