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
