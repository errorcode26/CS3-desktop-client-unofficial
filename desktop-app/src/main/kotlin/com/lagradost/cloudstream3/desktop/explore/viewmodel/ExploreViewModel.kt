package com.lagradost.cloudstream3.desktop.explore.viewmodel

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogClient
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogDiscoverer
import com.lagradost.cloudstream3.desktop.explore.client.ExploreHubClient
import com.lagradost.cloudstream3.desktop.explore.client.JikanClient
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ExploreShelf
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchEngine
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
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
    val availableAddons: List<String> = emptyList(),
    val selectedAddon: String = "All Sources",
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

            for (addon in enabledAddons) {
                val cats = ExploreCatalogDiscoverer.getCatalogsForAddon(addon)
                discovered.addAll(cats)
            }

            // Native Curated Anime Catalog Feeds (AniList + Jikan/MAL)
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

            val types = discovered.map {
                val t = it.type.lowercase(Locale.US)
                if (t == "tv") "series" else t
            }.distinct().sortedBy {
                when (it) {
                    "movie" -> 0
                    "series" -> 1
                    "anime" -> 2
                    else -> 3
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
                    loadShelves(targetType, forType)
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
            )
        }

        if (uiState.value.isShelvesMode) {
            loadShelves(type, forType)
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
            loadShelves(uiState.value.selectedType, forType)
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
        updateState {
            copy(
                searchQuery = query,
                displayItems = applyFilters(rawItems, query, selectedYear),
                isLiveSearching = trimmed.length >= 2,
                searchResults = if (trimmed.length < 2) null else searchResults,
            )
        }

        liveSearchJob?.cancel()
        if (trimmed.length < 2) {
            updateState { copy(isLiveSearching = false, searchResults = null) }
            return
        }

        liveSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300L)
            try {
                val enabledAddons = StremioAddonManager.addons.value.filter { it.enabled }
                val results = ExploreSearchEngine.search(
                    query = trimmed,
                    enabledAddons = enabledAddons,
                    limitPerCategory = 12,
                )
                updateState {
                    copy(
                        searchResults = results,
                        isLiveSearching = false,
                    )
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
        val genreArg = if (uiState.value.selectedGenre.equals("All", ignoreCase = true)) null else uiState.value.selectedGenre
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
        val genreArg = if (state.selectedGenre.equals("All", ignoreCase = true)) null else state.selectedGenre
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
            val cacheKey = "${cat.addonBaseUrl}_${cat.type}_${cat.id}_all_0"
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
                selectedGenre = "All",
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

    private fun returnToShelves() {
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
            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
}
