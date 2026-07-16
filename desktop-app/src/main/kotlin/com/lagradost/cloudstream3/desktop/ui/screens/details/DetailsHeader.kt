package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ActorData
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import dev.chrisbanes.haze.haze

@Composable
fun DetailsBackdrop(provider: MainAPI, data: LoadResponse, scrollState: LazyListState, hazeState: HazeState, enrichmentTrigger: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .graphicsLayer {
                if (scrollState.firstVisibleItemIndex == 0) {
                    val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
                    translationY = scrollOffset * 0.5f // Smooth 50% parallax
                    alpha = 1f - (scrollOffset / (size.height * 0.8f)).coerceIn(0f, 1f) // Fade out
                }
            }
            .haze(state = hazeState),
    ) {
        val currentTrigger = enrichmentTrigger
        val isFallback = data.backgroundPosterUrl.isNullOrBlank() || data.backgroundPosterUrl == data.posterUrl
        val bgUrl = remember(data, currentTrigger) {
            provider.fixUrlNull(data.backgroundPosterUrl) ?: provider.fixUrlNull(data.posterUrl)
        }

        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .run { if (isFallback) this.blur(80.dp) else this }
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val verticalFade = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1.0f to Color.Black
                        )
                        val logoVignette = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.75f),
                            radius = size.width * 0.42f
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(logoVignette)
                            drawRect(verticalFade)
                        }
                    },
                alignment = Alignment.TopCenter,
            )
        }
    }
}

