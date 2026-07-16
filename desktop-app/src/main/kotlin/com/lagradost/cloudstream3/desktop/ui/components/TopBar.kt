package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    isHome: Boolean,
) {
    val bg = Color.Transparent
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val theme = LocalDesktopTheme.current
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.SurfaceElevated.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))

            WindowControlsPill(isHome = isHome)
        }
        if (!isHome) {
            HorizontalDivider(color = LocalDesktopTheme.current.Divider, thickness = 0.5.dp)
        }
    }
}
