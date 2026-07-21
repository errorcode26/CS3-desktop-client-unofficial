# PHASE 5 — Architecture Red Flags Audit
> Branch: `refactor/mvi-architecture`
> Full two-pass review covering all ViewModels, screens, repositories, and UI sub-components.

---

## 🔴 CRITICAL (9 flags — Bugs / Runtime Risk)

### RF-01 · Synchronous Disk I/O on the Main Thread — `DetailsViewModel.kt`
`buildWatchHistory()` and `handleToggleEpisodeWatched()` are called from `handleEvent()`, which runs on `Dispatchers.Main.immediate`. Both perform **synchronous** `DesktopDataStore` reads and writes with no coroutine dispatcher switch:
```kotlin
val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)   // MAIN THREAD READ
DesktopDataStore.setLastWatched(history)                              // MAIN THREAD WRITE
```
**Risk:** UI freeze on slow storage. `handlePlayEpisode` and `handleToggleEpisodeWatched` must be launched on `Dispatchers.IO`.

---

### RF-02 · Raw JSON String Surgery in a ViewModel — `DetailsViewModel.kt`
`patchEpisodeData()` manually splices keys into a JSON string using `replaceFirst`:
```kotlin
patchedData = patchedData.replaceFirst("{", "{\"title\":\"$titleStr\",")
```
**Risk:** If `titleStr` contains an unescaped `"` or `\`, the JSON silently corrupts and the player receives malformed data. Belongs in a serialization utility or the plugin data layer.

---

### RF-03 · Massive Code Duplication — `EmbeddedPlayerViewModel.kt`
The entire `provider.loadLinks(...)` block (~120 lines) including `AtomicBoolean`, `subtitleCallback`, and link-first-play logic is **copy-pasted verbatim** between `init()` and `loadEpisode()`.
**Risk:** Any bug fix applied to one copy will be missed in the other. Extract into a single private `scrapeAndPlay(episode, startPositionMs)` function.

---

### RF-04 · `callbackFlow` Leaking Coroutines on Cancellation — `GetEnrichedDetailsUseCase.kt`
Inside `callbackFlow`, coroutines are launched via `launch { }` but `awaitClose {}` is **empty** — it cancels nothing:
```kotlin
launch { TmdbEnrichmentService.enrich(...) }  // orphaned if flow is cancelled
awaitClose { }   // does NOT cancel the enrich job above
```
**Risk:** Navigating away while enrichment is running keeps the TMDB network call and color extraction running against a dead scope, leaking work and memory.

---

### RF-05 · `LoadResponse.name` Mutated on a Shared Cached Object — `GetEnrichedDetailsUseCase.kt`
```kotlin
if (!preloadedName.isNullOrBlank() && rawData.name.isBlank()) {
    rawData.name = preloadedName   // mutating a shared object
}
```
`LoadResponse` is stored in `DetailsCache` (a shared static cache). Mutating `name` on it will affect every other caller that reads from the cache for this URL. Concurrency hazard.

---

### RF-21 · `DesktopDataStore` Read Synchronously Inside a Composable — `EmbeddedVideoPlayer.kt`
```kotlin
val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true
```
Line 205, **inside the Compose render function** — a synchronous disk read on the composition thread, called every recomposition. Should be read once in the ViewModel and pushed as part of state.

---

### RF-22 · Persistence Management (`saveJob`) Inside a Composable — `EmbeddedVideoPlayer.kt`
```kotlin
saveJob = coroutineScope.launch(Dispatchers.IO) {
    delay(2000)
    DesktopDataStore.setLastWatched(updatedHistory)
}
```
The player composable creates, cancels, and re-launches its own database write job every 5 seconds directly from `onPositionChange`. This is full persistence management inside a Composable. Must live in `EmbeddedPlayerViewModel` via an event.

---

### RF-23 · Direct Database Writes Inside a UI Sub-Component — `DetailsEpisodeSection.kt`
```kotlin
DesktopDataStore.setLastWatched(backup)
DesktopDataStore.removeEpisodeWatched(parentId, ep.data)
val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)
```
A Composable sub-component writes to the database when the user clicks "Mark Season Watched". Zero ViewModel involvement. Must go through `onToggleWatched` lambda → ViewModel event → `Dispatchers.IO`.

---

### RF-24 · `DesktopDataStore` Read + Write Inside `LinksSidePanel` Composable — `LinksScreen.kt`
```kotlin
// Read during composition:
var selectedPlayer by remember { mutableStateOf(DesktopDataStore.getKey<String>("preferred_player") ?: "mpv") }

