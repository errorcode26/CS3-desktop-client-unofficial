package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode
import kotlinx.coroutines.delay

@Composable
fun TopBar(
    isHome: Boolean,
    homeUiState: com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiState? = null,
    homeActionDispatcher: ((com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent) -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val bg = Color.Transparent
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockWidget()
            Spacer(Modifier.weight(1f))

            WindowControlsPill(
                isHome = isHome,
                homeUiState = homeUiState,
                homeActionDispatcher = homeActionDispatcher,
            )
        }
    }
}

@Composable
private fun ClockWidget() {
    val mode by AppearanceConfig.clockMode.collectAsState()
    if (mode == ClockDisplayMode.HIDDEN) return

    val timeFormat by AppearanceConfig.clockTimeFormat.collectAsState()
    val dateFormat by AppearanceConfig.clockDateFormat.collectAsState()

    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalDateTime.now()
            delay(1000)
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        if (mode == ClockDisplayMode.TIME_ONLY || mode == ClockDisplayMode.BOTH) {
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(timeFormat))
                } catch (_: Exception) {
                    "--:--"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mode == ClockDisplayMode.DATE_ONLY || mode == ClockDisplayMode.BOTH) {
            Text(
                text = try {
                    now.format(java.time.format.DateTimeFormatter.ofPattern(dateFormat))
                } catch (_: Exception) {
                    "---"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
