package com.lagradost.cloudstream3.desktop.ui.screens.person

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
import androidx.compose.ui.graphics.Brush
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
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.FilmographyCategory
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonDetail
import com.lagradost.cloudstream3.desktop.ui.screens.person.model.PersonMediaCredit

@Composable
fun PersonScreen(
    name: String,
    image: String? = null,
    tmdbId: Int? = null,
    onBack: () -> Unit,
    onNavigate: (Config) -> Unit,
    viewModel: PersonViewModel = remember { PersonViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = LocalDesktopTheme.current

    LaunchedEffect(name, tmdbId) {
        viewModel.loadPerson(name, tmdbId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.Background)
    ) {
        // Ambient backdrop glow from person's photo
        val ambientPhoto = uiState.personDetail?.profileUrl ?: image
        if (!ambientPhoto.isNullOrBlank()) {
            AsyncImage(
                model = ambientPhoto,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
                    .graphicsLayer(alpha = 0.12f),
            )
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
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
            } else if (uiState.error != null && uiState.personDetail == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
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
                        onClick = { viewModel.onEvent(PersonUiEvent.Retry) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.Accent),
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            } else {
                val detail = uiState.personDetail ?: return@Box
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (maxWidth >= 900.dp) {
                        // Desktop Split-View
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            // Left Sidebar: Hero & Bio
                            PersonHeroSidebar(
                                detail = detail,
                                fallbackImage = image,
                                isScrollable = true,
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight(),
                            )

                            // Right Area: Filmography Tabs & Grid
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                FilmographySection(
                                    uiState = uiState,
                                    onSelectCategory = { viewModel.onEvent(PersonUiEvent.SelectCategory(it)) },
                                    onCreditClick = { viewModel.onEvent(PersonUiEvent.SelectCreditForMatch(it)) },
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
                            PersonHeroSidebar(
                                detail = detail,
                                fallbackImage = image,
                                isScrollable = false,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            FilmographySection(
                                uiState = uiState,
                                onSelectCategory = { viewModel.onEvent(PersonUiEvent.SelectCategory(it)) },
                                onCreditClick = { viewModel.onEvent(PersonUiEvent.SelectCreditForMatch(it)) },
                            )
                        }
                    }
                }
            }
        }

        // Details-style top back button (exact same as DetailsScreen)
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

        // Details-style top right window controls pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            WindowControlsPill(isHome = false, isCompact = false)
        }

        // Multi-Provider Stream Match Resolver Dialog
        if (uiState.selectedCreditForMatch != null) {
            PersonProviderMatchDialog(
                credit = uiState.selectedCreditForMatch!!,
                matches = uiState.providerMatches,
                isSearching = uiState.isSearchingProviders,
                onDismissRequest = { viewModel.onEvent(PersonUiEvent.CloseProviderPicker) },
                onOpenDetails = { providerName, url, title, poster ->
                    viewModel.onEvent(PersonUiEvent.CloseProviderPicker)
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
private fun PersonHeroSidebar(
    detail: PersonDetail,
    fallbackImage: String?,
    isScrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val theme = LocalDesktopTheme.current
    var isBioExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.then(if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // High-res Portrait with Soft Border
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.SurfaceCard,
            border = BorderStroke(1.dp, theme.Divider),
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(2f / 3f),
        ) {
            val photoUrl = detail.profileUrl ?: fallbackImage
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = detail.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = theme.TextMuted.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Name & Department
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = theme.TextPrimary,
        )

        if (!detail.knownForDepartment.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = theme.Accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, theme.Accent.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = detail.knownForDepartment,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = theme.Accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Personal Details Card
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
                    text = "Personal Info",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.TextPrimary,
                )

                if (!detail.birthday.isNullOrBlank()) {
                    DetailRow(label = "Born", value = detail.birthday)
                }

                if (!detail.placeOfBirth.isNullOrBlank()) {
                    DetailRow(label = "Birthplace", value = detail.placeOfBirth)
                }

                if (!detail.deathday.isNullOrBlank()) {
                    DetailRow(label = "Died", value = detail.deathday)
                }

                DetailRow(
                    label = "Credits",
                    value = "${detail.movieCredits.size} Movies • ${detail.tvCredits.size} TV Shows"
                )
            }
        }

        // Biography Section
        if (!detail.biography.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.SurfaceCard,
                border = BorderStroke(1.dp, theme.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Biography",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = detail.biography,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = theme.TextMuted,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.biography.length > 280) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isBioExpanded) "Show Less" else "Read More",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.Accent,
                            modifier = Modifier
                                .clickable { isBioExpanded = !isBioExpanded }
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
private fun FilmographySection(
    uiState: PersonUiState,
    onSelectCategory: (FilmographyCategory) -> Unit,
    onCreditClick: (PersonMediaCredit) -> Unit,
) {
    val detail = uiState.personDetail ?: return
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
                count = detail.allCredits.size,
                isSelected = uiState.selectedCategory == FilmographyCategory.ALL,
                onClick = { onSelectCategory(FilmographyCategory.ALL) },
            )
            CategoryTabPill(
                title = "Movies",
                count = detail.movieCredits.size,
                isSelected = uiState.selectedCategory == FilmographyCategory.MOVIES,
                onClick = { onSelectCategory(FilmographyCategory.MOVIES) },
            )
            CategoryTabPill(
                title = "TV Series",
                count = detail.tvCredits.size,
                isSelected = uiState.selectedCategory == FilmographyCategory.TV_SHOWS,
                onClick = { onSelectCategory(FilmographyCategory.TV_SHOWS) },
            )
        }

        // Animated Filmography Grid
        AnimatedContent(
            targetState = uiState.selectedCategory,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "FilmographyCategoryTransition",
            modifier = Modifier.fillMaxSize(),
        ) { targetCategory ->
            val displayedCredits = when (targetCategory) {
                FilmographyCategory.ALL -> detail.allCredits
                FilmographyCategory.MOVIES -> detail.movieCredits
                FilmographyCategory.TV_SHOWS -> detail.tvCredits
            }

            if (displayedCredits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No credits found in this category",
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
                    items(displayedCredits, key = { "${it.mediaType}-${it.tmdbId}" }) { credit ->
                        FilmographyCreditCard(
                            credit = credit,
                            onClick = { onCreditClick(credit) },
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
private fun FilmographyCreditCard(
    credit: PersonMediaCredit,
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
                if (!credit.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = credit.posterUrl,
                        contentDescription = credit.title,
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
                            imageVector = if (credit.mediaType == TvType.TvSeries) Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // Top Media Type / Year Badge
                if (!credit.releaseYear.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = credit.releaseYear,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Rating Badge
                if (credit.voteAverage != null && credit.voteAverage > 0) {
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
                                text = String.format(java.util.Locale.US, "%.1f", credit.voteAverage),
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
            text = credit.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isHovered) theme.Accent else theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Character / Role
        if (!credit.characterOrJob.isNullOrBlank()) {
            Text(
                text = "as ${credit.characterOrJob}",
                style = MaterialTheme.typography.bodySmall,
                color = theme.TextMuted.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PersonProviderMatchDialog(
    credit: PersonMediaCredit,
    matches: List<ProviderMatch>,
    isSearching: Boolean,
    onDismissRequest: () -> Unit,
    onOpenDetails: (providerName: String, url: String, title: String, poster: String?) -> Unit,
) {
    val theme = LocalDesktopTheme.current

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.74f).fillMaxHeight(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFA0E1524))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            // Header Row: Title, Stream Counter Badge & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Extensions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Available Extensions & Streams",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                    )

                    // Results status pill
                    if (isSearching) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Searching extensions...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = "${matches.size} Streams Found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Body: Split 2-Column Desktop Layout
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Left Column: Media Info (32% width)
                Column(
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    ) {
                        val posterImg = credit.posterUrl ?: credit.backdropUrl
                        if (!posterImg.isNullOrBlank()) {
                            AsyncImage(
                                model = posterImg,
                                contentDescription = credit.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1C1C1E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (credit.mediaType == TvType.TvSeries) Icons.Default.Tv else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }

                        // Top left rating badge
                        credit.voteAverage?.let { rating ->
                            if (rating > 0.0) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    DesktopBadgeComponents.RatingGoldBadge(rating = rating)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = credit.title,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.TextPrimary,
                        lineHeight = 20.sp,
                    )

                    val mediaTypeStr = if (credit.mediaType == TvType.TvSeries) "TV Series" else "Movie"
                    val metaSubtitle = if (!credit.releaseYear.isNullOrBlank()) "${credit.releaseYear} • $mediaTypeStr" else mediaTypeStr
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metaSubtitle,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.TextMuted,
                    )

                    if (!credit.characterOrJob.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.Accent.copy(alpha = 0.14f),
                            border = BorderStroke(0.5.dp, theme.Accent.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = "as ${credit.characterOrJob}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.Accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    credit.overview?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = theme.TextMuted,
                            lineHeight = 17.sp,
                        )
                    }
                }

                // Right Column: Matching Providers List (68% width)
                Column(
                    modifier = Modifier
                        .weight(0.68f)
                        .fillMaxHeight(),
                ) {
                    if (isSearching && matches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(38.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                )
                                Text(
                                    text = "Searching active extensions for matching streams...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.TextMuted,
                                )
                            }
                        }
                    } else if (!isSearching && matches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = theme.TextMuted,
                                    modifier = Modifier.size(42.dp),
                                )
                                Text(
                                    text = "No matching streams found for this title",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.TextPrimary,
                                )
                                Text(
                                    text = "Try installing additional provider extensions in Extensions tab or search directly.",
                                    fontSize = 12.5.sp,
                                    color = theme.TextMuted,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(matches) { match ->
                                PersonProviderMatchRow(
                                    match = match,
                                    onClick = {
                                        onOpenDetails(
                                            match.providerName,
                                            match.searchResponse.url,
                                            match.searchResponse.name,
                                            match.searchResponse.posterUrl ?: credit.posterUrl,
                                        )
                                    },
                                )
                            }

                            if (isSearching) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Searching remaining extensions...",
                                            fontSize = 11.5.sp,
                                            color = theme.TextMuted,
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

@Composable
private fun PersonProviderMatchRow(
    match: ProviderMatch,
    onClick: () -> Unit,
) {
    val theme = LocalDesktopTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                0.5.dp,
                if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        color = if (isHovered) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Provider Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = match.providerName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Title
                Text(
                    text = match.displayTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Format Badges
                if (match.hasSub || match.hasDub) {
                    DesktopBadgeComponents.SubDubBadge(hasSub = match.hasSub, hasDub = match.hasDub)
                }
                if (!match.qualityText.isNullOrBlank()) {
                    DesktopBadgeComponents.QualityBadge(quality = match.qualityText)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Open Details Action Button
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "Open Details",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
