package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiEvent
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreUiState
import com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel
import com.lagradost.cloudstream3.desktop.ui.LocalHazeState
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun ExploreHeaderControls(
    uiState: ExploreUiState,
    viewModel: ExploreViewModel,
    dockPaddingStart: Dp,
    dockPaddingEnd: Dp,
    onNavigate: (Config) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val hazeState = LocalHazeState.current
    val isAmoled = theme.isAmoled
    val isLightMode = theme.isLightMode
    val performanceModeEnabled by AppearanceConfig.performanceModeEnabled.collectAsState()
    val barShape = RoundedCornerShape(16.dp)

    val hazeModifier = if (hazeState != null && !isAmoled && !performanceModeEnabled) {
        Modifier
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = if (isLightMode) Color(0xFFF0F0F4).copy(alpha = 0.50f) else Color(0xFF14141A).copy(alpha = 0.35f),
                    tint = HazeTint(if (isLightMode) Color.White.copy(alpha = 0.35f) else Color(0xFF0F0F14).copy(alpha = 0.40f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f,
                ),
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isLightMode) 0.15f else 0.06f),
                        Color.White.copy(alpha = if (isLightMode) 0.05f else 0.02f),
                    ),
                ),
            )
    } else {
        Modifier.background(
            if (isAmoled) {
                Color.Black.copy(alpha = 0.94f)
            } else if (isLightMode) {
                theme.SurfaceElevated.copy(alpha = 0.92f)
            } else {
                Color(0xFF121218).copy(alpha = 0.88f)
            },
        )
    }

    val drilledCat = uiState.drilledCatalog
    val drilledPlat = uiState.drilledPlatform
    val drilledSearchCat = uiState.drilledSearchCategory
    val selectedCollection = uiState.selectedCollection

    // ── Header Controls (Left Catalog Capsule + Centered Search Bar) ──
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dockPaddingStart, end = dockPaddingEnd),
    ) {
        val isHeaderWide = this.maxWidth >= 840.dp
        val leftMaxW = this.maxWidth.coerceAtLeast(120.dp)
        val borderGradient = Brush.linearGradient(
            listOf(
                if (isLightMode) theme.Divider.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                if (isLightMode) theme.Divider.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
            ),
        )

        // ── Left: Navigation & Catalog Filter Tabs Capsule ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = leftMaxW)
                .wrapContentWidth()
                .height(48.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = barShape,
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.35f),
                    ambientColor = Color.Transparent,
                ),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = leftMaxW)
                    .wrapContentWidth()
                    .height(48.dp)
                    .clip(barShape)
                    .then(hazeModifier)
                    .border(1.dp, borderGradient, barShape),
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = leftMaxW)
                        .wrapContentWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (drilledSearchCat != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.ReturnFromSearchCategory) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Search  ›  ${uiState.searchQuery}  ›  $drilledSearchCat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = drilledSearchCat,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }
                    } else if (selectedCollection != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.CloseCollection) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Explore  ›  Collections  ›  ${selectedCollection.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = selectedCollection.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }
                    } else if (drilledCat != null || drilledPlat != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.onEvent(ExploreUiEvent.ReturnToShelves) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        val headerTitle = drilledPlat?.let { "${it.displayName} Hub" } ?: drilledCat?.name ?: "Explore"
                        Column {
                            Text(
                                text = "Explore  ›  $headerTitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                        }

                        val isDrillWide = isHeaderWide

                        if (drilledPlat == null && drilledCat != null && !isDrillWide && drilledCat.genres.isNotEmpty()) {
                            GenreDropdown(
                                selectedGenre = uiState.selectedGenre,
                                genres = drilledCat.genres,
                                onSelectGenre = { viewModel.onEvent(ExploreUiEvent.SelectGenre(it)) },
                            )
                        }

                        if (drilledPlat == null && (!isDrillWide || drilledCat == null)) {
                            YearDropdown(
                                selectedYear = uiState.selectedYear,
                                onSelectYear = { viewModel.onEvent(ExploreUiEvent.SelectYear(it)) },
                            )
                        }
                    } else {
                        // Landing: Brand + Category Pills
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                            Text(
                                text = "Explore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                                fontSize = 15.sp,
                            )
                        }

                        // Sleek vertical separator between brand and category pills
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(if (isLightMode) theme.Divider else Color.White.copy(alpha = 0.15f)),
                        )

                        // Explore primary category pills (My Add-ons, Streaming Hubs, Franchise Sagas, Anime, Movies, TV Shows)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    0.5.dp,
                                    if (isLightMode) theme.Divider.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                ),
                            color = if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                uiState.availableTypes.forEach { type ->
                                    val isSelected = uiState.selectedType.equals(type, ignoreCase = true)
                                    val title = viewModel.formatTypeTitle(type)

                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            )
                                            .clickable { viewModel.onEvent(ExploreUiEvent.SelectType(type)) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else theme.TextMuted,
                                            letterSpacing = 0.2.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
