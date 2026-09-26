package com.lagradost.cloudstream3.desktop.explore.viewmodel

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogClient
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogDiscoverer
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCollectionDetail
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCollectionsHub
import com.lagradost.cloudstream3.desktop.explore.client.ExploreHubClient
import com.lagradost.cloudstream3.desktop.explore.client.JikanClient
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ExploreShelf
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchEngine
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
import com.lagradost.cloudstream3.desktop.explore.settings.ExploreCatalogSettingsManager
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.history.interactor.GetWatchHistory
import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.WatchHistory
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val STOP_WORDS = setOf("the", "a", "an", "and", "of", "in", "to", "for", "with", "on", "at", "by", "from", "season", "episode")

val EXPLORE_YEAR_OPTIONS = listOf(
    "All Years",
    "2026",
    "2025",
    "2024",
    "2023",
    "2022",
    "2021",
    "2020",
    "2010s",
    "2000s",
    "1990s & Older",
)

@androidx.compose.runtime.Immutable
data class ExploreUiState(
    val isInitializing: Boolean = true,
    val allCatalogs: List<ManifestCatalogDescriptor> = emptyList(),
    val availableTypes: List<String> = emptyList(),
    val selectedType: String = "movie",
    val filteredCatalogs: List<ManifestCatalogDescriptor> = emptyList(),
    val selectedCatalog: ManifestCatalogDescriptor? = null,
    val selectedGenre: String = "All",
    val selectedYear: String = "All Years",
    val searchQuery: String = "",
    val rawItems: List<ExploreItem> = emptyList(),
    val displayItems: List<ExploreItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val selectedItemForMatch: ExploreItem? = null,
    val providerMatches: List<ProviderMatch> = emptyList(),
    val isSearchingProviders: Boolean = false,
    val watchHistoryMap: Map<String, WatchHistory> = emptyMap(),
    val isShelvesMode: Boolean = true,
    val shelves: List<ExploreShelf> = emptyList(),
    val heroItems: List<ExploreItem> = emptyList(),
    val isShelvesLoading: Boolean = false,
    val drilledCatalog: ManifestCatalogDescriptor? = null,
    val platformShelves: List<ExploreShelf> = emptyList(),
    val isPlatformShelvesLoading: Boolean = false,
    val drilledPlatform: StreamingPlatform? = null,
    val searchResults: ExploreSearchResults? = null,
    val isLiveSearching: Boolean = false,
    val drilledSearchCategory: String? = null,
    val availableAddons: List<String> = emptyList(),
    val selectedAddon: String = "All Sources",
    val selectedCollection: ExploreCollectionDetail? = null,
    val isCollectionLoading: Boolean = false,
    val isCustomizeDialogOpen: Boolean = false,
) : UiState

sealed interface ExploreUiEvent : UiEvent {
    data class SelectType(val type: String) : ExploreUiEvent
    data class SelectAddon(val addonName: String) : ExploreUiEvent
    data class SelectCatalog(val catalog: ManifestCatalogDescriptor) : ExploreUiEvent
    data class SelectGenre(val genre: String) : ExploreUiEvent
    data class SelectYear(val year: String) : ExploreUiEvent
    data class UpdateSearchQuery(val query: String) : ExploreUiEvent
    data object ClearSearchQuery : ExploreUiEvent
    data class OpenProviderPicker(val item: ExploreItem) : ExploreUiEvent
    data object CloseProviderPicker : ExploreUiEvent
    data class SelectProviderMatch(val match: ProviderMatch) : ExploreUiEvent
    data object LoadMore : ExploreUiEvent
    data object RefreshCatalogs : ExploreUiEvent
    data class ToggleViewMode(val isShelves: Boolean) : ExploreUiEvent
    data class DrillIntoCatalog(val catalog: ManifestCatalogDescriptor) : ExploreUiEvent
    data class DrillIntoPlatform(val platform: StreamingPlatform) : ExploreUiEvent
    data object ReturnToShelves : ExploreUiEvent
    data class DrillIntoSearchCategory(val category: String) : ExploreUiEvent
    data object ReturnFromSearchCategory : ExploreUiEvent
    data class OpenCollection(val collectionId: Int) : ExploreUiEvent
    data object CloseCollection : ExploreUiEvent
    data object OpenCustomizeDialog : ExploreUiEvent
    data object CloseCustomizeDialog : ExploreUiEvent
}

sealed interface ExploreUiEffect : UiEffect {
    data class OpenDetails(val providerName: String, val url: String, val title: String) : ExploreUiEffect
}

