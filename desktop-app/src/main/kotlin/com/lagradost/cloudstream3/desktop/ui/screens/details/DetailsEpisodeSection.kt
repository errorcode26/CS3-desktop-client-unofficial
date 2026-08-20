package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.DesktopActionBadge
import com.lagradost.cloudstream3.desktop.ui.components.DesktopFilterChip
import com.lagradost.cloudstream3.desktop.ui.components.DesktopIconButton
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
                        DesktopActionBadge(
                            text = if (isSeasonWatched) "✓ Season Watched" else "Mark Season Watched",
                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                            isActive = isSeasonWatched,
                            activeColor = Color(0xFF4ADE80)
                        )
                    }

                    // Anti-spoiler
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(!isAntiSpoiler) }
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                "Anti-spoiler",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isAntiSpoiler) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAntiSpoiler) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isAntiSpoiler,
                                onCheckedChange = { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(it) },
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                ),
                            )
                        }
                    }

                    // Sort Button
                    DesktopFilterChip(
                        text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                        isSelected = false,
                        onClick = { isSortAscending = !isSortAscending },
                        minWidth = 70.dp
                    )

                    // View Toggle (Single Button)
                    DesktopIconButton(
                        icon = if (isEpisodesStackedView) Icons.AutoMirrored.Filled.List else Icons.Default.ViewModule,
                        contentDescription = "Toggle Episode View",
                        onClick = { onToggleEpisodesStackedView(!isEpisodesStackedView) },
                        isActive = false
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
                    val seasonListState = rememberLazyListState()
                    LaunchedEffect(selectedSeason, seasons) {
                        val targetIdx = seasons.indexOf(selectedSeason)
                        if (targetIdx >= 0) {
                            seasonListState.animateScrollToItem(targetIdx)
                        }
                    }

                    // Season selector + sort
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (seasons.isNotEmpty()) {
                                if (seasons.size == 1) {
                                    val singleSeason = seasons.first()
                                    val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == singleSeason }
                                    Text(
                                        text = selectedMeta?.name ?: if (singleSeason == 0) "Specials" else "Season $singleSeason",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                } else {
                                    LazyRow(
                                        state = seasonListState,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .pointerInput(Unit) {
                                                detectHorizontalDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    seasonListState.dispatchRawDelta(-dragAmount)
                                                }
                                            },
                                    ) {
                                        items(seasons, key = { it }) { season ->
                                            val isSelected = selectedSeason == season
                                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                            val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                            DesktopFilterChip(
                                                text = seasonName,
                                                isSelected = isSelected,
                                                onClick = { selectedSeason = season },
                                                minWidth = 90.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        rightSideControls()
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (allFilteredEpisodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            Text("No episodes available for this season", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (chunks.size > 1 && (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) != 0) {
                        Column {
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
                                onToggleSeasonWatched = onToggleSeasonWatched,
                                onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                            )

                            // Pagination row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                chunks.forEachIndexed { index, list ->
                                    val isSelected = selectedEpisodeChunk == index
                                    val startEp = list.firstOrNull()?.episode ?: (index * 20 + 1)
                                    val endEp = list.lastOrNull()?.episode ?: ((index + 1) * 20)
                                    
                                    DesktopFilterChip(
                                        text = "$startEp - $endEp",
                                        isSelected = isSelected,
                                        onClick = { selectedEpisodeChunk = index },
                                        height = 36.dp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
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
                            onToggleSeasonWatched = onToggleSeasonWatched,
                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        )
                        val showCarouselArrows = (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) == 0
                        if (showCarouselArrows) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DesktopIconButton(
                                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Scroll Left",
                                        onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(-600f) } }
                                    )
                                    DesktopIconButton(
                                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Scroll Right",
                                        onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(600f) } }
                                    )
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
                    val allFilteredEpisodes = chunks.getOrNull(selectedEpisodeChunk) ?: emptyList()
                    val animeSeasonListState = rememberLazyListState()
                    LaunchedEffect(selectedSeason, seasons) {
                        val targetIdx = seasons.indexOf(selectedSeason)
                        if (targetIdx >= 0) {
                            animeSeasonListState.animateScrollToItem(targetIdx)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (seasons.isNotEmpty()) {
                                if (seasons.size == 1) {
                                    val singleSeason = seasons.first()
                                    val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == singleSeason }
                                    Text(
                                        text = selectedMeta?.name ?: if (singleSeason == 0) "Specials" else "Season $singleSeason",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                } else {
                                    LazyRow(
                                        state = animeSeasonListState,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .pointerInput(Unit) {
                                                detectHorizontalDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    animeSeasonListState.dispatchRawDelta(-dragAmount)
                                                }
                                            },
                                    ) {
                                        items(seasons, key = { it }) { season ->
                                            val isSelected = selectedSeason == season
                                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                            val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                            DesktopFilterChip(
                                                text = seasonName,
                                                isSelected = isSelected,
                                                onClick = { selectedSeason = season },
                                                minWidth = 90.dp
                                            )
                                        }
                                    }
                                }
                            }

                            if (dubStatuses.size > 1) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    dubStatuses.forEach { dub ->
                                        val isSelected = selectedDub == dub
                                        DesktopFilterChip(
                                            text = dub.name,
                                            isSelected = isSelected,
                                            onClick = { selectedDub = dub },
                                            minWidth = 80.dp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                        rightSideControls()
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (allFilteredEpisodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            Text("No episodes available for this season", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (chunks.size > 1 && (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) != 0) {
                        Column {
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
                                onToggleSeasonWatched = onToggleSeasonWatched,
                                onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                            )

                            // Pagination row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                chunks.forEachIndexed { index, list ->
                                    val isSelected = selectedEpisodeChunk == index
                                    val startEp = list.firstOrNull()?.episode ?: (index * 20 + 1)
                                    val endEp = list.lastOrNull()?.episode ?: ((index + 1) * 20)
                                    
                                    DesktopFilterChip(
                                        text = "$startEp - $endEp",
                                        isSelected = isSelected,
                                        onClick = { selectedEpisodeChunk = index },
                                        height = 36.dp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
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
                            onToggleSeasonWatched = onToggleSeasonWatched,
                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        )
                        val showCarouselArrows = (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) == 0
                        if (showCarouselArrows) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DesktopIconButton(
                                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Scroll Left",
                                        onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(-600f) } }
                                    )
                                    DesktopIconButton(
                                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Scroll Right",
                                        onClick = { coroutineScope.launch { episodesScrollState.animateScrollBy(600f) } }
                                    )
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
    onToggleSeasonWatched: (List<com.lagradost.cloudstream3.Episode>, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
) {
    val handleMarkPreviousWatched: (com.lagradost.cloudstream3.Episode) -> Unit = { targetEp ->
        val targetIdx = allFilteredEpisodes.indexOfFirst { it.data == targetEp.data }
        if (targetIdx >= 0) {
            val epsToMark = allFilteredEpisodes.take(targetIdx + 1)
            onToggleSeasonWatched(epsToMark, true)
        }
    }

    if (isEpisodesStackedView) {
        // BoxWithConstraints gives us the real available pixel width so we can
        // pass an explicit width to each card instead of weight(1f).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            val desiredWidth = 560f
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
                        onMarkPreviousWatched = handleMarkPreviousWatched,
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = remember(maxWidth) {
                when {
                    maxWidth >= 1800.dp -> 580.dp
                    maxWidth >= 1400.dp -> 540.dp
                    maxWidth >= 1000.dp -> 480.dp
                    else -> minOf(420.dp, maxWidth * 0.85f)
                }
            }
            LazyRow(
                state = episodesScrollState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
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
                        modifier = Modifier.width(cardWidth),
                        enableDownloadButtons = enableDownloadButtons,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onToggleWatched = onToggleWatched,
                        onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        onMarkPreviousWatched = handleMarkPreviousWatched,
                    )
                }
            }
        }
    }
}
