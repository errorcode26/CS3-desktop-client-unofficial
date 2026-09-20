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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs.SeasonSelectionDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopActionBadge
import com.lagradost.cloudstream3.desktop.ui.components.DesktopFilterChip
import com.lagradost.cloudstream3.desktop.ui.components.DesktopIconButton
import com.lagradost.cloudstream3.desktop.ui.components.desktopDragScroll
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
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
    selectedSeason: Int? = null,
    onSeasonChange: ((Int) -> Unit)? = null,
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

    val dubStatuses = remember(data) {
        if (data is AnimeLoadResponse) {
            data.episodes.filter { it.value.isNotEmpty() }.keys.toList()
        } else emptyList()
    }
    var selectedDub by remember(latestHistory?.episodeId, data) {
        mutableStateOf(
            if (data is AnimeLoadResponse) {
                if (latestHistory != null) {
                    dubStatuses.find { dub -> data.episodes[dub]?.any { it.matchesHistory(latestHistory) } == true } ?: dubStatuses.firstOrNull()
                } else {
                    val topAudio = LanguagePriorityHelper.getOrderedAudioLanguages().firstOrNull() ?: "auto"
                    val prefersDub = topAudio !in listOf("jpn,ja", "original", "auto", "")
                    val preferredDubStatus = if (prefersDub) DubStatus.Dubbed else DubStatus.Subbed
                    dubStatuses.find { it == preferredDubStatus } ?: dubStatuses.firstOrNull()
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
    var localSelectedSeason by remember(latestHistory?.season, data, seasons) {
        mutableStateOf(
            if (data is TvSeriesLoadResponse || data is AnimeLoadResponse) {
                latestHistory?.season?.takeIf { it in seasons } ?: seasons.firstOrNull() ?: 1
            } else {
                1
            },
        )
    }
    val effectiveSeason = remember(selectedSeason, localSelectedSeason, seasons) {
        val target = selectedSeason ?: localSelectedSeason
        if (target in seasons) target else (seasons.firstOrNull() ?: 1)
    }
    val updateSeason: (Int) -> Unit = { newSeason ->
        localSelectedSeason = newSeason
        onSeasonChange?.invoke(newSeason)
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

            val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
            val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
            val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
            val historyLookup = remember(showHistory) { EpisodeHistoryLookup(showHistory) }

            val rawEpisodes: List<Episode> = remember(data, selectedDub, uiState?.episodeThumbnailVersion) {
                when (data) {
                    is AnimeLoadResponse -> {
                        selectedDub?.let { data.episodes[it] }?.takeIf { it.isNotEmpty() }
                            ?: data.episodes.values.firstOrNull { it.isNotEmpty() }
                            ?: emptyList()
                    }
                    is TvSeriesLoadResponse -> data.episodes
                    else -> emptyList()
                }
            }

            val preChunkedEpisodes = remember(rawEpisodes, effectiveSeason, seasons, isSortAscending) {
                val filtered = if (seasons.size <= 1) {
                    rawEpisodes
                } else {
                    rawEpisodes.filter { ep ->
                        ep.season == effectiveSeason || (ep.season == null && effectiveSeason == seasons.first())
                    }
                }
                val resolved = if (filtered.isEmpty() && rawEpisodes.isNotEmpty()) rawEpisodes else filtered
                resolved
                    .distinctBy { ep ->
                        if (ep.episode != null && ep.episode != 0) {
                            "ep:${ep.season ?: 1}:${ep.episode}"
                        } else {
                            "data:${ep.data.ifBlank { ep.name ?: ep.hashCode().toString() }}"
                        }
                    }
                    .let { list ->
                        val hasEpisodeNumbers = list.any { it.episode != null && it.episode != 0 }
                        if (hasEpisodeNumbers) {
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        } else {
                            if (isSortAscending) list else list.reversed()
                        }
                    }
            }
            val targetEpisodeIndex = remember(preChunkedEpisodes, latestHistory) {
                if (latestHistory != null && preChunkedEpisodes.isNotEmpty()) {
                    val isLatestCompleted = latestHistory.duration > 0 &&
                        PlayerLinkHandler.isCompleted(latestHistory.position, latestHistory.duration)
                    val currentIdx = preChunkedEpisodes.indexOfFirst { it.matchesHistory(latestHistory) }
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
            val chunks = remember(preChunkedEpisodes) { preChunkedEpisodes.chunked(20) }
            val currentMode = uiState?.episodeViewMode ?: if (isEpisodesStackedView) 1 else 0
            val safeChunkIndex = if (chunks.isEmpty()) 0 else selectedEpisodeChunk.coerceIn(0, chunks.size - 1)

            LaunchedEffect(effectiveSeason, selectedDub, isSortAscending, data.url, targetEpisodeIndex, currentMode) {
                if (currentMode == 0) {
                    if (targetEpisodeIndex >= 0 && preChunkedEpisodes.isNotEmpty()) {
                        episodesScrollState.scrollToItem(targetEpisodeIndex.coerceIn(0, preChunkedEpisodes.size - 1))
                    } else {
                        episodesScrollState.scrollToItem(0)
                    }
                } else {
                    val targetChunk = if (chunks.isNotEmpty()) (targetEpisodeIndex / 20).coerceIn(0, chunks.size - 1) else 0
                    selectedEpisodeChunk = targetChunk
                    episodesScrollState.scrollToItem(0)
                }
            }

            val allFilteredEpisodes = if (currentMode == 0) {
                preChunkedEpisodes
            } else {
                chunks.getOrNull(safeChunkIndex) ?: emptyList()
            }
            val seasonListState = rememberLazyListState()
            LaunchedEffect(effectiveSeason, seasons) {
                val targetIdx = seasons.indexOf(effectiveSeason)
                if (targetIdx >= 0) {
                    seasonListState.animateScrollToItem(targetIdx)
                }
            }

            val currentSeasonEpisodes = remember(rawEpisodes, effectiveSeason, seasons) {
                val filtered = if (seasons.size <= 1) {
                    rawEpisodes
                } else {
                    rawEpisodes.filter { ep ->
                        ep.season == effectiveSeason || (ep.season == null && effectiveSeason == seasons.first())
                    }
                }
                val resolved = if (filtered.isEmpty() && rawEpisodes.isNotEmpty()) rawEpisodes else filtered
                resolved.distinctBy { ep ->
                    if (ep.episode != null && ep.episode != 0) {
                        "ep:${ep.season ?: 1}:${ep.episode}"
                    } else {
                        "data:${ep.data.ifBlank { ep.name ?: ep.hashCode().toString() }}"
                    }
                }
            }
            val isSeasonWatched = remember(currentSeasonEpisodes, historyLookup) {
                currentSeasonEpisodes.isNotEmpty() && currentSeasonEpisodes.all { ep ->
                    val hist = historyLookup.find(ep)
                    hist != null && PlayerLinkHandler.isCompleted(hist.position, hist.duration)
                }
            }

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
                                            val isSelected = effectiveSeason == season
                                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                            val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                            DesktopFilterChip(
                                                text = seasonName,
                                                isSelected = isSelected,
                                                onClick = { updateSeason(season) },
                                                minWidth = 75.dp,
                                            )
                                        }
                                    }
                                } else {
                                    val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == effectiveSeason }
                                    val currentSeasonName = currentMeta?.name ?: if (effectiveSeason == 0) "Specials" else "Season $effectiveSeason"
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
                                        state = seasonListState,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .desktopDragScroll(seasonListState),
                                    ) {
                                        items(seasons, key = { it }) { season ->
                                            val isSelected = effectiveSeason == season
                                            val meta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == season }
                                            val seasonName = meta?.name ?: if (season == 0) "Specials" else "Season $season"

                                            DesktopFilterChip(
                                                text = seasonName,
                                                isSelected = isSelected,
                                                onClick = { updateSeason(season) },
                                                minWidth = 90.dp,
                                            )
                                        }
                                    }
                                } else {
                                    val currentMeta = uiState?.enrichedSeasonsMetadata?.find { it.seasonNumber == effectiveSeason }
                                    val currentSeasonName = currentMeta?.name ?: if (effectiveSeason == 0) "Specials" else "Season $effectiveSeason"
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
                } else {
                    Column {
                        RenderEpisodesSection(
                            allFilteredEpisodes = allFilteredEpisodes,
                            isEpisodesStackedView = isEpisodesStackedView,
                            episodesScrollState = episodesScrollState,
                            latestHistory = latestHistory,
                            historyLookup = historyLookup,
                            lockUnreleasedEpisodes = lockUnreleasedEpisodes,
                            posterHoverGlowEnabled = posterHoverGlowEnabled,
                            uiCardOpacity = uiCardOpacity,
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

                        if (currentMode != 0 && chunks.size > 1) {
                            // Pagination row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = hPadding, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                chunks.forEachIndexed { index, list ->
                                    val isSelected = safeChunkIndex == index
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

                        val showCarouselArrows = currentMode == 0 && !isCompact
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
        } // BoxWithConstraints

        val allEpisodesList = remember(data) {
            when (data) {
                is TvSeriesLoadResponse -> data.episodes
                is AnimeLoadResponse -> data.episodes.values.flatten()
                else -> emptyList()
            }
        }

        SeasonSelectionDialog(
            show = showSeasonModal,
            onDismissRequest = { showSeasonModal = false },
            seasons = seasons,
            selectedSeason = effectiveSeason,
            onSelectSeason = { updateSeason(it) },
            allEpisodesList = allEpisodesList,
            enrichedSeasonsMetadata = uiState?.enrichedSeasonsMetadata ?: emptyList(),
        )
    }
}

@Composable
private fun RenderEpisodesSection(
    allFilteredEpisodes: List<Episode>,
    isEpisodesStackedView: Boolean,
    episodesScrollState: androidx.compose.foundation.lazy.LazyListState,
    latestHistory: WatchHistory?,
    historyLookup: EpisodeHistoryLookup,
    lockUnreleasedEpisodes: Boolean,
    posterHoverGlowEnabled: Boolean,
    uiCardOpacity: Float,
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
        if (targetIdx > 0) {
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
                allFilteredEpisodes.forEachIndexed { index, ep ->
                    key(if (ep.data.isNotBlank()) "${ep.data}_$index" else "ep_$index") {
                        val isLatest = latestHistory != null && ep.matchesHistory(latestHistory)
                        val history = historyLookup.find(ep)
                        val canMarkPrevious = index > 0 && (ep.episode ?: (index + 1)) > 1
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
                                lockUnreleasedEpisodes = lockUnreleasedEpisodes,
                                uiCardOpacity = uiCardOpacity,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                enableDownloadButtons = enableDownloadButtons,
                                onPlay = onPlay,
                                onDownload = onDownload,
                                onToggleWatched = onToggleWatched,
                                onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                                onMarkPreviousWatched = if (canMarkPrevious) handleMarkPreviousWatched else null,
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
                                lockUnreleasedEpisodes = lockUnreleasedEpisodes,
                                posterHoverGlowEnabled = posterHoverGlowEnabled,
                                modifier = Modifier.width(cardWidth),
                                enableDownloadButtons = enableDownloadButtons,
                                onPlay = onPlay,
                                onDownload = onDownload,
                                onToggleWatched = onToggleWatched,
                                onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                                onMarkPreviousWatched = if (canMarkPrevious) handleMarkPreviousWatched else null,
                            )
                        }
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
                itemsIndexed(allFilteredEpisodes, key = { index, ep ->
                    if (ep.data.isNotBlank()) "${ep.data}_$index" else "ep_$index"
                }) { index, ep ->
                    val isLatest = latestHistory != null && ep.matchesHistory(latestHistory)
                    val history = historyLookup.find(ep)
                    val canMarkPrevious = index > 0 && (ep.episode ?: (index + 1)) > 1
                    EpisodeCard(
                        ep = ep,
                        isLatest = isLatest,
                        history = history,
                        provider = provider,
                        data = data,
                        uiState = uiState,
                        isAntiSpoiler = isAntiSpoiler,
                        thumbnailVersion = uiState?.episodeThumbnailVersion ?: 0,
                        lockUnreleasedEpisodes = lockUnreleasedEpisodes,
                        posterHoverGlowEnabled = posterHoverGlowEnabled,
                        modifier = Modifier.width(cardWidth),
                        enableDownloadButtons = enableDownloadButtons,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onToggleWatched = onToggleWatched,
                        onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                        onMarkPreviousWatched = if (canMarkPrevious) handleMarkPreviousWatched else null,
                    )
                }
            }
        }
    }
}

