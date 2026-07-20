package com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect

sealed interface ExtensionsUiEffect : UiEffect {
    data class ShowNotification(val message: String) : ExtensionsUiEffect
}
