package com.lagradost.cloudstream3.desktop.ui.screens.library.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen

sealed interface LibraryUiEffect : UiEffect {
    data class Navigate(val screen: Screen) : LibraryUiEffect
}
