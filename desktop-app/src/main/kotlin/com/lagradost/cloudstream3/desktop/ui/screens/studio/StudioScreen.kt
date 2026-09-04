package com.lagradost.cloudstream3.desktop.ui.screens.studio

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents
import com.lagradost.cloudstream3.desktop.ui.screens.studio.dialogs.StudioProviderMatchDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioCategory
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioDetail
import com.lagradost.cloudstream3.desktop.ui.screens.studio.model.StudioMediaItem

@Composable
fun StudioScreen(
    name: String,
    companyId: Int? = null,
    logoUrl: String? = null,
    originCountry: String? = null,
    onBack: () -> Unit,
    onNavigate: (Config) -> Unit,
    viewModel: StudioViewModel = remember { StudioViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = LocalDesktopTheme.current

    LaunchedEffect(name, companyId) {
        viewModel.loadStudio(name, companyId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.Background),
    ) {
        // Ambient backdrop glow from studio logo / artwork
        val ambientLogo = uiState.studioDetail?.logoUrl ?: logoUrl
        if (!ambientLogo.isNullOrBlank()) {
            AsyncImage(
                model = ambientLogo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(120.dp)
                    .graphicsLayer(alpha = 0.08f),
            )
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = theme.Accent,
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else if (uiState.error != null && uiState.studioDetail == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = theme.TextMuted.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error ?: "No information available",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.TextMuted,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.onEvent(StudioUiEvent.Retry) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.Accent),
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            } else {
                val detail = uiState.studioDetail ?: return@Box
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= 900.dp) {
                        // Desktop Split-View
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            // Left Sidebar: Studio Info
                            StudioHeroSidebar(
                                detail = detail,
                                fallbackLogo = logoUrl,
                                fallbackCountry = originCountry,
                                isScrollable = true,
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight(),
                            )

                            // Right Area: Catalog Tabs & Grid
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                StudioCatalogSection(
                                    uiState = uiState,
                                    onSelectCategory = { viewModel.onEvent(StudioUiEvent.SelectCategory(it)) },
                                    onItemClick = { viewModel.onEvent(StudioUiEvent.SelectItemForMatch(it)) },
                                )
                            }
                        }
                    } else {
                        // Compact Vertical Scroll
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            StudioHeroSidebar(
                                detail = detail,
                                fallbackLogo = logoUrl,
                                fallbackCountry = originCountry,
                                isScrollable = false,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            StudioCatalogSection(
                                uiState = uiState,
                                onSelectCategory = { viewModel.onEvent(StudioUiEvent.SelectCategory(it)) },
                                onItemClick = { viewModel.onEvent(StudioUiEvent.SelectItemForMatch(it)) },
                            )
                        }
                    }
                }
            }
        }

        // Top back button
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

        // Top right window controls pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            WindowControlsPill(isHome = false, isCompact = false)
        }

        // Multi-Provider Stream Match Resolver Dialog
        if (uiState.selectedItemForMatch != null) {
            StudioProviderMatchDialog(
                item = uiState.selectedItemForMatch!!,
                matches = uiState.providerMatches,
                isSearching = uiState.isSearchingProviders,
                onDismissRequest = { viewModel.onEvent(StudioUiEvent.CloseProviderPicker) },
                onOpenDetails = { providerName, url, title, poster ->
                    viewModel.onEvent(StudioUiEvent.CloseProviderPicker)
                    onNavigate(
                        Config.Details(
                            providerName = providerName,
                            url = url,
                            preloadedName = title,
                            preloadedPoster = poster,
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun StudioHeroSidebar(
    detail: StudioDetail,
    fallbackLogo: String?,
    fallbackCountry: String?,
    isScrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val theme = LocalDesktopTheme.current
    var isDescExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.then(if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // High-res Studio Logo Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .width(220.dp)
                .height(130.dp),
        ) {
            val logo = detail.logoUrl ?: fallbackLogo
            if (!logo.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = logo,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Studio Name
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = theme.TextPrimary,
        )

        // Studio Info Card
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.SurfaceCard,
            border = BorderStroke(1.dp, theme.Divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Studio Info",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.TextPrimary,
                )

                val country = detail.originCountry ?: fallbackCountry
                if (!country.isNullOrBlank()) {
                    DetailRow(label = "Origin Country", value = country.uppercase())
                }

                if (!detail.headquarters.isNullOrBlank()) {
                    DetailRow(label = "Headquarters", value = detail.headquarters)
                }

                DetailRow(
                    label = "Catalog",
                    value = "${detail.movieTitles.size} Movies • ${detail.tvTitles.size} TV Shows",
                )
            }
        }

        // Description / Overview Section
        if (!detail.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.SurfaceCard,
                border = BorderStroke(1.dp, theme.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = detail.description,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = theme.TextMuted,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.description.length > 280) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isDescExpanded) "Show Less" else "Read More",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.Accent,
                            modifier = Modifier
                                .clickable { isDescExpanded = !isDescExpanded }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val theme = LocalDesktopTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.TextMuted.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = theme.TextPrimary,
        )
    }
}

@Composable
private fun StudioCatalogSection(
    uiState: StudioUiState,
    onSelectCategory: (StudioCategory) -> Unit,
    onItemClick: (StudioMediaItem) -> Unit,
) {
    val detail = uiState.studioDetail ?: return
    val theme = LocalDesktopTheme.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTabPill(
                title = "All",
                count = detail.allTitles.size,
                isSelected = uiState.selectedCategory == StudioCategory.ALL,
                onClick = { onSelectCategory(StudioCategory.ALL) },
            )
            CategoryTabPill(
                title = "Movies",
                count = detail.movieTitles.size,
                isSelected = uiState.selectedCategory == StudioCategory.MOVIES,
                onClick = { onSelectCategory(StudioCategory.MOVIES) },
            )
            CategoryTabPill(
                title = "TV Series",
                count = detail.tvTitles.size,
                isSelected = uiState.selectedCategory == StudioCategory.TV_SHOWS,
                onClick = { onSelectCategory(StudioCategory.TV_SHOWS) },
            )
        }

        // Animated Catalog Grid
        AnimatedContent(
            targetState = uiState.selectedCategory,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "StudioCategoryTransition",
            modifier = Modifier.fillMaxSize(),
        ) { targetCategory ->
            val displayedTitles = when (targetCategory) {
                StudioCategory.ALL -> detail.allTitles
                StudioCategory.MOVIES -> detail.movieTitles
                StudioCategory.TV_SHOWS -> detail.tvTitles
            }

            if (displayedTitles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No titles found in this category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.TextMuted,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(displayedTitles, key = { "${it.mediaType}-${it.tmdbId}" }) { item ->
                        StudioMediaCard(
                            item = item,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabPill(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current

    val pillBg by animateColorAsState(
        targetValue = if (isSelected) theme.Accent else theme.SurfaceCard,
        animationSpec = tween(180),
        label = "pillBg",
    )
    val pillBorder by animateColorAsState(
        targetValue = if (isSelected) theme.Accent else theme.Divider,
        animationSpec = tween(180),
        label = "pillBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else theme.TextPrimary,
        animationSpec = tween(180),
        label = "textColor",
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = pillBg,
        border = BorderStroke(1.dp, pillBorder),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = textColor,
            )
            Surface(
                shape = CircleShape,
                color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.White else theme.TextMuted,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StudioMediaCard(
    item: StudioMediaItem,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        // Poster Box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = theme.SurfaceCard,
            border = BorderStroke(
                1.dp,
                if (isHovered) theme.Accent.copy(alpha = 0.75f) else theme.Divider,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF181818)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (item.mediaType == TvType.TvSeries) Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Top Media Type / Year Badge
                if (!item.releaseYear.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Rating Badge
                if (item.voteAverage != null && item.voteAverage > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", item.voteAverage),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isHovered) theme.Accent else theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Media Type subtitle
        Text(
            text = if (item.mediaType == TvType.TvSeries) "TV Series" else "Movie",
            style = MaterialTheme.typography.bodySmall,
            color = theme.TextMuted.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

