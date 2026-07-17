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
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsTesting() {
    val scope = rememberCoroutineScope()
    
    val allProviders = remember {
        APIHolder.allProviders.distinctBy { it::class.java.simpleName }.sortedBy { it.name }
    }

    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<Map<String, TestingUtils.TestResultProvider>>(emptyMap()) }
    var passed by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Header ---
        Text(
            text = "Provider Testing",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Automatically test if your installed plugins are successfully fetching data. This runs a search, load, and link extraction test on each provider.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Controls & Stats ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    if (!isRunning) {
                        isRunning = true
                        results = emptyMap()
                        passed = 0
                        failed = 0
                        total = allProviders.size
                        
                        scope.launch(Dispatchers.IO) {
                            TestingUtils.getDeferredProviderTests(this, allProviders.toTypedArray()) { api, result ->
                                // Update results map safely
                                results = results.toMutableMap().apply {
                                    put(api.name, result)
                                }
                                if (result.success) passed++ else failed++
                                
                                if (results.size == allProviders.size) {
                                    isRunning = false
                                }
                            }
                        }
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing... (${results.size}/${allProviders.size})")
                } else {
                    Text("Run All Tests")
                }
            }
            
            if (total > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Passed: $passed", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Text("Failed: $failed", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                    Text("Total: $total", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- Results List ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allProviders) { api ->
                val result = results[api.name]
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = api.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (result != null) {
                                if (result.success) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Passed", tint = Color(0xFF4CAF50))
                                } else {
                                    Icon(Icons.Default.Error, contentDescription = "Failed", tint = Color(0xFFF44336))
                                }
                            } else if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Not Tested", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        AnimatedVisibility(visible = result != null) {
                            if (result != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
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
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    if (result.exception != null) {
                                        Text(
                                            text = result.exception!!.stackTraceToString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF5252),
                                            modifier = Modifier.padding(top = 4.dp)
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
