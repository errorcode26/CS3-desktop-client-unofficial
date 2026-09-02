package com.lagradost.cloudstream3.desktop.explore.viewmodel

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogClient
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogDiscoverer
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.home.isRealProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

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
    val selectedItemForMatch: ExploreItem? = null,
    val providerMatches: List<ProviderMatch> = emptyList(),
    val stremioStreamMatches: List<ExtractorLink> = emptyList(),
    val isSearchingProviders: Boolean = false,
) : UiState

sealed interface ExploreUiEvent : UiEvent {
    data class SelectType(val type: String) : ExploreUiEvent
    data class SelectCatalog(val catalog: ManifestCatalogDescriptor) : ExploreUiEvent
    data class SelectGenre(val genre: String) : ExploreUiEvent
    data class SelectYear(val year: String) : ExploreUiEvent
    data class UpdateSearchQuery(val query: String) : ExploreUiEvent
    data object ClearSearchQuery : ExploreUiEvent
    data class OpenProviderPicker(val item: ExploreItem) : ExploreUiEvent
    data object CloseProviderPicker : ExploreUiEvent
    data object RefreshCatalogs : ExploreUiEvent
}

sealed interface ExploreUiEffect : UiEffect {
    data class OpenDetails(val providerName: String, val url: String, val title: String) : ExploreUiEffect
}

class ExploreViewModel : BaseMviViewModel<ExploreUiState, ExploreUiEvent, ExploreUiEffect>(ExploreUiState()) {
    private val TAG = "ExploreViewModel"

    private var loadJob: Job? = null
    private var providerSearchJob: Job? = null
    private val catalogItemsCache = java.util.concurrent.ConcurrentHashMap<String, List<ExploreItem>>()

    init {
        viewModelScope.launch {
            StremioAddonManager.addons.collect {
                refreshCatalogs()
            }
        }
    }

    override fun handleEvent(event: ExploreUiEvent) {
        when (event) {
            is ExploreUiEvent.SelectType -> selectType(event.type)
            is ExploreUiEvent.SelectCatalog -> selectCatalog(event.catalog)
            is ExploreUiEvent.SelectGenre -> selectGenre(event.genre)
            is ExploreUiEvent.SelectYear -> selectYear(event.year)
            is ExploreUiEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is ExploreUiEvent.ClearSearchQuery -> updateSearchQuery("")
            is ExploreUiEvent.OpenProviderPicker -> openProviderPicker(event.item)
            is ExploreUiEvent.CloseProviderPicker -> closeProviderPicker()
            is ExploreUiEvent.RefreshCatalogs -> refreshCatalogs()
        }
    }

    private fun refreshCatalogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabledAddons: List<ManagedStremioAddon> = StremioAddonManager.addons.value.filter { it.enabled }
            val discovered = mutableListOf<ManifestCatalogDescriptor>()

            for (addon in enabledAddons) {
                val cats = ExploreCatalogDiscoverer.getCatalogsForAddon(addon)
                discovered.addAll(cats)
            }

            val types = discovered.map { it.type.lowercase() }.distinct().sortedBy {
                when (it) {
                    "movie" -> 0
                    "series" -> 1
                    "anime" -> 2
                    else -> 3
                }
            }

            val currentType = uiState.value.selectedType
            val targetType = if (types.contains(currentType)) currentType else types.firstOrNull() ?: "movie"

            val forType = discovered.filter { it.type.equals(targetType, ignoreCase = true) }
            val nextCat = forType.firstOrNull()

