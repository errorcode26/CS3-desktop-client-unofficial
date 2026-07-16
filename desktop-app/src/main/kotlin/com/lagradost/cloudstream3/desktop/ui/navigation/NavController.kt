package com.lagradost.cloudstream3.desktop.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavController {
    var currentScreen: Screen by mutableStateOf(Screen.Home)
        private set

    private val backStack = mutableListOf<Screen>()

    fun navigate(screen: Screen) {
        // Don't push duplicate top-level screens onto the stack
        if (screen == currentScreen) return
        backStack.add(currentScreen)
        currentScreen = screen
    }

    fun goBack() {
        currentScreen = backStack.removeLastOrNull() ?: Screen.Home
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty()
}
