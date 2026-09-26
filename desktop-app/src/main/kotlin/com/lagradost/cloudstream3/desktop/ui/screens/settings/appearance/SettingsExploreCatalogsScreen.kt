package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogDiscoverer
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.settings.ExploreCatalogPreference
import com.lagradost.cloudstream3.desktop.explore.settings.ExploreCatalogSettingsManager
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsExploreCatalogsScreen() {
    val theme = LocalDesktopTheme.current
    val coroutineScope = rememberCoroutineScope()
    val preferences by ExploreCatalogSettingsManager.preferences.collectAsState()

    var discoveredCatalogs by remember { mutableStateOf<List<ManifestCatalogDescriptor>>(emptyList()) }
    var isLoadingDiscovered by remember { mutableStateOf(true) }
    var selectedTypeFilter by remember { mutableStateOf("All") }

    // Load and discover all catalogs from active addons + native anime feeds
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val enabledAddons = StremioAddonManager.addons.value.filter { it.enabled }
            val catalogDeferreds = enabledAddons.map { addon ->
                async { ExploreCatalogDiscoverer.getCatalogsForAddon(addon) }
            }
            val discovered = catalogDeferreds.awaitAll().flatten().toMutableList()

            // Native Anime Feeds
            val animeGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mecha", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "AniList",
                    addonBaseUrl = "anilist://trending",
                    type = "anime",
                    id = "trending",
                    name = "Trending Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "AniList",
                    addonBaseUrl = "anilist://top",
                    type = "anime",
                    id = "top_100",
                    name = "Top 100 Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://airing",
                    type = "anime",
                    id = "mal_airing",
                    name = "Airing Now",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://upcoming",
                    type = "anime",
                    id = "mal_upcoming",
                    name = "Upcoming Season",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://top_series",
                    type = "anime",
                    id = "mal_top_series",
                    name = "Top Series on MAL",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://top_movies",
                    type = "anime",
                    id = "mal_top_movies",
                    name = "Top Movies on MAL",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://popular",
                    type = "anime",
                    id = "mal_popular",
                    name = "Most Popular Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://all_time",
                    type = "anime",
                    id = "mal_all_time",
                    name = "All-Time Top Rated",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://era_2020s",
                    type = "anime",
                    id = "mal_era_2020s",
                    name = "2020s Anime Hits",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://era_2010s",
                    type = "anime",
                    id = "mal_era_2010s",
                    name = "2010s Classics",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://genre_action",
                    type = "anime",
                    id = "mal_genre_action",
                    name = "Action & Adventure Anime",
                    genre = "Action",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://genre_fantasy",
                    type = "anime",
                    id = "mal_genre_fantasy",
                    name = "Fantasy & Isekai Anime",
                    genre = "Fantasy",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )

            ExploreCatalogSettingsManager.syncWithDiscovered(discovered)
            discoveredCatalogs = discovered
            isLoadingDiscovered = false
        }
    }

    val orderedCatalogs = remember(discoveredCatalogs, preferences) {
        discoveredCatalogs.sortedBy { descriptor ->
            preferences[descriptor.key]?.order ?: Int.MAX_VALUE
        }
    }

    val filteredCatalogs = remember(orderedCatalogs, selectedTypeFilter) {
        if (selectedTypeFilter.equals("All", ignoreCase = true)) {
            orderedCatalogs
        } else {
            val filterLower = selectedTypeFilter.lowercase(Locale.US)
            orderedCatalogs.filter { descriptor ->
                val typeLower = descriptor.type.lowercase(Locale.US)
                if (filterLower == "movies") typeLower == "movie"
                else if (filterLower == "series") typeLower in listOf("series", "tv")
                else if (filterLower == "anime") typeLower == "anime"
                else typeLower == filterLower
            }
        }
    }

    val scrollState = rememberScrollState()

    // Drag-and-drop state
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 64.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, filteredCatalogs.lastIndex)
    } else dragInitialIndex

    val onDrop = rememberUpdatedState {
        val fromIdx = dragInitialIndex
        val slotH = effectiveSlotHeight
        val accY = dragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, filteredCatalogs.lastIndex)
        } else fromIdx
        draggingKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in filteredCatalogs.indices && toIdx in filteredCatalogs.indices) {
            val allKeys = orderedCatalogs.map { it.key }
            val fromKey = filteredCatalogs[fromIdx].key
            val toKey = filteredCatalogs[toIdx].key
            val globalFrom = allKeys.indexOf(fromKey)
            val globalTo = allKeys.indexOf(toKey)
            if (globalFrom != -1 && globalTo != -1) {
                ExploreCatalogSettingsManager.moveByIndex(allKeys, globalFrom, globalTo)
            }
        }
    }

    // Rename Shelf Dialog
    var editingCatalogForRename by remember { mutableStateOf<ManifestCatalogDescriptor?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    editingCatalogForRename?.let { cat ->
        CloudstreamAlertDialog(
            show = true,
            onDismissRequest = { editingCatalogForRename = null },
            title = { Text("Rename Catalog Shelf") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Customize how '${cat.name}' appears on the Explore landing page. Leave empty to use the default title.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("Custom Shelf Title") },
                        placeholder = { Text(cat.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ExploreCatalogSettingsManager.setCustomTitle(cat.key, renameInputText)
                        editingCatalogForRename = null
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCatalogForRename = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Explore Landing Page Shelves") {
            // Header Controls & Explanation
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reorder & Toggle Catalog Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Drag the handles (⠿) to reorder shelves on the Explore page, toggle categories on or off, or rename shelf titles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val keys = filteredCatalogs.map { it.key }
                            val allCurrentlyEnabled = keys.all { preferences[it]?.enabled != false }
                            ExploreCatalogSettingsManager.setAllEnabled(keys, !allCurrentlyEnabled)
                        },
                    ) {
                        val allEnabled = filteredCatalogs.all { preferences[it.key]?.enabled != false }
                        Icon(
                            imageVector = if (allEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (allEnabled) "Hide All" else "Show All")
                    }

                    TextButton(
                        onClick = {
                            ExploreCatalogSettingsManager.resetToDefaults(discoveredCatalogs)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Order")
                    }
                }
            }

            // Media Type Filter Segmented Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("All", "Movies", "Series", "Anime").forEach { filterType ->
                    val isSelected = selectedTypeFilter.equals(filterType, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTypeFilter = filterType },
                    ) {
                        Text(
                            text = filterType,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else theme.TextMuted,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (isLoadingDiscovered) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }
            } else if (filteredCatalogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No catalog shelves discovered for this media type.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextMuted,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredCatalogs.forEachIndexed { index, descriptor ->
                        val pref = preferences[descriptor.key]
                        val isEnabled = pref?.enabled != false
                        val customTitle = pref?.customTitle.orEmpty()
                        val displayTitle = customTitle.ifBlank { descriptor.name }
                        val isDraggingThis = draggingKey == descriptor.key

                        val targetShiftY = when {
                            isDraggingThis -> dragAccumulatedY
                            draggingKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                            draggingKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
                            else -> 0f
                        }
                        val animatedShiftY by animateFloatAsState(
                            targetValue = targetShiftY,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        )

                        val elevation by animateDpAsState(if (isDraggingThis) 16.dp else 0.dp)
                        val scale by animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDraggingThis) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                            } else if (isEnabled) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                            },
                            border = BorderStroke(
                                if (isDraggingThis) 1.5.dp else 0.5.dp,
                                if (isDraggingThis) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                            ),
                            shadowElevation = elevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    if (coordinates.size.height > 0 && slotHeightPx == 0f) {
                                        slotHeightPx = coordinates.size.height.toFloat() + 8f
                                    }
                                }
                                .zIndex(if (isDraggingThis) 100f else 1f)
                                .scale(scale)
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragAccumulatedY else animatedShiftY
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Drag handle
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                        .pointerInput(descriptor.key) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingKey = descriptor.key
                                                    dragInitialIndex = filteredCatalogs.indexOf(descriptor)
                                                    dragAccumulatedY = 0f
                                                },
                                                onDragEnd = { onDrop.value() },
                                                onDragCancel = {
                                                    draggingKey = null
                                                    dragAccumulatedY = 0f
                                                },
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragAccumulatedY += dragAmount.y
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else theme.TextMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Addon & Type Badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    ) {
                                        Text(
                                            text = descriptor.addonName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                    ) {
                                        Text(
                                            text = descriptor.type.uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = theme.TextMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Title & Subtitle Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isEnabled) theme.TextPrimary else theme.TextMuted,
                                        )

                                        if (customTitle.isNotBlank()) {
                                            Text(
                                                text = "(Custom)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }

                                    val subtitleText = if (descriptor.genre != null) {
                                        "Genre shelf: ${descriptor.genre}"
                                    } else {
                                        "Main catalog feed"
                                    }
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.TextMuted.copy(alpha = 0.7f),
                                    )
                                }

                                // Rename / Edit Button
                                IconButton(
                                    onClick = {
                                        editingCatalogForRename = descriptor
                                        renameInputText = customTitle
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename shelf",
                                        tint = theme.TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Visibility Toggle
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        ExploreCatalogSettingsManager.setEnabled(descriptor.key, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = theme.TextMuted,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                    ),
                                    modifier = Modifier.scale(0.85f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
