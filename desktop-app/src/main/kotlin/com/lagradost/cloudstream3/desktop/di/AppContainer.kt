package com.lagradost.cloudstream3.desktop.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.cloudstream3.desktop.data.bookmarks.BookmarksRepositoryImpl
import com.lagradost.cloudstream3.desktop.data.category.CategoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.data.history.WatchHistoryRepositoryImpl
import com.lagradost.cloudstream3.desktop.data.plugins.PluginRepositoryImpl
import com.lagradost.cloudstream3.desktop.data.tracking.TrackingRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.bookmarks.interactor.*
import com.lagradost.cloudstream3.desktop.domain.bookmarks.repository.BookmarksRepository
import com.lagradost.cloudstream3.desktop.domain.category.interactor.*
import com.lagradost.cloudstream3.desktop.domain.category.repository.CategoryRepository
import com.lagradost.cloudstream3.desktop.domain.history.interactor.*
import com.lagradost.cloudstream3.desktop.domain.history.repository.WatchHistoryRepository
import com.lagradost.cloudstream3.desktop.domain.plugins.interactor.*
import com.lagradost.cloudstream3.desktop.domain.plugins.repository.PluginRepository
import com.lagradost.cloudstream3.desktop.data.hero.HeroRepositoryImpl
import com.lagradost.cloudstream3.desktop.data.providers.ActiveProviderRepositoryImpl
import com.lagradost.cloudstream3.desktop.domain.hero.repository.HeroRepository
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.domain.tracking.interactor.GetTrackingAccounts
import com.lagradost.cloudstream3.desktop.domain.tracking.repository.TrackingRepository

/**
 * Clean Architecture Dependency Injection Container.
 * Holds lazy singletons for domain repositories and use-case interactors.
 */
interface AppContainer {
    // Repositories
    val watchHistoryRepository: WatchHistoryRepository
    val bookmarksRepository: BookmarksRepository
    val categoryRepository: CategoryRepository
    val pluginRepository: PluginRepository
    val trackingRepository: TrackingRepository
    val activeProviderRepository: ActiveProviderRepository
    val heroRepository: HeroRepository

    // History Interactors
    val getWatchHistory: GetWatchHistory
    val getContinueWatching: GetContinueWatching
    val upsertWatchHistory: UpsertWatchHistory
    val removeWatchHistory: RemoveWatchHistory

    // Bookmarks Interactors
    val getBookmarks: GetBookmarks
    val isBookmarked: IsBookmarked
    val toggleBookmark: ToggleBookmark
    val removeBookmark: RemoveBookmark

    // Category Interactors
    val getCategories: GetCategories
    val createCategory: CreateCategory
    val deleteCategory: DeleteCategory
    val renameCategory: RenameCategory
    val reorderCategories: ReorderCategories
    val setItemCategory: SetItemCategory

    // Plugin Interactors
    val getAvailablePlugins: GetAvailablePlugins
    val installPlugin: InstallPlugin
    val uninstallPlugin: UninstallPlugin
    val addPluginRepository: AddPluginRepository
    val removePluginRepository: RemovePluginRepository
    val syncPluginRepositories: SyncPluginRepositories

    // Tracking Interactors
    val getTrackingAccounts: GetTrackingAccounts
}

class DefaultAppContainer : AppContainer {
    override val watchHistoryRepository: WatchHistoryRepository by lazy { WatchHistoryRepositoryImpl() }
    override val bookmarksRepository: BookmarksRepository by lazy { BookmarksRepositoryImpl() }
    override val categoryRepository: CategoryRepository by lazy { CategoryRepositoryImpl() }
    override val pluginRepository: PluginRepository by lazy { PluginRepositoryImpl() }
    override val trackingRepository: TrackingRepository by lazy { TrackingRepositoryImpl() }
    override val activeProviderRepository: ActiveProviderRepository by lazy { ActiveProviderRepositoryImpl() }
    override val heroRepository: HeroRepository by lazy { HeroRepositoryImpl() }

    override val getWatchHistory by lazy { GetWatchHistory(watchHistoryRepository) }
    override val getContinueWatching by lazy { GetContinueWatching(watchHistoryRepository) }
    override val upsertWatchHistory by lazy { UpsertWatchHistory(watchHistoryRepository) }
    override val removeWatchHistory by lazy { RemoveWatchHistory(watchHistoryRepository) }

    override val getBookmarks by lazy { GetBookmarks(bookmarksRepository) }
    override val isBookmarked by lazy { IsBookmarked(bookmarksRepository) }
    override val toggleBookmark by lazy { ToggleBookmark(bookmarksRepository) }
    override val removeBookmark by lazy { RemoveBookmark(bookmarksRepository) }

    override val getCategories by lazy { GetCategories(categoryRepository) }
    override val createCategory by lazy { CreateCategory(categoryRepository) }
    override val deleteCategory by lazy { DeleteCategory(categoryRepository) }
    override val renameCategory by lazy { RenameCategory(categoryRepository) }
    override val reorderCategories by lazy { ReorderCategories(categoryRepository) }
    override val setItemCategory by lazy { SetItemCategory(categoryRepository) }

    override val getAvailablePlugins by lazy { GetAvailablePlugins(pluginRepository) }
    override val installPlugin by lazy { InstallPlugin(pluginRepository) }
    override val uninstallPlugin by lazy { UninstallPlugin(pluginRepository) }
    override val addPluginRepository by lazy { AddPluginRepository(pluginRepository) }
    override val removePluginRepository by lazy { RemovePluginRepository(pluginRepository) }
    override val syncPluginRepositories by lazy { SyncPluginRepositories(pluginRepository) }

    override val getTrackingAccounts by lazy { GetTrackingAccounts(trackingRepository) }
}

/** Global thread-safe holder for non-Composable instantiation contexts (e.g. Decompose ComponentContext) */
object AppContainerHolder {
    var container: AppContainer = DefaultAppContainer()
}

/** Scoped CompositionLocal for Compose Multiplatform UI trees */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    AppContainerHolder.container
}
