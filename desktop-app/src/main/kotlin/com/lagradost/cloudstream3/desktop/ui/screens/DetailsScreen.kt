package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.DesktopThemeColors.*
import com.lagradost.cloudstream3.desktop.ui.screens.details.*
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import dev.chrisbanes.haze.HazeState
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDetailsScreen(navController: NavController, provider: MainAPI, url: String, preloadedName: String? = null, preloadedPoster: String? = null, preloadedBg: String? = null) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember(url) { DetailsViewModel(coroutineScope, provider, url, preloadedName, preloadedPoster, preloadedBg) }

    val response by viewModel.response.collectAsState()
    val fakeData by viewModel.fakeData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeLinkData by viewModel.activeLinkData.collectAsState()
    val isPanelOpen by viewModel.isPanelOpen.collectAsState()
    val enrichmentTrigger by viewModel.enrichmentTrigger.collectAsState()
    val screenshots by viewModel.screenshots.collectAsState()

    var playbackError by remember { mutableStateOf<String?>(null) }
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current

    val handlePlay: (Triple<MainAPI, String, WatchHistory>) -> Unit = { (linkProvider, linkUrl, linkHistory) ->
        val autoPlay = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
        if (autoPlay) {
            val epTitle = buildString {
                append(linkHistory.showName)
                if (linkHistory.season != null && linkHistory.episode != null) {
                    append(" - S${linkHistory.season}E${linkHistory.episode}")
                } else if (linkHistory.episode != null) {
                    append(" - E${linkHistory.episode}")
                }
            }
            val isLive = (response ?: fakeData)?.type == com.lagradost.cloudstream3.TvType.Live
            val resumeMs = if (isLive) 0L else com.lagradost.player.impl.PlayerLinkHandler.resumeStartSeconds(linkHistory.position, linkHistory.duration) * 1000L
            playVideo(
                com.lagradost.cloudstream3.desktop.ui.VideoLaunchData(
                    links = emptyList(),
                    initialIndex = 0,
                    title = epTitle,
                    subtitles = emptyList(),
                    startPositionMs = resumeMs,
                    history = linkHistory,
                    loadResponse = response ?: fakeData,
                    onError = { err -> playbackError = err }
                ),
            )
        } else {
            viewModel.openLinksPanel(Triple(linkProvider, linkUrl, linkHistory))
        }
    }

    if (playbackError != null) {
        AlertDialog(
            onDismissRequest = { playbackError = null },
            title = { Text("Playback Failed") },
            text = { Text(playbackError ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { playbackError = null }) {
                    Text("OK")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Details Content
            if (isLoading) {
                if (fakeData != null) {
                    DetailsContent(navController, provider, fakeData!!, screenshots, enrichmentTrigger, isLoading = true, onPlay = handlePlay)
                } else {
                    DetailsSkeletonPlaceholder(onBack = { navController.goBack() })
                }
            } else if (response != null) {
                DetailsContent(navController, provider, response!!, screenshots, enrichmentTrigger, isLoading = false, onPlay = handlePlay)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (errorMessage != null) "Error: $errorMessage" else "Failed to load details.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.goBack() }) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // 2. Dim Overlay
            AnimatedVisibility(
                visible = isPanelOpen,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { viewModel.closeLinksPanel() },
                )
            }

            // 3. Side Panel with Links
            if (activeLinkData != null) {
                val offsetX by animateDpAsState(
                    targetValue = if (isPanelOpen) 0.dp else 450.dp,
                    animationSpec = tween(300),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = offsetX),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                            .clickable { if (isPanelOpen) viewModel.closeLinksPanel() else viewModel.openLinksPanel(activeLinkData!!) }
                            .padding(16.dp),
                    ) {
                        Icon(
                            if (isPanelOpen) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Toggle links",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(450.dp)
                            .shadow(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0C0C14).copy(alpha = 0.75f),
                                        Color(0xFF1A1A24).copy(alpha = 0.85f),
                                    ),
                                ),
                            ),
                    ) {
                        activeLinkData?.let { (linkProvider, linkUrl, linkHistory) ->
                            LinksSidePanel(
                                provider = linkProvider,
                                dataUrl = linkUrl,
                                history = linkHistory,
                                loadResponse = response, // Passed from ComposeDetailsScreen
                                onClose = { viewModel.closeLinksPanel() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsContent(navController: NavController, provider: MainAPI, data: LoadResponse, screenshots: List<String>?, enrichmentTrigger: Int, isLoading: Boolean = false, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hazeState = remember { HazeState() }

    val historyUpdatesVal = com.lagradost.common.storage.DesktopDataStore.historyUpdates.collectAsState().value
    val latestHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getLatestWatchHistoryForShow(data.url)
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

    val seasons = remember(data) { if (data is TvSeriesLoadResponse) data.episodes.mapNotNull { it.season }.distinct().sorted() else emptyList() }
    var selectedSeason by remember(latestHistory?.season, data) {
        mutableStateOf(if (data is TvSeriesLoadResponse) latestHistory?.season ?: seasons.firstOrNull() ?: 1 else 1)
    }

    val showHistory = remember(data.url, historyUpdatesVal) {
        com.lagradost.common.storage.DesktopDataStore.getAllWatchHistory()
            .filter { it.showUrl == data.url }
            .associateBy { it.episodeId ?: it.parentId }
    }

    var isSortAscending by remember(data.url) { mutableStateOf(true) }
    val chunkSize = 50
    var selectedChunkTv by remember(selectedSeason, isSortAscending, data) { mutableStateOf(0) }
    var selectedChunkAnime by remember(selectedDub, isSortAscending, data) { mutableStateOf(0) }

    var selectedScreenshot by remember { mutableStateOf<String?>(null) }

    val isMovieLike = remember(data) {
        data is MovieLoadResponse || data is TorrentLoadResponse || data is LiveStreamLoadResponse ||
            (data is TvSeriesLoadResponse && data.episodes.size == 1) ||
            (data is AnimeLoadResponse && data.episodes.values.sumOf { it.size } == 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val heroAction: @Composable () -> Unit = {
            // Instead of the awkward Play/Resume button, display a beautiful Provider Badge!
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Info,
                        contentDescription = "Provider",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = provider.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DetailsBackdrop(provider = provider, data = data, scrollState = scrollState, hazeState = hazeState, enrichmentTrigger = enrichmentTrigger, modifier = Modifier.matchParentSize())
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DetailsMetadata(provider = provider, data = data, hazeState = hazeState, heroAction = heroAction, enrichmentTrigger = enrichmentTrigger)
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            if (isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .shimmerBackground()
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(modifier = Modifier.widthIn(max = 1200.dp).padding(horizontal = 32.dp)) {
                            if (isMovieLike) {
                                val ep = when (data) {
                                    is MovieLoadResponse -> provider.newEpisode(data.dataUrl) {
                                        this.name = data.name
                                        this.description = data.plot
                                        this.posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                                    }
                                    is TorrentLoadResponse -> provider.newEpisode(data.torrent ?: data.magnet ?: "") {
                                        this.name = data.name
                                        this.description = data.plot
                                        this.posterUrl = data.posterUrl
                                    }
                                    is LiveStreamLoadResponse -> provider.newEpisode(data.dataUrl) {
                                        this.name = data.name
                                        this.description = data.plot
                                        this.posterUrl = data.backgroundPosterUrl ?: data.posterUrl
                                    }
                                    is TvSeriesLoadResponse -> data.episodes.firstOrNull()
                                    is AnimeLoadResponse -> data.episodes.values.flatten().firstOrNull()
                                    else -> null
                                }
                                if (ep != null) {
                                    val history = showHistory.values.find { it.episodeId == ep.data } ?: latestHistory
                                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                                        MoviePlayCard(ep, history, provider, data, onPlay)
                                    }
                                }
                            }

                            when (data) {
                                is MovieLoadResponse, is TorrentLoadResponse, is LiveStreamLoadResponse -> {
                                    // Handled by isMovieLike
                                }
                                is TvSeriesLoadResponse -> {
                                    if (isMovieLike) return@Column
                                    val allFilteredEpisodes = data.episodes
                                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                                        .let { list ->
                                            if (isSortAscending) {
                                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                                            } else {
                                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                                            }
                                        }
                                    val chunkedEpisodes = allFilteredEpisodes.chunked(chunkSize)

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            if (seasons.isNotEmpty()) {
                                                var expanded by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { expanded = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                                    ) {
                                                        Text("Season $selectedSeason", fontWeight = FontWeight.SemiBold)
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Season")
                                                    }
                                                    AppDropdownMenu(
                                                        expanded = expanded,
                                                        onDismissRequest = { expanded = false },
                                                        modifier = Modifier.heightIn(max = 300.dp),
                                                    ) {
                                                        seasons.forEach { season ->
                                                            DropdownMenuItem(
                                                                text = { Text("Season $season", fontWeight = FontWeight.SemiBold) },
                                                                onClick = {
                                                                    selectedSeason = season
                                                                    expanded = false
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (chunkedEpisodes.size > 1) {
                                                var expandedChunk by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { expandedChunk = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                                    ) {
                                                        val startEp = selectedChunkTv * chunkSize + 1
                                                        val endEp = minOf((selectedChunkTv + 1) * chunkSize, allFilteredEpisodes.size)
                                                        Text("Episodes $startEp - $endEp", fontWeight = FontWeight.SemiBold)
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Episodes")
                                                    }
                                                    AppDropdownMenu(
                                                        expanded = expandedChunk,
                                                        onDismissRequest = { expandedChunk = false },
                                                        modifier = Modifier.heightIn(max = 300.dp),
                                                    ) {
                                                        chunkedEpisodes.forEachIndexed { index, _ ->
                                                            val startEp = index * chunkSize + 1
                                                            val endEp = minOf((index + 1) * chunkSize, allFilteredEpisodes.size)
                                                            DropdownMenuItem(
                                                                text = { Text("Episodes $startEp - $endEp", fontWeight = FontWeight.SemiBold) },
                                                                onClick = {
                                                                    selectedChunkTv = index
                                                                    expandedChunk = false
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                            Text(
                                                text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                is AnimeLoadResponse -> {
                                    if (isMovieLike) return@Column
                                    val allFilteredEpisodes = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                                        .let { list ->
                                            if (isSortAscending) {
                                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                                            } else {
                                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                                            }
                                        }
                                    val chunkedEpisodes = allFilteredEpisodes.chunked(chunkSize)

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            if (dubStatuses.size > 1) {
                                                var expanded by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { expanded = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                                    ) {
                                                        Text(selectedDub?.name ?: "Unknown", fontWeight = FontWeight.SemiBold)
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Dub")
                                                    }
                                                    AppDropdownMenu(
                                                        expanded = expanded,
                                                        onDismissRequest = { expanded = false },
                                                        modifier = Modifier.heightIn(max = 300.dp),
                                                    ) {
                                                        dubStatuses.forEach { dub ->
                                                            DropdownMenuItem(
                                                                text = { Text(dub.name, fontWeight = FontWeight.SemiBold) },
                                                                onClick = {
                                                                    selectedDub = dub
                                                                    expanded = false
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (chunkedEpisodes.size > 1) {
                                                var expandedChunk by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { expandedChunk = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                                    ) {
                                                        val startEp = selectedChunkAnime * chunkSize + 1
                                                        val endEp = minOf((selectedChunkAnime + 1) * chunkSize, allFilteredEpisodes.size)
                                                        Text("Episodes $startEp - $endEp", fontWeight = FontWeight.SemiBold)
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Episodes")
                                                    }
                                                    AppDropdownMenu(
                                                        expanded = expandedChunk,
                                                        onDismissRequest = { expandedChunk = false },
                                                        modifier = Modifier.heightIn(max = 300.dp),
                                                    ) {
                                                        chunkedEpisodes.forEachIndexed { index, _ ->
                                                            val startEp = index * chunkSize + 1
                                                            val endEp = minOf((index + 1) * chunkSize, allFilteredEpisodes.size)
                                                            DropdownMenuItem(
                                                                text = { Text("Episodes $startEp - $endEp", fontWeight = FontWeight.SemiBold) },
                                                                onClick = {
                                                                    selectedChunkAnime = index
                                                                    expandedChunk = false
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        TextButton(onClick = { isSortAscending = !isSortAscending }) {
                                            Text(
                                                text = if (isSortAscending) "Sort ▼" else "Sort ▲",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                if (!isMovieLike && data is TvSeriesLoadResponse) {
                    val allFilteredEpisodes = data.episodes
                        .filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val chunkedEpisodes = allFilteredEpisodes.chunked(chunkSize)
                    val filteredEpisodes = chunkedEpisodes.getOrNull(selectedChunkTv) ?: emptyList()

                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val layoutWidthSetting by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.layoutWidth.collectAsState()
                                val maxWidthConstraint = when (layoutWidthSetting) {
                                    "Compact" -> 1000.dp
                                    "Modern" -> 1400.dp
                                    else -> androidx.compose.ui.unit.Dp.Unspecified
                                }
                                BoxWithConstraints(modifier = Modifier.widthIn(max = maxWidthConstraint).padding(horizontal = 32.dp).animateContentSize()) {
                                    val columns = maxOf(1, (maxWidth / 320.dp).toInt())
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        filteredEpisodes.chunked(columns).forEach { rowEps ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                                rowEps.forEach { ep ->
                                                    val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                                                    val history = showHistory.values.find { it.episodeId == ep.data }
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        EpisodeCard(ep, isLatest, history, provider, data, onPlay)
                                                    }
                                                }
                                                repeat(columns - rowEps.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isMovieLike && data is AnimeLoadResponse) {
                    val allFilteredEpisodes = (selectedDub?.let { data.episodes[it] } ?: emptyList())
                        .let { list ->
                            if (isSortAscending) {
                                list.sortedBy { it.episode ?: Int.MAX_VALUE }
                            } else {
                                list.sortedByDescending { it.episode ?: Int.MIN_VALUE }
                            }
                        }
                    val chunkedEpisodes = allFilteredEpisodes.chunked(chunkSize)
                    val filteredEpisodes = chunkedEpisodes.getOrNull(selectedChunkAnime) ?: emptyList()

                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Coming Soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Episodes are not available yet. Please check back later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val layoutWidthSetting by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.layoutWidth.collectAsState()
                                val maxWidthConstraint = when (layoutWidthSetting) {
                                    "Compact" -> 1000.dp
                                    "Modern" -> 1400.dp
                                    else -> androidx.compose.ui.unit.Dp.Unspecified
                                }
                                BoxWithConstraints(modifier = Modifier.widthIn(max = maxWidthConstraint).padding(horizontal = 32.dp).animateContentSize()) {
                                    val columns = maxOf(1, (maxWidth / 320.dp).toInt())
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        filteredEpisodes.chunked(columns).forEach { rowEps ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                                rowEps.forEach { ep ->
                                                    val isLatest = latestHistory != null && latestHistory.episodeId == ep.data
                                                    val history = showHistory.values.find { it.episodeId == ep.data }
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        EpisodeCard(ep, isLatest, history, provider, data, onPlay)
                                                    }
                                                }
                                                repeat(columns - rowEps.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
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

            if (!screenshots.isNullOrEmpty()) {
                item {
                    Text(
                        text = "Screenshots",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(screenshots, key = { it }) { imgUrl ->
                            Surface(
                                modifier = Modifier
                                    .width(280.dp) // Slightly smaller width so it acts as a divider
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedScreenshot = imgUrl },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                coil3.compose.AsyncImage(
                                    model = imgUrl,
                                    contentDescription = "Screenshot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ── Back button ──
        IconButton(
            onClick = { navController.goBack() },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }

        // ── Screenshot Overlay ──
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedScreenshot != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { selectedScreenshot = null },
                contentAlignment = Alignment.Center,
            ) {
                coil3.compose.AsyncImage(
                    model = selectedScreenshot,
                    contentDescription = "Screenshot Full",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                )
                IconButton(
                    onClick = { selectedScreenshot = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun DetailsSkeletonPlaceholder(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070E))
    ) {
        // Shimmering backdrop simulator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .shimmerBackground()
        )
        
        // Dark vertical scrim overlay matching actual backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF07070E))
                    )
                )
        )
        
        // Metadata alignment
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(start = 32.dp, end = 32.dp, top = 100.dp, bottom = 32.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Shimmering Poster
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .shimmerBackground()
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                    
                    // Shimmering Text Lines
                    Column(modifier = Modifier.weight(1f)) {
                        // Title line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .shimmerBackground()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Metadata badges row
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerBackground()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Plot lines
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Play button row
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .shimmerBackground()
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Cast Section
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerBackground()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(6) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                            Box(modifier = Modifier.size(96.dp).clip(CircleShape).shimmerBackground())
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.width(80.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                // Content/episodes section title
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerBackground()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Shimmering Episode Grid
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .shimmerBackground()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerBackground()
                            )
                        }
                    }
                }
            }
        }

        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