@Composable
fun InfoPanelRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsMetadata(
    provider: MainAPI,
    data: LoadResponse,
    hazeState: HazeState,
    heroAction: @Composable () -> Unit = {},
    enrichmentTrigger: Int,
    isLoading: Boolean = false,
) {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    var selectedActor by remember { mutableStateOf<com.lagradost.cloudstream3.ActorData?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, end = 64.dp, bottom = 108.dp, top = 160.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Hero Content
            Column(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.fillMaxWidth(0.45f).height(48.dp).clip(RoundedCornerShape(8.dp)).shimmerBackground())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.width(56.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            Box(modifier = Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                            Box(modifier = Modifier.width(64.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                    }
                } else {
                    val currentTrigger = enrichmentTrigger
                    val activeLogoUrl = remember(data, currentTrigger) { provider.fixUrlNull(data.logoUrl) }
                    if (!activeLogoUrl.isNullOrBlank()) {
                        coil3.compose.SubcomposeAsyncImage(
                            model = activeLogoUrl,
                            contentDescription = data.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(0.65f).heightIn(max = 200.dp),
                            alignment = Alignment.BottomStart,
                            error = {
                                Text(
                                    text = data.name,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        )
                    } else {
                        Text(
                            text = data.name,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (!isLoading && !data.tags.isNullOrEmpty()) {
                    Text(
                        text = data.tags!!.take(6).joinToString(" • "),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ── Dedicated Source / Provider Row ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Source",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = provider.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    heroAction()
                    
                    val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                    var currentBookmark by remember { 
                        mutableStateOf(DesktopDataStore.getBookmarks().find { it.id == bookmarkId }) 
                    }
                    var showBookmarkMenu by remember { mutableStateOf(false) }

                    Box {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (currentBookmark != null) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.18f)
                                )
                                .border(
                                    1.5.dp,
                                    if (currentBookmark != null) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { showBookmarkMenu = true }
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                    contentDescription = "Library",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                val text = currentBookmark?.let { b ->
                                    com.lagradost.common.storage.DesktopWatchType.entries.find { type -> type.id == b.watchType }?.stringRes
                                } ?: "Add to Library"
                                Text(
                                    text = text,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            DropdownMenu(
                                expanded = showBookmarkMenu,
                                onDismissRequest = { showBookmarkMenu = false },
                                modifier = Modifier
                                    .background(DesktopUi.SurfaceElevated, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                Text(
                                    "Add to Library",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                                    val isSelected = currentBookmark?.watchType == type.id
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                type.stringRes,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            val newBookmark = DesktopBookmark(
                                                id = bookmarkId,
                                                name = data.name,
                                                url = data.url,
                                                apiName = provider.name,
                                                posterUrl = data.posterUrl,
                                                watchType = type.id
                                            )
                                            DesktopDataStore.addBookmark(newBookmark)
                                            currentBookmark = newBookmark
                                            showBookmarkMenu = false
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    )
                                }
                                if (currentBookmark != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(
                                        text = { 
                                            Text("Remove from Library", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) 
                                        },
                                        onClick = {
                                            DesktopDataStore.removeBookmark(bookmarkId)
                                            currentBookmark = null
                                            showBookmarkMenu = false
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        data.year?.let {
                            Text(text = it.toString(), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        data.contentRating?.let { rating ->
                            Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(text = rating, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        data.score?.takeIf { it.toFloat(10) > 0f }?.let {
                            Icon(Icons.Default.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = it.toString(10), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        val director = data.actors?.find { it.roleString?.equals("Director", ignoreCase = true) == true }?.actor?.name
                        if (director != null) {
                            Text(text = "Director: $director", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    data.plot?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp,
                            modifier = Modifier.widthIn(max = 600.dp),
                        )
                    }
                }
            }
            
            if (!isLoading) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 36.dp)
                        .width(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val language = (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.episodes?.keys?.firstOrNull()?.name ?: "English"
                        InfoPanelRow("LANGUAGE", language)
                        
                        val firstDate = when(data) {
                            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> data.episodes.firstOrNull()?.date
                            is com.lagradost.cloudstream3.AnimeLoadResponse -> data.episodes.values.firstOrNull()?.firstOrNull()?.date
                            else -> null
                        }
                        if (firstDate != null) {
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            InfoPanelRow("FIRST AIRED", sdf.format(java.util.Date(firstDate)))
                        } else if (data.year != null) {
                            InfoPanelRow("FIRST AIRED", data.year.toString())
                        }
                        
                        val seasonsCount = (data as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.episodes?.mapNotNull { it.season }?.distinct()?.size 
                            ?: (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.episodes?.values?.flatten()?.mapNotNull { it.season }?.distinct()?.size
                        if (seasonsCount != null && seasonsCount > 0) {
                            InfoPanelRow("SEASONS", seasonsCount.toString())
                        }
                        
                        val episodesCount = (data as? com.lagradost.cloudstream3.TvSeriesLoadResponse)?.episodes?.size 
                            ?: (data as? com.lagradost.cloudstream3.AnimeLoadResponse)?.episodes?.values?.flatten()?.size
                        if (episodesCount != null && episodesCount > 0) {
                            InfoPanelRow("EPISODES", episodesCount.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsCastSection(data: LoadResponse, provider: MainAPI) {
    val coroutineScope = rememberCoroutineScope()
    var selectedActor by remember { mutableStateOf<ActorData?>(null) }
    val actors = data.actors ?: emptyList()
    
    val cast = actors.filter { it.roleString?.equals("Director", ignoreCase = true) != true }
    val directors = actors.filter { it.roleString?.equals("Director", ignoreCase = true) == true }

    if (cast.isNotEmpty() || directors.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 16.dp)
        ) {
            if (cast.isNotEmpty()) {
                CastRow(title = "Cast", actors = cast, provider = provider) { selectedActor = it }
                if (directors.isNotEmpty()) Spacer(modifier = Modifier.height(32.dp))
            }
            
            if (directors.isNotEmpty()) {
                CastRow(title = "Directors", actors = directors, provider = provider) { selectedActor = it }
            }
        }
    }

    if (selectedActor != null) {
        CastDetailsDialog(actor = selectedActor!!) {
            selectedActor = null
        }
    }
}

@Composable
fun CastRow(title: String, actors: List<ActorData>, provider: MainAPI, onActorClick: (ActorData) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { coroutineScope.launch { scrollState.animateScrollBy(-500f) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { coroutineScope.launch { scrollState.animateScrollBy(500f) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    
    val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }
    
    androidx.compose.foundation.lazy.LazyRow(
        state = scrollState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 8.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                scrollState.dispatchRawDelta(-dragAmount)
            }
        },
    ) {
        items(actors) { actor ->
            val isInverted = invertedMap[actor] == true
            
            val (mainImgRaw, cornerImgRaw) = if (!isInverted || actor.voiceActor?.image.isNullOrBlank()) {
                Pair(actor.actor.image, actor.voiceActor?.image)
            } else {
                Pair(actor.voiceActor?.image, actor.actor.image)
            }
            
            val (mainName, subName) = if (!isInverted || actor.voiceActor?.name.isNullOrBlank()) {
                Pair(actor.actor.name, actor.voiceActor?.name)
            } else {
                Pair(actor.voiceActor?.name ?: "", actor.actor.name)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onActorClick(actor) }
                    .padding(4.dp)
            ) {
                Box(modifier = Modifier.size(96.dp)) {
                    val actorImg = provider.fixUrlNull(mainImgRaw)
                    if (actorImg != null) {
                        AsyncImage(
                            model = actorImg,
                            contentDescription = mainName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = mainName,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }

                    val voiceActorImg = cornerImgRaw?.let { provider.fixUrlNull(it) }
                    if (voiceActorImg != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { invertedMap[actor] = !isInverted }
                        ) {
                            AsyncImage(
                                model = voiceActorImg,
                                contentDescription = subName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(mainName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                
                if (!subName.isNullOrBlank()) {
                    Text(
                        subName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                
                val roleStr = actor.role?.name ?: actor.roleString
                if (!roleStr.isNullOrBlank()) {
                    Text(
                        roleStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
