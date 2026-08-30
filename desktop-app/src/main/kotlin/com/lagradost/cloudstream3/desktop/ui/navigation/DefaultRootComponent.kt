package com.lagradost.cloudstream3.desktop.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    // Currently we don't serialize state, so we just use plain class name as serializer (which is allowed if not using StateKeeper serialization)
    // If we wanted to serialize, we would provide a kotlinx.serialization.KSerializer for Config,
    // but on desktop process death restoration isn't strictly necessary.
    override val childStack: Value<ChildStack<Config, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = null,
            initialConfiguration = Config.Home, // Initial screen
            handleBackButton = true, // Pops the stack on back button press
            childFactory = ::createChild,
        )

    private fun createChild(config: Config, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            is Config.Home -> RootComponent.Child.Home(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.HomeComponent(componentContext),
            )
            is Config.Explore -> RootComponent.Child.Explore(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.ExploreComponent(componentContext),
            )
            is Config.History -> RootComponent.Child.History(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.HistoryComponent(componentContext),
            )
            is Config.Search -> RootComponent.Child.Search(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.SearchComponent(componentContext),
            )
            is Config.Extensions -> RootComponent.Child.Extensions(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.ExtensionsComponent(componentContext, config.initialTab),
            )
            is Config.Library -> RootComponent.Child.Library(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.LibraryComponent(componentContext),
            )
            is Config.Downloads -> RootComponent.Child.Downloads(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.DownloadsComponent(componentContext),
            )
            is Config.Settings -> RootComponent.Child.Settings(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.SettingsComponent(componentContext),
            )
            is Config.Details -> RootComponent.Child.Details(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.DetailsComponent(componentContext, config),
            )
            is Config.CategoryGrid -> RootComponent.Child.CategoryGrid(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.CategoryGridComponent(
                    componentContext = componentContext,
                    providerName = config.providerName,
                    title = config.title,
                ),
            )
            is Config.Person -> RootComponent.Child.Person(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.PersonComponent(
                    componentContext = componentContext,
                    config = config,
                ),
            )
            is Config.Studio -> RootComponent.Child.Studio(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.StudioComponent(
                    componentContext = componentContext,
                    config = config,
                ),
            )
        }

    override fun push(config: Config) {
        navigation.push(config)
    }

    override fun pop() {
        navigation.pop()
    }

    override fun popTo(index: Int) {
        navigation.popTo(index)
    }

    override fun replaceAll(config: Config) {
        navigation.replaceAll(config)
    }

    override fun bringToFront(config: Config) {
        when (config) {
            is Config.Home -> {
                navigation.replaceAll(Config.Home)
            }
            is Config.Explore,
            is Config.Search,
            is Config.Library,
            is Config.Downloads,
            is Config.History,
            is Config.Extensions,
            is Config.Settings,
            -> {
                // Top-level Dock destinations are anchored directly to Home.
                // Pressing Back on any top-level tab cleanly returns to Home!
                navigation.navigate { listOf(Config.Home, config) }
            }
            else -> {
                navigation.bringToFront(config)
            }
        }
    }
}
