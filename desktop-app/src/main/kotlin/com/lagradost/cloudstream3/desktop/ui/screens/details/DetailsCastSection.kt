package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.fixUrlNull

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsCastSection(
    data: LoadResponse,
    provider: MainAPI,
    onActorClick: (ActorData) -> Unit = {},
    uiState: DetailsUiState? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    selectedSeason: Int? = null,
    seasonCredits: Map<Int, List<ActorData>>? = null,
) {
    val activeSeasonActors = if (selectedSeason != null && seasonCredits?.containsKey(selectedSeason) == true) {
        seasonCredits[selectedSeason]
    } else null
    val actors = activeSeasonActors ?: uiState?.enrichedActors ?: data.actors ?: emptyList()

    val directors = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) == true ||
            it.roleString?.equals("Creator", ignoreCase = true) == true
    }
    val cast = actors.filter {
        it.roleString?.equals("Director", ignoreCase = true) != true &&
            it.roleString?.equals("Creator", ignoreCase = true) != true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompact = maxWidth < 600.dp
        val hPad = if (isCompact) 12.dp else horizontalPadding
        val netWidth = (maxWidth - (hPad * 2)).coerceAtLeast(100.dp)
        val spacingDp = if (isCompact) 12.dp else 16.dp
        val minCardWidth = if (isCompact) 110.dp else 140.dp

        // Column calculation
        val columns = maxOf(2, ((netWidth + spacingDp) / (minCardWidth + spacingDp)).toInt())
        val totalSpacingDp = spacingDp * (columns - 1)
        val dynamicCardWidth = (netWidth - totalSpacingDp) / columns

        if (cast.isNotEmpty() || directors.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 8.dp),
            ) {
                val invertedMap = remember { androidx.compose.runtime.mutableStateMapOf<ActorData, Boolean>() }
                var showAllCast by remember { androidx.compose.runtime.mutableStateOf(false) }
                val displayedCast = if (showAllCast) cast else cast.take(columns * 2)

                if (cast.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (activeSeasonActors != null && selectedSeason != null) "Season $selectedSeason Cast & Characters" else "Cast & Crew",
                            style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!isCompact && cast.size > columns * 2) {
                            androidx.compose.material3.TextButton(
                                onClick = { showAllCast = !showAllCast },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (showAllCast) "Show Less" else "View All (${cast.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))
                    if (isCompact) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(cast.take(20)) { actor ->
                                CompactActorCard(
                                    actor = actor,
                                    provider = provider,
                                    onClick = { onActorClick(actor) },
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            maxItemsInEachRow = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayedCast.forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
                                    modifier = Modifier.width(dynamicCardWidth),
                                )
                            }
                        }
                    }
                }

                if (directors.isNotEmpty() && cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 32.dp))
                }

                if (directors.isNotEmpty()) {
                    var showAllDirectors by remember { androidx.compose.runtime.mutableStateOf(false) }
                    val displayedDirectors = if (showAllDirectors) directors else directors.take(columns)
                    val headerTitle = if (directors.any { it.roleString?.equals("Creator", ignoreCase = true) == true }) "Directors & Creators" else "Directors"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = headerTitle,
                            style = if (isCompact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!isCompact && directors.size > columns) {
                            androidx.compose.material3.TextButton(
                                onClick = { showAllDirectors = !showAllDirectors },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (showAllDirectors) "Show Less" else "View All (${directors.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))
                    if (isCompact) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(directors) { actor ->
                                CompactActorCard(
                                    actor = actor,
                                    provider = provider,
                                    onClick = { onActorClick(actor) },
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingDp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            maxItemsInEachRow = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            displayedDirectors.forEach { actor ->
                                ActorCard(
                                    actor = actor,
                                    provider = provider,
                                    isInverted = invertedMap[actor] == true,
                                    onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                    onClick = { onActorClick(actor) },
                                    modifier = Modifier.width(dynamicCardWidth),
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
    var isHovered by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
    )

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
        actor.role != null -> actor.role?.name?.uppercase()
        !actor.roleString.isNullOrBlank() && subName.isNullOrBlank() -> null
        else -> actor.roleString?.uppercase()
    }

    val secondaryText = when {
        !subName.isNullOrBlank() -> {
            if (!isInverted) "🎙 Voice: $subName" else {
                val raw = subName.trim()
                if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
            }
        }
        !actor.roleString.isNullOrBlank() && actor.roleString?.equals("Director", ignoreCase = true) != true && actor.roleString?.equals("Creator", ignoreCase = true) != true -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
        }
        else -> null
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(
                    elevation = if (isHovered) 16.dp else 6.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                    spotColor = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isHovered) 1.5.dp else 1.dp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            val actorImg = provider.fixUrlNull(mainImgRaw)
            if (actorImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(actorImg)
                        .size(400, 560)
                        .crossfade(true)
                        .build(),
                    contentDescription = mainName,
                    contentScale = ContentScale.Crop,
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
