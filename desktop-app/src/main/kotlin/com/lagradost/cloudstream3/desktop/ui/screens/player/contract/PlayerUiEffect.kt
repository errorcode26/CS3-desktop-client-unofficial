package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect

sealed interface PlayerUiEffect : UiEffect {
    data class ShowToast(val message: String) : PlayerUiEffect
}
