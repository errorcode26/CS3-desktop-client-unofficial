package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
        var passwordInput by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var hasError by remember { mutableStateOf(false) }

        val handleUnlock = {
            if (passwordInput.trim().equals("banana", ignoreCase = true)) {
                hasError = false
                com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager.setEnabled(true)
            } else {
                hasError = true
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
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
                text = "Developer tools include live LogCat, Network Inspector, and Provider Diagnostics. Enter password to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp),
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            if (hasError) hasError = false
                        },
                        label = { Text("Developer Password") },
                        placeholder = { Text("Enter password...") },
                        singleLine = true,
                        isError = hasError,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        supportingText = {
                            if (hasError) {
                                Text("Incorrect password. Try again.", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(
                                    "Hint: If you don't know what monke eats, you can't be trusted with live LogCat",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handleUnlock() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )

                    Button(
                        onClick = handleUnlock,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock Developer Mode", fontWeight = FontWeight.SemiBold)
                    }
                }
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
        TabData("Logcat", Icons.AutoMirrored.Filled.List),
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
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp).size(20.dp),
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
