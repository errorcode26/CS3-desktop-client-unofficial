package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun WatchProgressIndicator(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (PlayerLinkHandler.isCompleted(position, duration)) {
        1f
    } else {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
    val percentStr = "${(progress * 100).toInt()}%"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = percentStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
