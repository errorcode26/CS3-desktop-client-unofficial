# Phase 3 Red Team Audit & Engineering Backlog

**Branch:** `work`  
**Focus Areas:** MVVM Encapsulation, Coroutine Scope Boundaries, Thread Safety, Native OS Resource & Memory Leaks, and Compose Downsampling (`AsyncImage`).  
**Exclusions:** Core third-party extraction logic (`MainAPI` / `ExtractorApi` / `android-stubs`).

---

## 📊 Summary & Status Matrix

| ID | Category | Component / File | Severity | Status |
|---|---|---|---|---|
| **R01** | Threading / Player | `BaseMpvPlayer.kt:L803` | **Critical** | **OPEN** |
| **R02** | MVVM / Lifecycle | `ExtensionsViewModel`, `LinksViewModel`, `EmbeddedPlayerViewModel` | **Critical** | **OPEN** |
| **R03** | Coroutines / Repo | `BookmarksRepository.kt:L19, L34, L46` | **Critical** | **OPEN** |
| **R04** | Concurrency / Repo | `DesktopRepositoryManager.kt:L601, L622` | **Major** | **OPEN** |
| **R05** | MVVM / Encapsulation | `DesktopRepositoryManager` (`L54-55`), `ExtensionsViewModel` (`L46`) | **Major** | **OPEN** |
| **R06** | Memory / Player | `DesktopPlayerShield.kt:L13` | **Major** | **OPEN** |
| **R07** | Coroutines / MVVM | `EmbeddedPlayerViewModel.kt:L81, L93` | **Major** | **OPEN** |
| **R08** | Compose / Memory | `DetailsHeader`, `DetailsEpisodeList`, `CastDetails`, `ExtensionCard`, Overlays | **Major** | **OPEN** |

---

## 🛠️ Detailed Technical Findings & Root Causes

