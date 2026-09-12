package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.runtime.executor.PluginHealthStats
import com.lagradost.runtime.executor.PluginHealthStatus

// -------------------------------------------------------------------------------------------------
// TAB 4: Provider Health & Circuit Breakers
// -------------------------------------------------------------------------------------------------

@Composable
internal fun ProviderHealthTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plugin & Provider Circuit Breakers", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            if (state.pluginHealth.isNotEmpty()) {
                Button(
                    onClick = { onEvent(DevStudioUiEvent.ResetAllCircuits) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Reset All Circuits", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (state.pluginHealth.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("No provider traffic recorded yet in this session", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.pluginHealth.values.sortedBy { it.providerName }.forEach { health ->
                    androidx.compose.runtime.key(health.providerName) {
                        ProviderHealthDetailCard(
                            health = health,
                            onReset = { onEvent(DevStudioUiEvent.ResetCircuit(health.providerName)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHealthDetailCard(
    health: PluginHealthStats,
    onReset: () -> Unit,
) {
    val (statusColor, statusLabel) = when (health.status) {
        PluginHealthStatus.HEALTHY -> Pair(DevLevelDebug, "HEALTHY")
        PluginHealthStatus.DEGRADED -> Pair(DevLevelWarn, "DEGRADED (${health.consecutiveFailures} fails)")
        PluginHealthStatus.TRIPPED_AUTO_DISABLED -> Pair(DevLevelError, "TRIPPED / BLOCKED")
        PluginHealthStatus.HALF_OPEN -> Pair(DevAccentCyan, "HALF-OPEN (TESTING)")
    }

    Surface(
        color = DevCardDark,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED) DevLevelError else DevBorderDark,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(health.providerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Calls: ${health.totalCalls} (${health.successfulCalls} OK / ${health.failedCalls} Failed) • Avg Latency: ${health.averageLatencyMs}ms",
                    color = Color.Gray,
                    fontSize = 11.sp,
                )

                if (health.lastFailureReason != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Last Failure: ${health.lastFailureReason}",
                        color = DevLevelWarn,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (health.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED || health.status == PluginHealthStatus.DEGRADED) {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Reset Circuit", fontSize = 11.sp, color = DevAccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
