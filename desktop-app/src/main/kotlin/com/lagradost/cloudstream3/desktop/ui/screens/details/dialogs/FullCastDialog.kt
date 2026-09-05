package com.lagradost.cloudstream3.desktop.ui.screens.details.dialogs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.fixUrlNull

enum class CastFilterCategory(val label: String) {
    ALL("All"),
    CAST("Cast"),
    DIRECTORS("Directors"),
    WRITERS("Writers & Creators"),
    PRODUCERS("Producers"),
}

@Composable
fun FullCastDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    mediaTitle: String,
    cast: List<ActorData>,
    directors: List<ActorData>,
    writers: List<ActorData>,
    producers: List<ActorData>,
    provider: MainAPI,
    onActorClick: (ActorData) -> Unit,
) {
    if (!show) return

    var selectedCategory by remember { mutableStateOf(CastFilterCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val invertedMap = remember { mutableStateMapOf<ActorData, Boolean>() }
    val theme = LocalDesktopTheme.current

    // Combined unique list with stable categorization
    val allMembers = remember(cast, directors, writers, producers) {
        val list = mutableListOf<Pair<ActorData, CastFilterCategory>>()
        directors.forEach { list.add(it to CastFilterCategory.DIRECTORS) }
        writers.forEach { list.add(it to CastFilterCategory.WRITERS) }
        producers.forEach { list.add(it to CastFilterCategory.PRODUCERS) }
        cast.forEach { list.add(it to CastFilterCategory.CAST) }
        list
    }

    val filteredList = remember(allMembers, selectedCategory, searchQuery) {
        allMembers.filter { (actor, category) ->
            val matchesCategory = when (selectedCategory) {
                CastFilterCategory.ALL -> true
                else -> category == selectedCategory
            }
            if (!matchesCategory) return@filter false

            if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                actor.actor.name.lowercase().contains(q) ||
                    actor.voiceActor?.name?.lowercase()?.contains(q) == true ||
                    actor.roleString?.lowercase()?.contains(q) == true
            }
        }.map { it.first }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .fillMaxHeight(0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header Row: Title + Search Bar + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Full Cast & Crew",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (mediaTitle.isNotBlank()) {
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search cast & crew...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .width(260.dp)
                        .height(48.dp),
                )

                Spacer(Modifier.width(12.dp))

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val categories = listOf(
                    CastFilterCategory.ALL to allMembers.size,
                    CastFilterCategory.CAST to cast.size,
                    CastFilterCategory.DIRECTORS to directors.size,
                    CastFilterCategory.WRITERS to writers.size,
                    CastFilterCategory.PRODUCERS to producers.size,
                )

                categories.forEach { (cat, count) ->
                    if (count > 0 || cat == CastFilterCategory.ALL) {
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            // Virtualized Grid
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No cast or crew members match \"$searchQuery\"" else "No members in this category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 135.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(filteredList, key = { it.actor.name + (it.roleString ?: "") + (it.voiceActor?.name ?: "") }) { actor ->
                        FullCastGridCard(
                            actor = actor,
                            provider = provider,
                            isInverted = invertedMap[actor] == true,
                            onInvertToggle = { invertedMap[actor] = !(invertedMap[actor] ?: false) },
                            onClick = {
                                onDismissRequest()
                                onActorClick(actor)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullCastGridCard(
    actor: ActorData,
    provider: MainAPI,
    isInverted: Boolean,
    onInvertToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

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
        !actor.roleString.isNullOrBlank() -> {
            val raw = actor.roleString!!.trim()
            if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
        }
        else -> actor.role?.name
    }

    val secondaryVoice = if (!subName.isNullOrBlank()) {
        if (!isInverted) "🎙 $subName" else "as $subName"
    } else null

    val mainImg = provider.fixUrlNull(mainImgRaw)
    val cornerImg = provider.fixUrlNull(cornerImgRaw)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isHovered) 2.dp else 1.dp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (mainImg != null) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(mainImg)
                        .size(300, 420)
                        .crossfade(true)
                        .build(),
                    contentDescription = mainName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = mainName,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            // Anime character flip button
            if (cornerImg != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onInvertToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(cornerImg)
                            .size(80, 80)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Switch image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = mainName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!roleStr.isNullOrBlank()) {
            Text(
                text = roleStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (secondaryVoice != null) {
            Text(
                text = secondaryVoice,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
