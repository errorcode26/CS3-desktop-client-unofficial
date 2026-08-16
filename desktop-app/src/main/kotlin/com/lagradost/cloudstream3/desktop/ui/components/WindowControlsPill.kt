package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    val refreshRotation = remember { Animatable(0f) }

    // Fetch provider states for the global pill
    val providers = homeUiState?.providers ?: emptyList()
    val activeProviders = homeUiState?.activeProviders ?: emptyList()
    val mergedPluginIcons = homeUiState?.mergedPluginIcons ?: emptyMap()

    // Resolve logo URL for the single active provider, if any
    val activeIconUrl: String? = if (activeProviders.size == 1) {
        val pName = activeProviders.first().lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        mergedPluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            kName.length >= 3 && pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    } else {
        null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isHome && providers.isNotEmpty()) {
            // 1. Refresh Button Pill (BEFORE the selector)
            Surface(
                shape = CircleShape,
                color = theme.SurfaceElevated.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                shadowElevation = 8.dp.applyShadowMultiplier(),
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            refreshRotation.snapTo(0f)
                            refreshRotation.animateTo(360f, animationSpec = tween(600))
                        }
                        homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnProviderRefresh)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Home",
                        tint = theme.TextPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(refreshRotation.value),
                    )
                }
            }

            // 2. Provider Selector Pill (with Logo + Name)
            val displayText = when {
                activeProviders.size == 1 -> activeProviders.first()
                activeProviders.size > 1 -> "Multi-Provider"
                else -> "Select Provider"
            }

            Surface(
                shape = CircleShape,
                color = theme.SurfaceElevated.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                shadowElevation = 8.dp.applyShadowMultiplier(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnShowHomeManagement(true)) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    if (activeIconUrl != null) {
                        coil3.compose.AsyncImage(
                            model = activeIconUrl,
                            contentDescription = "Provider Logo",
                            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = "Providers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }

        // 3. Disconnected Fullscreen Pill
        Surface(
            shape = CircleShape,
            color = theme.SurfaceElevated.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
            shadowElevation = 8.dp.applyShadowMultiplier(),
        ) {
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
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = theme.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
