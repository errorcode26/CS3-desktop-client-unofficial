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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.profile.Profile
import com.lagradost.cloudstream3.desktop.profile.ProfileManager
import com.lagradost.cloudstream3.desktop.profile.ProfilePalette
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalWindowState
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.TopBarProviderStyle
import kotlinx.coroutines.launch

@Composable
fun WindowControlsPill(
    isHome: Boolean = false,
    isCompact: Boolean = false,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
) {
    val windowState = LocalWindowState.current
    val fullscreenController = LocalFullscreenController.current
    val isFullscreen = fullscreenController?.isFullscreen ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

    val theme = LocalDesktopTheme.current
    val coroutineScope = rememberCoroutineScope()
    val refreshRotation = remember { Animatable(0f) }
    val topBarProviderStyle by AppearanceConfig.topBarProviderStyle.collectAsState()

    // Fetch provider states for the global pill
    val providers = homeUiState?.providers ?: emptyList()
    val activeProviders = homeUiState?.activeProviders ?: emptyList()
    val mergedPluginIcons = homeUiState?.mergedPluginIcons ?: emptyMap()

    // Resolve logo URL for the single active provider, if any
    val activeIconUrl: String? = if (activeProviders.size == 1) {
        val pRaw = activeProviders.first()
        val pClean = pRaw.substringAfter("::")
        DesktopRepositoryManager.getPluginIcon(pClean)
            ?: DesktopRepositoryManager.getPluginIcon(pRaw)
            ?: run {
                val pName = pClean.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
                mergedPluginIcons.entries.firstOrNull { (k, _) ->
                    val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
                    kName.length >= 3 && pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
                }?.value
            }
    } else {
        null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (isHome && providers.isNotEmpty()) {
            if (!isCompact) {
                // 1. Refresh Button Pill (Desktop only)
                Surface(
                    shape = RoundedCornerShape(10.dp),
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
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Home",
                        tint = theme.TextPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(refreshRotation.value),
                    )
                }
            }
        }

            // 2. Provider Selector Pill (with Logo + Name or Icon Only)
            val activeApis = homeUiState?.activeProviderApis ?: emptyList()
            val displayText = when {
                activeApis.size == 1 -> {
                    val single = activeApis.first()
                    val isDuplicate = providers.count { it.name == single.name } > 1
                    if (isDuplicate) {
                        val repo = single.sourcePlugin?.let { java.io.File(it).parentFile?.name?.replace("_", " ") }
                        if (!repo.isNullOrBlank()) "${single.name} ($repo)" else single.name
                    } else {
                        single.name
                    }
                }
                activeProviders.size == 1 -> activeProviders.first().substringAfter("::")
                activeProviders.size > 1 -> "Multi-Provider"
                else -> "Select Provider"
            }

            val isIconOnly = topBarProviderStyle == TopBarProviderStyle.ICON_ONLY
            val resolvedIcon = activeIconUrl ?: DesktopRepositoryManager.getPluginIcon(displayText)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.SurfaceElevated.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                shadowElevation = 8.dp.applyShadowMultiplier(),
            ) {
                if (isIconOnly) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnShowHomeManagement(true)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (resolvedIcon != null) {
                            coil3.compose.AsyncImage(
                                model = resolvedIcon,
                                contentDescription = displayText,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        } else if (displayText != "Select Provider") {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = displayText.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        } else {
                            Icon(
                                PremiumIcons.Extensions,
                                contentDescription = displayText,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { homeActionDispatcher?.invoke(com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent.OnShowHomeManagement(true)) }
                            .padding(horizontal = 14.dp),
                    ) {
                        if (resolvedIcon != null) {
                            coil3.compose.AsyncImage(
                                model = resolvedIcon,
                                contentDescription = "Provider Logo",
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    PremiumIcons.Extensions,
                                    contentDescription = "Providers",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(9.dp))

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.TextPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Fullscreen handled natively via F11, video player gesture & controls
    }
}
