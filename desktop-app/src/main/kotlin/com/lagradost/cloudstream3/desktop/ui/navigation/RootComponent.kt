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
        class Explore(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.ExploreComponent) : Child()
        class History(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.HistoryComponent) : Child()
        class Search(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.SearchComponent) : Child()
        class Extensions(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.ExtensionsComponent) : Child()
        class Library(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.LibraryComponent) : Child()
        class Settings(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.SettingsComponent) : Child()
        class Details(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.DetailsComponent) : Child()
        class CategoryGrid(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.CategoryGridComponent) : Child()
        class Person(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.PersonComponent) : Child()
        class Studio(val component: com.lagradost.cloudstream3.desktop.ui.navigation.components.StudioComponent) : Child()
    }
}
