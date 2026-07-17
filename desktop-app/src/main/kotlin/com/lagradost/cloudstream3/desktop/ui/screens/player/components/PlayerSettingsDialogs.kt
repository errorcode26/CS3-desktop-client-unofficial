package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    playerState: PlayerState,
    launchData: com.lagradost.cloudstream3.desktop.ui.VideoLaunchData,
    lazyAudioTracks: List<PlayerState.LazyTrack> = emptyList(),
    lazySubtitleTracks: List<PlayerState.LazyTrack> = emptyList(),
    initialTab: Int = 0,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember(initialTab) { mutableStateOf(initialTab) } // 0: Audio, 1: Subtitles, 2: Speed, 3: Video

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141414),
            modifier = Modifier
                .padding(end = 48.dp, bottom = 120.dp)
                .widthIn(max = 400.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}, onDoubleTap = {}) },
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Player Settings", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                // Custom Segmented Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val tabs = listOf("Audio", "Subtitles", "Speed", "Video")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = currentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { currentTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (currentTab) {
                    0 -> AudioTab(playerState, lazyAudioTracks)
                    1 -> SubtitlesTab(playerState, launchData, lazySubtitleTracks)
                    2 -> SpeedTab(playerState)
                    3 -> VideoTab(playerState)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTab(playerState: PlayerState) {
    val isInterpolationEnabled by playerState.isInterpolationEnabled.collectAsState()
    val videoTracks by playerState.videoTracks.collectAsState()
    val lazyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()
    val isAutoSelected = videoTracks.none { it.isSelected }

    Column {
        Text("Video Quality", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
        ) {
            item {
                SubtitleItem(
                    name = "Auto (Highest Quality)",
                    isSelected = isAutoSelected,
                    onClick = { playerState.setVideoTrack(null) },
                )
            }
            items(videoTracks.size) { index ->
                val track = videoTracks[index]
                SubtitleItem(
                    name = track.name,
                    isSelected = track.isSelected,
                    onClick = { playerState.setVideoTrack(track.id) },
                )
            }
            items(lazyVideoTracks.size) { index ->
                val track = lazyVideoTracks[index]
                SubtitleItem(
                    name = "${track.name} (Stream)",
                    isSelected = false,
                    onClick = {
                        playerState.loadLazyVideoTrack(
                            PlayerState.LazyTrack(url = track.url, name = track.name, language = track.language),
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Video Rendering", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable { playerState.setInterpolation(!isInterpolationEnabled) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interpolation Blending",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Smooths motion for low-framerate video",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = isInterpolationEnabled,
                onCheckedChange = { playerState.setInterpolation(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray,
                ),
            )
        }
    }
}

@Composable
private fun SpeedTab(playerState: PlayerState) {
    val currentSpeed by playerState.playbackSpeed.collectAsState()

    Column {
        Text("Playback Speed", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            IconButton(
                onClick = {
                    val newSpeed = (currentSpeed - 0.25f).coerceAtLeast(0.25f)
                    playerState.setSpeed(newSpeed)
                },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease Speed", tint = Color.White)
            }

            Text(
                text = "${String.format("%.2f", currentSpeed)}x",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            IconButton(
                onClick = {
                    val newSpeed = (currentSpeed + 0.25f).coerceAtMost(3.0f)
                    playerState.setSpeed(newSpeed)
                },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase Speed", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AudioTab(playerState: PlayerState, lazyTracks: List<PlayerState.LazyTrack> = emptyList()) {
    val audioTracks by playerState.audioTracks.collectAsState()

    Column {
        Text("Audio Track", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
        ) {
            items(audioTracks.size) { index ->
                val track = audioTracks[index]
                SubtitleItem(
                    name = track.name,
                    isSelected = track.isSelected,
                    onClick = {
                        playerState.setAudioTrack(track.id)
                    },
                )
            }
            items(lazyTracks.size) { index ->
                val track = lazyTracks[index]
                SubtitleItem(
                    name = track.name + " (Fetch)",
                    isSelected = false,
                    onClick = {
                        playerState.loadLazyAudioTrack(track)
                    },
                )
            }
            if (audioTracks.isEmpty() && lazyTracks.isEmpty()) {
                item {
                    Text("No additional audio tracks found.", color = Color.Gray, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SubtitlesTab(
    playerState: PlayerState,
    launchData: com.lagradost.cloudstream3.desktop.ui.VideoLaunchData,
    lazyTracks: List<PlayerState.LazyTrack> = emptyList(),
) {
    val externalSubtitles = launchData.subtitles
    val subtitleDelay by playerState.subtitleDelayMs.collectAsState()
    val subtitleTracks by playerState.subtitleTracks.collectAsState()
    val isNoneSelected = subtitleTracks.none { it.isSelected }
    var showDownloadDialog by remember { mutableStateOf(false) }

    Column {
        Text("Subtitle Track", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Subtitles List
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        ) {
            item {
                SubtitleItem(
                    name = "None",
                    isSelected = isNoneSelected,
                    onClick = {
                        playerState.setSubtitleTrack(null)
                    },
                )
            }
            if (subtitleTracks.isNotEmpty()) {
                item {
                    Text("Embedded Tracks", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(subtitleTracks.size) { index ->
                    val track = subtitleTracks[index]
                    SubtitleItem(
                        name = track.name,
                        isSelected = track.isSelected,
                        onClick = {
                            playerState.setSubtitleTrack(track.id)
                        },
                    )
                }
            }
            if (externalSubtitles.isNotEmpty()) {
                item {
                    Text("External Tracks (Click to load)", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(externalSubtitles.size) { index ->
                    val sub = externalSubtitles[index]
                    SubtitleItem(
                        name = sub.lang,
                        isSelected = false, // External tracks become embedded tracks once loaded
                        onClick = {
                            playerState.loadExternalSubtitle(sub.url)
                        },
                    )
                }
            }
            if (lazyTracks.isNotEmpty()) {
                item {
                    Text("HLS Subtitles (Click to fetch)", color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(lazyTracks.size) { index ->
                    val track = lazyTracks[index]
                    SubtitleItem(
                        name = "${track.name} (${track.language})",
                        isSelected = false,
                        onClick = {
                            playerState.loadLazySubtitleTrack(track)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Subtitle File", java.awt.FileDialog.LOAD)
                    dialog.isVisible = true
                    val file = dialog.file
                    val dir = dialog.directory
                    if (file != null && dir != null) {
                        playerState.loadExternalSubtitle(java.io.File(dir, file).absolutePath)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Local Sub", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showDownloadDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Download", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        if (showDownloadDialog) {
            SubtitleDownloadDialog(
                playerState = playerState,
                launchData = launchData,
                onDismiss = { showDownloadDialog = false },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Subtitle Delay (Sync)", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            IconButton(
                onClick = { playerState.setSubtitleDelay(subtitleDelay - 250) },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Remove, contentDescription = "-250ms", tint = Color.White)
            }

            Text(
                text = "${if (subtitleDelay > 0) "+" else ""}$subtitleDelay ms",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            IconButton(
                onClick = { playerState.setSubtitleDelay(subtitleDelay + 250) },
                modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "+250ms", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SubtitleItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = if (name.length > 30) name.take(27) + "..." else name
        Text(title, color = if (isSelected) Color.White else Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SubtitleDownloadDialog(
    playerState: PlayerState,
    launchData: com.lagradost.cloudstream3.desktop.ui.VideoLaunchData,
    onDismiss: () -> Unit,
) {
    // State
    var searchQuery by remember { mutableStateOf(launchData.title ?: "") }
    var selectedLang by remember { mutableStateOf("English") }
    var seasonText by remember { mutableStateOf("") }
    var episodeText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity>>(emptyList()) }
    var downloadingIdx by remember { mutableStateOf<Int?>(null) }
    var langDropdown by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val langMap = linkedMapOf(
        "All" to null,
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "Portuguese" to "pt",
        "German" to "de",
        "Italian" to "it",
        "Arabic" to "ar",
        "Hindi" to "hi",
        "Korean" to "ko",
        "Japanese" to "ja",
        "Chinese" to "zh",
    )

    // Colors matching parent dialog
    val bgColor = Color(0xFF141414)
    val inputBg = Color(0xFF1E1E1E)
    val accentRed = Color(0xFFE53935)
    val labelGray = Color(0xFF888888)
    val cardBg = Color(0xFF1C1C1C)
    val borderColor = Color.White.copy(alpha = 0.08f)

    // Search function
    fun performSearch() {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            isLoading = true
            hasSearched = true
            val results = mutableListOf<com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity>()

            val query = searchQuery.trim()
            val lang = langMap[selectedLang]
            val ep = episodeText.trim().toIntOrNull()
            val seas = seasonText.trim().toIntOrNull()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val search = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch(
                    query = query,
                    lang = lang,
                    epNumber = ep,
                    seasonNumber = seas,
                )
                for (provider in com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders) {
                    val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
                    try {
                        val res = provider.search(auth, search)
                        if (res != null) results.addAll(res)
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("SubtitleSearch [${provider.name}]: ${e.message}")
                    }
                }
            }

            // Back on Main — Compose will now see the state change and recompose
            searchResults = results
            isLoading = false
        }
    }

    // Auto-search on first open
    LaunchedEffect(Unit) { performSearch() }

    // Dialog container
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 600.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Search Subtitles",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = labelGray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Search Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Query field
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .background(inputBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Title...", color = labelGray, fontSize = 14.sp)
                            inner()
                        },
                    )

                    // Language dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .background(inputBg, RoundedCornerShape(10.dp))
                                .clickable { langDropdown = true }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(selectedLang, color = Color.White, fontSize = 13.sp)
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Remove,
                                contentDescription = null,
                                tint = labelGray,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = langDropdown,
                            onDismissRequest = { langDropdown = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E)).heightIn(max = 300.dp),
                        ) {
                            langMap.keys.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        selectedLang = lang
                                        langDropdown = false
                                    },
                                    modifier = if (lang == selectedLang) {
                                        Modifier.background(Color.White.copy(alpha = 0.08f))
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }

                    // Season
                    BasicTextField(
                        value = seasonText,
                        onValueChange = { seasonText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .width(50.dp)
                            .background(inputBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (seasonText.isEmpty()) Text("S", color = labelGray, fontSize = 14.sp)
                            inner()
                        },
                    )

                    // Episode
                    BasicTextField(
                        value = episodeText,
                        onValueChange = { episodeText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .width(50.dp)
                            .background(inputBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (episodeText.isEmpty()) Text("E", color = labelGray, fontSize = 14.sp)
                            inner()
                        },
                    )

                    // Search button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accentRed, RoundedCornerShape(10.dp))
                            .clickable(enabled = !isLoading) { performSearch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Results list
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    hasSearched && searchResults.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No subtitles found.", color = labelGray, fontSize = 14.sp)
                                val loggedIn = com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders.any {
                                    com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[it.idPrefix]?.isNotEmpty() == true
                                }
                                if (!loggedIn) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tip: Log in via Settings → Accounts to get more results.",
                                        color = labelGray.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(searchResults.mapIndexed { i, s -> Pair(i, s) }, key = { it.first }) { (idx, sub) ->
                                val isDownloading = downloadingIdx == idx
                                // Language code badge (short 2-3 char uppercase)
                                val langCode = sub.lang.take(3).uppercase().let {
                                    if (it.length < 2) "??" else it
                                }
                                // Episode/season suffix
                                val epSuffix = buildString {
                                    sub.seasonNumber?.let { append(" S${it.toString().padStart(2,'0')}") }
                                    sub.epNumber?.let { append(" E${it.toString().padStart(2,'0')}") }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    // Lang badge
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            langCode,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                        )
                                    }

                                    // Name + source
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                sub.name.let { if (it.length > 40) it.take(37) + "..." else it },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (epSuffix.isNotBlank()) {
                                                Text(
                                                    epSuffix.trim(),
                                                    color = labelGray,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Normal,
                                                )
                                            }
                                        }
                                        Text(
                                            sub.source,
                                            color = labelGray,
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                        )
                                    }

                                    // Download button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isDownloading) {
                                                    labelGray.copy(alpha = 0.1f)
                                                } else {
                                                    Color.White.copy(alpha = 0.06f)
                                                },
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable(enabled = !isDownloading && downloadingIdx == null) {
                                                downloadingIdx = idx
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    try {
                                                        val provider = com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders
                                                            .find { it.idPrefix == sub.idPrefix }
                                                        if (provider != null) {
                                                            val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
                                                            val url = provider.load(auth, sub)
                                                            if (url != null) {
                                                                playerState.loadExternalSubtitle(url)
                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                    onDismiss()
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        com.lagradost.common.logging.AppLogger.e("SubtitleLoad: ${e.message}")
                                                    } finally {
                                                        downloadingIdx = null
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp),
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
