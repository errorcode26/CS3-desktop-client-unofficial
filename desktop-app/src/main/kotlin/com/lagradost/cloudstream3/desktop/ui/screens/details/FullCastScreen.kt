package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.FullCastUiEvent
import com.lagradost.cloudstream3.fixUrlNull

enum class FullCastCategory(val label: String) {
    ALL("All"),
    CAST("Cast"),
    DIRECTORS("Directors"),
    WRITERS("Writers & Creators"),
    PRODUCERS("Producers"),
}

@Composable
fun FullCastScreen(
    mediaTitle: String,
    provider: MainAPI?,
    onBack: () -> Unit,
    onNavigate: (Config) -> Unit,
    viewModel: FullCastViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val invertedMap = remember { mutableStateMapOf<ActorData, Boolean>() }
    val theme = LocalDesktopTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.Background),
    ) {
        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 32.dp, end = 32.dp, bottom = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row: Titles & Subtitle + Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                        val hasMultipleSeasons = uiState.availableSeasons.size > 1
                        val titleText = if (hasMultipleSeasons && uiState.activeSeason != null) {
                            if (uiState.activeSeason == 0) "Specials Cast & Crew" else "Season ${uiState.activeSeason} Cast & Crew"
                        } else {
                            "Full Cast & Crew"
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Search Field
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onEvent(FullCastUiEvent.OnUpdateSearchQuery(it)) },
                        placeholder = {
                            Text(
                                "Search cast or crew...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(visible = uiState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.onEvent(FullCastUiEvent.OnUpdateSearchQuery("")) }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier
                            .width(320.dp)
                            .height(52.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Category Filter Pills & Season Switcher
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            FullCastCategory.ALL to uiState.totalCount,
                            FullCastCategory.CAST to uiState.castCount,
                            FullCastCategory.DIRECTORS to uiState.directorsCount,
                            FullCastCategory.WRITERS to uiState.writersCount,
                            FullCastCategory.PRODUCERS to uiState.producersCount,
                        ).filter { it.second > 0 }.forEach { (category, count) ->
                            val isSelected = uiState.selectedCategory == category
                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                label = "castChipBg",
                            )
                            val chipTextColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "castChipText",
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = chipBg,
                                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
                                modifier = Modifier
                                    .clickable { viewModel.onEvent(FullCastUiEvent.OnSelectCategory(category)) }
                                    .height(36.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                        color = chipTextColor,
                                    )
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.availableSeasons.size > 1) {
                        var seasonMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { seasonMenuExpanded = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = if (uiState.activeSeason != null) "Season ${uiState.activeSeason}" else "All Seasons",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
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
                                        viewModel.onEvent(FullCastUiEvent.OnSelectSeason(null))
                                    },
                                )
                                uiState.availableSeasons.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (s == 0) "Specials" else "Season $s") },
                                        onClick = {
                                            seasonMenuExpanded = false
                                            viewModel.onEvent(FullCastUiEvent.OnSelectSeason(s))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Virtualized Grid
                if (uiState.isLoadingSeasonCredits) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                } else if (uiState.filteredMembers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "No cast or crew found matching \"${uiState.searchQuery}\"" else "No members in this category",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(uiState.filteredMembers, key = { it.actor.name + (it.roleString ?: "") + (it.voiceActor?.name ?: "") }) { actor ->
                            FullCastCard(
                                actor = actor,
                                provider = provider,
                                isInverted = invertedMap[actor] == true,
                                onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                                onClick = {
                                    val searchName = actor.voiceActor?.name?.takeIf { it.isNotBlank() } ?: actor.actor.name
                                    onNavigate(Config.Person(name = searchName, image = actor.actor.image, tmdbId = null))
                                },
                            )
                        }
                    }
                }
            }
        }

        // Top Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Top Right Window Controls Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            WindowControlsPill(isHome = false, isCompact = false)
        }
    }
}

@Composable
private fun FullCastCard(
    actor: ActorData,
    provider: MainAPI?,
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
        actor.roleString?.equals("Producer", ignoreCase = true) == true -> "PRODUCER"
        actor.roleString?.equals("Executive Producer", ignoreCase = true) == true -> "EXEC PRODUCER"
        actor.roleString?.equals("Writer", ignoreCase = true) == true -> "WRITER"
        actor.roleString?.equals("Screenplay", ignoreCase = true) == true -> "SCREENPLAY"
        else -> null
    }

    val secondaryText = when {
        !subName.isNullOrBlank() -> {
            if (!isInverted) "🎙 Voice: $subName" else {
                val raw = subName.trim()
                if (raw.contains("(voice)", ignoreCase = true)) {
                    val cleanChar = raw.replace(Regex("(?i)\\s*\\(voice(?:\\s+actor)?\\)"), "").trim()
                    if (cleanChar.isNotEmpty()) "Voice of $cleanChar" else "Voice"
                } else if (raw.startsWith("as ", ignoreCase = true) || raw.contains(" • ")) {
                    raw
                } else {
                    "as $raw"
                }
            }
        }
        !actor.roleString.isNullOrBlank() &&
            actor.roleString?.equals("Director", ignoreCase = true) != true &&
            actor.roleString?.equals("Creator", ignoreCase = true) != true &&
            actor.roleString?.equals("Producer", ignoreCase = true) != true &&
            actor.roleString?.equals("Executive Producer", ignoreCase = true) != true &&
            actor.roleString?.equals("Writer", ignoreCase = true) != true &&
            actor.roleString?.equals("Screenplay", ignoreCase = true) != true -> {
            val raw = actor.roleString!!.trim()
            if (raw.contains("(voice)", ignoreCase = true)) {
                val cleanChar = raw.replace(Regex("(?i)\\s*\\(voice(?:\\s+actor)?\\)"), "").trim()
                if (cleanChar.isNotEmpty()) "🎙 Voice of $cleanChar" else "🎙 Voice"
            } else if (raw.startsWith("as ", ignoreCase = true) || raw.contains(" • ")) {
                raw
            } else {
                "as $raw"
            }
        }
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
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
            val actorImg = provider?.fixUrlNull(mainImgRaw) ?: mainImgRaw
            if (!actorImg.isNullOrBlank()) {
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
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    ),
            )

            // Top-left Role Badge
            if (!roleStr.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
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
                            "PRODUCER", "EXEC PRODUCER" -> Color(0xFFA5D6A7)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            // Bottom-right Dual Cast (Voice Actor) mini avatar badge
            val voiceActorImg = cornerImgRaw?.let { provider?.fixUrlNull(it) ?: it }
            if (!voiceActorImg.isNullOrBlank()) {
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

        // Main Name
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

        // Secondary Text
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
