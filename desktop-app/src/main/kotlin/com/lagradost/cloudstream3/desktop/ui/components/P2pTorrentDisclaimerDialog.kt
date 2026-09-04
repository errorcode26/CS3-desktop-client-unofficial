package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun P2pTorrentDisclaimerDialog(
    show: Boolean,
    isSettingsContext: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!show) return

    val warningColor = Color(0xFFF59E0B)

    CloudstreamAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 480.dp, max = 540.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = if (isSettingsContext) "Enable P2P Torrent Streaming?" else "Torrent Provider Notice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Big prominent warning banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = warningColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, warningColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "Make sure you understand what torrenting is before continuing.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = warningColor,
                            lineHeight = 20.sp,
                            fontSize = 15.sp,
                        )
                    }
                }

                // Contextual body explanation with larger typography
                if (isSettingsContext) {
                    Text(
                        text = "Enabling this feature allows the app to connect directly to public peer-to-peer (P2P) swarms for playback and downloads.\n\nUnlike standard web streaming, torrenting broadcasts your real public IP address to all other peers in the swarm, which can be seen by third parties and internet providers. Depending on your region, this may lead to ISP notices or network restrictions without a VPN.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                } else {
                    Text(
                        text = "This provider relies on Peer-to-Peer (Torrent) streams, which are currently disabled in your app settings.\n\nConnecting to torrent swarms exposes your real public IP address to other peers and your internet provider. If torrent traffic is monitored on your network, proceeding without a VPN may result in ISP notices or restrictions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }

                // VPN Recommendation Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = if (isSettingsContext) {
                                "Always use a trusted VPN when P2P streaming is enabled."
                            } else {
                                "We strongly recommend using a VPN before proceeding."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                            fontSize = 13.5.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = warningColor,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    text = if (isSettingsContext) "I Understand & Enable Feature" else "I Understand & Enable",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text(
                    text = if (isSettingsContext) "Keep Disabled" else "Cancel",
                    fontSize = 14.sp,
                )
            }
        },
    )
}
