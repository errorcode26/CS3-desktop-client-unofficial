package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.desktop.ui.DesktopDimens
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@Composable
fun DetailsBackdrop(
    provider: MainAPI,
    data: LoadResponse,
    scrollState: LazyListState,
    hazeState: HazeState,
    enrichmentTrigger: Int,
    modifier: Modifier = Modifier,
    dynamicColorEnabled: Boolean = false,
    animatedHeroColor: Color = Color.Transparent,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsUiState? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (scrollState.firstVisibleItemIndex == 0) {
                    val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
                    translationY = scrollOffset * 0.5f
                    alpha = 1f - (scrollOffset / (size.height * 0.8f)).coerceIn(0f, 1f)
                }
            }
            .haze(state = hazeState),
    ) {
        val currentTrigger = enrichmentTrigger
        val isFallback = data.backgroundPosterUrl.isNullOrBlank() || data.backgroundPosterUrl == data.posterUrl
        val bgUrl = remember(data, currentTrigger, uiState) {
            uiState?.enrichedBackdropUrl?.takeIf { it.isNotBlank() }
                ?: data.backgroundPosterUrl?.takeIf { it.isNotBlank() }
                ?: data.posterUrl?.takeIf { it.isNotBlank() }
                ?: provider.fixUrlNull(data.backgroundPosterUrl) ?: provider.fixUrlNull(data.posterUrl)
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
                            0.0f to Color.Black,
                            0.5f to Color.Black,
                            1.0f to Color.Transparent,
                        )
                        val scrimBase = if (dynamicColorEnabled && animatedHeroColor != Color.Transparent) animatedHeroColor else Color.Black
                        // Smooth horizontal sweep from left — many stops so the edge is completely invisible
                        val logoVignette = Brush.horizontalGradient(
                            0.00f to scrimBase.copy(alpha = 0.82f),
                            0.08f to scrimBase.copy(alpha = 0.78f),
                            0.18f to scrimBase.copy(alpha = 0.68f),
                            0.30f to scrimBase.copy(alpha = 0.52f),
                            0.42f to scrimBase.copy(alpha = 0.32f),
                            0.54f to scrimBase.copy(alpha = 0.16f),
                            0.64f to scrimBase.copy(alpha = 0.06f),
                            0.72f to Color.Transparent,
                            1.00f to Color.Transparent,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(logoVignette)
                            drawRect(verticalFade, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
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
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsUiState? = null,
) {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    var selectedActor by remember { mutableStateOf<com.lagradost.cloudstream3.ActorData?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, end = 64.dp, bottom = 108.dp, top = 160.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
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
                    val activeLogoUrl = remember(data, currentTrigger, uiState) {
                        uiState?.enrichedLogoUrl?.takeIf { it.isNotBlank() }
                            ?: data.logoUrl?.takeIf { it.isNotBlank() }
                            ?: provider.fixUrlNull(data.logoUrl)
                    }
                    if (!activeLogoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .widthIn(
                                    min = DesktopDimens.HeroLogoMinWidth,
                                    max = DesktopDimens.HeroLogoMaxWidth,
                                )
                                .heightIn(max = DesktopDimens.HeroLogoMaxHeight),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            AsyncImage(
                                model = activeLogoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(
                                        x = DesktopDimens.LogoShadowOffsetX,
                                        y = DesktopDimens.LogoShadowOffsetY,
                                    )
                                    .blur(
                                        DesktopDimens.LogoShadowBlur,
                                        edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                                    ),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.BottomStart,
                                colorFilter = DesktopDimens.LogoShadowFilter,
                            )
                            coil3.compose.SubcomposeAsyncImage(
                                model = activeLogoUrl,
                                contentDescription = data.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                alignment = Alignment.BottomStart,
                                error = {
                                    Text(
                                        text = data.name,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                },
                            )
                        }
                    } else {
                        Text(
                            text = data.name,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                }
                val activeTagline = uiState?.enrichedTagline?.takeIf { it.isNotBlank() }
                if (activeTagline != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$activeTagline\"",
                        style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        data.year?.let {
                            Text(text = it.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        data.contentRating?.let { rating ->
                            Box(modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(text = rating, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val typeStr = when (data.type) {
                            TvType.TvSeries -> "TV Series"
                            TvType.Anime -> "Anime"
                            TvType.Movie -> "Movie"
                            TvType.AnimeMovie -> "Anime Movie"
                            TvType.OVA -> "OVA"
                            else -> data.type?.name?.replace(Regex("(?i)tv"), "TV")
                        }
                        if (!typeStr.isNullOrBlank()) {
                            Text(text = typeStr, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        data.score?.takeIf { it.toFloat(10) > 0f }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = it.toString(10), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (!isLoading && !data.tags.isNullOrEmpty()) {
                    Text(
                        text = data.tags!!.take(6).joinToString(" • "),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
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
                                .widthIn(min = 160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (currentBookmark != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = 0.18f)
                                    },
                                )
                                .border(
                                    1.2.dp,
                                    if (currentBookmark != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = 0.35f)
                                    },
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { showBookmarkMenu = true }
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                    contentDescription = "Library",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(8.dp))
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
                                    .padding(4.dp),
                            ) {
                                Text(
                                    "Add to Library",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                                com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                                    val isSelected = currentBookmark?.watchType == type.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                type.stringRes,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            val newBookmark = DesktopBookmark(
                                                id = bookmarkId,
                                                name = data.name,
                                                url = data.url,
                                                apiName = provider.name,
                                                posterUrl = data.posterUrl,
                                                watchType = type.id,
                                            )
                                            DesktopDataStore.addBookmark(newBookmark)
                                            currentBookmark = newBookmark
                                            showBookmarkMenu = false
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
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
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isLoading) {
                    data.plot?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp,
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                }
            }

            if (!isLoading) {
                    Spacer(modifier = Modifier.width(64.dp))
                    Column(
                        modifier = Modifier.width(240.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Status
                        val status = uiState?.enrichedStatus ?: (data as? TvSeriesLoadResponse)?.showStatus?.name
                        if (!status.isNullOrBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "STATUS",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(text = status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Source/Provider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "SOURCE",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Source",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = provider.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun InfoRowItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsCastSection(
    data: LoadResponse,
    provider: MainAPI,
    onMovieClick: (com.lagradost.cloudstream3.SearchResponse) -> Unit = {}
) {
    var selectedActor by remember { mutableStateOf<ActorData?>(null) }
    val actors = data.actors ?: emptyList()

    val directors = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) == true ||
        it.roleString?.equals("Creator", ignoreCase = true) == true
    }
    val cast = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) != true &&
        it.roleString?.equals("Creator", ignoreCase = true) != true
    }

    if (cast.isNotEmpty() || directors.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }

            if (directors.isNotEmpty()) {
                val headerTitle = if (directors.any { it.roleString?.equals("Creator", ignoreCase = true) == true }) "Directors & Creators" else "Directors"
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    directors.forEach { actor ->
                        ActorCard(
                            actor = actor,
                            provider = provider,
                            isInverted = invertedMap[actor] == true,
                            onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                            onClick = { selectedActor = actor }
                        )
                    }
                }
            }

            if (directors.isNotEmpty() && cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (cast.isNotEmpty()) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    cast.take(18).forEach { actor ->
                        ActorCard(
                            actor = actor,
                            provider = provider,
                            isInverted = invertedMap[actor] == true,
                            onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                            onClick = { selectedActor = actor }
                        )
                    }
                }
            }
        }
    }

    if (selectedActor != null) {
        CastDetailsDialog(
            actor = selectedActor!!,
            onDismiss = { selectedActor = null },
            onMovieClick = onMovieClick
        )
    }
}

@Composable
private fun ActorCard(
    actor: ActorData,
    provider: MainAPI,
    isInverted: Boolean,
    onInvertToggle: () -> Unit,
    onClick: () -> Unit
) {
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
            .clickable { onClick() }
            .padding(4.dp),
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
                        .clickable { onInvertToggle() },
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
        Text(
            mainName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

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
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsStatsSection(
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsUiState?
) {
    if (uiState == null) return

    val budget = uiState.enrichedBudget
    val revenue = uiState.enrichedRevenue
    val networks = uiState.enrichedNetworks
    val studios = uiState.enrichedStudios
    val country = uiState.enrichedCountry
    val lang = uiState.enrichedOriginalLanguage
    val relDate = uiState.enrichedReleaseDate
    val status = uiState.enrichedStatus
    val seasons = uiState.enrichedSeasonsCount
    val episodes = uiState.enrichedEpisodesCount

    val hasStats = budget != null || revenue != null || networks.isNotEmpty() || studios.isNotEmpty() || !country.isNullOrBlank() || !lang.isNullOrBlank() || !status.isNullOrBlank() || (seasons ?: 0) > 0 || (episodes ?: 0) > 0

    if (hasStats) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(
                text = "Information & Production",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                if (seasons != null && seasons > 0) {
                    val epStr = if (episodes != null && episodes > 0) " ($episodes Episodes)" else ""
                    InfoStatCard(label = "Seasons", value = "$seasons ${if (seasons == 1) "Season" else "Seasons"}$epStr")
                } else if (episodes != null && episodes > 0) {
                    InfoStatCard(label = "Episodes", value = "$episodes ${if (episodes == 1) "Episode" else "Episodes"}")
                }
                if (!status.isNullOrBlank()) {
                    InfoStatCard(label = "Status", value = status)
                }
                if (networks.isNotEmpty()) {
                    val label = if (networks.size > 1) "Networks" else "Network"
                    InfoStatCard(label = label, value = networks.joinToString(", "))
                }
                if (studios.isNotEmpty()) {
                    val label = if (studios.size > 1) "Production Companies" else "Production Company"
                    InfoStatCard(label = label, value = studios.joinToString(", "))
                }
                if (budget != null) {
                    InfoStatCard(label = "Budget", value = formatCurrency(budget))
                }
                if (revenue != null) {
                    InfoStatCard(label = "Box Office Revenue", value = formatCurrency(revenue))
                }
                if (!relDate.isNullOrBlank()) {
                    InfoStatCard(label = "Release Date", value = relDate)
                }
                if (!country.isNullOrBlank() || !lang.isNullOrBlank()) {
                    val combined = listOfNotNull(country, lang).joinToString(" • ")
                    InfoStatCard(label = "Origin", value = combined)
                }
            }
        }
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000_000)} Billion"
        amount >= 1_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000)} Million"
        else -> "$${java.text.NumberFormat.getIntegerInstance().format(amount)}"
    }
}

@Composable
private fun InfoStatCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .heightIn(min = 80.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
