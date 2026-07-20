package com.lagradost.cloudstream3.desktop.ui.screens.home.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect

sealed interface HomeUiEffect : UiEffect {
    data class ShowToast(val message: String) : HomeUiEffect
}
