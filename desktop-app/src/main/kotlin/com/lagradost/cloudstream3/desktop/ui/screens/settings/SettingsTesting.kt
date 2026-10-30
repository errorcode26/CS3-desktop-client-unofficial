package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.ProviderTestFilter
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.utils.TestingUtils

@Composable
fun SettingsTesting(
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val testState = uiState.providerTestState

    var providersVersion by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.cloudstream3.desktop.init.loadAllRemainingPlugins()
        }
        if (loaded > 0) {
            providersVersion++
        }
    }

    val allProviders = remember(providersVersion) {
        APIHolder.allProviders
            .filter { it.providerType != com.lagradost.cloudstream3.ProviderType.MetaProvider && it.name != "NONE" && it.name.isNotBlank() }
            .distinctBy { it.name }
            .sortedBy { it.name }
    }

    val filteredProviders = remember(allProviders, testState.results, testState.filter) {
        when (testState.filter) {
            ProviderTestFilter.ALL -> allProviders
            ProviderTestFilter.PASSED -> allProviders.filter { testState.results[it.name]?.success == true }
            ProviderTestFilter.FAILED -> allProviders.filter { testState.results[it.name]?.success == false }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Provider Testing",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Automatically test if your installed plugins are successfully fetching data. Tests continue in background when switching tabs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (!testState.isRunning) {
                        viewModel.onEvent(SettingsUiEvent.StartProviderTests)
                    }
                },
                enabled = !testState.isRunning && testState.currentlyTestingProvider == null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (testState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing... (${testState.results.size}/${allProviders.size})")
                } else {
                    Text("Run All Tests")
                }
            }

            // Cancel button — only visible while batch running
            if (testState.isRunning) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(SettingsUiEvent.CancelProviderTests) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                ) {
                    Text("Cancel")
                }
            }

            if (testState.total > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Passed: ${testState.passed}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Text("Failed: ${testState.failed}", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                    Text("Total: ${testState.total}", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val currentFilter = testState.filter
            FilterChip(
                selected = currentFilter == ProviderTestFilter.ALL,
                onClick = { viewModel.onEvent(SettingsUiEvent.SetProviderTestFilter(ProviderTestFilter.ALL)) },
                label = { Text("All (${allProviders.size})") },
            )
            FilterChip(
                selected = currentFilter == ProviderTestFilter.PASSED,
                onClick = { viewModel.onEvent(SettingsUiEvent.SetProviderTestFilter(ProviderTestFilter.PASSED)) },
                label = { Text("Passed (${testState.passed})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFF4CAF50),
                ),
            )
            FilterChip(
                selected = currentFilter == ProviderTestFilter.FAILED,
                onClick = { viewModel.onEvent(SettingsUiEvent.SetProviderTestFilter(ProviderTestFilter.FAILED)) },
                label = { Text("Failed (${testState.failed})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFF44336).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFFF44336),
                ),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filteredProviders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (testState.filter == ProviderTestFilter.PASSED) "No passed providers yet."
                                else if (testState.filter == ProviderTestFilter.FAILED) "No failed providers!"
                                else "No providers available to test.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            items(filteredProviders, key = { it.name }) { api ->
                val result = testState.results[api.name]
                val isThisProviderTesting = testState.currentlyTestingProvider == api.name

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = api.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (api.lang.isNotBlank()) {
                                    Text(
                                        text = api.lang.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when {
                                    isThisProviderTesting ->
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    result != null && result.success ->
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Passed", tint = Color(0xFF4CAF50))
                                    result != null && !result.success ->
                                        Icon(Icons.Default.Error, contentDescription = "Failed", tint = Color(0xFFF44336))
                                    else ->
                                        Text("Not Tested", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(
                                    onClick = { viewModel.onEvent(SettingsUiEvent.StartSingleProviderTest(api.name)) },
                                    enabled = !isThisProviderTesting && !testState.isRunning,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = if (result != null) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                        contentDescription = "Test ${api.name}",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (!isThisProviderTesting && !testState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = result != null) {
                            if (result != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                ) {
                                    result.log.forEach { logMsg ->
                                        val logColor = when (logMsg.level) {
                                            TestingUtils.Logger.LogLevel.Error -> Color(0xFFFF5252)
                                            TestingUtils.Logger.LogLevel.Warning -> Color(0xFFFFC107)
                                            TestingUtils.Logger.LogLevel.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = logMsg.toString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = logColor,
                                            modifier = Modifier.padding(bottom = 2.dp),
                                        )
                                    }
                                    if (result.exception != null) {
                                        Text(
                                            text = result.exception.stackTraceToString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF5252),
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
