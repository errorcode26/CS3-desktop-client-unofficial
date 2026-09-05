package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.fixUrlNull

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsCastSection(
    data: LoadResponse,
    provider: MainAPI,
    onActorClick: (ActorData) -> Unit = {},
    onNavigate: (Config) -> Unit = {},
    uiState: DetailsUiState? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    selectedSeason: Int? = null,
    seasonCredits: Map<Int, List<ActorData>>? = null,
    onSeasonChange: ((Int?) -> Unit)? = null,
    availableSeasons: List<Int> = emptyList(),
) {
    val activeSeasonActors = if (selectedSeason != null && seasonCredits?.containsKey(selectedSeason) == true) {
        seasonCredits[selectedSeason]
    } else null
    val actors = activeSeasonActors ?: uiState?.enrichedActors ?: data.actors ?: emptyList()

    val directors = remember(actors) {
        actors.filter { it.roleString?.contains("Director", ignoreCase = true) == true }
    }
    val writers = remember(actors) {
        actors.filter {
            it.roleString?.contains("Creator", ignoreCase = true) == true ||
                it.roleString?.contains("Writer", ignoreCase = true) == true ||
                it.roleString?.contains("Screenplay", ignoreCase = true) == true
        }
    }
    val producers = remember(actors) {
        actors.filter {
            it.roleString?.contains("Producer", ignoreCase = true) == true
        }
    }
    val cast = remember(actors) {
        actors.filter {
            val r = it.roleString?.trim() ?: ""
            !r.equals("Director", ignoreCase = true) &&
                !r.equals("Creator", ignoreCase = true) &&
                !r.equals("Writer", ignoreCase = true) &&
                !r.equals("Screenplay", ignoreCase = true) &&
                !r.equals("Producer", ignoreCase = true) &&
                !r.equals("Executive Producer", ignoreCase = true)
        }.distinctBy { it.actor.name }
    }

    val openFullCast: () -> Unit = {
        onNavigate(
            Config.FullCast(
                mediaTitle = data.name,
                providerName = provider.name,
                cast = cast,
                directors = directors,
                writers = writers,
                producers = producers,
                tmdbId = uiState?.tmdbId ?: data.syncData["tmdb"]?.toIntOrNull(),
                availableSeasons = availableSeasons,
                initialSeason = selectedSeason,
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        val hPad = if (isCompact) 12.dp else horizontalPadding
        val cardWidth = if (isCompact) 110.dp else 140.dp
        val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }
        val previewLimit = if (isCompact) 5 else 7
        val topCast = remember(cast, previewLimit) { cast.take(previewLimit) }
        val keyCrew = remember(directors, writers) { (directors + writers).distinctBy { it.actor.name }.take(3) }

        if (cast.isNotEmpty() || directors.isNotEmpty() || writers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = hPad),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (activeSeasonActors != null && selectedSeason != null) "Season $selectedSeason Cast & Characters" else "Cast & Crew",
                            style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (availableSeasons.size > 1 && onSeasonChange != null) {
                            var seasonMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable { seasonMenuExpanded = true },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = if (selectedSeason != null) "Season $selectedSeason" else "All Seasons",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = seasonMenuExpanded,
                                    onDismissRequest = { seasonMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Seasons (Series Cast)") },
                                        onClick = {
                                            seasonMenuExpanded = false
                                            onSeasonChange(null)
                                        },
                                    )
                                    availableSeasons.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(if (s == 0) "Specials" else "Season $s") },
                                            onClick = {
                                                seasonMenuExpanded = false
                                                onSeasonChange(s)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (actors.size > 8) {
                        androidx.compose.material3.TextButton(
                            onClick = openFullCast,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                // Virtualized Horizontal Cast Row
                if (topCast.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 16.dp),
                        contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(topCast, key = { it.actor.name + (it.roleString ?: "") }) { actor ->
                            ActorCard(
                                actor = actor,
                                provider = provider,
                                isInverted = invertedMap[actor] == true,
                                onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                onClick = { onActorClick(actor) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }

                        if (cast.size > topCast.size || keyCrew.isNotEmpty() || producers.isNotEmpty()) {
                            item(key = "view_all_card") {
                                ViewAllCastCard(
                                    totalCount = actors.size,
                                    onClick = openFullCast,
                                    modifier = Modifier.width(cardWidth),
                                )
                            }
                        }
                    }
                }

                // Directors & Creators Carousel (if present)
                if (keyCrew.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(if (isCompact) 18.dp else 24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (writers.isNotEmpty()) "Directors & Creators" else "Directors",
                            style = if (isCompact) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 16.dp),
                        contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(keyCrew, key = { it.actor.name + (it.roleString ?: "") }) { actor ->
                            ActorCard(
                                actor = actor,
                                provider = provider,
                                isInverted = false,
                                onInvertToggle = { },
                                onClick = { onActorClick(actor) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewAllCastCard(
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isHovered) 0.65f else 0.35f))
                .border(
                    width = if (isHovered) 2.dp else 1.dp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isHovered) 0.25f else 0.15f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Cast & Crew",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Full Roster",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactActorCard(
    actor: ActorData,
    provider: MainAPI,
    onClick: () -> Unit,
) {
    val actorImg = provider.fixUrlNull(actor.actor.image ?: actor.voiceActor?.image)
    val actorName = actor.actor.name
    val roleStr = when {
        actor.voiceActor?.name?.isNotBlank() == true -> "🎙 ${actor.voiceActor?.name}"
        !actor.roleString.isNullOrBlank() && !actor.roleString.equals("Director", ignoreCase = true) && !actor.roleString.equals("Creator", ignoreCase = true) -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
        }
        else -> actor.roleString ?: actor.role?.name
    }

    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(160, 160)
                        .crossfade(true)
                        .build(),
                    contentDescription = actorName,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = actorName,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actorName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!roleStr.isNullOrBlank()) {
            Text(
                text = roleStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActorCard(
    actor: ActorData,
    provider: MainAPI,
    isInverted: Boolean,
    onInvertToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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

    val roleStr = when {
        actor.roleString?.equals("Director", ignoreCase = true) == true -> "DIRECTOR"
        actor.roleString?.equals("Creator", ignoreCase = true) == true -> "CREATOR"
        actor.roleString?.equals("Writer", ignoreCase = true) == true -> "WRITER"
        actor.roleString?.equals("Producer", ignoreCase = true) == true -> "PRODUCER"
        actor.roleString?.equals("Executive Producer", ignoreCase = true) == true -> "EXEC PRODUCER"
        else -> null
    }

    val secondaryText = when {
        !subName.isNullOrBlank() -> {
            if (!isInverted) "🎙 Voice: $subName" else {
                val raw = subName.trim()
                if (raw.startsWith("as ", ignoreCase = true) || raw.contains(" • ")) raw else "as $raw"
            }
        }
        !actor.roleString.isNullOrBlank() &&
            actor.roleString?.equals("Director", ignoreCase = true) != true &&
            actor.roleString?.equals("Creator", ignoreCase = true) != true &&
            actor.roleString?.equals("Writer", ignoreCase = true) != true &&
            actor.roleString?.equals("Producer", ignoreCase = true) != true &&
            actor.roleString?.equals("Executive Producer", ignoreCase = true) != true -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true) || raw.contains(" • ")) raw else "as $raw"
        }
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .posterHoverEffect(cardShape)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val actorImg = provider.fixUrlNull(mainImgRaw)
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(400, 600)
                        .crossfade(true)
                        .build(),
                    contentDescription = mainName,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = mainName,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            // Top-left Role Badge
            if (!roleStr.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = roleStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = when (roleStr) {
                            "MAIN" -> MaterialTheme.colorScheme.primary
                            "SUPPORTING" -> Color(0xFF4DD0E1)
                            "DIRECTOR", "CREATOR" -> Color(0xFFFFB74D)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            // Bottom-right Dual Cast (Voice Actor) mini avatar badge
            val voiceActorImg = cornerImgRaw?.let { provider.fixUrlNull(it) }
            if (voiceActorImg != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .shadow(8.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onInvertToggle() },
                ) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(voiceActorImg)
                            .size(128, 128)
                            .crossfade(true)
                            .build(),
                        contentDescription = subName,
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Title (Character Name or Live Action Actor Name)
        Text(
            text = mainName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        // Secondary Text (Voice Actor name or Live Action Character Role)
        if (!secondaryText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
