package com.lagradost.cloudstream3.desktop.ui.navigation.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ExtensionsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.DesktopHomeViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.LibraryViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.search.SearchViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel

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
) : ComponentContext by componentContext

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
) : ComponentContext by componentContext

class DetailsComponent(
    componentContext: ComponentContext,
    val config: Config.Details,
) : ComponentContext by componentContext {
    val api: MainAPI? = APIHolder.allProviders.firstOrNull {
        it.name == config.providerName && it.mainUrl.isNotBlank() && config.url.startsWith(it.mainUrl)
    } ?: APIHolder.getApiFromNameNull(config.providerName)

    val viewModel = instanceKeeper.getOrCreate(key = "Details_${config.url}") {
        if (api != null) {
            DetailsViewModel(
                provider = api,
                url = config.url,
                preloadedName = config.preloadedName,
                preloadedPoster = config.preloadedPoster,
                preloadedBg = config.preloadedBg,
            )
        } else {
            // Dummy or throw, but UI handles api == null
            DetailsViewModel(
                provider = APIHolder.allProviders.firstOrNull() ?: object : MainAPI() {
                    override var name = "NONE"
                },
                url = config.url,
            )
        }
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
    val viewModel = instanceKeeper.getOrCreate(key = "Studio_${config.name}_${config.companyId}") {
        com.lagradost.cloudstream3.desktop.ui.screens.studio.StudioViewModel().apply {
            loadStudio(config.name, config.companyId)
        }
    }
}
