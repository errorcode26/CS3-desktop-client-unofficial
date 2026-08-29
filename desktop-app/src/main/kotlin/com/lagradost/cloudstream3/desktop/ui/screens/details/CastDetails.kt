package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDetailsDialog(
    actor: com.lagradost.cloudstream3.ActorData,
    onDismiss: () -> Unit,
    onMovieClick: (com.lagradost.cloudstream3.SearchResponse) -> Unit = {},
) {
    var details by remember { mutableStateOf<TmdbEnrichmentService.DesktopActorDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        show = true
    }

    val triggerDismiss: () -> Unit = {
        show = false
    }

    LaunchedEffect(show) {
        if (!show) {
            delay(300)
            onDismiss()
        }
    }

    LaunchedEffect(actor.actor.name) {
        isLoading = true
        // For anime dual-cast: actor = character art, voiceActor = human VA.
        // TMDB only knows real people, so search by voiceActor name if available.
        val searchName = actor.voiceActor?.name?.takeIf { it.isNotBlank() } ?: actor.actor.name
        details = TmdbEnrichmentService.getActorDetails(searchName)
        isLoading = false
    }

    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog(
        show = show,
        onDismissRequest = triggerDismiss,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (details == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No details available for ${actor.voiceActor?.name?.takeIf { it.isNotBlank() } ?: actor.actor.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val d = details ?: return@Box
                val coroutineScope = rememberCoroutineScope()

                val mainScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(mainScrollState),
                ) {
                    // Header with image and basic info
                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        if (d.profilePath != null) {
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(d.profilePath)
                                    .size(240, 240)
                                    .build(),
                                contentDescription = d.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Person, contentDescription = d.name, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        val characterRole = when {
                            actor.voiceActor != null -> "🎙 Voice for ${actor.actor.name}"
                            !actor.roleString.isNullOrBlank() && !actor.roleString.equals("Director", ignoreCase = true) && !actor.roleString.equals("Creator", ignoreCase = true) -> {
                                val raw = actor.roleString!!.trim()
                                if (raw.startsWith("as ", ignoreCase = true)) raw else "as $raw"
                            }
                            else -> null
                        }

                        Column {
                            Text(d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            if (!characterRole.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = characterRole,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (d.birthday != null) {
                                Text("Born: ${d.birthday}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (d.placeOfBirth != null) {
                                Text("Place of Birth: ${d.placeOfBirth}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (d.deathday != null) {
                                Text("Died: ${d.deathday}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    ) {
                        if (d.biography != null) {
                            Text("Biography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))
                            var isBioExpanded by remember { mutableStateOf(false) }
                            var showReadMoreButton by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                            ) {
                                Text(
                                    text = d.biography,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp,
                                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { textLayoutResult ->
                                        if (!isBioExpanded && textLayoutResult.hasVisualOverflow) {
                                            showReadMoreButton = true
                                        }
                                    },
                                )

                                if (showReadMoreButton) {
                                    Text(
                                        text = if (isBioExpanded) "Read less" else "Read more",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { isBioExpanded = !isBioExpanded }
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        if (d.knownFor.isNotEmpty()) {
                            val knownForScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Known For", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { coroutineScope.launch { knownForScrollState.animateScrollBy(-500f) } }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { coroutineScope.launch { knownForScrollState.animateScrollBy(500f) } }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            androidx.compose.foundation.lazy.LazyRow(
                                state = knownForScrollState,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        knownForScrollState.dispatchRawDelta(-dragAmount)
                                    }
                                },
                            ) {
                                items(d.knownFor) { item ->
                                    Column(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (item.url.isNotBlank()) {
                                                    triggerDismiss()
                                                    onMovieClick(item)
                                                }
                                            },
                                    ) {
                                        AsyncImage(
                                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                                .data(item.posterUrl)
                                                .size(320, 480)
                                                .build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.66f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            item.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Close Button
                IconButton(
                    onClick = triggerDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            } // Close else
        } // Close Box
    } // Close CloudstreamCustomDialog
} // Close Fun
