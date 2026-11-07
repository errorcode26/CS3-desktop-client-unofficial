package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.ExploreSearchScope
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.viewmodel.EXPLORE_YEAR_OPTIONS
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme

@Composable
internal fun CompactCatalogChip(
    catalog: ManifestCatalogDescriptor,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else if (isHovered) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.White.copy(alpha = 0.05f)
        },
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else if (isHovered) {
                Color.White.copy(alpha = 0.30f)
            } else {
                Color.White.copy(alpha = 0.12f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = catalog.name,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.Black else theme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun GenreDropdown(
    selectedGenre: String,
    genres: List<String>,
    onSelectGenre: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalDesktopTheme.current
    val allOptions = remember(genres) { listOf("All") + genres }
    val displayText = if (selectedGenre.equals("All", ignoreCase = true)) "All Genres" else selectedGenre

    Box {
        Surface(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = displayText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            allOptions.forEach { genre ->
                val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (genre == "All") "All Genres" else genre,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else theme.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelectGenre(genre)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun YearDropdown(
    selectedYear: String,
    onSelectYear: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalDesktopTheme.current

    Box {
        Surface(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = selectedYear,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            EXPLORE_YEAR_OPTIONS.forEach { yr ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = yr,
                            fontSize = 12.sp,
                            fontWeight = if (yr == selectedYear) FontWeight.Bold else FontWeight.Normal,
                            color = if (yr == selectedYear) MaterialTheme.colorScheme.primary else theme.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelectYear(yr)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun ExploreSearchField(
    query: String,
    searchScope: ExploreSearchScope,
    onSelectScope: (ExploreSearchScope) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isLiveSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val focusManager = LocalFocusManager.current
    val isLightMode = theme.isLightMode
    val searchShape = RoundedCornerShape(24.dp)

    var isFocused by remember { mutableStateOf(false) }
    val targetWidth = if (isFocused || query.isNotBlank()) 640.dp else 420.dp
    val animatedWidth by animateDpAsState(targetWidth, animationSpec = tween(200))

    val borderBrush = if (isFocused) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (isLightMode) theme.Divider.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f),
                if (isLightMode) theme.Divider.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
            ),
        )
    }

    Surface(
        modifier = modifier
            .height(48.dp)
            .width(animatedWidth)
            .clip(searchShape)
            .border(
                BorderStroke(1.dp, borderBrush),
                shape = searchShape,
            ),
        color = if (isFocused) {
            if (isLightMode) Color.White.copy(alpha = 0.95f) else Color(0xFF14141A).copy(alpha = 0.85f)
        } else {
            if (isLightMode) theme.SurfaceElevated.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.06f)
        },
        shape = searchShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (query.isNotBlank() || isFocused) MaterialTheme.colorScheme.primary else theme.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search catalogs, movies, series...",
                        fontSize = 13.5.sp,
                        color = theme.TextMuted.copy(alpha = 0.75f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.TextPrimary,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            // Scope Selector Filter Pills
            AnimatedVisibility(
                visible = isFocused || query.isNotBlank(),
                enter = fadeIn(tween(150)) + expandHorizontally(),
                exit = fadeOut(tween(100)) + shrinkHorizontally(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExploreSearchScope.entries.forEach { scope ->
                        val isScopeSelected = searchScope == scope
                        Surface(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectScope(scope) },
                            color = if (isScopeSelected) MaterialTheme.colorScheme.primary else (if (isLightMode) theme.Divider.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (scope) {
                                        ExploreSearchScope.ALL -> "All"
                                        ExploreSearchScope.ADDONS -> "Addons"
                                        ExploreSearchScope.PLUGINS -> "Plugins"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isScopeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isScopeSelected) Color.Black else theme.TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            if (isLiveSearching) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = theme.TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onClear() },
                )
            }

            // Sleek Cancel Pill Button
            AnimatedVisibility(
                visible = isFocused || query.isNotBlank(),
                enter = fadeIn(tween(150)) + expandHorizontally(),
                exit = fadeOut(tween(100)) + shrinkHorizontally(),
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            focusManager.clearFocus()
                            if (query.isNotBlank()) onClear()
                        },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyCatalogState(
    onNavigateToSettings: () -> Unit,
) {
    val theme = LocalDesktopTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )

            Text(
                text = "No Catalog Addons Configured",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = theme.TextPrimary,
            )

            Text(
                text = "Install catalog metadata add-ons in Extensions to explore real-time movie, series, and anime catalogs.",
                fontSize = 12.5.sp,
                color = theme.TextMuted,
            )

            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Install Add-ons", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
