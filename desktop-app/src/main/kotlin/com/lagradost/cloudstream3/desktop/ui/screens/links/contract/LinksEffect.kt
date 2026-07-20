package com.lagradost.cloudstream3.desktop.ui.screens.links.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect

sealed interface LinksUiEffect : UiEffect {
    data class ShowToast(val message: String) : LinksUiEffect
}
