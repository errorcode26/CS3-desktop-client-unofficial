package com.lagradost.cloudstream3.desktop.ui.navigation

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

interface RootComponent {
    val childStack: Value<ChildStack<Config, Child>>

    fun push(config: Config)
    fun pop()
    fun popTo(index: Int)
    fun replaceAll(config: Config)
    fun bringToFront(config: Config)

    sealed class Child {
        class Home(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.HomeComponent) : Child()
        data object History : Child()
        class Search(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.SearchComponent) : Child()
        data class Extensions(val initialTab: Int) : Child()
        data object Library : Child()
        data object Settings : Child()
        class Details(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.DetailsComponent) : Child()
        data class CategoryGrid(
            val providerName: String,
            val title: String,
            val items: List<com.lagradost.cloudstream3.SearchResponse>,
        ) : Child()
    }
}
