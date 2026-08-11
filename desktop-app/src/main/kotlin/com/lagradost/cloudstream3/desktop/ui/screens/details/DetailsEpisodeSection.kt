package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DetailsEpisodeSection(
    provider: MainAPI,
    data: LoadResponse,
    showHistory: Map<String, WatchHistory>,
    latestHistory: WatchHistory?,
    isMovieLike: Boolean,
    isLoading: Boolean,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    enableDownloadButtons: Boolean = false,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onToggleSeasonWatched: (List<com.lagradost.cloudstream3.Episode>, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
    onToggleEpisodesStackedView: (Boolean) -> Unit,
) {
    val isEpisodesStackedView = uiState?.isEpisodesStackedView == true
    val coroutineScope = rememberCoroutineScope()
    if (isMovieLike) return
    val hasEpisodes = when (data) {
        is TvSeriesLoadResponse -> data.episodes.isNotEmpty()
        is AnimeLoadResponse -> data.episodes.isNotEmpty()
        else -> false
    }
    if (!isLoading && !hasEpisodes) return

    val dubStatuses = remember(data) { if (data is AnimeLoadResponse) data.episodes.keys.toList() else emptyList() }
    var selectedDub by remember(latestHistory?.episodeId, data) {
        mutableStateOf(
            if (data is AnimeLoadResponse) {
                if (latestHistory != null) {
                    dubStatuses.find { dub -> data.episodes[dub]?.any { it.data == latestHistory.episodeId } == true } ?: dubStatuses.firstOrNull()
                } else {
                    dubStatuses.firstOrNull()
                }
            } else {
                null
            },
        )
    }

    val seasons = remember(data) {
        val list = when (data) {
            is TvSeriesLoadResponse -> data.episodes.mapNotNull { it.season }.distinct().sorted()
            is AnimeLoadResponse -> data.episodes.values.flatten().mapNotNull { it.season }.distinct().sorted()
            else -> emptyList()
        }
        if (list.isEmpty() && (data is TvSeriesLoadResponse || data is AnimeLoadResponse)) {
            listOf(1)
        } else {
            list
        }
    }
    var selectedSeason by remember(latestHistory?.season, data) {
        mutableStateOf(
            if (data is TvSeriesLoadResponse || data is AnimeLoadResponse) {
                latestHistory?.season ?: seasons.firstOrNull() ?: 1
            } else {
                1
            },
        )
    }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }
    var selectedEpisodeChunk by remember(data.url) { mutableStateOf(0) }
    LaunchedEffect(selectedSeason, selectedDub, isSortAscending) {
        selectedEpisodeChunk = 0
    }
    val isAntiSpoiler by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.antiSpoilerEnabled.collectAsState()
    val episodesScrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerBackground(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(5) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(135.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .shimmerBackground(),
                        )
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerBackground(),
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            // Card Header: Episodes title, season watch toggle, and anti-spoiler settings
            val rightSideControls: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val currentSeasonEpisodes = (data as? TvSeriesLoadResponse)?.episodes
                        ?.filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        ?: (selectedDub?.let { (data as? AnimeLoadResponse)?.episodes?.get(it) } ?: emptyList())
                            .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                    if (currentSeasonEpisodes.isNotEmpty()) {
                        val isSeasonWatched = currentSeasonEpisodes.all { ep ->
                            val hist = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                            hist != null && PlayerLinkHandler.isCompleted(hist.position, hist.duration)
                        }
                        Button(
                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSeasonWatched) Color(0xFF1B4D2E).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = if (isSeasonWatched) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            ),
                            border = BorderStroke(1.dp, if (isSeasonWatched) Color(0xFF4CAF50).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            elevation = null,
                        ) {
                            Text(if (isSeasonWatched) "✓ Season Watched" else "Mark Season Watched", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Anti-spoiler
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Anti-spoiler", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAntiSpoiler,
                            onCheckedChange = { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(it) },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ),
                        )
                    }

                    // Sort
                    Button(
                        onClick = { isSortAscending = !isSortAscending },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                        elevation = null,
                    ) {
                        Text(if (isSortAscending) "Sort ▼" else "Sort ▲", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    // View Toggle (Single Button)
                    Button(
                        onClick = { onToggleEpisodesStackedView(!isEpisodesStackedView) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(36.dp),
                        elevation = null,
                    ) {
                        Icon(
                            imageVector = if (isEpisodesStackedView) Icons.AutoMirrored.Filled.List else Icons.Default.ViewModule,
                            contentDescription = "Toggle Episode View",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (data) {
                is MovieLoadResponse, is TorrentLoadResponse, is LiveStreamLoadResponse -> {
                    // Handled by isMovieLike above
                }
                is TvSeriesLoadResponse -> {
                    if (isMovieLike) return@Column
                    val preChunkedEpisodes = data.episodes
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val chunks = preChunkedEpisodes.chunked(20)
                    if (selectedEpisodeChunk >= chunks.size) selectedEpisodeChunk = 0
                    val allFilteredEpisodes = chunks.getOrNull(selectedEpisodeChunk) ?: emptyList()

                    // Season selector + sort
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (seasons.isNotEmpty()) {
                                var seasonMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { seasonMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = null,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                        Text(selectedMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Season")
                                    }
                                    if (seasonMenuExpanded) {
                                        androidx.compose.ui.window.Popup(
                                            onDismissRequest = { seasonMenuExpanded = false },
                                            properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                        ) {
                                            var animateIn by remember { mutableStateOf(false) }
                                            LaunchedEffect(Unit) { animateIn = true }

                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = animateIn,
                                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.95f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f))
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                                                    shadowElevation = 8.dp,
                                                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 480.dp)
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Select Season", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                            IconButton(onClick = { seasonMenuExpanded = false }) {
                                                                Icon(Icons.Default.Clear, contentDescription = "Close")
                                                            }
                                                        }
                                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 120.dp),
                                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                                            modifier = Modifier.heightIn(max = 400.dp)
                                                        ) {
                                                            items(seasons) { season ->
                                                                val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                                val isSelected = selectedSeason == season
                                                                val fallbackThumbnail = (data as? TvSeriesLoadResponse)?.episodes?.find { it.season == season }?.posterUrl
                                                                val posterUrl = meta?.posterUrl ?: fallbackThumbnail
                                                                
                                                                Card(
                                                                    onClick = { 
                                                                        selectedSeason = season
                                                                        seasonMenuExpanded = false
                                                                    },
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                                    ),
                                                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                                                    modifier = Modifier.fillMaxWidth()
                                                                ) {
                                                                    Column {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .aspectRatio(2f / 3f)
                                                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            if (posterUrl != null) {
                                                                                coil3.compose.AsyncImage(
                                                                                    model = posterUrl,
                                                                                    contentDescription = null,
                                                                                    modifier = Modifier.fillMaxSize(),
                                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                                )
                                                                            } else {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .background(
                                                                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                                                                listOf(
                                                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                                                                ),
                                                                                            ),
                                                                                        ),
                                                                                    contentAlignment = Alignment.Center,
                                                                                ) {
                                                                                    Text(
                                                                                        text = if (season == 0) "S" else "$season",
                                                                                        style = MaterialTheme.typography.titleLarge,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = MaterialTheme.colorScheme.primary,
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                                            Text(
                                                                                text = meta?.name ?: if (season == 0) "Specials" else "Season $season",
                                                                                style = MaterialTheme.typography.titleSmall,
                                                                                fontWeight = FontWeight.Bold,
                                                                                maxLines = 1,
                                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                            )
                                                                            if (meta?.episodeCount != null) {
                                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                                Text(
                                                                                    text = "${meta.episodeCount} eps",
                                                                                    style = MaterialTheme.typography.bodySmall,
                                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            if (chunks.size > 1) {
                                var chunkMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { chunkMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = null,
                                    ) {
                                        val fEp = allFilteredEpisodes.firstOrNull()?.episode ?: "?"
                                        val lEp = allFilteredEpisodes.lastOrNull()?.episode ?: "?"
                                        Text(if (fEp == lEp) "Episode $fEp" else "Episodes $fEp-$lEp", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Range")
                                    }
                                    DropdownMenu(
                                        expanded = chunkMenuExpanded,
                                        onDismissRequest = { chunkMenuExpanded = false },
                                    ) {
                                        chunks.forEachIndexed { index, chunk ->
                                            DropdownMenuItem(
                                                text = {
                                                    val fEp = chunk.firstOrNull()?.episode ?: "?"
                                                    val lEp = chunk.lastOrNull()?.episode ?: "?"
                                                    Text(if (fEp == lEp) "Episode $fEp" else "$fEp-$lEp")
                                                },
                                                onClick = {
                                                    selectedEpisodeChunk = index
                                                    chunkMenuExpanded = false
                                                },
                                                trailingIcon = if (selectedEpisodeChunk == index) {
                                                    { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                        rightSideControls()
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (allFilteredEpisodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        RenderEpisodesSection(
                            allFilteredEpisodes = allFilteredEpisodes,
                            isEpisodesStackedView = isEpisodesStackedView,
                            episodesScrollState = episodesScrollState,
                            latestHistory = latestHistory,
                            showHistory = showHistory,
                            provider = provider,
                            data = data,
                            uiState = uiState,
                            isAntiSpoiler = isAntiSpoiler,
                            enableDownloadButtons = enableDownloadButtons,
                            coroutineScope = coroutineScope,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onToggleWatched = onToggleWatched,
                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        )
                        if (!isEpisodesStackedView) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ) {
                                    Row {
                                        IconButton(onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(-600f) } }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                        IconButton(onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(600f) } }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is AnimeLoadResponse -> {
                    if (isMovieLike) return@Column
                    val preChunkedEpisodes: List<Episode> = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val chunks = preChunkedEpisodes.chunked(20)
                    if (selectedEpisodeChunk >= chunks.size) selectedEpisodeChunk = 0
                    val allFilteredEpisodes = chunks.getOrNull(selectedEpisodeChunk) ?: emptyList()

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (seasons.isNotEmpty()) {
                                var seasonMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { seasonMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = null,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                        Text(selectedMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Season")
                                    }
                                    if (seasonMenuExpanded) {
                                        androidx.compose.ui.window.Popup(
                                            onDismissRequest = { seasonMenuExpanded = false },
                                            properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                                                shadowElevation = 8.dp,
                                                modifier = Modifier.padding(top = 8.dp).widthIn(max = 700.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Select Season", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                        IconButton(onClick = { seasonMenuExpanded = false }) {
                                                            Icon(Icons.Default.Clear, contentDescription = "Close")
                                                        }
                                                    }
                                                    LazyRow(
                                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    ) {
                                                        items(seasons) { season ->
                                                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                            val isSelected = selectedSeason == season
                                                            val fallbackThumbnail = (selectedDub?.let { (data as? AnimeLoadResponse)?.episodes?.get(it) } ?: emptyList()).find { it.season == season }?.posterUrl
                                                            val posterUrl = meta?.posterUrl ?: fallbackThumbnail
                                                            
                                                            Card(
                                                                onClick = { 
                                                                    selectedSeason = season
                                                                    seasonMenuExpanded = false
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                                                                ),
                                                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                                                modifier = Modifier.width(100.dp)
                                                            ) {
                                                                Column(modifier = Modifier.padding(8.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .aspectRatio(2f / 3f)
                                                                            .clip(RoundedCornerShape(6.dp)),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        if (posterUrl != null) {
                                                                            coil3.compose.AsyncImage(
                                                                                model = posterUrl,
                                                                                contentDescription = null,
                                                                                modifier = Modifier.fillMaxSize(),
                                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                            )
                                                                        } else {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .fillMaxSize()
                                                                                    .background(
                                                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                                                            listOf(
                                                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                contentAlignment = Alignment.Center,
                                                                            ) {
                                                                                Text(
                                                                                    text = if (season == 0) "S" else "$season",
                                                                                    style = MaterialTheme.typography.headlineMedium,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = MaterialTheme.colorScheme.primary,
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                    Text(
                                                                        text = meta?.name ?: if (season == 0) "Specials" else "Season $season",
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        maxLines = 1,
                                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                    )
                                                                    if (meta?.episodeCount != null) {
                                                                        Text(
                                                                            text = "${meta.episodeCount} eps",
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            if (dubStatuses.size > 1) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    dubStatuses.forEach { dub ->
                                        val isSelected = selectedDub == dub
                                        Button(
                                            onClick = { selectedDub = dub },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            ),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                            elevation = null,
                                        ) {
                                            Text(dub.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            if (chunks.size > 1) {
                                var chunkMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { chunkMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = null,
                                    ) {
                                        val fEp = allFilteredEpisodes.firstOrNull()?.episode ?: "?"
                                        val lEp = allFilteredEpisodes.lastOrNull()?.episode ?: "?"
                                        Text(if (fEp == lEp) "Episode $fEp" else "Episodes $fEp-$lEp", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Range")
                                    }
                                    DropdownMenu(
                                        expanded = chunkMenuExpanded,
                                        onDismissRequest = { chunkMenuExpanded = false },
                                    ) {
                                        chunks.forEachIndexed { index, chunk ->
                                            DropdownMenuItem(
                                                text = {
                                                    val fEp = chunk.firstOrNull()?.episode ?: "?"
                                                    val lEp = chunk.lastOrNull()?.episode ?: "?"
                                                    Text(if (fEp == lEp) "Episode $fEp" else "$fEp-$lEp")
                                                },
                                                onClick = {
                                                    selectedEpisodeChunk = index
                                                    chunkMenuExpanded = false
                                                },
                                                trailingIcon = if (selectedEpisodeChunk == index) {
                                                    { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                        rightSideControls()
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (allFilteredEpisodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        RenderEpisodesSection(
                            allFilteredEpisodes = allFilteredEpisodes,
                            isEpisodesStackedView = isEpisodesStackedView,
                            episodesScrollState = episodesScrollState,
                            latestHistory = latestHistory,
                            showHistory = showHistory,
                            provider = provider,
                            data = data,
                            uiState = uiState,
                            enableDownloadButtons = enableDownloadButtons,
                            isAntiSpoiler = isAntiSpoiler,
                            coroutineScope = coroutineScope,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onToggleWatched = onToggleWatched,
                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        )
                        if (!isEpisodesStackedView) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ) {
                                    Row {
                                        IconButton(onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(-600f) } }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                        IconButton(onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(600f) } }) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        } // Column
    }
}

@Composable
private fun RenderEpisodesSection(
    allFilteredEpisodes: List<Episode>,
    isEpisodesStackedView: Boolean,
    episodesScrollState: androidx.compose.foundation.lazy.LazyListState,
    latestHistory: WatchHistory?,
    showHistory: Map<String, WatchHistory>,
    provider: MainAPI,
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    enableDownloadButtons: Boolean,
    isAntiSpoiler: Boolean,
    coroutineScope: CoroutineScope,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)?,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
) {
    if (isEpisodesStackedView) {
        // BoxWithConstraints gives us the real available pixel width so we can
        // pass an explicit width to each card instead of weight(1f).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            val desiredWidth = 500f
            val columns = maxOf(1, kotlin.math.round(maxWidth.value / desiredWidth).toInt())
            val gapDp = 24.dp
            val totalGapDp = gapDp * (columns - 1)
            val cardWidth = (maxWidth - totalGapDp - 1.dp) / columns

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gapDp),
                verticalArrangement = Arrangement.spacedBy(gapDp),
                maxItemsInEachRow = columns,
            ) {
                allFilteredEpisodes.forEach { ep ->
                    val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                    val history = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                    EpisodeCard(
                        ep = ep,
                        isLatest = isLatest,
                        history = history,
                        provider = provider,
                        data = data,
                        uiState = uiState,
                        isAntiSpoiler = isAntiSpoiler,
                        thumbnailVersion = uiState?.episodeThumbnailVersion ?: 0,
                        modifier = Modifier.width(cardWidth),
                        enableDownloadButtons = enableDownloadButtons,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onToggleWatched = onToggleWatched,
                        onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                    )
                }
            }
        }
    } else {
        LazyRow(
            state = episodesScrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                detectHorizontalDragGestures { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                    change.consume()
                    episodesScrollState.dispatchRawDelta(-dragAmount)
                }
            },
        ) {
            items(allFilteredEpisodes) { ep ->
                val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                val history = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                EpisodeCard(
                    ep = ep,
                    isLatest = isLatest,
                    history = history,
                    provider = provider,
                    data = data,
                    uiState = uiState,
                    isAntiSpoiler = isAntiSpoiler,
                    thumbnailVersion = uiState?.episodeThumbnailVersion ?: 0,
                    modifier = Modifier.width(500.dp),
                    enableDownloadButtons = enableDownloadButtons,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onToggleWatched = onToggleWatched,
                    onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                )
            }
        }
    }
}
