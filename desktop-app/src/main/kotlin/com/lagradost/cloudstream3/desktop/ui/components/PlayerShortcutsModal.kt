package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShortcutItem(val key: String, val description: String)
data class ShortcutSection(val category: String, val items: List<ShortcutItem>)

@Composable
fun PlayerShortcutsModal(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val sections = listOf(
        ShortcutSection(
            category = "Playback & Speed",
            items = listOf(
                ShortcutItem("Space / K", "Play / Pause"),
                ShortcutItem("+ / ]", "Speed up (+0.25x)"),
                ShortcutItem("- / [", "Slow down (-0.25x)"),
                ShortcutItem("Backspace", "Reset speed to 1.0x"),
                ShortcutItem("F / Double Click", "Toggle Fullscreen"),
                ShortcutItem("Esc / Q", "Exit Player"),
            ),
        ),
        ShortcutSection(
            category = "Navigation & Seeking",
            items = listOf(
                ShortcutItem("← / →", "Seek ±10 seconds"),
                ShortcutItem("Shift + ← / →", "Micro-seek ±2 seconds"),
                ShortcutItem("Ctrl + →", "Skip 85s (OP / Intro)"),
                ShortcutItem("0 – 9", "Jump to 0% – 90%"),
            ),
        ),
        ShortcutSection(
            category = "Audio & Volume",
            items = listOf(
                ShortcutItem("↑ / ↓", "Volume ±5%"),
                ShortcutItem("M", "Mute / Unmute"),
                ShortcutItem("# / A", "Cycle Audio Tracks"),
            ),
        ),
        ShortcutSection(
            category = "Subtitles & Sync",
            items = listOf(
                ShortcutItem("C", "Cycle Subtitle Tracks"),
                ShortcutItem("V", "Toggle Subtitles On / Off"),
                ShortcutItem("Z / X", "Subtitle Delay (±100ms sync)"),
            ),
        ),
    )

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.70f)
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF10111A))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = "Shortcuts",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Player Keyboard Shortcuts",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Grid of Sections
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = section.category.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            section.items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.description,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                    )

                                    // Key badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = item.key,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                if (index < section.items.lastIndex) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