### 1. [CRITICAL] `R01` — Unmanaged OS Thread Spawning Inside `BaseMpvPlayer.removeNotify()`
* **Location:** [BaseMpvPlayer.kt:L803-L806](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/player/BaseMpvPlayer.kt#L803-L806)
* **Root Cause:** When the AWT player canvas is detached (`removeNotify()`), `BaseMpvPlayer` spawns a raw OS `Thread { Thread.sleep(150); mpv_terminate_destroy(h) }.start()` to defer native C++ handle destruction while Skia draws the transition.
* **Impact:** Spawning raw OS `Thread` instances completely bypasses structured concurrency (`appScope` and coroutine supervisors). If the user rapidly toggles fullscreen or exits/enters the player, multiple unmanaged 150ms threads spawn concurrently. If the application shuts down during this sleep window, the JVM terminates while C++ memory is mid-destruction, leading to access violations (`SIGSEGV` / `STATUS_ACCESS_VIOLATION`).
* **Fix Plan:** Replace raw `Thread` spawning with supervised coroutine jobs bound to our application lifecycle (`appScope.launch(Dispatchers.IO)`) or `DisposableEffect` teardown.

---

### 2. [CRITICAL] `R02` — Composable Scope Borrowing (`rememberCoroutineScope()`) Across ViewModels
* **Locations:**
  * [ExtensionsViewModel.kt:L27](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/extensions/ExtensionsViewModel.kt#L27): `class ExtensionsViewModel(private val coroutineScope: CoroutineScope)`
  * [LinksViewModel.kt:L17](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/LinksViewModel.kt#L17): `class LinksViewModel(private val viewModelScope: CoroutineScope)`
  * [EmbeddedPlayerViewModel.kt:L17](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/player/EmbeddedPlayerViewModel.kt#L17): `class EmbeddedPlayerViewModel(private val coroutineScope: CoroutineScope)`
* **Root Cause:** Unlike `DetailsViewModel` or `DesktopHomeViewModel` (which own internal `SupervisorJob()` scopes), these three ViewModels take `coroutineScope` as a constructor argument passed directly from `rememberCoroutineScope()` inside `@Composable` functions (`ExtensionsScreen.kt`, `LinksScreen.kt`, `EmbeddedVideoPlayer.kt`).
* **Impact:** `rememberCoroutineScope()` is tied strictly to the composition lifecycle of the calling `@Composable`. When the user navigates away from the Extensions tab or closes the player while a network fetch or repository sync is running, the composition scope is cancelled (`onForgotten`), instantly killing ongoing jobs mid-flight or throwing `CancellationException`.
* **Fix Plan:** ViewModels must own their own coroutine lifecycle (`private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`) with an explicit `dispose()` method bound to `DisposableEffect` in the UI.

---

### 3. [CRITICAL] `R03` — Unparented Coroutine Scope Instantiation in `BookmarksRepository`
* **Locations:** [BookmarksRepository.kt:L19, L34, L46](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/repo/BookmarksRepository.kt#L19)
* **Root Cause:** `BookmarksRepository` creates anonymous `CoroutineScope(Dispatchers.IO).launch` jobs on every repository read/write instead of utilizing a supervised parent scope.
* **Impact:** Creating `CoroutineScope(Dispatchers.IO)` without `SupervisorJob()` creates unparented, untracked background jobs (equivalent to `GlobalScope.launch`). The application explicitly provides `appScope` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`) precisely to prevent untracked coroutine lifecycles.
* **Fix Plan:** Replace all `CoroutineScope(Dispatchers.IO).launch` calls in `BookmarksRepository.kt` with `appScope.launch` or structured repository-scoped coroutines.

---

### 4. [MAJOR] `R04` — Non-Atomic Data Races on `lastAutoUpdateTime` Primitive and `pluginsCache` Map
* **Locations:** [DesktopRepositoryManager.kt:L601, L622](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/repo/DesktopRepositoryManager.kt#L601)
* **Root Cause:** `lastAutoUpdateTime` (`private var lastAutoUpdateTime = 0L`) and `pluginsCache` (`ConcurrentHashMap`) check (`pluginsCache[listUrl] ?: fetchPlugins(...)`) lack atomic check-then-act synchronization or lock protection (`AtomicLong` / `Mutex`).
* **Impact:** If `autoUpdatePlugins()` and manual `fetchPlugins()` run simultaneously across UI button clicks and background sync loops, both coroutines pass `if (now - lastAutoUpdateTime < ...)` and both miss `pluginsCache[listUrl]`, triggering duplicate parallel HTTP downloads and race conditions.
* **Fix Plan:** Protect `lastAutoUpdateTime` using `AtomicLong` (or `Mutex`) and use atomic `computeIfAbsent` / lock synchronization for `pluginsCache` access.

---

### 5. [MAJOR] `R05` — Public `MutableStateFlow` Exposed Without Read-Only Encapsulation
* **Locations:**
  * [DesktopRepositoryManager.kt:L54-L55](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/repo/DesktopRepositoryManager.kt#L54-L55): `val remotePluginIcons = MutableStateFlow(...)`, `val syncGeneration = MutableStateFlow(0)`
  * [ExtensionsViewModel.kt:L46](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/extensions/ExtensionsViewModel.kt#L46): `val inspectedRepoName = MutableStateFlow<String?>(null)`
* **Root Cause:** Properties are declared directly as public `MutableStateFlow` instead of using private backing properties (`private val _state = MutableStateFlow(...)` exposed as `val state: StateFlow = _state.asStateFlow()`).
* **Impact:** Any external UI component, helper function, or background callback can mutate `syncGeneration.value = ...` or `inspectedRepoName.value = ...` from any thread without validation, breaking unidirectional data flow (`UDF`).
* **Fix Plan:** Encapsulate all public `MutableStateFlow` properties behind read-only `StateFlow` getters and expose explicit state mutation methods (`fun inspectRepository(name: String?)`).

---

### 6. [MAJOR] `R06` — Swing `JWindow` Strong-Reference Memory Leak in Static `DesktopPlayerShield`
* **Location:** [DesktopPlayerShield.kt:L13-L32](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/player/DesktopPlayerShield.kt#L13-L32)
* **Root Cause:** `DesktopPlayerShield` is an `object` singleton that stores a strong reference to `shieldWindow` (`JWindow(owner)`), where `owner` is the top-level AWT `Window` / `RootPaneContainer`.
* **Impact:** Because `shieldWindow` holds a direct reference to its `owner` frame, if the main application window or secondary windows are re-created across monitor/DPI changes, `DesktopPlayerShield` retains the entire old AWT/Compose window hierarchy in static memory indefinitely (`dispose()` is never called on `shieldWindow`).
* **Fix Plan:** Ensure `shieldWindow?.dispose()` is called cleanly when hiding or re-creating the shield, and use weak references (`WeakReference<Window>`) for owner checking.

---

### 7. [MAJOR] `R07` — Redundant Main-Thread Scope Dispatches Inside `EmbeddedPlayerViewModel`
* **Locations:** [EmbeddedPlayerViewModel.kt:L81, L93](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/player/EmbeddedPlayerViewModel.kt#L81)
* **Root Cause:** Inside background `provider.loadLinks` callbacks (`Dispatchers.IO`), `EmbeddedPlayerViewModel` launches new coroutines on `coroutineScope.launch(Dispatchers.Main)` just to assign `_launchData.value = ...`.
* **Impact:** In Kotlin Coroutines, `MutableStateFlow.value` (`or update {}`) is **100% thread-safe** and can be called directly from `Dispatchers.IO`. Jumping back to `Dispatchers.Main` via `coroutineScope.launch` creates unnecessary coroutine allocations and risks crashes if the composable scope has been cancelled while `loadLinks` is emitting subtitle callbacks.
* **Fix Plan:** Remove `coroutineScope.launch(Dispatchers.Main)` inside callbacks and mutate `_launchData.update { ... }` or `_nextEpisodeSubtitles.update { ... }` directly from the IO callback.

---

### 8. [MAJOR] `R08` — Unbounded `AsyncImage` / `SubcomposeAsyncImage` Decodes Across Secondary Screens (`P10`)
* **Locations:**
  * [DetailsHeader.kt:L80, L182, L199, L632, L668](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/details/DetailsHeader.kt#L80) (Hero backdrop, poster, logo, trailer thumbnails)
  * [DetailsEpisodeList.kt:L105, L115, L125, L470](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/details/DetailsEpisodeList.kt#L105) (Episode preview cards, fallbacks)
  * [CastDetails.kt:L119, L219](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/details/CastDetails.kt#L119) (Actor profile avatars)
  * [ExtensionCard.kt:L92](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/components/ExtensionCard.kt#L92) (Repository plugin icons)
  * [RepositoriesTab.kt:L317](../desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/ui/screens/extensions/RepositoriesTab.kt#L317)
  * Player overlays (`EpisodesOverlay.kt`, `PausedDetailsOverlay.kt`, `PlayerLoadingOverlay.kt`)
* **Root Cause:** While `PosterCards.kt` and `HomeHeroCarousel.kt` were fixed in `P10` by wrapping image URLs in explicit `ImageRequest.Builder(context).data(url).size(width, height).build()` targets, secondary screens still pass raw `model = url` strings to `AsyncImage`.
* **Impact:** When a user opens a TV show with 50+ episodes (`DetailsEpisodeList.kt`) or expands a large cast list (`CastDetails.kt`), Coil decodes 50+ full-resolution bitmaps directly into the JVM heap without downsampling limits (`size(240, 135)` for episodes or `size(120, 180)` for cast avatars), creating heavy GC pressure and memory spikes.
* **Fix Plan:** Wrap all `AsyncImage` and `SubcomposeAsyncImage` model requests across secondary screens in explicit `ImageRequest.Builder(LocalPlatformContext.current).data(url).size(...).build()` downsampled targets.

---

## ✅ Progress Checklist
- [ ] `R01` — Replace raw `Thread` inside `BaseMpvPlayer.removeNotify()` with structured coroutine cleanup
- [ ] `R02` — Refactor `ExtensionsViewModel`, `LinksViewModel`, and `EmbeddedPlayerViewModel` to own internal scopes (`SupervisorJob()`) + `dispose()`
- [ ] `R03` — Replace `CoroutineScope(Dispatchers.IO).launch` in `BookmarksRepository.kt` with `appScope.launch`
- [ ] `R04` — Synchronize `lastAutoUpdateTime` (`AtomicLong`) and `pluginsCache` (`computeIfAbsent` / lock) in `DesktopRepositoryManager.kt`
- [ ] `R05` — Encapsulate public `MutableStateFlow` across `DesktopRepositoryManager` and `ExtensionsViewModel` with `asStateFlow()`
- [ ] `R06` — Add `shieldWindow?.dispose()` and clean memory lifecycle in `DesktopPlayerShield.kt`
- [ ] `R07` — Remove redundant `coroutineScope.launch(Dispatchers.Main)` calls inside `EmbeddedPlayerViewModel.kt` IO callbacks
- [ ] `R08` — Enforce explicit downsampled `size()` limits on all `AsyncImage` / `SubcomposeAsyncImage` decodes across secondary screens
