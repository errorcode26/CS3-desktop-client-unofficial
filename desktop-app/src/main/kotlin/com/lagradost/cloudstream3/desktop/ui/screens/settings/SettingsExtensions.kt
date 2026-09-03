package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ComposeExtensionScreen
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ExtensionsViewModel

@Composable
fun SettingsExtensions(
    onNavigate: (Config) -> Unit = {},
    initialTab: Int = 0,
    viewModel: ExtensionsViewModel = remember { ExtensionsViewModel() },
) {
    ComposeExtensionScreen(
        onNavigate = onNavigate,
        initialTab = initialTab,
        viewModel = viewModel,
        isInsideSettings = true,
    )
}

@Composable
fun SettingsPluginsAndAddonsScreen(
    onNavigate: (Config) -> Unit = {},
) {
    SettingsExtensions(onNavigate = onNavigate)
}
