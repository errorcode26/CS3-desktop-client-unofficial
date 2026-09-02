package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopActionBadge
import com.lagradost.cloudstream3.desktop.ui.components.DesktopFilterChip
import com.lagradost.cloudstream3.desktop.ui.components.DesktopIconButton
import com.lagradost.cloudstream3.desktop.ui.components.desktopDragScroll
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
    enableDownloadButtons: Boolean = true,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onToggleSeasonWatched: (List<com.lagradost.cloudstream3.Episode>, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
    onToggleEpisodesStackedView: (Boolean) -> Unit,
    onSetEpisodeViewMode: (Int) -> Unit = {},
) {
    val isEpisodesStackedView = uiState?.isEpisodesStackedView == true
    val coroutineScope = rememberCoroutineScope()
    if (isMovieLike) return
    val hasEpisodes = when (data) {
        is TvSeriesLoadResponse -> data.episodes.isNotEmpty()
        is AnimeLoadResponse -> data.episodes.isNotEmpty()
        else -> false
    }
    if (!isLoading && !hasEpisodes) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1414).copy(alpha = 0.7f),
            border = BorderStroke(1.2.dp, Color(0xFFE50914).copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE50914).copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "No Episodes Found on ${provider.name}",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "The provider did not return any streaming links or episodes for this title. You may want to check another provider.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
        return
    }

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
    var showSeasonModal by remember { mutableStateOf(false) }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }
    var selectedEpisodeChunk by remember(data.url) { mutableStateOf(0) }
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 28.dp),
        ) {
            val isCompact = maxWidth < 600.dp
            val hPadding = if (isCompact) 12.dp else if (maxWidth < 1100.dp) 24.dp else 64.dp

            when (data) {
                is MovieLoadResponse, is TorrentLoadResponse, is LiveStreamLoadResponse -> {
                    // Handled by isMovieLike above
                }
                is TvSeriesLoadResponse -> {
                    if (isMovieLike) return@BoxWithConstraints
                    val preChunkedEpisodes = data.episodes
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val targetEpisodeIndex = remember(preChunkedEpisodes, latestHistory) {
                        if (latestHistory != null && preChunkedEpisodes.isNotEmpty()) {
                            val isLatestCompleted = latestHistory.duration > 0 &&
                                PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)
                            val currentIdx = preChunkedEpisodes.indexOfFirst { it.data == latestHistory.episodeId }
                            if (currentIdx != -1) {
                                if (isLatestCompleted && currentIdx + 1 < preChunkedEpisodes.size) {
                                    currentIdx + 1
                                } else {
                                    currentIdx
                                }
                            } else {
                                0
                            }
                        } else {
                            0
                        }
                    }
                    val chunks = preChunkedEpisodes.chunked(20)
                    LaunchedEffect(selectedSeason, isSortAscending, data.url, targetEpisodeIndex) {
                        val targetChunk = if (chunks.isNotEmpty()) (targetEpisodeIndex / 20).coerceIn(0, chunks.size - 1) else 0
                        selectedEpisodeChunk = targetChunk
                        val targetInChunk = targetEpisodeIndex % 20
                        if (targetInChunk > 0) {
                            episodesScrollState.scrollToItem(targetInChunk)
                        } else {
                            episodesScrollState.scrollToItem(0)
                        }
                    }
                    if (selectedEpisodeChunk >= chunks.size) selectedEpisodeChunk = 0
                    val allFilteredEpisodes = chunks.getOrNull(selectedEpisodeChunk) ?: emptyList()
                    val seasonListState = rememberLazyListState()
                    LaunchedEffect(selectedSeason, seasons) {
                        val targetIdx = seasons.indexOf(selectedSeason)
                        if (targetIdx >= 0) {
                            seasonListState.animateScrollToItem(targetIdx)
                        }
                    }

                    val currentSeasonEpisodes = data.episodes
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                    val isSeasonWatched = currentSeasonEpisodes.isNotEmpty() && currentSeasonEpisodes.all { ep ->
                        val hist = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                        hist != null && PlayerLinkHandler.isCompleted(hist.position, hist.duration)
                    }
                    val currentMode = uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (seasons.size <= 1) {
                                            val singleSeason = seasons.firstOrNull() ?: 1
                                            val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == singleSeason }
                                            Text(
                                                text = selectedMeta?.name ?: if (singleSeason == 0) "Specials" else "Season $singleSeason",
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (seasons.size <= 4) {
                                            LazyRow(
                                                state = seasonListState,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                items(seasons, key = { it }) { season ->
                                                    val isSelected = selectedSeason == season
                                                    val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                    val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                                    DesktopFilterChip(
                                                        text = seasonName,
                                                        isSelected = isSelected,
                                                        onClick = { selectedSeason = season },
                                                        minWidth = 75.dp,
                                                    )
                                                }
                                            }
                                        } else {
                                            val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                            val currentSeasonName = currentMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason"
                                            SeasonSelectorButton(
                                                seasonName = currentSeasonName,
                                                onClick = { showSeasonModal = true },
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    DesktopFilterChip(
                                        text = if (isSortAscending) "▼" else "▲",
                                        isSelected = false,
                                        onClick = { isSortAscending = !isSortAscending },
                                        minWidth = 36.dp,
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    DesktopIconButton(
                                        icon = when (currentMode) {
                                            0 -> Icons.Default.ViewCarousel
                                            1 -> Icons.Default.GridView
                                            2 -> Icons.Default.TableRows
                                            else -> Icons.Default.ViewCarousel
                                        },
                                        contentDescription = when (currentMode) {
                                            0 -> "Current: Carousel (Click for Grid)"
                                            1 -> "Current: Grid (Click for List)"
                                            2 -> "Current: List (Click for Carousel)"
                                            else -> "Toggle Episode View"
                                        },
                                        onClick = {
                                            val nextMode = (currentMode + 1) % 3
                                            onSetEpisodeViewMode(nextMode)
                                        },
                                        isActive = false,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (currentSeasonEpisodes.isNotEmpty()) {
                                        DesktopActionBadge(
                                            text = if (isSeasonWatched) "✓ Watched" else "Mark Watched",
                                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                                            isActive = isSeasonWatched,
                                            activeColor = Color(0xFF4ADE80),
                                        )
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(!isAntiSpoiler) }
                                                .padding(horizontal = 8.dp),
                                        ) {
                                            Text(
                                                "Anti-spoiler",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isAntiSpoiler) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAntiSpoiler) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Switch(
                                                checked = isAntiSpoiler,
                                                onCheckedChange = { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(it) },
                                                modifier = Modifier.scale(0.7f),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Desktop single-line header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                        } else if (seasons.size <= 4) {
                                            LazyRow(
                                                state = seasonListState,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .desktopDragScroll(seasonListState),
                                            ) {
                                                items(seasons, key = { it }) { season ->
                                                    val isSelected = selectedSeason == season
                                                    val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                    val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                                    DesktopFilterChip(
                                                        text = seasonName,
                                                        isSelected = isSelected,
                                                        onClick = { selectedSeason = season },
                                                        minWidth = 90.dp,
                                                    )
                                                }
                                            }
                                        } else {
                                            val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                            val currentSeasonName = currentMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason"
                                            SeasonSelectorButton(
                                                seasonName = currentSeasonName,
                                                onClick = { showSeasonModal = true },
                                            )
                                        }

                                        if (currentSeasonEpisodes.isNotEmpty()) {
                                            Text(
                                                text = "•   ${currentSeasonEpisodes.size} ${if (currentSeasonEpisodes.size == 1) "Episode" else "Episodes"}",
                                                color = Color.White.copy(alpha = 0.55f),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (currentSeasonEpisodes.isNotEmpty()) {
                                        DesktopActionBadge(
                                            text = if (isSeasonWatched) "✓ Season Watched" else "Mark Season Watched",
                                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                                            isActive = isSeasonWatched,
                                            activeColor = Color(0xFF4ADE80),
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(!isAntiSpoiler) }
                                                .padding(horizontal = 12.dp),
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

                                    DesktopFilterChip(
                                        text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                        isSelected = false,
                                        onClick = { isSortAscending = !isSortAscending },
                                        minWidth = 70.dp,
                                    )

                                    DesktopIconButton(
                                        icon = when (currentMode) {
                                            0 -> Icons.Default.ViewCarousel
                                            1 -> Icons.Default.GridView
                                            2 -> Icons.Default.TableRows
                                            else -> Icons.Default.ViewCarousel
                                        },
                                        contentDescription = when (currentMode) {
                                            0 -> "Current: Carousel (Click for Grid)"
                                            1 -> "Current: Grid (Click for List)"
                                            2 -> "Current: List (Click for Carousel)"
                                            else -> "Toggle Episode View"
                                        },
                                        onClick = {
                                            val nextMode = (currentMode + 1) % 3
                                            onSetEpisodeViewMode(nextMode)
                                        },
                                        isActive = false,
                                    )
                                }
                            }
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding, vertical = 12.dp),
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
                                        onClick = {
                                            selectedEpisodeChunk = index
                                            coroutineScope.launch { episodesScrollState.scrollToItem(0) }
                                        },
                                        height = 36.dp,
                                        modifier = Modifier.padding(horizontal = 4.dp),
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
                        val showCarouselArrows = (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) == 0 && !isCompact
                        if (showCarouselArrows) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding), horizontalArrangement = Arrangement.End) {
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
                }
                is AnimeLoadResponse -> {
                    if (isMovieLike) return@BoxWithConstraints
                    val preChunkedEpisodes: List<Episode> = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val targetEpisodeIndex = remember(preChunkedEpisodes, latestHistory) {
                        if (latestHistory != null && preChunkedEpisodes.isNotEmpty()) {
                            val isLatestCompleted = latestHistory.duration > 0 &&
                                PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)
                            val currentIdx = preChunkedEpisodes.indexOfFirst { it.data == latestHistory.episodeId }
                            if (currentIdx != -1) {
                                if (isLatestCompleted && currentIdx + 1 < preChunkedEpisodes.size) {
                                    currentIdx + 1
                                } else {
                                    currentIdx
                                }
                            } else {
                                0
                            }
                        } else {
                            0
                        }
                    }
                    val chunks = preChunkedEpisodes.chunked(20)
                    LaunchedEffect(selectedSeason, selectedDub, isSortAscending, data.url, targetEpisodeIndex) {
                        val targetChunk = if (chunks.isNotEmpty()) (targetEpisodeIndex / 20).coerceIn(0, chunks.size - 1) else 0
                        selectedEpisodeChunk = targetChunk
                        val targetInChunk = targetEpisodeIndex % 20
                        if (targetInChunk > 0) {
                            episodesScrollState.scrollToItem(targetInChunk)
                        } else {
                            episodesScrollState.scrollToItem(0)
                        }
                    }
                    if (selectedEpisodeChunk >= chunks.size) selectedEpisodeChunk = 0
                    val allFilteredEpisodes = chunks.getOrNull(selectedEpisodeChunk) ?: emptyList()
                    val animeSeasonListState = rememberLazyListState()
                    LaunchedEffect(selectedSeason, seasons) {
                        val targetIdx = seasons.indexOf(selectedSeason)
                        if (targetIdx >= 0) {
                            animeSeasonListState.animateScrollToItem(targetIdx)
                        }
                    }

                    val currentSeasonEpisodes = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                    val isSeasonWatched = currentSeasonEpisodes.isNotEmpty() && currentSeasonEpisodes.all { ep ->
                        val hist = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                        hist != null && PlayerLinkHandler.isCompleted(hist.position, hist.duration)
                    }
                    val currentMode = uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (seasons.size <= 1) {
                                            val singleSeason = seasons.firstOrNull() ?: 1
                                            val selectedMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == singleSeason }
                                            Text(
                                                text = selectedMeta?.name ?: if (singleSeason == 0) "Specials" else "Season $singleSeason",
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (seasons.size <= 4) {
                                            LazyRow(
                                                state = animeSeasonListState,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                items(seasons, key = { it }) { season ->
                                                    val isSelected = selectedSeason == season
                                                    val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                    val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                                    DesktopFilterChip(
                                                        text = seasonName,
                                                        isSelected = isSelected,
                                                        onClick = { selectedSeason = season },
                                                        minWidth = 75.dp,
                                                    )
                                                }
                                            }
                                        } else {
                                            val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                            val currentSeasonName = currentMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason"
                                            SeasonSelectorButton(
                                                seasonName = currentSeasonName,
                                                onClick = { showSeasonModal = true },
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    DesktopFilterChip(
                                        text = if (isSortAscending) "▼" else "▲",
                                        isSelected = false,
                                        onClick = { isSortAscending = !isSortAscending },
                                        minWidth = 36.dp,
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    DesktopIconButton(
                                        icon = when (currentMode) {
                                            0 -> Icons.Default.ViewCarousel
                                            1 -> Icons.Default.GridView
                                            2 -> Icons.Default.TableRows
                                            else -> Icons.Default.ViewCarousel
                                        },
                                        contentDescription = when (currentMode) {
                                            0 -> "Current: Carousel (Click for Grid)"
                                            1 -> "Current: Grid (Click for List)"
                                            2 -> "Current: List (Click for Carousel)"
                                            else -> "Toggle Episode View"
                                        },
                                        onClick = {
                                            val nextMode = (currentMode + 1) % 3
                                            onSetEpisodeViewMode(nextMode)
                                        },
                                        isActive = false,
                                    )
                                }

                                if (dubStatuses.size > 1) {
                                    SubDubSegmentedSwitch(
                                        dubStatuses = dubStatuses,
                                        selectedDub = selectedDub,
                                        onSelectDub = { selectedDub = it },
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    if (currentSeasonEpisodes.isNotEmpty()) {
                                        DesktopActionBadge(
                                            text = if (isSeasonWatched) "✓ Watched" else "Mark Watched",
                                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                                            isActive = isSeasonWatched,
                                            activeColor = Color(0xFF4ADE80),
                                        )
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(!isAntiSpoiler) }
                                                .padding(horizontal = 8.dp),
                                        ) {
                                            Text(
                                                "Anti-spoiler",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isAntiSpoiler) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAntiSpoiler) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Switch(
                                                checked = isAntiSpoiler,
                                                onCheckedChange = { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(it) },
                                                modifier = Modifier.scale(0.7f),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Desktop single-line header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                        } else if (seasons.size <= 4) {
                                            LazyRow(
                                                state = animeSeasonListState,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .desktopDragScroll(animeSeasonListState),
                                            ) {
                                                items(seasons, key = { it }) { season ->
                                                    val isSelected = selectedSeason == season
                                                    val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                                    val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                                    DesktopFilterChip(
                                                        text = seasonName,
                                                        isSelected = isSelected,
                                                        onClick = { selectedSeason = season },
                                                        minWidth = 90.dp,
                                                    )
                                                }
                                            }
                                        } else {
                                            val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == selectedSeason }
                                            val currentSeasonName = currentMeta?.name ?: if (selectedSeason == 0) "Specials" else "Season $selectedSeason"
                                            SeasonSelectorButton(
                                                seasonName = currentSeasonName,
                                                onClick = { showSeasonModal = true },
                                            )
                                        }

                                        if (currentSeasonEpisodes.isNotEmpty()) {
                                            Text(
                                                text = "•   ${currentSeasonEpisodes.size} ${if (currentSeasonEpisodes.size == 1) "Episode" else "Episodes"}",
                                                color = Color.White.copy(alpha = 0.55f),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }

                                    if (dubStatuses.size > 1) {
                                        SubDubSegmentedSwitch(
                                            dubStatuses = dubStatuses,
                                            selectedDub = selectedDub,
                                            onSelectDub = { selectedDub = it },
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (currentSeasonEpisodes.isNotEmpty()) {
                                        DesktopActionBadge(
                                            text = if (isSeasonWatched) "✓ Season Watched" else "Mark Season Watched",
                                            onClick = { onToggleSeasonWatched(currentSeasonEpisodes, !isSeasonWatched) },
                                            isActive = isSeasonWatched,
                                            activeColor = Color(0xFF4ADE80),
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable { com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.setAntiSpoilerEnabled(!isAntiSpoiler) }
                                                .padding(horizontal = 12.dp),
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

                                    DesktopFilterChip(
                                        text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                        isSelected = false,
                                        onClick = { isSortAscending = !isSortAscending },
                                        minWidth = 70.dp,
                                    )

                                    DesktopIconButton(
                                        icon = when (currentMode) {
                                            0 -> Icons.Default.ViewCarousel
                                            1 -> Icons.Default.GridView
                                            2 -> Icons.Default.TableRows
                                            else -> Icons.Default.ViewCarousel
                                        },
                                        contentDescription = when (currentMode) {
                                            0 -> "Current: Carousel (Click for Grid)"
                                            1 -> "Current: Grid (Click for List)"
                                            2 -> "Current: List (Click for Carousel)"
                                            else -> "Toggle Episode View"
                                        },
                                        onClick = {
                                            val nextMode = (currentMode + 1) % 3
                                            onSetEpisodeViewMode(nextMode)
                                        },
                                        isActive = false,
                                    )
                                }
                            }
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding, vertical = 12.dp),
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
                                        onClick = {
                                            selectedEpisodeChunk = index
                                            coroutineScope.launch { episodesScrollState.scrollToItem(0) }
                                        },
                                        height = 36.dp,
                                        modifier = Modifier.padding(horizontal = 4.dp),
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
                        val showCarouselArrows = (uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0) == 0 && !isCompact
                        if (showCarouselArrows) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding), horizontalArrangement = Arrangement.End) {
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
                }
                else -> {}
            }
        } // BoxWithConstraints

        if (showSeasonModal) {
            val allEpisodesList = remember(data) {
                when (data) {
                    is TvSeriesLoadResponse -> data.episodes
                    is AnimeLoadResponse -> data.episodes.values.flatten()
                    else -> emptyList()
                }
            }

            CloudstreamCustomDialog(
                show = showSeasonModal,
                onDismissRequest = { showSeasonModal = false },
                modifier = Modifier
                    .widthIn(min = 340.dp, max = 440.dp)
                    .heightIn(max = 520.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Select Season",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = { showSeasonModal = false },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(seasons, key = { it }) { season ->
                            val isSelected = season == selectedSeason
                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                            val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"
                            val epCount = allEpisodesList.count { it.season == season || (it.season == null && season == 1) }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSeason = season
                                        showSeasonModal = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Text(
                                            text = seasonName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 15.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    if (epCount > 0) {
                                        Text(
                                            text = "$epCount Episodes",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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

    val currentMode = uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0

    if (currentMode == 1 || currentMode == 2) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 600.dp
            val hPadding = if (isCompact) 12.dp else if (maxWidth < 1100.dp) 24.dp else 64.dp
            val desiredWidth = if (isCompact) 280f else 340f
            val columns = if (currentMode == 2) 1 else maxOf(1, kotlin.math.round(maxWidth.value / desiredWidth).toInt())
            val gapDp = if (isCompact) 10.dp else 14.dp
            val totalGapDp = gapDp * (columns - 1)
            val cardWidth = if (currentMode == 2) maxWidth else (maxWidth - (hPadding * 2) - totalGapDp - 1.dp) / columns

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding),
                horizontalArrangement = Arrangement.spacedBy(gapDp),
                verticalArrangement = Arrangement.spacedBy(gapDp),
                maxItemsInEachRow = columns,
            ) {
                allFilteredEpisodes.forEach { ep ->
                    val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                    val history = showHistory.values.find { (it.episodeId ?: "") == ep.data }
                    if (currentMode == 2) {
                        EpisodeListItem(
                            ep = ep,
                            isLatest = isLatest,
                            history = history,
                            provider = provider,
                            data = data,
                            uiState = uiState,
                            isAntiSpoiler = isAntiSpoiler,
                            thumbnailVersion = uiState?.episodeThumbnailVersion ?: 0,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            enableDownloadButtons = enableDownloadButtons,
                            onPlay = onPlay,
                            onDownload = onDownload,
                            onToggleWatched = onToggleWatched,
                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                            onMarkPreviousWatched = handleMarkPreviousWatched,
                        )
                    } else {
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
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 600.dp
            val hPadding = if (isCompact) 12.dp else if (maxWidth < 1100.dp) 24.dp else 64.dp
            val itemSpacing = if (isCompact) 10.dp else 16.dp

            val cardWidth = remember(maxWidth) {
                val netWidth = maxWidth - (hPadding * 2)
                when {
                    maxWidth < 600.dp -> (netWidth * 0.85f).coerceIn(280.dp, 340.dp)
                    maxWidth < 1100.dp -> ((netWidth - itemSpacing * 2) / 2.3f).coerceIn(320.dp, 390.dp)
                    maxWidth < 1600.dp -> ((netWidth - itemSpacing * 3) / 3.4f).coerceIn(360.dp, 440.dp)
                    maxWidth < 2200.dp -> ((netWidth - itemSpacing * 4) / 4.4f).coerceIn(380.dp, 460.dp)
                    else -> ((netWidth - itemSpacing * 5) / 5.4f).coerceIn(400.dp, 480.dp)
                }
            }
            LazyRow(
                state = episodesScrollState,
                contentPadding = PaddingValues(horizontal = hPadding, vertical = if (isCompact) 6.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                modifier = Modifier.fillMaxWidth().desktopDragScroll(episodesScrollState),
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

@Composable
private fun SeasonSelectorButton(
    seasonName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = seasonName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Select Season",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SubDubSegmentedSwitch(
    dubStatuses: List<DubStatus>,
    selectedDub: DubStatus?,
    onSelectDub: (DubStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dubStatuses.size <= 1) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(3.dp),
        ) {
            dubStatuses.forEach { dub ->
                val isSelected = selectedDub == dub
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                val label = when (dub) {
                    DubStatus.Subbed -> "SUB"
                    DubStatus.Dubbed -> "DUB"
                    else -> dub.name.uppercase()
                }

                Surface(
                    onClick = { onSelectDub(dub) },
                    shape = RoundedCornerShape(7.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (isHovered) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
        }
    }
}
