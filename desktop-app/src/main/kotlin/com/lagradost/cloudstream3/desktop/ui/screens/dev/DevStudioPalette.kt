package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark Studio Palette
internal val DevBgDark = Color(0xFF0F1117)
internal val DevSurfaceDark = Color(0xFF161822)
internal val DevCardDark = Color(0xFF1C1F2D)
internal val DevBorderDark = Color(0xFF282C3E)
internal val DevAccentCyan = Color(0xFF8BE9FD)
internal val DevLevelError = Color(0xFFFF5555)
internal val DevLevelWarn = Color(0xFFFFB86C)
internal val DevLevelInfo = Color(0xFF8BE9FD)
internal val DevLevelDebug = Color(0xFF50FA7B)
internal val DevLevelVerbose = Color(0xFF6272A4)
internal val DevMethodGet = Color(0xFF50FA7B)
internal val DevMethodPost = Color(0xFF8BE9FD)
internal val DevMethodOther = Color(0xFFFFB86C)

@Composable
internal fun InspectorField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
