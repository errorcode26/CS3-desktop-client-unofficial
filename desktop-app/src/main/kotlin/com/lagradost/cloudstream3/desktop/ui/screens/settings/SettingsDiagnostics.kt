package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.network.DiagnosticResult
import com.lagradost.cloudstream3.desktop.network.DiagnosticsRunner
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent

@Composable
fun SettingsDiagnostics(
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val diagState = uiState.diagnosticsState
    val isRunning = diagState.isNetworkTesting || diagState.isMetaTesting
    val results = diagState.results
    val currentTest = diagState.currentTest
    val lastRunTime = diagState.lastRunTime
    var copiedNet by remember { mutableStateOf(false) }
    var copiedMeta by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    var containerCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalSettingsScrollState provides scrollState,
        LocalScrollContainerCoordinates provides containerCoordinates,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoordinates = it }
                .verticalScroll(scrollState)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "Network Diagnostics",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Test infrastructure connectivity and metadata provider availability.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Network infrastructure tests ──────────────────────────────────
            Text(
                text = "Network Infrastructure",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Tests basic internet connectivity, DNS, TMDB API, and GitHub access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        copiedNet = false
                        viewModel.onEvent(SettingsUiEvent.RunNetworkDiagnostics)
                    },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (diagState.isNetworkTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...")
                    } else {
                        Text("Test Network")
                    }
                }

                if (results.isNotEmpty() && results.none { it.name.startsWith("Provider:") }) {
                    OutlinedButton(
                        onClick = {
                            val report = DiagnosticsRunner.formatReport(results)
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(report), null)
                            copiedNet = true
                        },
                    ) {
                        Text(if (copiedNet) "Copied!" else "Copy Results")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Metadata provider tests ───────────────────────────────────────
            Text(
                text = "Metadata Providers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Tests each installed content provider by running a live search and checking for results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        copiedMeta = false
                        viewModel.onEvent(SettingsUiEvent.RunMetaDiagnostics)
                    },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    if (diagState.isMetaTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Providers...")
                    } else {
                        Text("Test Providers")
                    }
                }

                if (results.any { it.name.startsWith("Provider:") }) {
                    OutlinedButton(
                        onClick = {
                            val report = DiagnosticsRunner.formatReport(results.filter { it.name.startsWith("Provider:") })
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(report), null)
                            copiedMeta = true
                        },
                    ) {
                        Text(if (copiedMeta) "Copied!" else "Copy Results")
                    }
                }
            }

            if (isRunning && currentTest.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Testing: $currentTest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (results.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "",
                                modifier = Modifier.width(32.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Test",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Time",
                                modifier = Modifier.width(72.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Detail",
                                modifier = Modifier.weight(1.5f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        results.forEach { result ->
                            DiagnosticResultRow(result)
                        }
                    }
                }

                val passCount = results.count { it.passed }
                val failCount = results.count { !it.passed }
                val avgTime = results.filter { it.passed && it.timeMs > 0 }
                    .let { passed -> if (passed.isNotEmpty()) passed.sumOf { it.timeMs } / passed.size else 0 }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SummaryChip("✅ $passCount passed", MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    if (failCount > 0) {
                        SummaryChip("❌ $failCount failed", MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    }
                    Text(
                        text = "Avg response: ${avgTime}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lastRunTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Last run: $lastRunTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagnosticResultRow(result: DiagnosticResult) {
    val statusIcon = if (result.passed) "✅" else "❌"
    val timeText = if (result.timeMs > 0) "${result.timeMs}ms" else "--"
    val detailColor = if (result.passed) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (!result.passed) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusIcon,
                modifier = Modifier.width(32.dp),
                fontSize = 14.sp,
            )
            Text(
                text = result.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = timeText,
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (result.timeMs > 3000) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = result.detail,
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SummaryChip(text: String, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
