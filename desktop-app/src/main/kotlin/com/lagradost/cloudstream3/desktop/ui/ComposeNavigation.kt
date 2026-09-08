package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.navigation.RootComponent
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeDetailsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeHomeScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeLibraryScreen
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ComposeExtensionScreen
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.launch

data class VideoLaunchData(
    val links: List<com.lagradost.cloudstream3.utils.ExtractorLink> = emptyList(),
    val initialIndex: Int,
    val title: String?,
    val subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    val startPositionMs: Long,
    val history: WatchHistory,
    val loadResponse: com.lagradost.cloudstream3.LoadResponse? = null,
    val episodes: List<com.lagradost.cloudstream3.Episode> = emptyList(),
    val enrichedLogoUrl: String? = null,
    val enrichedBackdropUrl: String? = null,
    val enrichedActors: List<com.lagradost.cloudstream3.ActorData>? = null,
)

val LocalVideoPlayer = androidx.compose.runtime.staticCompositionLocalOf<(VideoLaunchData?) -> Unit> { { } }
val LocalVideoPlayerActive = androidx.compose.runtime.compositionLocalOf<Boolean> { false }
val LocalWindowState = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.window.WindowState?> { null }
val LocalComposeWindow = androidx.compose.runtime.staticCompositionLocalOf<java.awt.Window?> { null }


/**
 * Provides real AWT exclusive fullscreen control across the entire Compose tree.
 * Uses GraphicsDevice.setFullScreenWindow() which is the only way to get true fullscreen on Windows
 * (WindowPlacement.Fullscreen is "fake" — the OS title bar and taskbar still render on top).
 */
@androidx.compose.runtime.Stable
class FullscreenController(
    isFullscreen: Boolean,
    var toggle: () -> Unit,
    popupKey: Int = 0,
    var mainFrame: java.awt.Window? = null,
    /**
     * Tracks the main window's content-pane size in physical pixels.
     * Updated from an AWT ComponentListener on the EDT, so it always reflects
     * the true post-resize dimensions — unlike BoxWithConstraints which can
     * report stale values during the fullscreen ↔ maximized transition.
     * Zero means "not yet measured; fall back to BoxWithConstraints."
     */
    contentAreaPx: Pair<Int, Int> = Pair(0, 0),
) {
    var isFullscreen by androidx.compose.runtime.mutableStateOf(isFullscreen)
    var popupKey by androidx.compose.runtime.mutableStateOf(popupKey)
    var contentAreaPx by androidx.compose.runtime.mutableStateOf(contentAreaPx)
}
val LocalFullscreenController = androidx.compose.runtime.staticCompositionLocalOf<FullscreenController?> { null }

