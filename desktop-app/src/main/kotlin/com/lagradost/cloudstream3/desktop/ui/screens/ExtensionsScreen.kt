package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.runtime.Composable
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController

@Composable
fun ComposeExtensionScreen(navController: NavController, initialTab: Int = 0) {
    com.lagradost.cloudstream3.desktop.ui.screens.extensions.ComposeExtensionScreen(navController, initialTab)
}
