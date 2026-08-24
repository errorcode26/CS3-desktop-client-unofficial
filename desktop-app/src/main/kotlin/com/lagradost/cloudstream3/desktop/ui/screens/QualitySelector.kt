package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

data class QualityOption(
    val qualityValue: Int,
    val label: String,
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Resolution Quality", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)

            TextButton(
                onClick = onOpenPriorityDialog,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp),
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Priorities & Sources", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
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
    }
}
