package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Hoisted test state — survives tab switches
class ProviderTestState {
    var isRunning by mutableStateOf(false)
    var results by mutableStateOf<Map<String, TestingUtils.TestResultProvider>>(emptyMap())
    var passed by mutableStateOf(0)
    var failed by mutableStateOf(0)
    var total by mutableStateOf(0)
    var currentJob: Job? = null

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        isRunning = false
    }

    fun start(scope: CoroutineScope, providers: List<com.lagradost.cloudstream3.MainAPI>) {
        cancel()
        results = emptyMap()
        passed = 0
        failed = 0
        total = providers.size
        isRunning = true
        currentJob = scope.launch(Dispatchers.IO) {
            TestingUtils.getDeferredProviderTests(this, providers.toTypedArray()) { api, result ->
                results = results.toMutableMap().apply { put(api.name, result) }
                if (result.success) passed++ else failed++
                if (results.size == providers.size) isRunning = false
            }
        }
    }
}

@Composable
fun SettingsDeveloper() {
    val isDevEnabled = com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager.isEnabled

    if (!isDevEnabled) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Developer Options are Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Developer tools include live LogCat, Network Inspector, and Provider Diagnostics. Turn on to unlock advanced debugging tools.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager.setEnabled(true) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text("Enable Developer Mode", fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val testState = remember { ProviderTestState() }
    val scope = rememberCoroutineScope()

    data class TabData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val tabs = listOf(
        TabData("Provider Testing", Icons.Default.Build),
        TabData("Network Diagnostics", Icons.Default.NetworkCheck),
        TabData("Logcat", Icons.Default.List),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Developer Mode Status Banner
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🛠️",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Column {
                        Text(
                            text = "Developer Mode Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Press F12 anywhere to open floating DevStudio inspector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = { com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager.setEnabled(false) },
                ) {
                    Text("Turn Off", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Tab bar
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        onClick = { selectedTabIndex = index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(imageVector = tab.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTabIndex) {
                0 -> SettingsTesting(testState = testState, scope = scope)
                1 -> SettingsDiagnostics()
                2 -> SettingsLogcat()
            }
        }
    }
}
