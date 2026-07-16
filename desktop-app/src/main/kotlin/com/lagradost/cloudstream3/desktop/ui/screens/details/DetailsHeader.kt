package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import dev.chrisbanes.haze.HazeState
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import dev.chrisbanes.haze.haze

@Composable
fun DetailsBackdrop(provider: MainAPI, data: LoadResponse, scrollState: LazyListState, hazeState: HazeState, enrichmentTrigger: Int, modifier: Modifier = Modifier) {
    val trigger = enrichmentTrigger
    Box(
        modifier = modifier
            .haze(state = hazeState),
    ) {
        // Full backdrop image
        val bgUrl = provider.fixUrlNull(data.backgroundPosterUrl) ?: provider.fixUrlNull(data.posterUrl)
        if (bgUrl != null) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop, // Crop to fill the height beautifully
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .run { if (data.backgroundPosterUrl == null) this.blur(40.dp) else this },
                alignment = Alignment.TopCenter,
            )
        }

        // Gradient: transparent top -> solid background bottom
        val bgColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.85f to bgColor.copy(alpha = 0.80f),
                            1.00f to bgColor,
                        ),
                    ),
                ),
        )

        // Left vignette so text pops
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to bgColor.copy(alpha = 0.95f),
                            0.35f to bgColor.copy(alpha = 0.75f),
                            0.60f to Color.Transparent,
                        ),
                    ),
                ),
        )
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
    val trigger = enrichmentTrigger
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .padding(start = 32.dp, end = 32.dp, top = 100.dp, bottom = 32.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Poster (Styled exactly like Home Hero)
                AsyncImage(
                    model = provider.fixUrlNull(data.posterUrl),
                    contentDescription = data.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .shadow(24.dp, RoundedCornerShape(16.dp)),
                )
                Spacer(modifier = Modifier.width(48.dp))

                // Metadata Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Logo or Text Title
                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxWidth(0.6f).height(56.dp).clip(RoundedCornerShape(8.dp)).shimmerBackground())
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                if (!data.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = provider.fixUrlNull(data.logoUrl),
                                        contentDescription = data.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth(0.6f).heightIn(max = 120.dp),
                                        alignment = Alignment.CenterStart,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                } else {
                                    Text(
                                        text = data.name,
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 48.sp,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Rating & Year
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLoading) {
                                    Box(modifier = Modifier.width(180.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                                } else {
                                    data.score?.let {
                                        androidx.compose.material3.Icon(
                                            androidx.compose.material.icons.Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFD700), // Gold
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = it.toString(10),
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    data.year?.let {
                                        Text(
                                            text = it.toString(),
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    data.duration?.let {
                                        Text(
                                            text = "${it}m",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    data.contentRating?.let { rating ->
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = rating,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Row (Play Button + Bookmark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Insert Hero Action (Play / Resume button)
                        heroAction()

                        // Bookmark button
                        val bookmarkId = "${provider.name}_${data.url.hashCode()}"
                        var isBookmarked by remember { mutableStateOf(DesktopDataStore.isBookmarked(bookmarkId)) }
                        IconButton(
                            onClick = {
                                if (isBookmarked) {
                                    DesktopDataStore.removeBookmark(bookmarkId)
                                } else {
                                    DesktopDataStore.addBookmark(
                                        DesktopBookmark(
                                            id = bookmarkId,
                                            name = data.name,
                                            url = data.url,
                                            apiName = provider.name,
                                            posterUrl = data.posterUrl,
                                        ),
                                    )
                                }
                                isBookmarked = !isBookmarked
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        ) {
                            androidx.compose.material3.Icon(
                                if (isBookmarked) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color.Red else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tags
                    // Tags
                    if (isLoading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Box(modifier = Modifier.width(60.dp).height(28.dp).clip(RoundedCornerShape(20.dp)).shimmerBackground())
                            }
                        }
                    } else if (!data.tags.isNullOrEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.tags!!.take(6).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(tag, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Plot
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.fillMaxWidth(0.9f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Box(modifier = Modifier.fillMaxWidth(0.85f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Box(modifier = Modifier.fillMaxWidth(0.9f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }
                    } else {
                        data.plot?.let {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                    }
                }
            }

            // Cast
            if (isLoading) {
                Spacer(modifier = Modifier.height(48.dp))
                Box(modifier = Modifier.width(100.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmerBackground())
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(5) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                            Box(modifier = Modifier.size(96.dp).clip(CircleShape).shimmerBackground())
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth(0.8f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                        }
                    }
                }
            } else if (!data.actors.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(48.dp))
                Text("Cast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                val castScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.foundation.lazy.LazyRow(
                    state = castScrollState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            castScrollState.dispatchRawDelta(-dragAmount)
                        }
                    },
                ) {
                    items(data.actors!!) { actor ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                            Box(modifier = Modifier.size(96.dp)) {
                                val actorImg = provider.fixUrlNull(actor.actor.image)
                                if (actorImg != null) {
                                    AsyncImage(
                                        model = actorImg,
                                        contentDescription = actor.actor.name,
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
                                            contentDescription = actor.actor.name,
                                            modifier = Modifier.size(52.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }

                                val voiceActorImg = actor.voiceActor?.image?.let { provider.fixUrlNull(it) }
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
                                    ) {
                                        AsyncImage(
                                            model = voiceActorImg,
                                            contentDescription = actor.voiceActor?.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(actor.actor.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            
                            val subText = actor.voiceActor?.name ?: actor.roleString
                            if (!subText.isNullOrBlank()) {
                                Text(
                                    subText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