@androidx.compose.ui.ExperimentalComposeUiApi
@Composable
fun CloudstreamApp(rootComponent: RootComponent) {
    var showErrorsDialog by remember { mutableStateOf(false) }
    var currentVideo by remember { mutableStateOf<VideoLaunchData?>(null) }
    val childStack by rootComponent.childStack.subscribeAsState()

    val isLightMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.themeAccent.collectAsState()
    val appThemeBackground by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.appThemeBackground.collectAsState()
    val customThemeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.customThemeAccent.collectAsState()
    val customAppThemeBackground by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.customAppThemeBackground.collectAsState()
    val uiCardOpacity by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.uiCardOpacity.collectAsState()

    val primaryColor = com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName(themeAccent, customThemeAccent)
    val desktopColors = com.lagradost.cloudstream3.desktop.ui.theme.buildDesktopColors(primaryColor, isLightMode, amoledMode, appThemeBackground, customAppThemeBackground).copy(cardOpacity = uiCardOpacity)
    val selectedFont by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.selectedFont.collectAsState()
    val typography = androidx.compose.runtime.remember(selectedFont) {
        com.lagradost.cloudstream3.desktop.ui.theme.buildTypography(
            com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(selectedFont),
        )
    }

    val profiles by com.lagradost.cloudstream3.desktop.profile.ProfileManager.profiles.collectAsState()
    val isPickerOnStartup by com.lagradost.cloudstream3.desktop.profile.ProfileManager.isPickerOnStartup.collectAsState()
    val autoSignIn by com.lagradost.cloudstream3.desktop.profile.ProfileManager.autoSignIn.collectAsState()
    val activeProfile by com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.collectAsState()

    var showStartupProfileSelect by remember {
        mutableStateOf(
            if (com.lagradost.cloudstream3.desktop.profile.ProfileManager.autoSignIn.value) {
                com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.value.hasPin
            } else {
                true
            }
        )
    }
    var showProfileManagerModal by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        if (!showStartupProfileSelect) {
            com.lagradost.cloudstream3.desktop.profile.ProfileManager.triggerWelcomeToast(
                com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.value
            )
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val launcher: (VideoLaunchData) -> Unit = { currentVideo = it }
        com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.globalPlayerLauncher.set(launcher)
        onDispose {
            com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.globalPlayerLauncher.set(null)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalVideoPlayer provides { currentVideo = it },
        LocalVideoPlayerActive provides (currentVideo != null),
        com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme provides desktopColors,
    ) {
        com.lagradost.cloudstream3.desktop.ui.GlobalMediaLauncher.GlobalNetworkStreamDialog()

        val appColorScheme = com.lagradost.cloudstream3.desktop.ui.theme.buildColorScheme(primaryColor, desktopColors, isLightMode)

        androidx.compose.material3.MaterialTheme(colorScheme = appColorScheme, typography = typography) {
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (showStartupProfileSelect || showProfileManagerModal) {
                        com.lagradost.cloudstream3.desktop.ui.screens.profile.ProfileSelectScreen(
                        onNavigateHome = {
                            showStartupProfileSelect = false
                            showProfileManagerModal = false
                            com.lagradost.cloudstream3.desktop.profile.ProfileManager.triggerWelcomeToast(
                                com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfile.value
                            )
                        },
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Release) {
                                            // Ignore back/forward navigation if the video player is open
                                            if (currentVideo == null) {
                                                when (event.button) {
                                                    PointerButton.Back -> {
                                                        if (com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.isActive) {
                                                            com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.dismiss()
                                                        } else if (com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSession.activeSubScreen != null) {
                                                            com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSession.activeSubScreen = null
                                                        } else {
                                                            rootComponent.pop()
                                                        }
                                                    }
                                                    PointerButton.Forward -> {
                                                        // Decompose doesn't natively have forward stack out of the box unless implemented.
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                    ) {
                    val blurRadius by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (com.lagradost.cloudstream3.desktop.ui.components.GlobalDialogState.isAnyDialogOpen ||
                            com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.isActive
                        ) {
                            16.dp
                        } else {
                            0.dp
                        },
                    )

                    val rootContentModifier = if (blurRadius > 0.dp) {
                        androidx.compose.ui.Modifier.fillMaxSize().blur(blurRadius)
                    } else {
                        androidx.compose.ui.Modifier.fillMaxSize()
                    }

                    androidx.compose.foundation.layout.Box(
                        modifier = rootContentModifier,
                    ) {
                        val activeInstance = childStack.active.instance

                        val title = when (activeInstance) {
                            is RootComponent.Child.Home -> "Home"
                            is RootComponent.Child.Explore -> "Explore & Catalogs"
                            is RootComponent.Child.History -> "Watch History"
                            is RootComponent.Child.Search -> "Search"
                            is RootComponent.Child.Extensions -> "Extensions"
                            is RootComponent.Child.Library -> "Library"
                            is RootComponent.Child.Downloads -> "Downloads"
                            is RootComponent.Child.Settings -> "Settings"
                            is RootComponent.Child.CategoryGrid -> activeInstance.component.title
                            is RootComponent.Child.Details -> activeInstance.component.config.preloadedName?.let { "Details: $it" } ?: "Details"
                            is RootComponent.Child.Person -> activeInstance.component.config.name
                            is RootComponent.Child.Studio -> activeInstance.component.config.name
                            is RootComponent.Child.FullCast -> "${activeInstance.component.config.mediaTitle} - Cast & Crew"
                        }

                        LaunchedEffect(activeInstance, currentVideo) {
                            if (currentVideo == null) {
                                when (activeInstance) {
                                    is RootComponent.Child.Details -> {
                                        val name = activeInstance.component.config.preloadedName
                                        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateBrowsing("Details", name)
                                    }
                                    is RootComponent.Child.Person -> {
                                        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateBrowsing("Person", activeInstance.component.config.name)
                                    }
                                    is RootComponent.Child.Studio -> {
                                        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateBrowsing("Studio", activeInstance.component.config.name)
                                    }
                                    is RootComponent.Child.FullCast -> {
                                        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateBrowsing("Cast & Crew", activeInstance.component.config.mediaTitle)
                                    }
                                    else -> {
                                        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateBrowsing(title)
                                    }
                                }
                            }
                        }

                        val fullscreenController = LocalFullscreenController.current
                        val isFullscreen = fullscreenController?.isFullscreen == true
                        LaunchedEffect(isFullscreen, currentVideo) {
                            if (currentVideo != null) {
                                com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updateFullscreen(isFullscreen)
                            }
                        }

                        val applySafePadding = when (activeInstance) {
                            is RootComponent.Child.Details -> false // Details manually pads itself
                            is RootComponent.Child.Person -> false // Person manually pads itself
                            is RootComponent.Child.Studio -> false // Studio manually pads itself
                            is RootComponent.Child.FullCast -> false // FullCast manually pads itself
                            is RootComponent.Child.Home -> false // Home needs full-bleed for Hero
                            else -> true
                        }
                        val showDock = when (activeInstance) {
                            is RootComponent.Child.Details -> false
                            is RootComponent.Child.Person -> false
                            is RootComponent.Child.Studio -> false
                            is RootComponent.Child.FullCast -> false
                            else -> true
                        }
                        val showTopBar = when (activeInstance) {
                            is RootComponent.Child.Details -> false
                            is RootComponent.Child.Person -> false
                            is RootComponent.Child.Studio -> false
                            is RootComponent.Child.FullCast -> false
                            else -> true
                        }

                        val globalUiScale by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.globalUiScale.collectAsState()
                        val baseDensity = androidx.compose.ui.platform.LocalDensity.current
                        val scaledDensity = remember(baseDensity, globalUiScale) {
                            androidx.compose.ui.unit.Density(
                                density = baseDensity.density * globalUiScale,
                                fontScale = baseDensity.fontScale * globalUiScale,
                            )
                        }

                        if (currentVideo != null) {
                            val launchData = currentVideo!!
                            androidx.compose.runtime.key(launchData.history.showUrl, launchData.history.episodeId) {
                                val playerViewModel = remember(launchData.history.showUrl, launchData.history.episodeId) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.player.EmbeddedPlayerViewModel()
                                }
                                com.lagradost.cloudstream3.desktop.ui.screens.player.EmbeddedVideoPlayer(
                                    launchData = launchData,
                                    viewModel = playerViewModel,
                                    isExiting = false,
                                    onClose = {
                                        currentVideo = null
                                    },
                                    onError = { err ->
                                        com.lagradost.cloudstream3.desktop.DesktopErrorReporter.report("Player Error: $err")
                                        showErrorsDialog = true
                                    },
                                )
                            }
                        } else {
                            CompositionLocalProvider(
                                androidx.compose.ui.platform.LocalDensity provides scaledDensity,
                            ) {
                                DesktopAppShell(
                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                    onBack = { rootComponent.pop() },
                                    title = title,
                                    homeUiState = (activeInstance as? RootComponent.Child.Home)?.component?.viewModel?.uiState?.collectAsState()?.value,
                                    homeActionDispatcher = { ev -> (activeInstance as? RootComponent.Child.Home)?.component?.viewModel?.onEvent(ev) },
                                    showDock = showDock,
                                    showTopBar = showTopBar,
                                    applySafePadding = applySafePadding,
                                    onOpenProfileManager = { showProfileManagerModal = true },
                                ) {
                                    Children(
                                        stack = childStack,
                                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                        animation = stackAnimation(
                                            fade(tween(160, easing = FastOutSlowInEasing)),
                                        ),
                                    ) {
                                        when (val child = it.instance) {
                                            is RootComponent.Child.Details -> {
                                                val api = child.component.api
                                                if (api != null) {
                                                    ComposeDetailsScreen(
                                                        onBack = { rootComponent.pop() },
                                                        onNavigate = { config -> rootComponent.bringToFront(config) },
                                                        viewModel = child.component.viewModel,
                                                        autoPlay = child.component.config.autoPlay,
                                                    )
                                                } else {
                                                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                                        androidx.compose.material3.Text("Plugin unloaded. Cannot load details.")
                                                    }
                                                }
                                            }

                                            is RootComponent.Child.Home -> {
                                                ComposeHomeScreen(
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Explore -> {
                                                com.lagradost.cloudstream3.desktop.explore.ui.ExploreScreen(
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.History -> {
                                                com.lagradost.cloudstream3.desktop.ui.screens.ComposeHistoryScreen(
                                                    onNavigate = { rootComponent.bringToFront(it) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Search -> {
                                                com.lagradost.cloudstream3.desktop.ui.screens.search.ComposeSearchScreen(
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Extensions -> {
                                                ComposeExtensionScreen(
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    initialTab = child.component.initialTab,
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Library -> {
                                                ComposeLibraryScreen(
                                                    onNavigate = { rootComponent.bringToFront(it) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Downloads -> {
                                                val launchPlayer = LocalVideoPlayer.current
                                                val scope = androidx.compose.runtime.rememberCoroutineScope()
                                                com.lagradost.cloudstream3.desktop.ui.screens.downloads.DownloadsScreen(
                                                    viewModel = child.component.viewModel,
                                                    onPlayOffline = { task ->
                                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                            val file = java.io.File(task.filePath)
                                                            if (file.exists()) {
                                                                val offlineLink = com.lagradost.cloudstream3.utils.newExtractorLink(
                                                                    source = "Downloaded (Offline)",
                                                                    name = task.displayTitle,
                                                                    url = file.absolutePath,
                                                                    type = if (file.name.contains(".m3u8", ignoreCase = true)) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO,
                                                                ) {
                                                                    this.referer = ""
                                                                    this.quality = task.quality
                                                                }

                                                                val allCompletedTasks = child.component.viewModel.uiState.value.completedTasks
                                                                    .filter { it.showName == task.showName }
                                                                    .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))

                                                                val siblingEpisodes = allCompletedTasks.map { createOfflineEpisode(it) }

                                                                launchPlayer(
                                                                    VideoLaunchData(
                                                                        links = listOf(offlineLink),
                                                                        initialIndex = 0,
                                                                        title = task.displayTitle,
                                                                        subtitles = emptyList(),
                                                                        startPositionMs = 0L,
                                                                        history = WatchHistory(
                                                                            parentId = "offline_media",
                                                                            showName = task.showName,
                                                                            showUrl = task.filePath,
                                                                            apiName = "Offline",
                                                                            posterUrl = task.posterUrl,
                                                                            episodeThumbnailUrl = null,
                                                                            screenshotUrl = null,
                                                                            episode = task.episode,
                                                                            season = task.season,
                                                                            episodeId = task.filePath,
                                                                            position = 0L,
                                                                            duration = 0L,
                                                                            updateTime = System.currentTimeMillis(),
                                                                            episodeName = task.cleanEpisodeTitle ?: task.episodeTitle,
                                                                        ),
                                                                        episodes = siblingEpisodes,
                                                                        enrichedBackdropUrl = task.backdropUrl,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                            is RootComponent.Child.Settings -> {
                                                com.lagradost.cloudstream3.desktop.ui.screens.settings.ComposeSettingsScreen(
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.CategoryGrid -> {
                                                val api = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(child.component.providerName)
                                                if (api != null) {
                                                    val items = com.lagradost.cloudstream3.desktop.ui.screens.CategoryGridCache.get(child.component.providerName, child.component.title) ?: emptyList()
                                                    com.lagradost.cloudstream3.desktop.ui.screens.ComposeCategoryGridScreen(onNavigate = { rootComponent.bringToFront(it) }, onBack = { rootComponent.pop() }, api, child.component.title, items)
                                                } else {
                                                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                                        androidx.compose.material3.Text("Plugin unloaded. Cannot load category.")
                                                    }
                                                }
                                            }
                                            is RootComponent.Child.Person -> {
                                                com.lagradost.cloudstream3.desktop.ui.screens.person.PersonScreen(
                                                    name = child.component.config.name,
                                                    image = child.component.config.image,
                                                    tmdbId = child.component.config.tmdbId,
                                                    onBack = { rootComponent.pop() },
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.Studio -> {
                                                com.lagradost.cloudstream3.desktop.ui.screens.studio.StudioScreen(
                                                    name = child.component.config.name,
                                                    companyId = child.component.config.companyId,
                                                    logoUrl = child.component.config.logoUrl,
                                                    originCountry = child.component.config.originCountry,
                                                    onBack = { rootComponent.pop() },
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    viewModel = child.component.viewModel,
                                                )
                                            }
                                            is RootComponent.Child.FullCast -> {
                                                val api = child.component.config.providerName?.let { com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(it) }
                                                com.lagradost.cloudstream3.desktop.ui.screens.details.FullCastScreen(
                                                    mediaTitle = child.component.config.mediaTitle,
                                                    cast = child.component.config.cast,
                                                    directors = child.component.config.directors,
                                                    writers = child.component.config.writers,
                                                    producers = child.component.config.producers,
                                                    provider = api,
                                                    onBack = { rootComponent.pop() },
                                                    onNavigate = { config -> rootComponent.bringToFront(config) },
                                                    tmdbId = child.component.config.tmdbId,
                                                    availableSeasons = child.component.config.availableSeasons,
                                                    initialSeason = child.component.config.initialSeason,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Context Menu Overlay (unblurred)
                    com.lagradost.cloudstream3.desktop.ui.components.ContextMenuOverlay()

                    // Global Toast & Notification Overlay
                    com.lagradost.cloudstream3.desktop.ui.components.GlobalToastOverlay()

                    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog(
                        show = showErrorsDialog,
                        onDismissRequest = { showErrorsDialog = false },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.padding(20.dp)) {
                            androidx.compose.material3.Text("Error Logs", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                            val errorSnapshot = com.lagradost.cloudstream3.desktop.DesktopErrorReporter.getSnapshot()
                            androidx.compose.material3.OutlinedTextField(
                                value = errorSnapshot,
                                onValueChange = {},
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth().weight(1f),
                            )

                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                            androidx.compose.foundation.layout.Row(
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                            ) {
                                androidx.compose.material3.TextButton(onClick = {
                                    com.lagradost.cloudstream3.desktop.utils.ExternalLinkHandler.copyToClipboard(errorSnapshot)
                                }) {
                                    androidx.compose.material3.Text("Copy")
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                                androidx.compose.material3.Button(onClick = {
                                    showErrorsDialog = false
                                }) {
                                    androidx.compose.material3.Text("Close")
                                }
                            }
                        }
                    }
                }
            }

            // Global Development Unit Watermark (Visible across every Compose screen)
            GlobalDevelopmentWatermark(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 10.dp)
                    .zIndex(99f),
            )
        }
    }
}
}
}

@Composable
private fun GlobalDevelopmentWatermark(modifier: Modifier = Modifier) {
    val dateStr = remember {
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .alpha(0.40f),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = "PRE-ALPHA • v${com.lagradost.cloudstream3.desktop.AppConfig.APP_VERSION} • $dateStr",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Suppress("DEPRECATION_ERROR")
private fun createOfflineEpisode(task: com.lagradost.cloudstream3.desktop.downloader.DownloadTask): com.lagradost.cloudstream3.Episode {
    return com.lagradost.cloudstream3.Episode(
        data = task.filePath,
        name = task.cleanEpisodeTitle ?: task.episodeTitle ?: "Episode ${task.episode ?: 1}",
        season = task.season,
        episode = task.episode,
        posterUrl = task.posterUrl,
    )
}
