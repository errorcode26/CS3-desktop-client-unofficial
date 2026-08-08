package com.lagradost.cloudstream3.desktop.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
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
            is Config.History -> RootComponent.Child.History
            is Config.Search -> RootComponent.Child.Search(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.SearchComponent(componentContext),
            )
            is Config.Extensions -> RootComponent.Child.Extensions(config.initialTab)
            is Config.Library -> RootComponent.Child.Library
            is Config.Settings -> RootComponent.Child.Settings
            is Config.Details -> RootComponent.Child.Details(
                com.lagradost.cloudstream3.desktop.ui.navigation.components.DetailsComponent(componentContext, config),
            )
            is Config.CategoryGrid -> RootComponent.Child.CategoryGrid(
                providerName = config.providerName,
                title = config.title,
                items = config.items,
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
        navigation.bringToFront(config)
    }
}