// Write inside onClick:
DesktopDataStore.setKey("preferred_player", player)
```
Both the read on composition and the write on click bypass the ViewModel entirely. `selectedPlayer` preference should be in `LinksUiState`, loaded by `LinksViewModel`.

---

## 🟠 ARCHITECTURE VIOLATIONS (11 flags)

### RF-06 · Compose `Color` Type in a Repository — `HeroRepository.kt`
```kotlin
import androidx.compose.ui.graphics.Color
```
`HeroRepository` imports and uses `Color` — a Compose UI type. Repositories must be UI-framework-agnostic. Return a `Long` ARGB int or a domain `ColorValue` type instead.

---

### RF-07 · Compose `Color` Type in a UseCase — `GetEnrichedDetailsUseCase.kt`
`EnrichmentUpdate.ExtractedColor(val color: Color)` has a hard Compose UI dependency at the domain layer. Unit testing this UseCase requires a full Compose runtime.

---

### RF-08 · Raw `StateFlow`s Exposed on ViewModel Bypassing MVI — `ExtensionsViewModel.kt`
```kotlin
val savedRepositories = DesktopRepositoryManager.savedRepositories
val remotePluginIcons = DesktopRepositoryManager.remotePluginIcons
val syncGeneration = DesktopRepositoryManager.syncGeneration
```
Three repository `StateFlow`s are passed directly through the ViewModel to the UI, bypassing `uiState` entirely. All state should funnel through `updateState { }`.

---

### RF-09 · Public `suspend` Functions on a ViewModel — `ExtensionsViewModel.kt`
```kotlin
suspend fun addRepositoryFromInput(input: String): List<Repository>?
suspend fun syncAllRepos()
```
ViewModels must not expose `suspend` functions. The UI should dispatch events; the ViewModel handles them internally. These force the caller to manage coroutine scope, breaking encapsulation.

---

### RF-10 · Utility Passthrough Getters on a ViewModel — `ExtensionsViewModel.kt`
```kotlin
fun getPluginsJsonUrl(url: String): String
fun getExtensionsDir(): File
fun isIconFailed(url: String): Boolean
fun markIconFailed(url: String)
```
Direct repository passthrough utilities on a ViewModel. Move to a utility object or expose the relevant data via `uiState`.

---

### RF-11 · Imperative Getter Methods Instead of Pushed State — `EmbeddedPlayerViewModel.kt`
```kotlin
fun getEpisodesList(): List<Episode>
fun hasNextEpisode(): Boolean
fun getNextEpisode(): Episode?
fun hasPrevEpisode(): Boolean
```
The UI is **pulling** derived state out of the ViewModel. In MVI, the ViewModel **pushes** all state. These should be computed properties on `PlayerUiState`, recalculated whenever `launchData` changes.

---

### RF-12 · ViewModel Exposed Via `CompositionLocal` Globally — `ComposeNavigation.kt`
```kotlin
val LocalHomeViewModel = staticCompositionLocalOf<DesktopHomeViewModel> { ... }
```
A `DesktopHomeViewModel` is injectable anywhere in the entire Compose tree. Any component deep in the tree can silently grab it. Invisible, untraceable coupling.

---

### RF-13 · Lambda Callbacks Inside a `data class` — `ComposeNavigation.kt`
```kotlin
data class VideoLaunchData(
    val onError: ((String) -> Unit)? = null,
    val onClosed: (() -> Unit)? = null,
)
```
Lambdas in `data class` break `equals()`/`hashCode()` (compared by reference, not value). Will cause broken `remember` keys and unexpected recompositions. Callbacks belong in `UiEffect`, not in data objects.

---

### RF-14 · Repository Takes UI-Update Lambdas Instead of Returning a Flow — `HeroRepository.kt`
```kotlin
suspend fun prefetchHeroItem(
    onMetaUpdate: (String, HeroMeta) -> Unit,
    onColorUpdate: (String, String) -> Unit
)
```
A repository function taking UI-update lambdas is an inversion of responsibility. Return a `Flow<HeroUpdate>` and let the ViewModel subscribe.

---

### RF-26 · Composable Calls Imperative ViewModel Getters During Render — `EmbeddedVideoPlayer.kt`
```kotlin
val episodes = viewModel.getEpisodesList()   // called in render
val hasNext = viewModel.hasNextEpisode()     // called inside a coroutine launched from a callback
val nextEp = viewModel.getNextEpisode()
```
This is RF-11 actively manifesting as composable-render-time ViewModel queries. These violate UDF and will produce stale reads.

---

### RF-27 · 18-Parameter God Function with Business Logic — `LinksScreen.kt`
```kotlin
private fun playLink(
    link, links, subtitles, subtitleFiles, selectedPlayer, displayTitle,
    history, loadResponse, isLaunchingPlayer, currentPlayingUrl, filteredLinks,
    coroutineScope, vlcPlayer, playVideo,
    onStatusChange, onLaunching, onCurrentUrl, onEmbeddedError
)
```
Player routing logic (VLC vs MPV vs embedded), validation, resume-position calculation, and status text formatting all in one 18-parameter function. This is ViewModel business logic dressed up as a Kotlin top-level function.

---

### RF-28 · API Key Hardcoded in Source Code — `TmdbEnrichmentService.kt`
```kotlin
?: "3828864585df9d4f006c09403eb9a888"
```
A real API key committed in source as a fallback. It is now permanently in git history and will be scraped. Move to a config file excluded from version control, or to a build-time constant.

---

### RF-29 · Mixed `@Synchronized` + Coroutine `Mutex` Locking Strategy — `DesktopRepositoryManager.kt`
```kotlin
@Synchronized fun saveRepository(...)        // JVM monitor — blocks threads
@Synchronized fun writeRepositoriesToDisk(...)
private val syncMutex = Mutex()              // Coroutine-aware — suspends
private val fetchMutexes = ConcurrentHashMap<String, Mutex>()
```
`@Synchronized` (JVM monitor) mixed with coroutine `Mutex` is a deadlock waiting to happen. A suspended coroutine holding a `Mutex` that then calls a `@Synchronized` method can deadlock the thread pool. Pick one: all coroutine `Mutex`, or all `synchronized`.

---

### RF-30 · `CancellationException` Swallowed in Auto-Update Loop — `DesktopRepositoryManager.kt`
```kotlin
savedRepos.forEach { saved ->
    try {
        ...downloadPlugin(...)
        ...ExtensionLoader.loadAndInit(newJar)
    } catch (e: Exception) {   // swallows CancellationException!
        e.printStackTrace()
    }
}
```
Catching bare `Exception` in a coroutine without rethrowing `CancellationException` means if the scope is cancelled mid-update, the loop continues running against a dead scope. Always rethrow `CancellationException`.

---

## 🟡 QUALITY / MAINTAINABILITY (10 flags)

### RF-15 · `e.printStackTrace()` in Production — `EmbeddedPlayerViewModel.kt`
Two occurrences. `AppLogger` is used everywhere else. Inconsistent and lazy.

---

### RF-16 · Stale Map Snapshot Passed to Long-Running Suspend Function — `HeroRepository.kt`
```kotlin
suspend fun prefetchHeroItem(currentMetaMap: Map<String, HeroMeta>, ...)
```
The map snapshot is stale by the time the function finishes multiple `delay()` calls. Read from live `uiState.value` at the point of use, not from a snapshot passed in at call time.

---

### RF-17 · Two Redundant Error Fields in `DetailsUiState` — `DetailsUiState.kt`
```kotlin
val error: String? = null,
val errorMessage: String? = null,
```
Both are set to the same value simultaneously and read interchangeably. One must be deleted.

---

### RF-18 · `enrichmentTrigger: Int` Hack — `DetailsUiState.kt`
```kotlin
val enrichmentTrigger: Int = 0,
```
An ever-incrementing counter used to force recomposition. Wrong abstraction. Replace with a sealed `EnrichmentPhase` state (`Idle`, `InProgress`, `Complete`).

---

### RF-19 · `System.gc()` in Plugin Uninstall — `ExtensionsViewModel.kt`
```kotlin
System.gc()
kotlinx.coroutines.delay(100)
```
Calling `System.gc()` in production code signals that JAR file lock release is relying on GC timing. Must be addressed at the plugin runtime / classloader lifecycle level.

---

### RF-20 · Magic Hardcoded Delay Retry Loop — `HeroRepository.kt`
```kotlin
while (attempt < 3 && details == null) {
    kotlinx.coroutines.delay(if (attempt == 0) 1500L else 2000L)
    details = ...
}
```
1.5 and 2-second magic delays in a retry loop. Use exponential backoff with a proper retry utility, or at minimum make delays named constants.

---

### RF-25 · `e.printStackTrace()` in `autoUpdatePlugins` — `DesktopRepositoryManager.kt`
Single occurrence missed among the otherwise correct `AppLogger` usage in the same file.

---

## Summary Table

| ID | Severity | File | Issue | Status |
|---|---|---|---|---|
| RF-01 | 🔴 Critical | `DetailsViewModel.kt` | Sync disk I/O on main thread | ✅ Fixed |
| RF-02 | 🔴 Critical | `DetailsViewModel.kt` | Raw JSON string mutation (data corruption risk) | ✅ Fixed |
| RF-03 | 🔴 Critical | `EmbeddedPlayerViewModel.kt` | ~120 lines of duplicated link-scraping code | ✅ Fixed |
| RF-04 | 🔴 Critical | `GetEnrichedDetailsUseCase.kt` | `callbackFlow` leaks coroutines on cancellation | ✅ Fixed |
| RF-05 | 🔴 Critical | `GetEnrichedDetailsUseCase.kt` | Mutating shared cached `LoadResponse` object | ✅ Fixed |
| RF-21 | 🔴 Critical | `EmbeddedVideoPlayer.kt` | Sync DataStore read inside Compose render | ✅ Fixed |
| RF-22 | 🔴 Critical | `EmbeddedVideoPlayer.kt` | saveJob persistence management inside a Composable | ✅ Fixed |
| RF-23 | 🔴 Critical | `DetailsEpisodeSection.kt` | Direct DB writes inside a UI sub-component | ✅ Fixed |
| RF-24 | 🔴 Critical | `LinksScreen.kt` | DataStore read+write inside Composable and onClick | ✅ Fixed |
| RF-06 | 🟠 Arch | `HeroRepository.kt` | Compose `Color` type in a Repository | ✅ Fixed |
| RF-07 | 🟠 Arch | `GetEnrichedDetailsUseCase.kt` | Compose `Color` type in a UseCase | ✅ Fixed |
| RF-08 | 🟠 Arch | `ExtensionsViewModel.kt` | Raw `StateFlow`s bypassing MVI `uiState` | ✅ Fixed |
| RF-09 | 🟠 Arch | `ExtensionsViewModel.kt` | Public `suspend` functions on ViewModel | ✅ Fixed |
| RF-10 | 🟠 Arch | `ExtensionsViewModel.kt` | Utility passthrough getters on ViewModel | ✅ Fixed |
| RF-11 | 🟠 Arch | `EmbeddedPlayerViewModel.kt` | Imperative getter methods instead of pushed state | ✅ Fixed |
| RF-12 | 🟠 Arch | `ComposeNavigation.kt` | ViewModel exposed via `CompositionLocal` globally | ✅ Fixed |
| RF-13 | 🟠 Arch | `ComposeNavigation.kt` | Lambda callbacks inside a `data class` | ✅ Fixed |
| RF-14 | 🟠 Arch | `HeroRepository.kt` | Repository takes UI-update lambdas instead of returning Flow | ✅ Fixed |
| RF-26 | 🟠 Arch | `EmbeddedVideoPlayer.kt` | Composable calls imperative ViewModel getters during render | ✅ Fixed |
| RF-27 | 🟠 Arch | `LinksScreen.kt` | 18-parameter god function with business logic | ✅ Fixed |
| RF-28 | 🟠 Arch | `TmdbEnrichmentService.kt` | API key hardcoded in source code | ⚠️ Intentionally open (User Request) |
| RF-29 | 🟠 Arch | `DesktopRepositoryManager.kt` | Mixed `@Synchronized` + coroutine `Mutex` locking | ✅ Fixed |
| RF-30 | 🟠 Arch | `DesktopRepositoryManager.kt` | `CancellationException` swallowed in update loop | ✅ Fixed |
| RF-15 | 🟡 Quality | `EmbeddedPlayerViewModel.kt` | `e.printStackTrace()` instead of `AppLogger` | ✅ Fixed |
| RF-16 | 🟡 Quality | `HeroRepository.kt` | Stale map snapshot in long-running suspend function | ✅ Fixed |
| RF-17 | 🟡 Quality | `DetailsUiState.kt` | Two redundant error fields (`error` + `errorMessage`) | ✅ Fixed |
| RF-18 | 🟡 Quality | `DetailsUiState.kt` | `enrichmentTrigger: Int` hack instead of proper state | ✅ Fixed |
| RF-19 | 🟡 Quality | `ExtensionsViewModel.kt` | `System.gc()` in plugin uninstall | ✅ Fixed |
| RF-20 | 🟡 Quality | `HeroRepository.kt` | Magic hardcoded delay retry loop | ✅ Fixed |
| RF-25 | 🟡 Quality | `DesktopRepositoryManager.kt` | `e.printStackTrace()` in `autoUpdatePlugins` | ✅ Fixed |

**Progress: 29 / 30 fixed**

---

## Fix Priority Order

### Fix First (Critical — actual bugs)
1. **RF-21, RF-22, RF-23, RF-24** — All the scattered `DesktopDataStore` read/writes in Composables. One sweep fixes the pattern.
2. **RF-01** — `handleToggleEpisodeWatched` and `buildWatchHistory` on main thread in `DetailsViewModel`.
3. **RF-04** — `awaitClose {}` not cancelling launched coroutines in `GetEnrichedDetailsUseCase`.
4. **RF-05** — `rawData.name` mutation on a cached object.
5. **RF-03** — Extract duplicated scraping logic in `EmbeddedPlayerViewModel`.
6. **RF-02** — Replace JSON string surgery with a proper serialization approach.
7. **RF-30** — Rethrow `CancellationException` in `autoUpdatePlugins`.

### Fix Second (Architecture — correctness & testability)
8. **RF-11 + RF-26** — Promote episode list / hasNext / getNext into `PlayerUiState`.
9. **RF-27** — Break `playLink()` into a ViewModel event.
10. **RF-08, RF-09, RF-10** — Clean up `ExtensionsViewModel` public API.
11. **RF-06, RF-07** — Replace Compose `Color` in domain layer with `Long` ARGB.
12. **RF-29** — Unify locking in `DesktopRepositoryManager` to all coroutine `Mutex`.
13. **RF-13** — Remove lambdas from `VideoLaunchData`.
14. **RF-14** — Convert `prefetchHeroItem` callbacks to a Flow.
15. **RF-12** — Remove `LocalHomeViewModel` CompositionLocal.
16. **RF-28** — Remove hardcoded API key fallback from source.

### Fix Third (Quality — polish)
17. **RF-17** — Delete the duplicate error field.
18. **RF-18** — Replace `enrichmentTrigger` with `EnrichmentPhase` sealed class.
19. **RF-15, RF-25** — Replace all `e.printStackTrace()` with `AppLogger`.
20. **RF-16** — Fix stale snapshot in `prefetchHeroItem`.
21. **RF-19** — Remove `System.gc()`.
22. **RF-20** — Replace magic delay retry with proper backoff.
