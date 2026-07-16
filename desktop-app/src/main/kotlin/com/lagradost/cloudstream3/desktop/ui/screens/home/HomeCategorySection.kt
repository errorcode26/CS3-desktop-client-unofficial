package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, HomePageResponse>()
private val categoryMutex = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

@Composable
fun HomeCategorySection(
    pageData: MainPageData,
    provider: MainAPI,
    isFirstPage: Boolean = false,
    parentScope: CoroutineScope,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.home.HomeViewModel,
    afterHeroContent: @Composable () -> Unit = {},
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?, Boolean) -> Unit,
) {
    val cacheKey = "${provider.name}_${pageData.name}"
    var homePage by remember(cacheKey) { mutableStateOf<HomePageResponse?>(categoryCache[cacheKey]) }
    var isLoading by remember(cacheKey) { mutableStateOf(homePage == null) }
    var visible by remember { mutableStateOf(false) }

    var errorMessage by remember(cacheKey) { mutableStateOf<String?>(null) }

    val fetchPage = {
        parentScope.launch {
            val mutex = categoryMutex.getOrPut(cacheKey) { kotlinx.coroutines.sync.Mutex() }
            isLoading = true
            mutex.withLock {
                if (categoryCache[cacheKey] == null) {
                    errorMessage = null
                    try {
                        val request = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                        val response = withContext(Dispatchers.IO) { provider.getMainPage(1, request) }
                        if (response != null && response.items.isNotEmpty()) {
                            categoryCache[cacheKey] = response
                        } else {
                            errorMessage = "No items found."
                        }
                    } catch (e: Throwable) {
                        DesktopErrorReporter.report("getMainPage failed for ${provider.name} - ${pageData.name.ifBlank { "Unknown Category" }}", e)
                        errorMessage = e.localizedMessage ?: "Connection error"
                    }
                }
                homePage = categoryCache[cacheKey]
            }
            isLoading = false
        }
    }

    LaunchedEffect(pageData, provider) {
        visible = true
        if (homePage == null) {
            fetchPage()
        }
    }

    val layoutWidthSetting by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.layoutWidth.collectAsState()
    val maxWidthConstraint = when (layoutWidthSetting) {
        "Compact" -> 1000.dp
        "Modern" -> 1400.dp
        else -> androidx.compose.ui.unit.Dp.Unspecified
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        // Header logic moved inside the loop to prevent awkward gaps

        if (isLoading) {
            if (isFirstPage) {
                HomeHeroCarouselPlaceholder()
            } else {
                CategoryRowPlaceholder(
                    title = pageData.name,
                    maxWidthConstraint = maxWidthConstraint,
                    showLargeHeader = !isFirstPage
                )
            }
        } else if (homePage != null && homePage!!.items.isNotEmpty()) {
            homePage!!.items.forEachIndexed { sectionIndex, section ->
                if (isFirstPage && sectionIndex == 0 && section.list.size >= 3) {
                    HomeHeroCarousel(
                        items = section.list,
                        provider = provider,
                        viewModel = viewModel,
                        onItemClick = { item, backdrop, autoPlay -> onItemClick(provider, item, backdrop, autoPlay) },
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.widthIn(max = maxWidthConstraint)) {
                            afterHeroContent()
                        }
                    }
                } else {
                    val titleStr = section.name.takeIf { it.isNotBlank() } ?: pageData.name
                    val showLargeHeader = sectionIndex == 0 && !isFirstPage && !titleStr.equals(pageData.name, ignoreCase = true)

                    // Calculate horizontal padding so items align with the constrained header.
                    // The LazyRow itself fills full width (no clipping), using contentPadding
                    // to indent items. This way edge cards are never cut off.
                    val rowPadding = if (maxWidthConstraint == androidx.compose.ui.unit.Dp.Unspecified) {
                        androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    } else {
                        // Let Compose compute the side insets reactively
                        androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    }

                    // Header row — constrained to maxWidthConstraint and centered
                    if (showLargeHeader) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(modifier = Modifier.widthIn(max = maxWidthConstraint)) {
                                Text(
                                    text = pageData.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 0.dp),
                                )
                            }
                        }
                    }

                    val isLoop = section.list.size >= 4

                    // The category row header (title + chevrons) is constrained; the LazyRow
                    // extends full-width with BoxWithConstraints-derived padding so items align.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BoxWithConstraints(modifier = Modifier.widthIn(max = maxWidthConstraint).fillMaxWidth()) {
                            // Compute how much horizontal inset the list needs so that when
                            // the window is wider than maxWidthConstraint, items still start
                            // at the same x as the header text.
                            val constrainedWidth = this.maxWidth
                            CategoryRowWithHeader(
                                title = titleStr,
                                itemCount = section.list.size,
                                isInfinite = isLoop,
                                onViewAll = { onViewAll(provider, section.name, section.list) },
                                rowContentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(
                                    count = if (isLoop) Int.MAX_VALUE else section.list.size,
                                    key = { index ->
                                        val itemIndex = if (isLoop) index % section.list.size else index
                                        "${section.list[itemIndex].url}_$index"
                                    },
                                ) { index ->
                                    val itemIndex = if (isLoop) index % section.list.size else index
                                    val posterItem = section.list[itemIndex]
                                    PosterCard(
                                        item = posterItem,
                                        provider = provider,
                                        onClick = { onItemClick(provider, posterItem, null, false) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val minHeight = if (isFirstPage) 350.dp else 150.dp
            val topPadding = if (isFirstPage) 80.dp else 0.dp
            Box(modifier = Modifier.fillMaxWidth().height(minHeight).padding(top = topPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        errorMessage ?: "Failed to load category.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { fetchPage() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