            if (discovered.isEmpty()) {
                updateState {
                    copy(
                        isInitializing = false,
                        allCatalogs = emptyList(),
                        availableTypes = emptyList(),
                        filteredCatalogs = emptyList(),
                        selectedCatalog = null,
                        rawItems = emptyList(),
                        displayItems = emptyList(),
                        isLoading = false,
                    )
                }
            } else {
                updateState {
                    copy(
                        allCatalogs = discovered,
                        availableTypes = types,
                        selectedType = targetType,
                        filteredCatalogs = forType,
                        selectedCatalog = nextCat,
                        selectedGenre = "All",
                        selectedYear = "All Years",
                        searchQuery = "",
                    )
                }
                loadCurrentCatalog()
            }
        }
    }

    private fun selectType(type: String) {
        if (uiState.value.selectedType == type) return

        val forType = uiState.value.allCatalogs.filter { it.type.equals(type, ignoreCase = true) }
        val nextCat = forType.firstOrNull()

        updateState {
            copy(
                selectedType = type,
                filteredCatalogs = forType,
                selectedCatalog = nextCat,
                selectedGenre = "All",
                selectedYear = "All Years",
                searchQuery = "",
            )
        }

        loadCurrentCatalog()
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

    private fun updateSearchQuery(query: String) {
        updateState {
            copy(
                searchQuery = query,
                displayItems = applyFilters(rawItems, query, selectedYear),
            )
        }
    }

    private fun applyFilters(items: List<ExploreItem>, query: String, year: String): List<ExploreItem> {
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

        // 0ms instant display if already in memory
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
                    )
                }
                return
            }
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isLoading = true) }
            try {
                val fetched = ExploreCatalogClient.fetchCatalogItems(
                    baseUrl = cat.addonBaseUrl,
                    type = cat.type,
                    catalogId = cat.id,
                    genre = genreArg,
                    skip = skip,
                )

                if (fetched.isNotEmpty()) {
                    catalogItemsCache[cacheKey] = fetched
                }

                val newRaw = if (skip == 0) fetched else uiState.value.rawItems + fetched
                val filtered = applyFilters(newRaw, uiState.value.searchQuery, uiState.value.selectedYear)

                updateState {
                    copy(
                        isInitializing = false,
                        rawItems = newRaw,
                        displayItems = filtered,
                        isLoading = false,
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

    private fun isTitleRelevant(targetTitle: String, candidateTitle: String): Boolean {
        val targetNorm = CardTitleSanitizer.sanitize(targetTitle).displayTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), " ")
            .trim()
        val candidateNorm = CardTitleSanitizer.sanitize(candidateTitle).displayTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), " ")
            .trim()

        if (targetNorm.isBlank() || candidateNorm.isBlank()) return false
        if (targetNorm == candidateNorm) return true
        if (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm)) return true

        val stopWords = setOf("the", "a", "an", "and", "of", "in", "to", "for", "with", "on", "at", "by", "from", "season", "episode")
        val targetWords = targetNorm.split(Regex("\\s+")).filter { it.length > 1 && it !in stopWords }

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
                stremioStreamMatches = emptyList(),
                isSearchingProviders = true,
            )
        }

        providerSearchJob?.cancel()
        providerSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanImdb = item.id.takeIf { it.startsWith("tt", ignoreCase = true) }?.substringBefore(":")
                val aggregatedStremioLinks = CopyOnWriteArrayList<ExtractorLink>()

                // Decoupled Stremio Stream query (runs safely in parallel)
                if (cleanImdb != null) {
                    launch {
                        StremioAddonManager.searchStreams(
                            imdbId = cleanImdb,
                            season = 1,
                            episode = 1,
                            onLink = { link ->
                                aggregatedStremioLinks.add(link)
                                updateState { copy(stremioStreamMatches = aggregatedStremioLinks.toList()) }
                            }
                        )
                    }
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
                        try {
                            val res = SafePluginInvoker.invokeOrNull(
                                tag = "Explore:Search:${provider.name}",
                                timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                            ) {
                                provider.search(searchTitle, 1)
                            }

                            val searchItems = res?.items
                            if (!searchItems.isNullOrEmpty()) {
                                for (searchRes in searchItems) {
                                    // Strictly filter for title relevance to discard random search noise
                                    if (isTitleRelevant(searchTitle, searchRes.name)) {
                                        val meta = CardTitleSanitizer.sanitize(searchRes.name)
                                        aggregatedMatches.add(
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
                                updateState { copy(providerMatches = aggregatedMatches.toList()) }
                            }
                        } catch (_: Exception) {
                            // Ignored per provider failure
                        }
                    }
                }

                jobs.joinAll()
                updateState {
                    copy(
                        providerMatches = aggregatedMatches.toList(),
                        stremioStreamMatches = aggregatedStremioLinks.toList(),
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

    fun formatTypeTitle(type: String): String {
        return when (type.lowercase(Locale.US)) {
            "movie" -> "Movies"
            "series" -> "Series"
            "anime" -> "Anime"
            else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
}