class ExploreViewModel(
    private val getWatchHistory: GetWatchHistory = AppContainerHolder.container.getWatchHistory,
) : BaseMviViewModel<ExploreUiState, ExploreUiEvent, ExploreUiEffect>(ExploreUiState()) {
    private val TAG = "ExploreViewModel"

    private var refreshJob: Job? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var loadShelvesJob: Job? = null
    private var loadPlatformShelvesJob: Job? = null
    private var providerSearchJob: Job? = null
    private val searchSemaphore = Semaphore(8)
    private val shelfFetchSemaphore = Semaphore(6)

    companion object {
        private const val MAX_CACHE_ENTRIES = 30
    }

    private val catalogItemsCache: MutableMap<String, List<ExploreItem>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<ExploreItem>>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ExploreItem>>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )

    init {
        viewModelScope.launch {
            StremioAddonManager.addons.collect {
                refreshCatalogs()
            }
        }

        viewModelScope.launch {
            getWatchHistory.subscribeAll().collect { histories ->
                val map = mutableMapOf<String, WatchHistory>()
                histories.sortedBy { it.updateTime }.forEach { hist ->
                    map[hist.parentId] = hist
                    val cleanId = hist.showUrl.substringAfterLast("/").takeIf { it.isNotBlank() }
                    if (cleanId != null) {
                        map[cleanId] = hist
                    }
                    val epId = hist.episodeId
                    if (!epId.isNullOrBlank()) {
                        val epClean = epId.substringBefore(":")
                        map[epClean] = hist
                    }
                }
                updateState { copy(watchHistoryMap = map) }
            }
        }

        viewModelScope.launch {
            ExploreCatalogSettingsManager.preferences.collect {
                val state = uiState.value
                if (state.isShelvesMode && state.drilledCatalog == null && state.drilledPlatform == null && state.searchQuery.isBlank() && state.allCatalogs.isNotEmpty()) {
                    val forType = state.allCatalogs.filter {
                        matchesType(it.type, state.selectedType) &&
                            (state.selectedAddon == "All Sources" || it.addonName.equals(state.selectedAddon, ignoreCase = true))
                    }
                    val orderedAndFiltered = ExploreCatalogSettingsManager.filterAndSort(forType)
                    loadShelves(state.selectedType, orderedAndFiltered)
                }
            }
        }
    }

    override fun handleEvent(event: ExploreUiEvent) {
        when (event) {
            is ExploreUiEvent.SelectType -> selectType(event.type)
            is ExploreUiEvent.SelectAddon -> selectAddon(event.addonName)
            is ExploreUiEvent.SelectCatalog -> selectCatalog(event.catalog)
            is ExploreUiEvent.SelectGenre -> selectGenre(event.genre)
            is ExploreUiEvent.SelectYear -> selectYear(event.year)
            is ExploreUiEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is ExploreUiEvent.ClearSearchQuery -> clearSearchQuery()
            is ExploreUiEvent.OpenProviderPicker -> openProviderPicker(event.item)
            is ExploreUiEvent.CloseProviderPicker -> closeProviderPicker()
            is ExploreUiEvent.SelectProviderMatch -> selectProviderMatch(event.match)
            is ExploreUiEvent.LoadMore -> loadMore()
            is ExploreUiEvent.RefreshCatalogs -> refreshCatalogs()
            is ExploreUiEvent.ToggleViewMode -> toggleViewMode(event.isShelves)
            is ExploreUiEvent.DrillIntoCatalog -> drillIntoCatalog(event.catalog)
            is ExploreUiEvent.DrillIntoPlatform -> drillIntoPlatform(event.platform)
            is ExploreUiEvent.ReturnToShelves -> returnToShelves()
            is ExploreUiEvent.DrillIntoSearchCategory -> updateState { copy(drilledSearchCategory = event.category) }
            is ExploreUiEvent.ReturnFromSearchCategory -> updateState { copy(drilledSearchCategory = null) }
            is ExploreUiEvent.OpenCollection -> openCollection(event.collectionId)
            is ExploreUiEvent.CloseCollection -> updateState { copy(selectedCollection = null, isCollectionLoading = false) }
            is ExploreUiEvent.OpenCustomizeDialog -> updateState { copy(isCustomizeDialogOpen = true) }
            is ExploreUiEvent.CloseCustomizeDialog -> updateState { copy(isCustomizeDialogOpen = false) }
        }
    }

    private fun selectProviderMatch(match: ProviderMatch) {
        closeProviderPicker()
        sendEffect(
            ExploreUiEffect.OpenDetails(
                providerName = match.providerName,
                url = match.searchResponse.url,
                title = match.displayTitle,
            )
        )
    }

    private fun refreshCatalogs() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val enabledAddons: List<ManagedStremioAddon> = StremioAddonManager.addons.value.filter { it.enabled }
            val discovered = mutableListOf<ManifestCatalogDescriptor>()
            val catalogDeferreds = enabledAddons.map { addon ->
                async { ExploreCatalogDiscoverer.getCatalogsForAddon(addon) }
            }
            val addonCatalogs = catalogDeferreds.awaitAll().flatten()
            discovered.addAll(addonCatalogs)

            // Native Curated Movie Feeds (TMDB Parametric Discovery)
            val movieGenres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western")
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://movie/in_theaters",
                    type = "movie",
                    id = "tmdb_in_theaters",
                    name = "In Theaters Now",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://movie/critics_picks",
                    type = "movie",
                    id = "tmdb_critics_picks",
                    name = "Critics' Picks",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://movie/hidden_gems",
                    type = "movie",
                    id = "tmdb_hidden_gems",
                    name = "Hidden Gems",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://movie/under_ninety",
                    type = "movie",
                    id = "tmdb_under_ninety",
                    name = "Quick Watches (Under 90)",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Decades",
                    addonBaseUrl = "tmdb://movie/decade_2010s",
                    type = "movie",
                    id = "tmdb_decade_2010s",
                    name = "Defining the 2010s",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Decades",
                    addonBaseUrl = "tmdb://movie/decade_90s",
                    type = "movie",
                    id = "tmdb_decade_90s",
                    name = "Essential 90s",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Decades",
                    addonBaseUrl = "tmdb://movie/decade_80s",
                    type = "movie",
                    id = "tmdb_decade_80s",
                    name = "80s Classics",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "World Cinema",
                    addonBaseUrl = "tmdb://movie/lang_jp",
                    type = "movie",
                    id = "tmdb_lang_jp",
                    name = "Japanese Cinema",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "World Cinema",
                    addonBaseUrl = "tmdb://movie/lang_kr",
                    type = "movie",
                    id = "tmdb_lang_kr",
                    name = "Korean Cinema",
                    genres = movieGenres,
                    supportsSearch = false,
                )
            )

            // Native Curated TV Series Feeds (TMDB Parametric Discovery & Networks)
            val tvGenres = listOf("Action & Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Kids", "Mystery", "News", "Reality", "Sci-Fi & Fantasy", "Soap", "Talk", "War & Politics", "Western")
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://series/trending",
                    type = "series",
                    id = "tmdb_series_trending",
                    name = "Trending Shows This Week",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Featured",
                    addonBaseUrl = "tmdb://series/airing_today",
                    type = "series",
                    id = "tmdb_series_airing_today",
                    name = "On Tonight / Airing Today",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_hbo",
                    type = "series",
                    id = "tmdb_net_hbo",
                    name = "From HBO",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_netflix",
                    type = "series",
                    id = "tmdb_net_netflix",
                    name = "Netflix Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_apple",
                    type = "series",
                    id = "tmdb_net_apple",
                    name = "Apple TV+ Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_disney",
                    type = "series",
                    id = "tmdb_net_disney",
                    name = "Disney+ Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_amazon",
                    type = "series",
                    id = "tmdb_net_amazon",
                    name = "Prime Video Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_fx",
                    type = "series",
                    id = "tmdb_net_fx",
                    name = "FX Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Networks",
                    addonBaseUrl = "tmdb://series/net_amc",
                    type = "series",
                    id = "tmdb_net_amc",
                    name = "AMC Originals",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Spotlight",
                    addonBaseUrl = "tmdb://series/prestige_drama",
                    type = "series",
                    id = "tmdb_prestige_drama",
                    name = "Prestige Drama",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Spotlight",
                    addonBaseUrl = "tmdb://series/kdrama",
                    type = "series",
                    id = "tmdb_kdrama",
                    name = "Korean Dramas (K-Drama)",
                    genres = tvGenres,
                    supportsSearch = false,
                )
            )
            val animeGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mecha", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "AniList",
                    addonBaseUrl = "anilist://trending",
                    type = "anime",
                    id = "trending",
                    name = "Trending Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "AniList",
                    addonBaseUrl = "anilist://top",
                    type = "anime",
                    id = "top_100",
                    name = "Top 100 Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://airing",
                    type = "anime",
                    id = "mal_airing",
                    name = "Airing Now",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://upcoming",
                    type = "anime",
                    id = "mal_upcoming",
                    name = "Upcoming Season",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://top_series",
                    type = "anime",
                    id = "mal_top_series",
                    name = "Top Series on MAL",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://top_movies",
                    type = "anime",
                    id = "mal_top_movies",
                    name = "Top Movies on MAL",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://popular",
                    type = "anime",
                    id = "mal_popular",
                    name = "Most Popular Anime",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://all_time",
                    type = "anime",
                    id = "mal_all_time",
                    name = "All-Time Top Rated",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://era_2020s",
                    type = "anime",
                    id = "mal_era_2020s",
                    name = "2020s Anime Hits",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://era_2010s",
                    type = "anime",
                    id = "mal_era_2010s",
                    name = "2010s Classics",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://genre_action",
                    type = "anime",
                    id = "mal_genre_action",
                    name = "Action & Adventure",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "MyAnimeList",
                    addonBaseUrl = "jikan://genre_fantasy",
                    type = "anime",
                    id = "mal_genre_fantasy",
                    name = "Fantasy & Isekai",
                    genres = animeGenres,
                    supportsSearch = false,
                )
            )

            // Native Curated Franchise & Sagas Feeds
            val collectionCategories = listOf("Sagas", "Superheroes", "Action", "Sci-Fi", "Animation", "Horror", "Crime")
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/sagas",
                    type = "collections",
                    id = "tmdb_coll_sagas",
                    name = "Epic Sagas & Universes",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/superheroes",
                    type = "collections",
                    id = "tmdb_coll_superheroes",
                    name = "Superhero Universes",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/action",
                    type = "collections",
                    id = "tmdb_coll_action",
                    name = "Action & Adrenaline Franchises",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/scifi",
                    type = "collections",
                    id = "tmdb_coll_scifi",
                    name = "Sci-Fi & Cyberpunk Universes",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/animation",
                    type = "collections",
                    id = "tmdb_coll_animation",
                    name = "Animated Sagas & Family Universes",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/horror",
                    type = "collections",
                    id = "tmdb_coll_horror",
                    name = "Horror & Dark Universes",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )
            discovered.add(
                ManifestCatalogDescriptor(
                    addonName = "Franchises",
                    addonBaseUrl = "tmdb://collection/crime",
                    type = "collections",
                    id = "tmdb_coll_crime",
                    name = "Crime, Heists & Mob Classics",
                    genres = collectionCategories,
                    supportsSearch = false,
                    posterShape = "landscape",
                )
            )

            ExploreCatalogSettingsManager.syncWithDiscovered(discovered)

            val types = discovered.map {
                val t = it.type.lowercase(Locale.US)
                if (t == "tv") "series" else t
            }.distinct().sortedBy {
                when (it) {
                    "movie" -> 0
                    "series" -> 1
                    "anime" -> 2
                    "collections" -> 3
                    else -> 4
                }
            }

            val currentType = uiState.value.selectedType
            val targetType = if (types.contains(currentType)) currentType else types.firstOrNull() ?: "movie"

            val addons = listOf("All Sources") + discovered.map { it.addonName }.filter { it.isNotBlank() }.distinct().sorted()
            val currentAddon = uiState.value.selectedAddon
            val targetAddon = if (addons.contains(currentAddon)) currentAddon else "All Sources"

            val forType = discovered.filter {
                matchesType(it.type, targetType) &&
                    (targetAddon == "All Sources" || it.addonName.equals(targetAddon, ignoreCase = true))
            }
            val nextCat = forType.firstOrNull()

            if (discovered.isEmpty()) {
                updateState {
                    copy(
                        isInitializing = false,
                        allCatalogs = emptyList(),
                        availableTypes = emptyList(),
                        availableAddons = emptyList(),
                        selectedAddon = "All Sources",
                        filteredCatalogs = emptyList(),
                        selectedCatalog = null,
                        rawItems = emptyList(),
                        displayItems = emptyList(),
                        isLoading = false,
                        shelves = emptyList(),
                        heroItems = emptyList(),
                        isShelvesLoading = false,
                    )
                }
            } else {
                updateState {
                    copy(
                        allCatalogs = discovered,
                        availableTypes = types,
                        availableAddons = addons,
                        selectedAddon = targetAddon,
                        selectedType = targetType,
                        filteredCatalogs = forType,
                        selectedCatalog = nextCat,
                        selectedGenre = "All",
                        selectedYear = "All Years",
                        searchQuery = "",
                    )
                }
                if (uiState.value.isShelvesMode && uiState.value.drilledCatalog == null) {
                    val shelvesCatalogs = ExploreCatalogSettingsManager.filterAndSort(forType)
                    loadShelves(targetType, shelvesCatalogs)
                } else {
                    loadCurrentCatalog()
                }
            }
        }
    }

    private fun selectType(type: String) {
        if (uiState.value.selectedType == type) return

        val forType = uiState.value.allCatalogs.filter {
            matchesType(it.type, type) &&
                (uiState.value.selectedAddon == "All Sources" || it.addonName.equals(uiState.value.selectedAddon, ignoreCase = true))
        }
        val nextCat = forType.firstOrNull()

        updateState {
            copy(
                selectedType = type,
                filteredCatalogs = forType,
                selectedCatalog = nextCat,
                selectedGenre = "All",
                selectedYear = "All Years",
                searchQuery = "",
                drilledCatalog = null,
                drilledPlatform = null,
                platformShelves = emptyList(),
                heroItems = emptyList(),
                selectedCollection = null,
                isCollectionLoading = false,
            )
        }

        if (uiState.value.isShelvesMode) {
            val shelvesCatalogs = ExploreCatalogSettingsManager.filterAndSort(forType)
            loadShelves(type, shelvesCatalogs)
        } else {
            loadCurrentCatalog()
        }
    }

    private fun selectAddon(addonName: String) {
        if (uiState.value.selectedAddon == addonName) return

        val forType = uiState.value.allCatalogs.filter {
            matchesType(it.type, uiState.value.selectedType) &&
                (addonName == "All Sources" || it.addonName.equals(addonName, ignoreCase = true))
        }
        val nextCat = forType.firstOrNull()

        updateState {
            copy(
                selectedAddon = addonName,
                filteredCatalogs = forType,
                selectedCatalog = nextCat,
                selectedGenre = "All",
                selectedYear = "All Years",
                searchQuery = "",
                drilledCatalog = null,
                drilledPlatform = null,
                platformShelves = emptyList(),
                heroItems = emptyList(),
            )
        }

        if (uiState.value.isShelvesMode) {
            val shelvesCatalogs = ExploreCatalogSettingsManager.filterAndSort(forType)
            loadShelves(uiState.value.selectedType, shelvesCatalogs)
        } else {
            loadCurrentCatalog()
        }
    }

    private fun selectCatalog(catalog: ManifestCatalogDescriptor) {
        if (uiState.value.selectedCatalog?.id == catalog.id && uiState.value.selectedCatalog?.addonBaseUrl == catalog.addonBaseUrl) return

        updateState {
            copy(
                selectedCatalog = catalog,
                selectedGenre = "All",
                selectedYear = "All Years",
                searchQuery = "",
            )
        }

        loadCurrentCatalog()
    }

    private fun selectGenre(genre: String) {
        if (uiState.value.selectedGenre == genre) return

        updateState { copy(selectedGenre = genre) }
        loadCurrentCatalog()
    }

    private fun selectYear(year: String) {
        if (uiState.value.selectedYear == year) return
        updateState {
            copy(
                selectedYear = year,
                displayItems = applyFilters(rawItems, searchQuery, year),
            )
        }
    }

    private var liveSearchJob: Job? = null

    private fun updateSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearSearchQuery()
            return
        }

        updateState {
            copy(
                searchQuery = query,
                displayItems = applyFilters(rawItems, query, selectedYear),
                isLiveSearching = true,
            )
        }

        liveSearchJob?.cancel()
        liveSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            try {
                val enabledAddons = StremioAddonManager.addons.value.filter { it.enabled }
                ExploreSearchEngine.searchFlow(
                    query = trimmed,
                    enabledAddons = enabledAddons,
                    limitPerCategory = 24,
                ).collect { results ->
                    updateState {
                        copy(
                            searchResults = results,
                            isLiveSearching = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Explore search failed: ${e.message}")
                updateState { copy(isLiveSearching = false) }
            }
        }
    }

    private fun clearSearchQuery() {
        liveSearchJob?.cancel()
        updateState {
            copy(
                searchQuery = "",
                displayItems = applyFilters(rawItems, "", selectedYear),
                searchResults = null,
                isLiveSearching = false,
                drilledSearchCategory = null,
            )
        }
    }

    internal fun applyFilters(items: List<ExploreItem>, query: String, year: String): List<ExploreItem> {
        val q = query.trim().lowercase(Locale.US)
        return items.filter { item ->
            val matchesQuery = if (q.isBlank()) true else {
                item.name.lowercase(Locale.US).contains(q) ||
                    item.genres.any { it.lowercase(Locale.US).contains(q) } ||
                    item.releaseYear?.contains(q) == true
            }

            val itemYearInt = item.releaseYear?.toIntOrNull() ?: 0
            val matchesYear = when (year) {
                "All Years" -> true
                "2026" -> item.releaseYear == "2026"
                "2025" -> item.releaseYear == "2025"
                "2024" -> item.releaseYear == "2024"
                "2023" -> item.releaseYear == "2023"
                "2022" -> item.releaseYear == "2022"
                "2021" -> item.releaseYear == "2021"
                "2020" -> item.releaseYear == "2020"
                "2010s" -> itemYearInt in 2010..2019
                "2000s" -> itemYearInt in 2000..2009
                "1990s & Older" -> itemYearInt in 1..1999
                else -> item.releaseYear == year
            }

            matchesQuery && matchesYear
        }
    }

    private fun loadCurrentCatalog(skip: Int = 0) {
        val cat = uiState.value.selectedCatalog ?: return
        val genreArg = if (uiState.value.selectedGenre.equals("All", ignoreCase = true)) cat.genre else uiState.value.selectedGenre
        val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_${genreArg ?: "all"}_$skip"

        // Instant display if already in memory
        if (skip == 0) {
            val cached = catalogItemsCache[cacheKey]
            if (cached != null && cached.isNotEmpty()) {
                val filtered = applyFilters(cached, uiState.value.searchQuery, uiState.value.selectedYear)
                updateState {
                    copy(
                        isInitializing = false,
                        rawItems = cached,
                        displayItems = filtered,
                        isLoading = false,
                        canLoadMore = cached.size >= 20,
                    )
                }
                return
            }
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoading = true, canLoadMore = true) }
            try {
                val fetched = fetchItemsForCatalog(
                    cat = cat,
                    genreArg = genreArg,
                    skip = skip,
                )

                val distinctFetched = fetched.distinctBy { it.id }
                if (distinctFetched.isNotEmpty()) {
                    catalogItemsCache[cacheKey] = distinctFetched
                }

                val newRaw = if (skip == 0) distinctFetched else (uiState.value.rawItems + distinctFetched).distinctBy { it.id }
                val filtered = applyFilters(newRaw, uiState.value.searchQuery, uiState.value.selectedYear)

                updateState {
                    copy(
                        isInitializing = false,
                        rawItems = newRaw,
                        displayItems = filtered,
                        isLoading = false,
                        canLoadMore = distinctFetched.size >= 20,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed loading catalog ${cat.name}: ${e.message}")
                updateState { copy(isLoading = false, isInitializing = false) }
            }
        }
    }

    private fun loadMore() {
        val state = uiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.selectedCatalog == null) return
        if (state.rawItems.isEmpty()) return

        val cat = state.selectedCatalog
        val genreArg = if (state.selectedGenre.equals("All", ignoreCase = true)) cat.genre else state.selectedGenre
        val skip = state.rawItems.size

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoadingMore = true) }
            try {
                val fetched = fetchItemsForCatalog(
                    cat = cat,
                    genreArg = genreArg,
                    skip = skip,
                )

                if (fetched.isEmpty()) {
                    updateState { copy(isLoadingMore = false, canLoadMore = false) }
                } else {
                    val distinctFetched = fetched.distinctBy { it.id }
                    val newRaw = (uiState.value.rawItems + distinctFetched).distinctBy { it.id }
                    val filtered = applyFilters(newRaw, uiState.value.searchQuery, uiState.value.selectedYear)
                    updateState {
                        copy(
                            rawItems = newRaw,
                            displayItems = filtered,
                            isLoadingMore = false,
                            canLoadMore = distinctFetched.size >= 20,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed loading more items for ${cat.name}: ${e.message}")
                updateState { copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun fetchItemsForCatalog(
        cat: ManifestCatalogDescriptor,
        genreArg: String?,
        skip: Int,
    ): List<ExploreItem> {
        return when {
            cat.addonBaseUrl.startsWith("tmdb://platform/") -> {
                val fullUrl = cat.addonBaseUrl.removePrefix("tmdb://platform/")
                val platformId = fullUrl.substringBefore("?")
                val queryParams = if (fullUrl.contains("?")) {
                    fullUrl.substringAfter("?").split("&").associate {
                        val parts = it.split("=")
                        parts[0] to (parts.getOrNull(1) ?: "")
                    }
                } else emptyMap()

                val platform = StreamingPlatform.fromIdOrNull(platformId) ?: StreamingPlatform.NETFLIX
                val page = (skip / 20) + 1

                if (platform == StreamingPlatform.CRUNCHYROLL) {
                    val sort = queryParams["sort"]?.takeIf { it.isNotBlank() } ?: "TRENDING_DESC"
                    val genre = queryParams["genre"]?.takeIf { it.isNotBlank() } ?: genreArg
                    ExploreHubClient.fetchAnilistShelfRow(sort = sort, genre = genre, page = page, perPage = 24)
                } else {
                    val rowType = queryParams["row"]
                    when (rowType) {
                        "trending" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "popularity.desc", targetType = "both", page = page)
                        "new_movies" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "primary_release_date.desc", targetType = "movie", page = page)
                        "new_series" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "first_air_date.desc", targetType = "series", page = page)
                        "top_rated" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "vote_average.desc", targetType = "both", minVoteCount = 150, page = page)
                        "action" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "popularity.desc", targetType = "both", movieGenreId = 28, tvGenreId = 10759, page = page)
                        "drama" -> ExploreHubClient.fetchPlatformShelfRow(platform, sortBy = "popularity.desc", targetType = "both", movieGenreId = 18, tvGenreId = 18, page = page)
                        else -> ExploreHubClient.fetchPlatformItems(
                            platform = platform,
                            page = page,
                            genreName = genreArg,
                            targetType = cat.type,
                        )
                    }
                }
            }
            cat.addonBaseUrl.startsWith("tmdb://movie/") -> {
                val page = (skip / 20) + 1
                val endpoint = cat.addonBaseUrl.removePrefix("tmdb://movie/")
                when (endpoint) {
                    "in_theaters" -> ExploreHubClient.fetchInTheaters(page, genreArg)
                    "critics_picks" -> ExploreHubClient.fetchCriticsPicks(page, genreArg)
                    "hidden_gems" -> ExploreHubClient.fetchHiddenGems(page, genreArg)
                    "under_ninety" -> ExploreHubClient.fetchUnderNinety(page, genreArg)
                    "decade_2010s" -> ExploreHubClient.fetchDecadeMovies(2010, 2019, 1000, 7.5, page, genreArg)
                    "decade_90s" -> ExploreHubClient.fetchDecadeMovies(1990, 1999, 800, 7.5, page, genreArg)
                    "decade_80s" -> ExploreHubClient.fetchDecadeMovies(1980, 1989, 500, 7.4, page, genreArg)
                    "lang_jp" -> ExploreHubClient.fetchForeignCinema("ja", 7.4, page, genreArg)
                    "lang_kr" -> ExploreHubClient.fetchForeignCinema("ko", 7.4, page, genreArg)
                    else -> ExploreHubClient.fetchCriticsPicks(page, genreArg)
                }
            }
            cat.addonBaseUrl.startsWith("tmdb://series/") -> {
                val page = (skip / 20) + 1
                val endpoint = cat.addonBaseUrl.removePrefix("tmdb://series/")
                when (endpoint) {
                    "trending" -> ExploreHubClient.fetchTrendingTv(page, genreArg)
                    "airing_today" -> ExploreHubClient.fetchAiringTodayTv(page, genreArg)
                    "net_hbo" -> ExploreHubClient.fetchNetworkTv(49, minVotes = 200, minRating = 7.5, page = page, genreName = genreArg)
                    "net_netflix" -> ExploreHubClient.fetchNetworkTv(213, minVotes = 200, minRating = 7.0, page = page, genreName = genreArg)
                    "net_apple" -> ExploreHubClient.fetchNetworkTv(2552, minVotes = 100, minRating = 7.0, page = page, genreName = genreArg)
                    "net_disney" -> ExploreHubClient.fetchNetworkTv(2739, minVotes = 100, minRating = 6.8, page = page, genreName = genreArg)
                    "net_amazon" -> ExploreHubClient.fetchNetworkTv(1024, minVotes = 150, minRating = 7.0, page = page, genreName = genreArg)
                    "net_fx" -> ExploreHubClient.fetchNetworkTv(88, minVotes = 150, minRating = 7.2, page = page, genreName = genreArg)
                    "net_amc" -> ExploreHubClient.fetchNetworkTv(174, minVotes = 150, minRating = 7.2, page = page, genreName = genreArg)
                    "prestige_drama" -> ExploreHubClient.fetchCuratedTvGenre(18, minVotes = 500, minRating = 7.8, page = page, genreName = genreArg)
                    "kdrama" -> ExploreHubClient.fetchForeignTv("KR", isCountry = true, minRating = 7.5, minVotes = 100, page = page, genreName = genreArg)
                    else -> ExploreHubClient.fetchTrendingTv(page, genreArg)
                }
            }
            cat.addonBaseUrl.startsWith("anilist://") -> {
                val page = (skip / 24) + 1
                val sort = if (cat.addonBaseUrl.contains("top")) "SCORE_DESC" else "TRENDING_DESC"
                ExploreHubClient.fetchAnilistShelfRow(sort = sort, genre = genreArg, page = page, perPage = 24)
            }
            cat.addonBaseUrl.startsWith("jikan://") -> {
                val page = (skip / 24) + 1
                val endpoint = cat.addonBaseUrl.removePrefix("jikan://")
                when (endpoint) {
                    "airing" -> JikanClient.fetchAiringNow(page)
                    "upcoming" -> JikanClient.fetchUpcoming(page)
                    "top_series" -> JikanClient.fetchTopAnime(page = page, type = "tv")
                    "top_movies" -> JikanClient.fetchTopAnime(page = page, type = "movie")
                    "popular" -> JikanClient.fetchTopAnime(page = page, filter = "bypopularity")
                    "all_time" -> JikanClient.fetchTopAnime(page = page)
                    "era_2020s" -> JikanClient.fetchByEra("2020-01-01", "2029-12-31", page)
                    "era_2010s" -> JikanClient.fetchByEra("2010-01-01", "2019-12-31", page)
                    "genre_action" -> JikanClient.fetchByGenre(1, page)
                    "genre_fantasy" -> JikanClient.fetchByGenre(10, page)
                    else -> JikanClient.fetchTopAnime(page = page)
                }
            }
            cat.addonBaseUrl.startsWith("tmdb://collection/") -> {
                val page = (skip / 12) + 1
                val categoryKey = cat.addonBaseUrl.removePrefix("tmdb://collection/")
                val categoryName = when (categoryKey) {
                    "sagas" -> "Sagas"
                    "superheroes" -> "Superheroes"
                    "action" -> "Action"
                    "scifi" -> "Sci-Fi"
                    "animation" -> "Animation"
                    "horror" -> "Horror"
                    "crime" -> "Crime"
                    else -> genreArg ?: "All"
                }
                ExploreCollectionsHub.fetchCategoryShelf(categoryName, page)
            }
            else -> {
                ExploreCatalogClient.fetchCatalogItems(
                    baseUrl = cat.addonBaseUrl,
                    type = cat.type,
                    catalogId = cat.id,
                    genre = genreArg,
                    skip = skip,
                )
            }
        }
    }

    private fun loadShelves(type: String, catalogs: List<ManifestCatalogDescriptor>) {
        loadShelvesJob?.cancel()
        if (catalogs.isEmpty()) {
            updateState {
                copy(
                    isInitializing = false,
                    isShelvesLoading = false,
                    shelves = emptyList(),
                    heroItems = emptyList(),
                )
            }
            return
        }

        // Initialize shelves with cached items immediately if present
        val initialShelves = catalogs.map { cat ->
            val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_${cat.genre ?: "all"}_0"
            val cached = catalogItemsCache[cacheKey]
            ExploreShelf(
                catalog = cat,
                items = cached ?: emptyList(),
                isLoading = cached == null,
            )
        }

        val cachedHeroes = initialShelves.firstOrNull { it.items.isNotEmpty() }?.items?.take(5) ?: emptyList()

        updateState {
            copy(
                isInitializing = false,
                isShelvesLoading = initialShelves.any { it.isLoading },
                shelves = initialShelves,
                heroItems = cachedHeroes,
            )
        }

        loadShelvesJob = viewModelScope.launch(Dispatchers.IO) {
            val updatedShelves = initialShelves.toMutableList()

            val jobs = catalogs.mapIndexed { index, cat ->
                launch {
                    val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_${cat.genre ?: "all"}_0"
                    val cached = catalogItemsCache[cacheKey]
                    if (cached != null && cached.isNotEmpty()) {
                        return@launch
                    }

                    shelfFetchSemaphore.withPermit {
                        try {
                            val items = fetchItemsForCatalog(
                                cat = cat,
                                genreArg = cat.genre,
                                skip = 0,
                            ).distinctBy { it.id }

                            if (items.isNotEmpty()) {
                                catalogItemsCache[cacheKey] = items
                            }

                            synchronized(updatedShelves) {
                                updatedShelves[index] = ExploreShelf(
                                    catalog = cat,
                                    items = items,
                                    isLoading = false,
                                )
                            }
                            updateState {
                                val currentShelves = updatedShelves.toList()
                                val heroes = if (heroItems.isEmpty()) {
                                    currentShelves.firstOrNull { it.items.isNotEmpty() }?.items?.take(5) ?: emptyList()
                                } else heroItems
                                copy(
                                    shelves = currentShelves,
                                    heroItems = heroes,
                                    isShelvesLoading = currentShelves.any { it.isLoading },
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed loading shelf for ${cat.name}: ${e.message}")
                            synchronized(updatedShelves) {
                                updatedShelves[index] = ExploreShelf(
                                    catalog = cat,
                                    items = emptyList(),
                                    isLoading = false,
                                    error = e.message,
                                )
                            }
                            updateState {
                                val currentShelves = updatedShelves.toList()
                                copy(
                                    shelves = currentShelves,
                                    isShelvesLoading = currentShelves.any { it.isLoading },
                                )
                            }
                        }
                    }
                }
            }

            jobs.joinAll()
            updateState {
                val finalShelves = updatedShelves.toList()
                val heroes = if (heroItems.isEmpty()) {
                    finalShelves.firstOrNull { it.items.isNotEmpty() }?.items?.take(5) ?: emptyList()
                } else heroItems
                copy(
                    shelves = finalShelves,
                    heroItems = heroes,
                    isShelvesLoading = false,
                )
            }
        }
    }

    private fun drillIntoCatalog(catalog: ManifestCatalogDescriptor) {
        updateState {
            copy(
                isShelvesMode = false,
                drilledPlatform = null,
                drilledCatalog = catalog,
                selectedCatalog = catalog,
                selectedGenre = catalog.genre ?: "All",
                selectedYear = "All Years",
                searchQuery = "",
            )
        }
        loadCurrentCatalog(skip = 0)
    }

    private fun drillIntoPlatform(platform: StreamingPlatform) {
        val platformCatalog = ManifestCatalogDescriptor(
            addonName = platform.displayName,
            addonBaseUrl = "tmdb://platform/${platform.id}",
            type = if (platform == StreamingPlatform.CRUNCHYROLL) "anime" else uiState.value.selectedType,
            id = platform.id,
            name = "${platform.displayName} Hub",
            genres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller"),
            supportsSearch = false,
        )
        updateState {
            copy(
                drilledPlatform = platform,
                drilledCatalog = platformCatalog,
                selectedCatalog = platformCatalog,
                selectedGenre = "All",
                selectedYear = "All Years",
                searchQuery = "",
            )
        }
        loadPlatformShelves(platform)
    }

    private fun loadPlatformShelves(platform: StreamingPlatform) {
        loadPlatformShelvesJob?.cancel()

        val descriptors: List<ManifestCatalogDescriptor> = if (platform == StreamingPlatform.CRUNCHYROLL) {
            listOf(
                ManifestCatalogDescriptor(
                    addonName = "Crunchyroll",
                    addonBaseUrl = "tmdb://platform/crunchyroll?sort=TRENDING_DESC",
                    type = "anime",
                    id = "cr_trending",
                    name = "Trending on Crunchyroll",
                ),
                ManifestCatalogDescriptor(
                    addonName = "Crunchyroll",
                    addonBaseUrl = "tmdb://platform/crunchyroll?sort=SCORE_DESC",
                    type = "anime",
                    id = "cr_top_rated",
                    name = "Top Rated Anime",
                ),
                ManifestCatalogDescriptor(
                    addonName = "Crunchyroll",
                    addonBaseUrl = "tmdb://platform/crunchyroll?sort=POPULARITY_DESC",
                    type = "anime",
                    id = "cr_popular",
                    name = "Most Popular Anime",
                ),
                ManifestCatalogDescriptor(
                    addonName = "Crunchyroll",
                    addonBaseUrl = "tmdb://platform/crunchyroll?sort=POPULARITY_DESC&genre=Action",
                    type = "anime",
                    id = "cr_action",
                    name = "Action & Adventure",
                ),
                ManifestCatalogDescriptor(
                    addonName = "Crunchyroll",
                    addonBaseUrl = "tmdb://platform/crunchyroll?sort=POPULARITY_DESC&genre=Fantasy",
                    type = "anime",
                    id = "cr_fantasy",
                    name = "Fantasy & Supernatural",
                ),
            )
        } else {
            listOf(
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=trending",
                    type = "movie",
                    id = "${platform.id}_trending",
                    name = "Trending on ${platform.displayName}",
                ),
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=new_movies",
                    type = "movie",
                    id = "${platform.id}_new_movies",
                    name = "New Movies",
                ),
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=new_series",
                    type = "series",
                    id = "${platform.id}_new_series",
                    name = "Popular Series",
                ),
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=top_rated",
                    type = "movie",
                    id = "${platform.id}_top_rated",
                    name = "Top Rated & Acclaimed",
                ),
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=action",
                    type = "movie",
                    id = "${platform.id}_action",
                    name = "Action & Adrenaline",
                ),
                ManifestCatalogDescriptor(
                    addonName = platform.displayName,
                    addonBaseUrl = "tmdb://platform/${platform.id}?row=drama",
                    type = "movie",
                    id = "${platform.id}_drama",
                    name = "Drama & Emotion",
                ),
            )
        }

        val initialShelves = descriptors.map { cat ->
            val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_all_0"
            val cached = catalogItemsCache[cacheKey]
            ExploreShelf(
                catalog = cat,
                items = cached ?: emptyList(),
                isLoading = cached == null,
            )
        }

        updateState {
            copy(
                isPlatformShelvesLoading = initialShelves.any { it.isLoading },
                platformShelves = initialShelves,
            )
        }

        loadPlatformShelvesJob = viewModelScope.launch(Dispatchers.IO) {
            val updatedShelves = initialShelves.toMutableList()

            val jobs = descriptors.mapIndexed { index, cat ->
                launch {
                    val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_all_0"
                    val cached = catalogItemsCache[cacheKey]
                    if (cached != null && cached.isNotEmpty()) {
                        return@launch
                    }

                    shelfFetchSemaphore.withPermit {
                        try {
                            val items = fetchItemsForCatalog(
                                cat = cat,
                                genreArg = null,
                                skip = 0,
                            ).distinctBy { it.id }

                            if (items.isNotEmpty()) {
                                catalogItemsCache[cacheKey] = items
                            }

                            synchronized(updatedShelves) {
                                updatedShelves[index] = ExploreShelf(
                                    catalog = cat,
                                    items = items,
                                    isLoading = false,
                                )
                            }
                            updateState {
                                val currentShelves = updatedShelves.toList()
                                copy(
                                    platformShelves = currentShelves,
                                    isPlatformShelvesLoading = currentShelves.any { it.isLoading },
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed loading platform shelf for ${cat.name}: ${e.message}")
                            synchronized(updatedShelves) {
                                updatedShelves[index] = ExploreShelf(
                                    catalog = cat,
                                    items = emptyList(),
                                    isLoading = false,
                                    error = e.message,
                                )
                            }
                            updateState {
                                val currentShelves = updatedShelves.toList()
                                copy(
                                    platformShelves = currentShelves,
                                    isPlatformShelvesLoading = currentShelves.any { it.isLoading },
                                )
                            }
                        }
                    }
                }
            }

            jobs.joinAll()
            updateState {
                copy(
                    platformShelves = updatedShelves.toList(),
                    isPlatformShelvesLoading = false,
                )
            }
        }
    }

    private var collectionLoadJob: Job? = null

    private fun openCollection(collectionId: Int) {
        collectionLoadJob?.cancel()
        collectionLoadJob = viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isCollectionLoading = true) }
            val detail = ExploreCollectionsHub.fetchCollectionDetails(collectionId)
            updateState {
                copy(
                    selectedCollection = detail,
                    isCollectionLoading = false,
                )
            }
        }
    }

    private fun returnToShelves() {
        if (uiState.value.selectedCollection != null) {
            updateState {
                copy(
                    selectedCollection = null,
                    isCollectionLoading = false,
                )
            }
            return
        }
        val currentDrilledCat = uiState.value.drilledCatalog
        if (uiState.value.drilledPlatform != null) {
            updateState {
                copy(
                    drilledPlatform = null,
                    drilledCatalog = null,
                    platformShelves = emptyList(),
                    isPlatformShelvesLoading = false,
                    isShelvesMode = true,
                    searchQuery = "",
                )
            }
            if (uiState.value.shelves.isEmpty()) {
                loadShelves(uiState.value.selectedType, uiState.value.filteredCatalogs)
            }
        } else if (currentDrilledCat?.addonBaseUrl?.startsWith("tmdb://platform/") == true) {
            val platformId = currentDrilledCat.addonBaseUrl.removePrefix("tmdb://platform/").substringBefore("?")
            val platform = StreamingPlatform.fromIdOrNull(platformId)
            if (platform != null) {
                drillIntoPlatform(platform)
            } else {
                updateState {
                    copy(
                        isShelvesMode = true,
                        drilledCatalog = null,
                        drilledPlatform = null,
                        searchQuery = "",
                    )
                }
                if (uiState.value.shelves.isEmpty()) {
                    loadShelves(uiState.value.selectedType, uiState.value.filteredCatalogs)
                }
            }
        } else {
            updateState {
                copy(
                    isShelvesMode = true,
                    drilledCatalog = null,
                    drilledPlatform = null,
                    searchQuery = "",
                )
            }
            if (uiState.value.shelves.isEmpty()) {
                loadShelves(uiState.value.selectedType, uiState.value.filteredCatalogs)
            }
        }
    }

    private fun toggleViewMode(isShelves: Boolean) {
        if (uiState.value.isShelvesMode == isShelves) return
        updateState { copy(isShelvesMode = isShelves) }
        if (isShelves) {
            if (uiState.value.shelves.isEmpty()) {
                loadShelves(uiState.value.selectedType, uiState.value.filteredCatalogs)
            }
        } else {
            if (uiState.value.rawItems.isEmpty()) {
                loadCurrentCatalog(skip = 0)
            }
        }
    }

    internal fun isTitleRelevant(targetTitle: String, candidateTitle: String): Boolean {
        val targetNorm = NON_ALPHANUMERIC_REGEX.replace(
            CardTitleSanitizer.sanitize(targetTitle).displayTitle.lowercase(Locale.US),
            " "
        ).trim()
        val candidateNorm = NON_ALPHANUMERIC_REGEX.replace(
            CardTitleSanitizer.sanitize(candidateTitle).displayTitle.lowercase(Locale.US),
            " "
        ).trim()

        if (targetNorm.isBlank() || candidateNorm.isBlank()) return false
        if (targetNorm == candidateNorm) return true
        if (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm)) return true

        val targetWords = targetNorm.split(WHITESPACE_REGEX).filter { it.length > 1 && it !in STOP_WORDS }

        if (targetWords.isNotEmpty() && targetWords.all { candidateNorm.contains(it) }) {
            return true
        }

        return false
    }

    private fun openProviderPicker(item: ExploreItem) {
        updateState {
            copy(
                selectedItemForMatch = item,
                providerMatches = emptyList(),
                isSearchingProviders = true,
            )
        }

        providerSearchJob?.cancel()
        providerSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Asynchronously enrich metadata (clear logo, backdrop, etc.)
                launch {
                    try {
                        val meta = com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient.getMeta(item.id, item.type)
                        if (meta != null) {
                            val current = uiState.value.selectedItemForMatch ?: item
                            val enriched = current.copy(
                                logoUrl = meta.logo?.takeIf { it.isNotBlank() } ?: current.logoUrl,
                                backgroundUrl = (meta.background?.replace("t/p/original//", "t/p/original/"))?.takeIf { it.isNotBlank() } ?: current.backgroundUrl,
                                posterUrl = meta.poster?.takeIf { it.isNotBlank() } ?: current.posterUrl,
                                description = meta.description?.takeIf { it.isNotBlank() } ?: current.description,
                                rating = meta.imdbRating?.toDoubleOrNull() ?: current.rating,
                                releaseYear = meta.releaseInfo?.takeIf { it.isNotBlank() } ?: current.releaseYear,
                                genres = if (meta.genres.isNullOrEmpty()) current.genres else meta.genres,
                            )
                            updateState { copy(selectedItemForMatch = enriched) }
                        }
                    } catch (_: Exception) { }
                }

                // Uses the single-source-of-truth real content providers
                val activeProviders: List<MainAPI> = com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.allRealProviders.value
                    .ifEmpty { APIHolder.allProviders.filter { com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.isRealContentProvider(it) } }
                if (activeProviders.isEmpty()) {
                    updateState { copy(isSearchingProviders = false) }
                    return@launch
                }

                val cleanTitle = CardTitleSanitizer.sanitize(item.name).displayTitle
                val searchTitle = cleanTitle.ifBlank { item.name }
                val aggregatedMatches = CopyOnWriteArrayList<ProviderMatch>()

                val jobs = activeProviders.map { provider ->
                    launch {
                        searchSemaphore.withPermit {
                            try {
                                val res = SafePluginInvoker.invokeOrNull(
                                    tag = "Explore:Search:${provider.name}",
                                    timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                                ) {
                                    provider.search(searchTitle, 1)
                                }

                                val searchItems = res?.items
                                if (!searchItems.isNullOrEmpty()) {
                                    val validMatches = mutableListOf<ProviderMatch>()
                                    for (searchRes in searchItems) {
                                        // Strictly filter for title relevance to discard random search noise
                                        if (isTitleRelevant(searchTitle, searchRes.name)) {
                                            val meta = CardTitleSanitizer.sanitize(searchRes.name)
                                            validMatches.add(
                                                ProviderMatch(
                                                    providerName = provider.name,
                                                    searchResponse = searchRes,
                                                    displayTitle = meta.displayTitle,
                                                    qualityText = meta.qualityText,
                                                    hasSub = meta.hasSub,
                                                    hasDub = meta.hasDub,
                                                )
                                            )
                                        }
                                    }
                                    if (validMatches.isNotEmpty()) {
                                        aggregatedMatches.addAll(validMatches)
                                        updateState { copy(providerMatches = aggregatedMatches.toList()) }
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignored per provider failure
                            }
                        }
                    }
                }

                jobs.joinAll()
                updateState {
                    copy(
                        providerMatches = aggregatedMatches.toList(),
                        isSearchingProviders = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Provider resolution failed: ${e.message}")
                updateState { copy(isSearchingProviders = false) }
            }
        }
    }

    private fun closeProviderPicker() {
        providerSearchJob?.cancel()
        updateState {
            copy(
                selectedItemForMatch = null,
                providerMatches = emptyList(),
                isSearchingProviders = false,
            )
        }
    }

    private fun matchesType(catalogType: String, targetType: String): Boolean {
        val normCat = if (catalogType.equals("tv", ignoreCase = true)) "series" else catalogType.lowercase(Locale.US)
        val normTarget = if (targetType.equals("tv", ignoreCase = true)) "series" else targetType.lowercase(Locale.US)
        return normCat == normTarget
    }

    fun formatTypeTitle(type: String): String {
        return when (type.lowercase(Locale.US)) {
            "movie" -> "Movies"
            "series", "tv" -> "TV Shows"
            "anime" -> "Anime"
            "collections", "collection" -> "Collections"
            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
}
