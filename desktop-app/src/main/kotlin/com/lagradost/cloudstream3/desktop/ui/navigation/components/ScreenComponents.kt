package com.lagradost.cloudstream3.desktop.ui.navigation.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.DesktopHomeViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.LibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.search.SearchViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "HomeViewModel") { DesktopHomeViewModel() }
}

class SearchComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "SearchViewModel") { SearchViewModel() }
}

class ExploreComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "ExploreViewModel") { com.lagradost.cloudstream3.desktop.explore.viewmodel.ExploreViewModel() }
}

class HistoryComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "HistoryViewModel") {
        com.lagradost.cloudstream3.desktop.ui.screens.history.HistoryViewModel()
    }
}

class ExtensionsComponent(
    componentContext: ComponentContext,
    val initialTab: Int,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "ExtensionsViewModel") { ExtensionsViewModel() }
}

class LibraryComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "LibraryViewModel") { LibraryViewModel() }
}

class DownloadsComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "DownloadsViewModel") { com.lagradost.cloudstream3.desktop.ui.screens.downloads.DownloadsViewModel() }
}

class SettingsComponent(
    componentContext: ComponentContext,
    rootInstanceKeeper: InstanceKeeper = componentContext.instanceKeeper,
) : ComponentContext by componentContext {
    val viewModel = rootInstanceKeeper.getOrCreate(key = "SettingsViewModel") { PluginSettingsViewModel() }
}

class CategoryGridComponent(
    componentContext: ComponentContext,
    val providerName: String,
    val title: String,
) : ComponentContext by componentContext {
    private fun findMountedApi(): MainAPI? = APIHolder.getApiFromNameNull(providerName)

    private val initialApi = findMountedApi()
    val apiState = MutableStateFlow<MainAPI?>(initialApi)
    val isMountingState = MutableStateFlow<Boolean>(initialApi == null)
    val mountFailedState = MutableStateFlow<Boolean>(false)

    val api: MainAPI? get() = apiState.value

    init {
        if (initialApi == null) {
            com.lagradost.cloudstream3.desktop.utils.appScope.launch(Dispatchers.IO) {
                val loaded = com.lagradost.cloudstream3.desktop.init.loadPluginForProvider(providerName)
                val resolved = findMountedApi()
                if (loaded && resolved != null) {
                    apiState.value = resolved
                    isMountingState.value = false
                } else if (resolved != null) {
                    apiState.value = resolved
                    isMountingState.value = false
                } else {
                    mountFailedState.value = true
                    isMountingState.value = false
                }
            }
        }
    }
}

class DetailsComponent(
    componentContext: ComponentContext,
    val config: Config.Details,
) : ComponentContext by componentContext {

    private fun findMountedApi(): MainAPI? {
        return APIHolder.allProviders.firstOrNull {
            it.name == config.providerName && it.mainUrl.isNotBlank() && config.url.startsWith(it.mainUrl)
        } ?: APIHolder.getApiFromNameNull(config.providerName)
    }

    private val initialApi = findMountedApi()
    val apiState = MutableStateFlow<MainAPI?>(initialApi)
    val isMountingState = MutableStateFlow<Boolean>(initialApi == null)
    val mountFailedState = MutableStateFlow<Boolean>(false)

    val api: MainAPI? get() = apiState.value

    init {
        if (initialApi == null) {
            com.lagradost.cloudstream3.desktop.utils.appScope.launch(Dispatchers.IO) {
                val loaded = com.lagradost.cloudstream3.desktop.init.loadPluginForProvider(config.providerName)
                val resolved = findMountedApi()
                if (loaded && resolved != null) {
                    apiState.value = resolved
                    isMountingState.value = false
                } else if (resolved != null) {
                    apiState.value = resolved
                    isMountingState.value = false
                } else {
                    mountFailedState.value = true
                    isMountingState.value = false
                }
            }
        }
    }

    fun getOrCreateViewModel(resolvedApi: MainAPI): DetailsViewModel {
        return instanceKeeper.getOrCreate(key = "Details_${config.url}") {
            DetailsViewModel(
                provider = resolvedApi,
                url = config.url,
                preloadedName = config.preloadedName,
                preloadedPoster = config.preloadedPoster,
                preloadedBg = config.preloadedBg,
                initialSeason = config.targetSeason,
                targetEpisodeId = config.targetEpisodeId,
            )
        }.apply {
            if (config.targetSeason != null) {
                onEvent(DetailsUiEvent.OnSelectSeason(config.targetSeason))
            }
        }
    }

    val viewModel: DetailsViewModel
        get() {
            val current = apiState.value ?: (APIHolder.allProviders.firstOrNull() ?: object : MainAPI() {
                override var name = "NONE"
            })
            return getOrCreateViewModel(current)
        }
}

class PersonComponent(
    componentContext: ComponentContext,
    val config: Config.Person,
) : ComponentContext by componentContext {
    val viewModel = instanceKeeper.getOrCreate(key = "Person_${config.name}_${config.tmdbId}") {
        com.lagradost.cloudstream3.desktop.ui.screens.person.PersonViewModel().apply {
            loadPerson(config.name, config.tmdbId)
        }
    }
}

class StudioComponent(
    componentContext: ComponentContext,
    val config: Config.Studio,
) : ComponentContext by componentContext {
    val viewModel = instanceKeeper.getOrCreate(key = "Studio_${config.name}_${config.companyId}_${config.isNetwork}") {
        com.lagradost.cloudstream3.desktop.ui.screens.studio.StudioViewModel().apply {
            loadStudio(config.name, config.companyId, config.isNetwork)
        }
    }
}

class FullCastComponent(
    componentContext: ComponentContext,
    val config: Config.FullCast,
) : ComponentContext by componentContext {
    val viewModel = instanceKeeper.getOrCreate(key = "FullCast_${config.mediaTitle}_${config.tmdbId}") {
        com.lagradost.cloudstream3.desktop.ui.screens.details.FullCastViewModel(config)
    }
}
