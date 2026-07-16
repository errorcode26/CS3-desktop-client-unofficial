package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TopBarOverlay(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = title.split(" - ", limit = 2)
    val mainTitle = parts.firstOrNull() ?: title
    val subTitle = parts.getOrNull(1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Centered Titles
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = mainTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            if (subTitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}
