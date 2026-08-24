package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.screens.links.LinksViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent
import com.lagradost.cloudstream3.utils.ExtractorLink
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

    // Observe unified MVI state
    val uiState by viewModel.uiState.collectAsState()
    val links = uiState.links
    val subtitles = uiState.subtitles
    val statusText = uiState.statusText
    val isScraping = uiState.isScraping

    // Local UI-only state (player launch feedback, filters)
    val playVideo = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayer.current
    val isVideoPlayerActive = com.lagradost.cloudstream3.desktop.ui.LocalVideoPlayerActive.current
    val selectedPlayer = uiState.preferredPlayer
    var isLaunchingPlayer by remember { mutableStateOf(false) }
    var playerLaunchError by remember { mutableStateOf<String?>(null) }
    var embeddedError by remember { mutableStateOf<String?>(null) }
    var currentPlayingUrl by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<Int?>(null) }
    var selectedType by remember { mutableStateOf("All") }
    var showPriorityDialog by remember { mutableStateOf(false) }
    val availableTypes = listOf("All", "HLS (Fast Stream)", "MP4 (Downloadable)")

    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect.ShowToast -> {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect.NotifyLaunching -> {
                    isLaunchingPlayer = effect.isLaunching
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect.NotifyCurrentUrl -> {
                    currentPlayingUrl = effect.url
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect.LaunchVlc -> {
                    coroutineScope.launch {
                        val result = vlcPlayer.play(effect.link, effect.displayTitle, effect.subtitles, effect.startMs)
                        if (!result.isSuccess) {
                            viewModel.onEvent(com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent.OnStatusTextChanged("Could not start player."))
                        }
                        isLaunchingPlayer = false
                        currentPlayingUrl = null
                    }
                }
                is com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEffect.LaunchEmbeddedPlayer -> {
                    isLaunchingPlayer = false
                    currentPlayingUrl = null
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

    val availableQualities = remember(links) {
        links.groupBy { it.quality }
            .map { (qual, list) ->
                QualityOption(
                    qualityValue = qual,
                    label = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(qual),
                    count = list.size,
                )
            }
            .sortedByDescending { it.qualityValue }
    }
    val filteredLinks = remember(links, selectedQuality, selectedType) {
        links.filter { link ->
            val qualityMatches = selectedQuality == null || link.quality == selectedQuality
            val isHls = link.isM3u8 || link.name.contains("HLS", ignoreCase = true) || link.url.contains(".m3u8")
            val typeMatches = when (selectedType) {
                "HLS (Fast Stream)" -> isHls
                "MP4 (Downloadable)" -> !isHls
                else -> true
            }
            qualityMatches && typeMatches
        }
    }

    // Kick off scraping whenever dataUrl changes
    LaunchedEffect(dataUrl) {
        viewModel.onEvent(LinksUiEvent.OnScrape(provider, dataUrl))
    }

    // VLC state observations
    val vlcState = vlcPlayer.state.collectAsState().value
    val isAnyPlaying = vlcState.isPlaying
    var lastVlcSavedPositionSec by remember { mutableStateOf(0L) }

    LaunchedEffect(vlcState.position) {
        val posMs = if (vlcState.isPlaying) vlcState.position else 0L
        val durMs = if (vlcState.isPlaying) vlcState.duration else 0L
        if (posMs > 0 && durMs > 0) {
            val posSec = posMs / 1000L
            if (kotlin.math.abs(posSec - lastVlcSavedPositionSec) >= 5) {
                lastVlcSavedPositionSec = posSec
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
            isLaunchingPlayer = false
            currentPlayingUrl = null
        }
    }

    // Auto-skip on VLC error
    LaunchedEffect(vlcState.error, embeddedError) {
        val errorMessage = vlcState.error ?: embeddedError
        if (errorMessage != null) {
            val autoPlay = uiState.autoPlayEnabled
            val currentIndex = filteredLinks.indexOfFirst { it.url == currentPlayingUrl }
            val isVlcError = vlcState.error != null
            if (autoPlay && isVlcError && currentIndex != -1 && currentIndex + 1 < filteredLinks.size) {
                val nextLink = filteredLinks[currentIndex + 1]
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Link failed. Auto-trying next: ${nextLink.name}"))
                embeddedError = null
                playerLaunchError = null
                delay(800)
                viewModel.onEvent(
                    com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent.OnPlayLink(
                        link = nextLink,
                        displayTitle = displayTitle,
                        history = history,
                        loadResponse = loadResponse,
                        currentPlayingUrl = currentPlayingUrl,
                    ),
                )
            } else {
                playerLaunchError = errorMessage
                viewModel.onEvent(LinksUiEvent.OnStatusTextChanged("Playback failed: $errorMessage"))
                isLaunchingPlayer = false
                currentPlayingUrl = null
                embeddedError = null
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 700.dp).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Select stream",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DesktopUi.TextPrimary,
                        )
                        Text(
                            history.showName,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopUi.TextMuted,
                        )
                    }
                }
                HorizontalDivider(color = DesktopUi.Divider)

                StreamStatusCard(
                    statusText = statusText,
                    isLoading = isScraping || isLaunchingPlayer,
                    isScraping = isScraping,
                    onStop = { viewModel.onEvent(LinksUiEvent.OnCancelScrape) },
                )

                PlayerSelector(
                    selectedPlayer = selectedPlayer,
                    onSelect = { player ->
                        viewModel.onEvent(LinksUiEvent.OnPreferredPlayerChanged(player))
                    },
                )

                if (availableQualities.isNotEmpty()) {
                    QualitySelector(
                        totalLinkCount = links.size,
                        availableQualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onSelect = { selectedQuality = it },
                        onOpenPriorityDialog = { showPriorityDialog = true },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text("Stream", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DesktopUi.AccentSoft,
                                    selectedLabelColor = DesktopUi.Accent,
                                ),
                                shape = CircleShape,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!isScraping && filteredLinks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No Streams Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No playable links were returned. Try another episode or provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    itemsIndexed(filteredLinks, key = { index, it -> "${it.name}-${it.url}-$index" }) { _, link ->
                        StreamLinkCard(
                            link = link,
                            isBusy = isLaunchingPlayer && currentPlayingUrl != link.url,
                            onPlay = {
                                viewModel.onEvent(
                                    com.lagradost.cloudstream3.desktop.ui.screens.links.contract.LinksUiEvent.OnPlayLink(
                                        link = link,
                                        displayTitle = displayTitle,
                                        history = history,
                                        loadResponse = loadResponse,
                                        currentPlayingUrl = currentPlayingUrl,
                                    ),
                                )
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
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }

            com.lagradost.cloudstream3.desktop.ui.screens.player.SourcePriorityDialog(
                show = showPriorityDialog,
                onDismissRequest = { showPriorityDialog = false },
            )

            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
                show = playerLaunchError != null,
                onDismissRequest = { playerLaunchError = null },
                title = { Text("Player error") },
                text = { Text(playerLaunchError ?: "") },
                confirmButton = {
                    TextButton(onClick = { playerLaunchError = null }) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun StreamStatusCard(
    statusText: String,
    isLoading: Boolean,
    isScraping: Boolean,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = DesktopUi.SurfaceCard,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = DesktopUi.Accent)
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = DesktopUi.TextPrimary, modifier = Modifier.weight(1f))
            if (isScraping) {
                FilledTonalButton(
                    onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF3D2028), contentColor = Color(0xFFFF8A8A)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSelector(selectedPlayer: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Player", style = MaterialTheme.typography.labelMedium, color = DesktopUi.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mpv", "vlc").forEach { id ->
                FilterChip(
                    selected = selectedPlayer == id,
                    onClick = { onSelect(id) },
                    label = { Text(id.uppercase()) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DesktopUi.AccentSoft, selectedLabelColor = DesktopUi.Accent),
                    shape = CircleShape,
                )
            }
        }
    }
}

@Composable
private fun StreamLinkCard(link: ExtractorLink, isBusy: Boolean, onPlay: () -> Unit, onCopy: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.02f else 1f, tween(200), label = "linkScale")

    val formattedQuality = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(link.quality)
    val is4k = link.quality >= 2160 || formattedQuality == "4K"
    val is1080p = link.quality == 1080 || formattedQuality == "1080p"
    val is720p = link.quality == 720 || formattedQuality == "720p"

    val qualityContainerColor = when {
        is4k -> Color(0xFFD4AF37).copy(alpha = 0.22f)
        is1080p -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        is720p -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
        else -> DesktopUi.AccentSoft
    }
    val qualityTextColor = when {
        is4k -> Color(0xFFFFD700)
        is1080p -> MaterialTheme.colorScheme.primary
        is720p -> MaterialTheme.colorScheme.secondary
        else -> DesktopUi.Accent
    }

    Surface(
        modifier = Modifier.fillMaxWidth().scale(scale).hoverable(interaction),
        shape = RoundedCornerShape(16.dp),
        color = if (hovered) DesktopUi.SurfaceElevated else DesktopUi.SurfaceCard,
        tonalElevation = if (hovered) 8.dp else 2.dp,
        border = if (hovered) androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Accent.copy(alpha = 0.3f)) else null,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(link.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = DesktopUi.TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = qualityContainerColor,
                    ) {
                        Text(
                            text = formattedQuality,
                            color = qualityTextColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    if (link.source.isNotBlank() && link.source != link.name) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = link.source,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Text(
                        if (link.isM3u8) {
                            "HLS"
                        } else if (link.isDash) {
                            "DASH"
                        } else {
                            "Direct MP4"
                        },
                        color = DesktopUi.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedButton(
                onClick = onCopy,
                enabled = !isBusy,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, DesktopUi.Divider),
            ) { Text("Copy", color = DesktopUi.TextPrimary) }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onPlay,
                enabled = !isBusy,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DesktopUi.Accent, contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play", fontWeight = FontWeight.Bold)
            }
        }
    }
}
