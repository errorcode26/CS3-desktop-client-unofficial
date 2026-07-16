package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(availableQualities: List<String>, selectedQuality: String?, onSelect: (String?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text("Quality", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedQuality == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DesktopUi.AccentSoft,
                    selectedLabelColor = DesktopUi.Accent,
                ),
            )
            availableQualities.forEach { q ->
                FilterChip(
                    selected = selectedQuality == q,
                    onClick = { onSelect(q) },
                    label = { Text(q) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesktopUi.AccentSoft,
                        selectedLabelColor = DesktopUi.Accent,
                    ),
                )
            }
        }
    }
}
