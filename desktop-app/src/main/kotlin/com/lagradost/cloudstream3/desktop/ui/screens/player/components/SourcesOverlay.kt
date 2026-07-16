package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.screens.QualitySelector
import com.lagradost.cloudstream3.utils.ExtractorLink

@Composable
fun SourcesOverlay(
    links: List<ExtractorLink>,
    currentIndex: Int,
    failedLinks: Set<Int> = emptySet(),
    onLinkSelected: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var selectedQuality by remember { mutableStateOf<Int?>(null) }

    val availableQualities = remember(links) {
        links.map { it.quality }.distinct().sortedDescending()
    }

    val filteredLinks = remember(links, selectedQuality) {
        if (selectedQuality == null) {
            links.mapIndexed { index, link -> index to link }
        } else {
            links.mapIndexed { index, link -> index to link }.filter { it.second.quality == selectedQuality }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight(0.85f)
            .width(420.dp)
            .padding(end = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F16)) // Dark bluish/black exact to screenshot
            .pointerInput(Unit) { detectTapGestures(onTap = {}, onDoubleTap = {}) }
            .padding(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Close button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Text(
                text = "VIDEO SOURCES",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
                lineHeight = 24.sp,
            )

            if (availableQualities.isNotEmpty()) {
                val stringQualities = availableQualities.map { "${it}p" }
                QualitySelector(
                    availableQualities = stringQualities,
                    selectedQuality = selectedQuality?.let { "${it}p" },
                    onSelect = { str -> selectedQuality = str?.replace("p", "")?.toIntOrNull() },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(filteredLinks.size) { i ->
                    val (originalIndex, link) = filteredLinks[i]
                    val isSelected = originalIndex == currentIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                            .clickable {
                                onLinkSelected(originalIndex)
                                onClose()
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = link.name,
                                color = if (failedLinks.contains(originalIndex)) Color.Red else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (link.url.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = link.url,
                                    color = if (failedLinks.contains(originalIndex)) Color.Red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (failedLinks.contains(originalIndex)) {
                            Text(
                                text = "Failed",
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (isSelected) {
                            Text(
                                text = "Playing",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
