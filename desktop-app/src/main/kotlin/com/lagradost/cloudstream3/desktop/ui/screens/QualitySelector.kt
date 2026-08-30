package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

data class QualityOption(
    val qualityValue: Int,
    val label: String,
    val count: Int,
)

enum class StreamFormatFilter(val label: String) {
    ALL("All Formats"),
    DIRECT("Direct (MP4/MKV)"),
    HLS("HLS (m3u8)"),
    DASH("DASH (mpd)"),
    TORRENT("Torrent / P2P"),
}

data class FormatOption(
    val format: StreamFormatFilter,
    val count: Int,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    totalLinkCount: Int,
    availableQualities: List<QualityOption>,
    selectedQuality: Int?,
    onSelect: (Int?) -> Unit,
    onOpenPriorityDialog: () -> Unit,
    availableFormats: List<FormatOption> = emptyList(),
    selectedFormat: StreamFormatFilter = StreamFormatFilter.ALL,
    onSelectFormat: (StreamFormatFilter) -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Divider.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Resolution Quality Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DesktopUi.Accent,
                    )
                    Text(
                        "Resolution Quality",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DesktopUi.TextPrimary,
                    )
                }

                TextButton(
                    onClick = onOpenPriorityDialog,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Priorities & Sources",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Resolution Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedQuality == null,
                    onClick = { onSelect(null) },
                    label = {
                        Text(
                            if (totalLinkCount > 0) "All ($totalLinkCount)" else "All",
                            fontWeight = if (selectedQuality == null) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesktopUi.AccentSoft,
                        selectedLabelColor = DesktopUi.Accent,
                    ),
                    shape = CircleShape,
                )
                availableQualities.forEach { option ->
                    FilterChip(
                        selected = selectedQuality == option.qualityValue,
                        onClick = { onSelect(option.qualityValue) },
                        label = {
                            Text(
                                "${option.label} (${option.count})",
                                fontWeight = if (selectedQuality == option.qualityValue) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DesktopUi.AccentSoft,
                            selectedLabelColor = DesktopUi.Accent,
                        ),
                        shape = CircleShape,
                    )
                }
            }

            // Format & Protocol Filters (Direct MP4, HLS, DASH, Torrent)
            if (availableFormats.size > 1) {
                HorizontalDivider(color = DesktopUi.Divider.copy(alpha = 0.4f))

                Text(
                    "Stream Format & Protocol",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopUi.TextMuted,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableFormats.forEach { formatOpt ->
                        FilterChip(
                            selected = selectedFormat == formatOpt.format,
                            onClick = { onSelectFormat(formatOpt.format) },
                            label = {
                                Text(
                                    "${formatOpt.format.label} (${formatOpt.count})",
                                    fontWeight = if (selectedFormat == formatOpt.format) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
    }
}
