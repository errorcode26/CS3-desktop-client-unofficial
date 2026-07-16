package com.lagradost.cloudstream3.desktop.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavController {
    var currentScreen: Screen by mutableStateOf(Screen.Home)
        private set

    enum class NavAction { Push, Pop, Idle }
    var lastAction: NavAction by mutableStateOf(NavAction.Idle)
        private set

    private val backStack = mutableListOf<Screen>()
    private val forwardStack = mutableListOf<Screen>()

    fun navigate(screen: Screen) {
        // Don't push duplicate top-level screens onto the stack
        if (screen == currentScreen) return
        backStack.add(currentScreen)
        forwardStack.clear() // Clear forward stack on new navigation
        currentScreen = screen
        lastAction = NavAction.Push
    }

    fun navigateRoot(screen: Screen) {
        if (screen == currentScreen) return
        backStack.clear()
        forwardStack.clear()
        currentScreen = screen
        lastAction = NavAction.Pop
    }

    fun goBack() {
        val previous = backStack.removeLastOrNull()
        if (previous != null) {
            forwardStack.add(currentScreen)
            currentScreen = previous
            lastAction = NavAction.Pop
        } else if (currentScreen != Screen.Home) {
            forwardStack.add(currentScreen)
            currentScreen = Screen.Home
            lastAction = NavAction.Pop
        }
    }

    fun goForward() {
        val next = forwardStack.removeLastOrNull()
        if (next != null) {
            backStack.add(currentScreen)
            currentScreen = next
            lastAction = NavAction.Push
        }
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty() || currentScreen != Screen.Home
    fun canGoForward(): Boolean = forwardStack.isNotEmpty()
}
