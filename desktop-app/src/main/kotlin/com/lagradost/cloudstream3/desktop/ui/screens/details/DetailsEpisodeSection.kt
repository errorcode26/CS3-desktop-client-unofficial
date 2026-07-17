package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.common.storage.DesktopDataStore
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
    coroutineScope: CoroutineScope,
    onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit,
) {
    if (isMovieLike) return
    val hasEpisodes = when (data) {
        is TvSeriesLoadResponse -> data.episodes.isNotEmpty()
        is AnimeLoadResponse -> data.episodes.isNotEmpty()
        else -> false
    }
    if (!isLoading && !hasEpisodes) return
    val backupSeasonHistory = remember { mutableMapOf<String, WatchHistory?>() }

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

    val seasons = remember(data) { if (data is TvSeriesLoadResponse) data.episodes.mapNotNull { it.season }.distinct().sorted() else emptyList() }
    var selectedSeason by remember(latestHistory?.season, data) {
        mutableStateOf(if (data is TvSeriesLoadResponse) latestHistory?.season ?: seasons.firstOrNull() ?: 1 else 1)
    }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }
    var selectedEpisodeChunk by remember(data.url) { mutableStateOf(0) }
    LaunchedEffect(selectedSeason, selectedDub, isSortAscending) {
        selectedEpisodeChunk = 0
    }
    var isEpisodesStackedView by remember { mutableStateOf(DesktopDataStore.getKey<Boolean>("pref_episodes_stacked_view") ?: false) }
    var isAntiSpoiler by remember { mutableStateOf(true) }
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
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(vertical = 24.dp),
        ) {
            // Card Header: Episodes title, season watch toggle, and anti-spoiler settings
            if (!isMovieLike) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val currentSeasonEpisodes = (data as? TvSeriesLoadResponse)?.episodes
                        ?.filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        ?: (data as? AnimeLoadResponse)?.episodes?.values?.flatten()
                        ?: emptyList()
                    if (currentSeasonEpisodes.isNotEmpty()) {
                        val isSeasonWatched = currentSeasonEpisodes.all { ep ->
                            val hist = showHistory.values.find { it.episodeId == ep.data }
                            hist != null && PlayerLinkHandler.isCompleted(hist.position, hist.duration)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSeasonWatched) {
                                        Color(0xFF1B4D2E).copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (isSeasonWatched) {
                                        Color(0xFF4CAF50).copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    if (!isSeasonWatched) {
                                        // Marking as watched. Save backup of current states.
                                        backupSeasonHistory.clear()
                                        currentSeasonEpisodes.forEach { ep ->
                                            backupSeasonHistory[ep.data] = showHistory.values.find { it.episodeId == ep.data }
                                            toggleEpisodeWatched(
                                                provider = provider,
                                                data = data,
                                                ep = ep,
                                                isWatched = false, // We are marking it watched
                                            )
                                        }
                                    } else {
                                        // Unmarking. Restore from backup.
                                        val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)
                                        currentSeasonEpisodes.forEach { ep ->
                                            val backup = backupSeasonHistory[ep.data]
                                            if (backup != null) {
                                                DesktopDataStore.setLastWatched(backup)
                                            } else {
                                                // If there was no backup, it means it was previously unwatched. Delete the fake history entry.
                                                DesktopDataStore.removeEpisodeWatched(parentId, ep.data)
                                            }
                                        }
                                        backupSeasonHistory.clear()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isSeasonWatched) "✓ Season Watched" else "Mark Season Watched",
                                color = if (isSeasonWatched) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text("Anti-spoiler", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAntiSpoiler,
                        onCheckedChange = { isAntiSpoiler = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
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
                            if (seasons.size > 1) {
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
                                    ) {
                                        Text(if (selectedSeason == 0) "Specials" else "Season $selectedSeason", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Season")
                                    }
                                    DropdownMenu(
                                        expanded = seasonMenuExpanded,
                                        onDismissRequest = { seasonMenuExpanded = false },
                                    ) {
                                        seasons.forEach { season ->
                                            DropdownMenuItem(
                                                text = { Text(if (season == 0) "Specials" else "Season $season") },
                                                onClick = {
                                                    selectedSeason = season
                                                    seasonMenuExpanded = false
                                                },
                                                trailingIcon = if (selectedSeason == season) {
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

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                Text(
                                    text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    isEpisodesStackedView = !isEpisodesStackedView
                                    DesktopDataStore.setKey("pref_episodes_stacked_view", isEpisodesStackedView)
                                },
                            ) {
                                Icon(
                                    imageVector = if (isEpisodesStackedView) Icons.Default.List else Icons.Default.ViewModule,
                                    contentDescription = "Toggle Episode View",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
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
                            isAntiSpoiler = isAntiSpoiler,
                            coroutineScope = coroutineScope,
                            onPlay = onPlay,
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

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                Text(
                                    text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    isEpisodesStackedView = !isEpisodesStackedView
                                    DesktopDataStore.setKey("pref_episodes_stacked_view", isEpisodesStackedView)
                                },
                            ) {
                                Icon(
                                    imageVector = if (isEpisodesStackedView) Icons.Default.List else Icons.Default.ViewModule,
                                    contentDescription = "Toggle Episode View",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
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
                            isAntiSpoiler = isAntiSpoiler,
                            coroutineScope = coroutineScope,
                            onPlay = onPlay,
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
    isAntiSpoiler: Boolean,
    coroutineScope: CoroutineScope,
    onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit,
) {
    if (isEpisodesStackedView) {
        // BoxWithConstraints gives us the real available pixel width so we can
        // pass an explicit width to each card instead of weight(1f).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            val desiredWidth = 400f
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
                    val history = showHistory.values.find { it.episodeId == ep.data }
                    EpisodeCard(
                        ep = ep,
                        isLatest = isLatest,
                        history = history,
                        provider = provider,
                        data = data,
                        isAntiSpoiler = isAntiSpoiler,
                        modifier = Modifier.width(cardWidth),
                        onPlay = onPlay,
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
                val history = showHistory.values.find { it.episodeId == ep.data }
                EpisodeCard(ep, isLatest, history, provider, data, isAntiSpoiler, modifier = Modifier.width(400.dp), onPlay = onPlay)
            }
        }
    }
}
