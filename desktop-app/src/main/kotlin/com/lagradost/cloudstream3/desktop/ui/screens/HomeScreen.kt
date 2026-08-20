package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.home.*
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun ComposeHomeScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.home.DesktopHomeViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val providers = uiState.providers
    val activeProviders = uiState.activeProviders
    val activeProviderApis = uiState.activeProviderApis
    val historyList = uiState.historyList
    val mergedPluginIcons = uiState.mergedPluginIcons
    val errorSnapshot = uiState.errorSnapshot

    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()

    DisposableEffect(viewModel) {
        val unregister = com.lagradost.cloudstream3.desktop.ui.GlobalRefreshHandler.register {
            viewModel.onEvent(HomeUiEvent.OnProviderRefresh)
        }
        onDispose { unregister() }
    }

    var currentHeroImageUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (heroBackgroundBlurEnabled && currentHeroImageUrl != null) {
            androidx.compose.animation.Crossfade(
                targetState = currentHeroImageUrl,
                animationSpec = tween(2000),
                label = "home_global_backdrop_crossfade",
                modifier = Modifier.fillMaxSize(),
            ) { targetBgUrl ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val context = coil3.compose.LocalPlatformContext.current
                    val imageRequest = remember(targetBgUrl) {
                        coil3.request.ImageRequest.Builder(context)
                            .data(targetBgUrl)
                            .size(320, 180)
                            .crossfade(true)
                            .build()
                    }
                    coil3.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(heroBackdropBlurRadius.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = heroBackdropDarkening)))
                }
            }
        }

        // Main content area
        val allPages = remember(activeProviderApis, uiState.disabledCatalogs) {
            activeProviderApis.flatMap { prov ->
                val disabledForProv = uiState.disabledCatalogs[prov.name] ?: emptySet()
                prov.mainPage.filter { it.name !in disabledForProv }.map { prov to it }
            }
        }

        HomeManagementDialog(
            show = uiState.showHomeManagement,
            allProviders = providers,
            activeProviders = activeProviders,
            disabledCatalogs = uiState.disabledCatalogs,
            pluginIcons = mergedPluginIcons,
            onDismissRequest = { viewModel.onEvent(HomeUiEvent.OnShowHomeManagement(false)) },
            onSetSingleProvider = { name -> viewModel.onEvent(HomeUiEvent.OnSetSingleProvider(name)) },
            onToggleProviderActive = { name, isActive -> viewModel.onEvent(HomeUiEvent.OnToggleProviderActive(name, isActive)) },
            onMoveProvider = { from, to -> viewModel.onEvent(HomeUiEvent.OnMoveProvider(from, to)) },
            onToggleCatalog = { prov, cat, enabled -> viewModel.onEvent(HomeUiEvent.OnToggleCatalog(prov, cat, enabled)) },
        )

        if (allPages.isNotEmpty()) {
            val listState = rememberLazyListState()
            val safeArea = com.lagradost.cloudstream3.desktop.ui.LocalSafeArea.current

            // Extract individual safe padding components
            val safeLeft = safeArea.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val safeRight = safeArea.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
            val safeTop = safeArea.calculateTopPadding()
            val safeBottom = safeArea.calculateBottomPadding()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(homeVerticalSpacingDp.dp),
                    contentPadding = PaddingValues(
                        top = if (!heroEnabled) safeTop else 0.dp,
                        bottom = safeBottom + 32.dp,
                    ),
                ) {
                    items(allPages.size, key = { index -> "${allPages[index].first.name}_${allPages[index].second.name}" }) { index ->
                        val (currentProvider, pageData) = allPages[index]
                        val isFirstPage = index == 0
                        val horizontalPad = if (isFirstPage && heroEnabled) 0.dp else 20.dp
                        Box(modifier = Modifier.fillMaxWidth()) {
                            HomeCategorySection(
                                pageData = pageData,
                                provider = currentProvider,
                                isFirstPage = isFirstPage,
                                parentScope = coroutineScope,
                                heroMetaMap = uiState.heroMetaMap,
                                allBookmarks = uiState.bookmarks,
                                onPrefetchHeroItem = { prov, item -> viewModel.onEvent(HomeUiEvent.OnPrefetchHeroItem(prov, item)) },
                                onHeroBackgroundChanged = { url -> currentHeroImageUrl = url },
                                outerPadding = horizontalPad,
                                afterHeroContent = if (isFirstPage) {
                                    {
                                        HomeHistoryRow(
                                            historyList = historyList,
                                            providers = providers,
                                            onClearHistory = { viewModel.onEvent(HomeUiEvent.OnClearHistory) },
                                            onRemoveHistoryItem = { viewModel.onEvent(HomeUiEvent.OnRemoveHistoryItem(it)) },
                                            onViewAllClick = {
                                                onNavigate(Config.History)
                                            },
                                            onItemClick = { prov, hist ->
                                                onNavigate(Config.Details(prov.name, hist.showUrl, hist.showName, hist.posterUrl, null, autoPlay = false))
                                            },
                                            onPlayClick = { prov, hist ->
                                                onNavigate(Config.Details(prov.name, hist.showUrl, hist.showName, hist.posterUrl, null, autoPlay = true))
                                            },
                                        )
                                    }
                                } else {
                                    {}
                                },
                                isHistoryVisible = isFirstPage && historyList.isNotEmpty(),
                                onViewAll = { provider, title, items ->
                                    CategoryGridCache.put(provider.name, title, items)
                                    onNavigate(Config.CategoryGrid(provider.name, title))
                                },
                                onItemClick = { provider, item, backdrop, autoPlay ->
                                    onNavigate(Config.Details(provider.name, item.url, item.name, item.posterUrl, backdrop, autoPlay))
                                },
                            )
                        }
                    }
                }
            }
        } else if (providers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "No providers",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        com.lagradost.cloudstream3.desktop.utils.DesktopStrings.NO_PROVIDERS_FOUND,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        com.lagradost.cloudstream3.desktop.utils.DesktopStrings.PLEASE_INSTALL_PLUGINS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onNavigate(Config.Extensions(initialTab = 2)) }) {
                        Text(com.lagradost.cloudstream3.desktop.utils.DesktopStrings.GO_TO_EXTENSIONS)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeHeroCarouselPlaceholder()
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", showLargeHeader = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryRowPlaceholder(title = "Loading...", showLargeHeader = true)
                }
            }
        }
    }
}
