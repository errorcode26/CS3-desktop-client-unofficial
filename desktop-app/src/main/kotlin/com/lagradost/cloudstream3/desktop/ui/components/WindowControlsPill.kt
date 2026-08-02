package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState

@Composable
fun WindowControlsPill(
    isHome: Boolean = false,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
) {
    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

    val theme = LocalDesktopTheme.current

    // Fetch provider states for the global pill
    val providers = homeUiState?.providers ?: emptyList()
    val activeProviders = homeUiState?.activeProviders ?: emptyList()
    val mergedPluginIcons = homeUiState?.mergedPluginIcons ?: emptyMap()
    
    val providerText = when {
        activeProviders.isEmpty() -> "Select Provider"
        activeProviders.size == 1 -> activeProviders.first()
        else -> "Mixed Feed"
    }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return mergedPluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    Surface(
        shape = CircleShape,
        color = theme.SurfaceElevated.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
        shadowElevation = 8.dp.applyShadowMultiplier(), // Always use elevated shadow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            if (isHome && providers.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnShowHomeManagement(true)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Providers",
                        tint = theme.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = providerText,
                        color = theme.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(theme.Divider),
                )
            }

            IconButton(
                onClick = {
                    if (fullscreenController != null) {
                        fullscreenController.toggle()
                    } else {
                        windowState?.placement = if (isFullscreen) {
                            androidx.compose.ui.window.WindowPlacement.Floating
                        } else {
                            androidx.compose.ui.window.WindowPlacement.Fullscreen
                        }
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = theme.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
