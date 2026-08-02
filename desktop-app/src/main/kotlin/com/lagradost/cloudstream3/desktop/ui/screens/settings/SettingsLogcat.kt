package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioView

@Composable
fun SettingsLogcat() {
    Box(modifier = Modifier.fillMaxSize()) {
        DevStudioView(
            isDetached = false,
            onClose = { /* Keep embedded in settings */ },
        )
    }
}
