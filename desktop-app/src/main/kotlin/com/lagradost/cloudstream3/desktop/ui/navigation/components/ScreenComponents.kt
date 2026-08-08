package com.lagradost.cloudstream3.desktop.ui.navigation.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.details.DetailsViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.home.DesktopHomeViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.search.SearchViewModel

class HomeComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext {
    val viewModel = instanceKeeper.getOrCreate { DesktopHomeViewModel() }
}

class SearchComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext {
    val viewModel = instanceKeeper.getOrCreate { SearchViewModel() }
}

class DetailsComponent(
    componentContext: ComponentContext,
    val config: Config.Details,
) : ComponentContext by componentContext {
    val api: MainAPI? = APIHolder.getApiFromNameNull(config.providerName)

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
