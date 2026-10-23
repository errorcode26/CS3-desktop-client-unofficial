package com.lagradost.cloudstream3.desktop.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName
import com.lagradost.cloudstream3.desktop.ui.theme.buildColorScheme
import com.lagradost.cloudstream3.desktop.ui.theme.buildDesktopColors

/**
 * In-process verification modal hosting the Microsoft Edge WebView2 canvas.
 * Conforms strictly to Rule 9 (Desktop-first unified dialog architecture).
 * Observes active desktop theme tokens to guarantee seamless contrast and styling.
 */
@Composable
fun EmbeddedVerificationHost() {
    val isPlaybackActive by com.lagradost.cloudstream3.desktop.ui.GlobalPlayerState.isPlaybackActive.collectAsState()
    val activeSession by EmbeddedVerificationState.activeSession.collectAsState()

    val session = activeSession
    val isVisible = !isPlaybackActive && session != null && session.isDialogVisible

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val customAppThemeBackground by AppearanceConfig.customAppThemeBackground.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val primaryColor = accentColorFromName(themeAccent, customThemeAccent)
    val desktopColors = buildDesktopColors(
        primaryColor = primaryColor,
        isLightMode = isLightMode,
        isAmoled = amoledMode,
        appThemeBackground = appThemeBackground,
        customBgHex = customAppThemeBackground,
    ).copy(cardOpacity = uiCardOpacity)
    val colorScheme = buildColorScheme(primaryColor, desktopColors, isLightMode)
    val dialogContainerColor = if (amoledMode) Color(0xFF101010) else desktopColors.SurfaceCard

    CompositionLocalProvider(LocalDesktopTheme provides desktopColors) {
        MaterialTheme(colorScheme = colorScheme) {
            CloudstreamCustomDialog(
                show = isVisible,
                onDismissRequest = { EmbeddedVerificationState.dismiss() },
                containerColor = dialogContainerColor,
                modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
            ) {
                if (session != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(dialogContainerColor),
                    ) {
                        val isSuccess = session.isSuccess
                        val iconTint = if (isSuccess) Color(0xFF4CAF50) else colorScheme.primary
                        val headerTitle = if (isSuccess) "Verification Complete" else "Security Verification"
                        val headerSubtitle = if (isSuccess) "Clearance verified for ${session.hostName} — closing..." else "Resolving clearance for ${session.hostName} — click below if prompted"

                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(iconTint.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = headerTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = desktopColors.TextPrimary,
                                    )
                                    Text(
                                        text = headerSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = desktopColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { EmbeddedVerificationState.reloadCurrentSession() },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Reload",
                                        tint = desktopColors.TextMuted,
                                    )
                                }

                                Spacer(Modifier.width(4.dp))

                                IconButton(
                                    onClick = { EmbeddedVerificationState.dismiss() },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = desktopColors.TextMuted,
                                    )
                                }
                            }
                        }

                        // WebView Viewport
                        val viewportBg = if (amoledMode) Color.Black else desktopColors.Background
                        val viewportBorder = if (amoledMode) Color.White.copy(alpha = 0.12f) else desktopColors.Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(viewportBg)
                                .border(1.dp, viewportBorder, RoundedCornerShape(12.dp)),
                        ) {
                            SwingPanel(
                                modifier = Modifier.fillMaxSize(),
                                factory = { session.session.createCanvas() },
                            )
                        }
                    }
                }
            }
        }
    }
}
