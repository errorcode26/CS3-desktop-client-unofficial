package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.torrent.TorrentPlayability
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.P2pTorrentDisclaimerDialog
import com.lagradost.cloudstream3.desktop.ui.components.TorrServerInstallRequiredDialog
import com.lagradost.cloudstream3.desktop.ui.screens.links.LinksViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.StreamLinkCard
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.links.dialogs.DownloadConfirmationDialog
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.VlcPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksSidePanel(
    provider: MainAPI,
    dataUrl: String,
    history: WatchHistory,
    loadResponse: com.lagradost.cloudstream3.LoadResponse?,
    enrichedActors: List<com.lagradost.cloudstream3.ActorData>? = null,
    enrichedLogoUrl: String? = null,
    enrichedBackdropUrl: String? = null,
    onClose: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { LinksViewModel() }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    val vlcPlayer = remember { VlcPlayer() }
    DisposableEffect(vlcPlayer) {
        onDispose {
            vlcPlayer.destroy()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val links = uiState.links
    val statusText = uiState.statusText
    val isScraping = uiState.isScraping

    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    val isVideoPlayerActive = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayerActive.current
    val selectedPlayer = uiState.preferredPlayer
    val isLaunchingPlayer = uiState.isLaunchingPlayer
    val playerLaunchError = uiState.playerLaunchError
    val currentPlayingUrl = uiState.currentPlayingUrl
    val selectedQuality = uiState.selectedQuality
    val selectedFormat = uiState.selectedFormat
    val selectedSource = uiState.selectedSource
    val isP2pEnabled = uiState.isP2pEnabled
    var showPriorityDialog by remember { mutableStateOf(false) }
    var p2pDisclaimerTargetAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var torrServerInstallTargetAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is LinksUiEffect.ShowToast -> {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
                }
                is LinksUiEffect.LaunchVlc -> {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(effect.link, effect.displayTitle, effect.subtitles, effect.startMs)
                        if (!result.isSuccess) {
                            viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Could not start player."))
                            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished("Could not start player."))
                        } else {
                            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
                        }
                    }
                }
                is LinksUiEffect.LaunchEmbeddedPlayer -> {
                    viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
                    playVideo(effect.launchData)
                }
            }
        }
    }

    val displayTitle = remember(history) {
        buildString {
            append(history.showName)
            if (history.season != null && history.episode != null) {
                append(" - S${history.season}E${history.episode}")
            } else if (history.episode != null) {
                append(" - E${history.episode}")
            }
        }
    }

    val availableSources = remember(links, provider.name) {
        val groups = links.groupBy { link ->
            val src = link.source.trim()
            if (src.isNotBlank()) src else provider.name
        }
        groups.map { (srcName, list) ->
            val isAddon = list.any { it.name.startsWith("⚡") } || !srcName.equals(provider.name, ignoreCase = true)
            SourceOption(
                name = srcName,
                count = list.size,
                isAddon = isAddon,
            )
        }.sortedWith(compareByDescending<SourceOption> { it.count }.thenBy { it.name })
    }

    val availableQualities = remember(links) {
        links.groupBy { com.lagradost.cloudstream3.desktop.player.QualityDataHelper.extractEffectiveQuality(it) }
            .map { (qual, list) ->
                QualityOption(
                    qualityValue = qual,
                    label = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(qual),
                    count = list.size,
                )
            }
            .sortedByDescending { it.qualityValue }
    }

    val availableFormats = remember(links) {
        val totalDirect = links.count { link ->
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val isDash = link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
            val isTorrent = DesktopTorrentEngine.isTorrentLink(link)
            !isHls && !isDash && !isTorrent
        }
        val totalHls = links.count { link ->
            link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
        }
        val totalDash = links.count { link ->
            link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
        }
        val totalTorrent = links.count { link ->
            DesktopTorrentEngine.isTorrentLink(link)
        }

        val list = mutableListOf<FormatOption>()
        list.add(FormatOption(StreamFormatFilter.ALL, links.size))
        if (totalDirect > 0) list.add(FormatOption(StreamFormatFilter.DIRECT, totalDirect))
        if (totalHls > 0) list.add(FormatOption(StreamFormatFilter.HLS, totalHls))
        if (totalDash > 0) list.add(FormatOption(StreamFormatFilter.DASH, totalDash))
        if (totalTorrent > 0) list.add(FormatOption(StreamFormatFilter.TORRENT, totalTorrent))
        list
    }

    val filteredLinks = remember(links, selectedQuality, selectedFormat, selectedSource, isP2pEnabled) {
        val base = links.filter { link ->
            val linkSrc = link.source.trim().ifBlank { provider.name }
            val sourceMatches = selectedSource == null || linkSrc.equals(selectedSource, ignoreCase = true)

            val effQual = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.extractEffectiveQuality(link)
            val qualityMatches = selectedQuality == null || effQual == selectedQuality
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val isDash = link.isDash || link.name.contains("DASH", ignoreCase = true) || link.url.contains(".mpd")
            val isTorrent = DesktopTorrentEngine.isTorrentLink(link)
            val isDirect = !isHls && !isDash && !isTorrent

            val formatMatches = when (selectedFormat) {
                StreamFormatFilter.ALL -> true
                StreamFormatFilter.DIRECT -> isDirect
                StreamFormatFilter.HLS -> isHls
                StreamFormatFilter.DASH -> isDash
                StreamFormatFilter.TORRENT -> isTorrent
            }
            sourceMatches && qualityMatches && formatMatches
        }
        if (!DesktopTorrentEngine.isP2pReady && selectedFormat == StreamFormatFilter.ALL) {
            base.sortedBy { DesktopTorrentEngine.isTorrentLink(it) }
        } else {
            base
        }
    }

    LaunchedEffect(dataUrl) {
        val showTitle = history.showName.takeIf { it.isNotBlank() } ?: loadResponse?.name
        val isSeries = loadResponse?.type == com.lagradost.cloudstream3.TvType.TvSeries ||
            loadResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse ||
            history.season != null ||
            history.episode != null
        viewModel.onEvent(
            LinksUiEvent.OnScrape(
                provider = provider,
                dataUrl = dataUrl,
                title = showTitle,
                isSeries = isSeries,
                season = history.season,
                episode = history.episode,
            ),
        )
    }

    val vlcState = vlcPlayer.state.collectAsState().value
    val isAnyPlaying = vlcState.isPlaying
    LaunchedEffect(vlcState.position) {
        val posMs = if (vlcState.isPlaying) vlcState.position else 0L
        val durMs = if (vlcState.isPlaying) vlcState.duration else 0L
        if (posMs > 0 && durMs > 0) {
            val posSec = posMs / 1000L
            if (kotlin.math.abs(posSec - uiState.lastVlcSavedPositionSec) >= 5) {
                viewModel.onEvent(LinksUiEvent.OnUpdateVlcSavedPosition(posSec))
                viewModel.onEvent(LinksUiEvent.OnSaveWatchPosition(history, posMs, durMs))
            }
        }
    }

    DisposableEffect(isAnyPlaying) {
        onDispose {
            if (!isAnyPlaying && vlcState.position > 0 && vlcState.duration > 0) {
                viewModel.onEvent(LinksUiEvent.OnSaveWatchPosition(history, vlcState.position, vlcState.duration))
            }
        }
    }

    LaunchedEffect(isAnyPlaying, isVideoPlayerActive) {
        if (!isAnyPlaying && !isVideoPlayerActive) {
            if (statusText == "Player started." || statusText.startsWith("Playing:")) {
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Ready — ${links.size} stream${if (links.size == 1) "" else "s"} available."))
            }
            viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
        }
    }

    LaunchedEffect(vlcState.error, uiState.embeddedError) {
        val errorMessage = vlcState.error ?: uiState.embeddedError
        if (errorMessage != null) {
            val autoPlay = uiState.autoPlayEnabled
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Link failed. Auto-trying next: ${nextLink.name}"))
                viewModel.onEvent(LinksUiEvent.OnSetEmbeddedError(null))
                viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null))
                delay(800)
                viewModel.onEvent(
                    LinksUiEvent.OnPlayLink(
                        link = nextLink,
                        displayTitle = displayTitle,
                        history = history,
                        loadResponse = loadResponse,
                        currentPlayingUrl = currentPlayingUrl,
                        enrichedActors = enrichedActors,
                        enrichedLogoUrl = enrichedLogoUrl,
                        enrichedBackdropUrl = enrichedBackdropUrl,
                    ),
                )
            } else {
                viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(errorMessage))
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Playback failed: $errorMessage"))
                viewModel.onEvent(LinksUiEvent.OnSetEmbeddedError(null))
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val epSubtitle = if (history.season != null && history.episode != null) {
                            "Season ${history.season} • Episode ${history.episode}"
                        } else if (history.episode != null) {
                            "Episode ${history.episode}"
                        } else {
                            "Movie"
                        }
                        Text(
                            text = history.showName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = epSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            text = "${links.size} Streams",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            val showTitle = history.showName.takeIf { it.isNotBlank() } ?: loadResponse?.name
                            val isSeries = loadResponse?.type == com.lagradost.cloudstream3.TvType.TvSeries ||
                                loadResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse ||
                                history.season != null ||
                                history.episode != null
                            viewModel.onEvent(
                                LinksUiEvent.OnScrape(
                                    provider = provider,
                                    dataUrl = dataUrl,
                                    title = showTitle,
                                    forceRefresh = true,
                                    isSeries = isSeries,
                                    season = history.season,
                                    episode = history.episode,
                                ),
                            )
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Streams",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                HorizontalDivider(color = DesktopUi.Divider.copy(alpha = 0.5f))

                // Scraping Progress & Slim Status
                if (isScraping) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopUi.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = { viewModel.onEvent(LinksUiEvent.OnCancelScrape) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp),
                        ) {
                            Text("Stop", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isLaunchingPlayer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Unified Desktop Control & Source Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Player Selector Toggle (MPV / VLC)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Divider.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf("mpv" to "MPV", "vlc" to "VLC").forEach { (id, label) ->
                                val isSel = selectedPlayer == id
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.onEvent(LinksUiEvent.OnPreferredPlayerChanged(id)) },
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else DesktopUi.TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Addon / Provider Source Chips
                    val sourceScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(sourceScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = selectedSource == null,
                            onClick = { viewModel.onEvent(LinksUiEvent.OnFilterSource(null)) },
                            label = {
                                Text(
                                    "All Sources (${links.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedSource == null) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )

                        availableSources.forEach { source ->
                            val isSelected = selectedSource.equals(source.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onEvent(LinksUiEvent.OnFilterSource(if (isSelected) null else source.name)) },
                                leadingIcon = {
                                    Icon(
                                        if (source.isAddon) Icons.Default.Extension else Icons.Default.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        "${source.name} (${source.count})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Priority Settings Icon Button
                    IconButton(
                        onClick = { showPriorityDialog = true },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Source Priorities",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Quality & Protocol Filter Strip
                if (availableQualities.isNotEmpty() || availableFormats.size > 1) {
                    val filterScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp)
                            .horizontalScroll(filterScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (availableQualities.isNotEmpty()) {
                            FilterChip(
                                selected = selectedQuality == null,
                                onClick = { viewModel.onEvent(LinksUiEvent.OnFilterQuality(null)) },
                                label = {
                                    Text(
                                        "All Qualities",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selectedQuality == null) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DesktopUi.AccentSoft,
                                    selectedLabelColor = DesktopUi.Accent,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )

                            availableQualities.forEach { option ->
                                val isSel = selectedQuality == option.qualityValue
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.onEvent(LinksUiEvent.OnFilterQuality(if (isSel) null else option.qualityValue)) },
                                    label = {
                                        Text(
                                            "${option.label} (${option.count})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DesktopUi.AccentSoft,
                                        selectedLabelColor = DesktopUi.Accent,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }

                        if (availableFormats.size > 1) {
                            Box(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(1.dp)
                                    .background(DesktopUi.Divider.copy(alpha = 0.5f)),
                            )

                            availableFormats.forEach { formatOpt ->
                                val isSel = selectedFormat == formatOpt.format
                                FilterChip(
                                    selected = isSel,
                                    onClick = { viewModel.onEvent(LinksUiEvent.OnFilterFormat(formatOpt.format)) },
                                    label = {
                                        Text(
                                            "${formatOpt.format.label} (${formatOpt.count})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }
                    }
                }

                // Stream Link List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!isScraping && filteredLinks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No Streams Found",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No playable links match the selected filter criteria.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { _, link ->
                        StreamLinkCard(
                            link = link,
                            isP2pEnabled = isP2pEnabled,
                            isPlaying = (currentPlayingUrl == link.url),
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                val playAction: () -> Unit = {
                                    viewModel.onEvent(
                                        LinksUiEvent.OnPlayLink(
                                            link = link,
                                            displayTitle = displayTitle,
                                            history = history,
                                            loadResponse = loadResponse,
                                            currentPlayingUrl = currentPlayingUrl,
                                            enrichedActors = enrichedActors,
                                            enrichedLogoUrl = enrichedLogoUrl,
                                            enrichedBackdropUrl = enrichedBackdropUrl,
                                        ),
                                    )
                                }
                                when (DesktopTorrentEngine.checkTorrentPlayability(link)) {
                                    TorrentPlayability.NEEDS_P2P_ENABLED -> {
                                        p2pDisclaimerTargetAction = playAction
                                    }
                                    TorrentPlayability.NEEDS_TORRSERVER_INSTALL -> {
                                        torrServerInstallTargetAction = playAction
                                    }
                                    TorrentPlayability.READY -> {
                                        playAction()
                                    }
                                }
                            },
                            onDownload = {
                                val downloadAction: () -> Unit = {
                                    viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(link))
                                }
                                when (DesktopTorrentEngine.checkTorrentPlayability(link)) {
                                    TorrentPlayability.NEEDS_P2P_ENABLED -> {
                                        p2pDisclaimerTargetAction = downloadAction
                                    }
                                    TorrentPlayability.NEEDS_TORRSERVER_INSTALL -> {
                                        torrServerInstallTargetAction = downloadAction
                                    }
                                    TorrentPlayability.READY -> {
                                        downloadAction()
                                    }
                                }
                            },
                            onCopy = {
                                if (link.url.isNotBlank()) {
                                    val selection = java.awt.datatransfer.StringSelection(link.url)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("URL copied to clipboard."))
                                }
                            },
                        )
                    }
                }
            }

            // Download Confirmation Dialog
            DownloadConfirmationDialog(
                show = uiState.linkToDownload != null,
                onDismiss = { viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(null)) },
                link = uiState.linkToDownload,
                displayTitle = displayTitle,
                history = history,
                loadResponse = loadResponse,
                providerName = provider.name,
                onConfirmDownload = {
                    val targetLink = uiState.linkToDownload ?: return@DownloadConfirmationDialog
                    viewModel.onEvent(LinksUiEvent.OnSetLinkToDownload(null))
                    com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager.enqueue(
                        canonicalKey = history.showUrl,
                        showName = history.showName,
                        showUrl = history.showUrl,
                        episodeTitle = history.episodeId,
                        posterUrl = history.posterUrl,
                        backdropUrl = loadResponse?.backgroundPosterUrl,
                        season = history.season,
                        episode = history.episode,
                        link = targetLink,
                        apiName = provider.name,
                    )
                },
            )

            // P2P Torrent Disclaimer Dialog
            P2pTorrentDisclaimerDialog(
                show = p2pDisclaimerTargetAction != null,
                onDismiss = { p2pDisclaimerTargetAction = null },
                onConfirm = {
                    val action = p2pDisclaimerTargetAction
                    p2pDisclaimerTargetAction = null
                    viewModel.onEvent(LinksUiEvent.OnP2pEnabledChanged(true))
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("P2P Torrent Streaming enabled")
                    if (!DesktopTorrentEngine.binary.isInstalled()) {
                        torrServerInstallTargetAction = action
                    } else {
                        action?.invoke()
                    }
                },
            )

            // TorrServer Installation Required Dialog
            TorrServerInstallRequiredDialog(
                show = torrServerInstallTargetAction != null,
                onDismiss = { torrServerInstallTargetAction = null },
                onInstalled = {
                    val action = torrServerInstallTargetAction
                    torrServerInstallTargetAction = null
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("TorrServer installed successfully")
                    action?.invoke()
                },
            )

            com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
                show = showPriorityDialog,
                onDismissRequest = { showPriorityDialog = false },
            )

            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
                show = playerLaunchError != null,
                onDismissRequest = { viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null)) },
                title = { Text("Player error") },
                text = { Text(playerLaunchError ?: "") },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(LinksUiEvent.OnPlayerLaunchFinished(null)) }) { Text("OK") }
                },
            )
        }
    }
}

internal data class SourceOption(
    val name: String,
    val count: Int,
    val isAddon: Boolean = false,
)
