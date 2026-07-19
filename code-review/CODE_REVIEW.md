# CloudStream Desktop — Comprehensive Code Review

> **Reviewed files:** `Main.kt`, `ComposeNavigation.kt`, `DesktopAppShell.kt`, `NavController.kt`, `Screen.kt`, `DetailsScreen.kt`, `DetailsViewModel.kt`, `DetailsRepository.kt`, `DesktopHomeViewModel.kt`, `EmbeddedPlayerViewModel.kt`, `BaseMpvPlayer.kt` (head), `ComposeNativeWebPlayer.kt` (head), `DesktopRepositoryManager.kt` (head), `LinksScreen.kt` (head), `PosterCards.kt` (head), `DataStore.kt`

> [!CAUTION]
> **Android-Port Boundary — Read Before Fixing Anything**
> This desktop client is a port of the upstream CS3 Android codebase. It compiles against `android-stubs` and `android-reference` modules to maintain compatibility with Android-compiled plugins. Several patterns that *look* wrong from a pure desktop/Compose perspective are **intentional compatibility shims** for the plugin API surface. Fixing them as if this were a greenfield desktop app will break plugin loading.
> Before touching any code in `DataStore`, `APIHolder`, `MainAPI`, or any Android extension function, verify that the change does not affect the interface visible to plugins loaded from JARs.

---

## Part 1 — Architectural Review

### ISSUE-A01 · God Object: `GlobalDetailsCache` (object in `DetailsRepository.kt`)

**Evidence:** `desktop/ui/screens/details/DetailsRepository.kt` — the `GlobalDetailsCache` singleton handles: in-memory LRU caching, raw HTTP fetching, TMDB API calls, color extraction, actor lookups, screenshot fetching, collection lookups, and enrichment callbacks — all in one 867-line file.

**Why it's a problem:** This is a textbook God Object. It violates SRP catastrophically. You cannot unit-test any single concern without the whole cache, the network stack, and TMDB being present. Adding a new enrichment field requires touching the same class that owns the HTTP logic.

**Severity:** Major

**Fix:** Split into:
- `DetailsCache` — pure in-memory LRU, zero network.
- `DetailsRepository` — coordinates fetching + caching.
- `TmdbEnrichmentService` — all TMDB queries.
- `ImageColorExtractor` — pixel sampling logic (already partially isolated in `DetailsViewModel`).

**Fix now or after alpha?** After alpha — this is a correctness-stable God Object, not a crash risk. But it will block testability indefinitely if left.

---

### ISSUE-A02 · Race Condition in `TmdbRateLimiter`

**Evidence:** `DetailsRepository.kt` L13–L24

```kotlin
object TmdbRateLimiter {
    @Volatile private var lastRequestTime = 0L
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val wait = minInterval - (now - lastRequestTime)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestTime = System.currentTimeMillis()  // ← not atomic
    }
}
```

**Why it's a problem:** The read-compute-write on `lastRequestTime` is not atomic. Two coroutines running `acquire()` concurrently can both read the same `lastRequestTime`, both decide no wait is needed, and both proceed simultaneously, violating the rate limit. `@Volatile` only guarantees visibility, not atomicity of compound operations.

**Severity:** Major (can cause HTTP 429 cascade from TMDB)

**Fix:** Use a `Mutex`:
```kotlin
private val mutex = Mutex()
suspend fun acquire() = mutex.withLock {
    val now = System.currentTimeMillis()
    val wait = minInterval - (now - lastRequestTime)
    if (wait > 0) delay(wait)
    lastRequestTime = System.currentTimeMillis()
}
```

**Fix now or after alpha?** Now — this actively breaks TMDB enrichment under normal use.

---

### ISSUE-A03 · Mutable Model Written From IO Thread Without Synchronization

**Evidence:** `DetailsViewModel.kt` L228

```kotlin
// On Dispatchers.IO (inside launch)
if (!preloadedName.isNullOrBlank() && currentData.name.isBlank()) {
    currentData.name = preloadedName  // ← mutating a shared LoadResponse
}
```

**Why it's a problem:** `LoadResponse` appears to be a mutable data model (it's from the upstream CS3 library, not a desktop-local `data class`). Mutating it on `Dispatchers.IO` while the UI thread may be reading it from `_response.value` is an unsynchronized write. This is a data race. On the JVM this can produce stale reads or, with certain JIT optimisations, torn values.

**Severity:** Critical — data race with undefined behavior.

**Fix:** Never mutate the shared response. Instead copy it:
```kotlin
_response.value = currentData.copy(name = preloadedName)
// OR treat LoadResponse as immutable and make a local wrapper.
```

**Fix now or after alpha?** Now.

---

### ISSUE-A04 · Leaked CoroutineScope in `DetailsViewModel`

**Evidence:** `DetailsViewModel.kt` L38–L45

```kotlin
class DetailsViewModel(
    private val viewModelScope: CoroutineScope,
    ...
)
```

The `CoroutineScope` is obtained from `rememberCoroutineScope()` inside the composable and passed into the ViewModel. That scope is tied to the composable's lifecycle. However, the ViewModel is created with `remember(url)`, meaning it survives recompositions — but if the composable leaves composition and re-enters (e.g. back-stack restoration), the old scope is cancelled while the ViewModel's reference to it is stale. There is no `cancel()` or `close()` hook on `DetailsViewModel`.

**Why it's a problem:** Coroutines launched by the old VM survive into the new scope context or orphan silently. There is no cleanup mechanism at all — the VM has no `onCleared()` equivalent.

**Severity:** Major — can cause ghost network requests and state updates on a destroyed composable.

**Fix:** The ViewModel should own its scope internally:
```kotlin
private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
fun dispose() { scope.cancel() }
```
Call `dispose()` from a `DisposableEffect` in the composable.

**Fix now or after alpha?** Now.

---

### ISSUE-A05 · `DesktopHomeViewModel` Scope Never Cancelled

**Evidence:** `DesktopHomeViewModel.kt` L38

```kotlin
class DesktopHomeViewModel {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

This VM is created with `remember { DesktopHomeViewModel() }` in `CloudstreamApp`. Unlike `DetailsViewModel`, this scope is self-owned — but there is no teardown. When the application exits, the SupervisorJob keeps threads alive until JVM termination. More critically, there is no graceful shutdown of in-flight network calls on app close.

**Severity:** Minor (desktop JVM exits anyway), but is a pattern to fix before mobile/server if scope ever grows.

**Fix:** Tie destruction to `DisposableEffect(Unit) { onDispose { homeViewModel.dispose() } }`.

**Fix now or after alpha?** After alpha.

---

### ISSUE-A06 · `Screen.Details` Holds a Live `MainAPI` Reference

**Evidence:** `Screen.kt` L11

```kotlin
data class Details(val provider: MainAPI, ...)
```

**Why it's a problem:** `Screen` objects are stored in the `backStack` and `forwardStack` of `NavController`. Holding a `MainAPI` reference (a live, stateful plugin-loaded object) inside a nav stack means:
1. Plugins cannot be garbage collected while they are in the nav stack.
2. If a plugin is unloaded/reloaded via the Extensions screen, the back-stack still holds the stale `MainAPI`. Navigating back will use the old plugin instance.
3. `data class` equality checks on `MainAPI` (which doesn't implement structural equality) will almost certainly break duplicate-prevention logic in `navigate()`.

**Severity:** Major

**Fix (non-trivial — read carefully):** The naive fix of "store only the provider name (String) and re-resolve from `APIHolder`" is more complex than it sounds. `MainAPI` instances are plugin-loaded and may carry instance-level state (cookies, session tokens, headers set at load time). Re-resolving by name may return a *different* object with no state. The correct fix is:
1. First determine whether any providers in the ecosystem carry instance-level state between `loadPage()` calls.
2. If yes — keep the live reference but add a `WeakReference` wrapper and handle the `null` case on navigation.
3. If no — store the name and re-resolve. Add a guard in `goBack()` for the case where `getApiFromNameNull()` returns `null` (plugin was unloaded).

> [!WARNING]
> Do **not** change `Screen.Details` to store a String name without first auditing whether any loaded plugins depend on live provider state. An incorrect fix here will cause silent failures where the wrong plugin instance handles navigation.

**Fix now or after alpha?** After alpha. For now, document that navigating back after a plugin reload may use a stale provider instance.

---

### ISSUE-A07 · `NavController` Uses Plain `mutableListOf` (Thread Unsafe)

**Evidence:** `NavController.kt` L15–L16

```kotlin
private val backStack = mutableListOf<Screen>()
private val forwardStack = mutableListOf<Screen>()
```

`NavController.navigate()` / `goBack()` are called from the Compose main thread but the mouse-button handler in `ComposeNavigation.kt` fires from `pointerInput` which runs on a background dispatcher. If both hit simultaneously, you have a concurrent list modification.

**Severity:** Minor (rare race, non-crash on JVM ArrayList), but a correctness issue.

**Fix:** Use `@GuardedBy` annotations + synchronize, or better, route all mutations through a single `StateFlow`/channel.

**Fix now or after alpha?** After alpha.

---

### ISSUE-A08 · `DesktopRepositoryManager` Owns Two Separate `OkHttpClient` Instances

**Evidence:** `DesktopRepositoryManager.kt` L30–L48

Two separate `OkHttpClient` instances (`client` and `redirectClient`) are created inside the repository manager object. The app already has `com.lagradost.cloudstream3.app.baseClient` configured with proxy, SSL overrides, and interceptors. These two clients bypass all of that, including any user-configured proxy.

**Why it's a problem:** Plugin sync traffic goes through un-proxied connections, leaking the request even when the user has configured a proxy for privacy.

**Severity:** Major (privacy regression)

**Fix:** Use `app.baseClient` (or a derivative with `.newBuilder().followRedirects(true).build()`) so all outbound connections respect the configured proxy and SSL settings.

**Fix now or after alpha?** Now.

---

### ISSUE-A09 · Hardcoded API Key in Source

**Evidence:** `DetailsRepository.kt` L29

```kotlin
private val TMDB_API_KEY: String
    get() = DesktopDataStore.getKey<String>("tmdb_api_key")?.takeIf { it.isNotBlank() }
        ?: "<key>"   // ← hardcoded fallback key
```

**Why it's a problem:** A real v3 API key is hardcoded as a fallback. It's embedded in compiled bytecode. It will be extracted, rate-limited into the ground, or revoked. The ToS also prohibits API key sharing.

**Severity:** Major (operational — key will eventually be killed)

**Fix:** Remove the hardcoded key. Require the user to provide their own key. Expose a clear, friendly prompt in Settings.

**Fix now or after alpha?** Now. Every day this is in a published binary increases the chance the key is found.

---

### ISSUE-A10 · `vlcPlayer` is a File-Level Singleton with No Lifecycle

**Evidence:** `LinksScreen.kt` L35

```kotlin
private val vlcPlayer = VlcPlayer()
```

This is a top-level Kotlin property. It is created once when the class is first referenced and lives for the entire application lifetime. It holds native resources (VLC/JNA handles). It is never disposed.

**Severity:** Major — native resource leak guaranteed.

**Fix:** Instantiate inside the composable with `remember { VlcPlayer() }` and dispose it via `DisposableEffect`.

**Fix now or after alpha?** Now.

---

### ISSUE-A11 · `SearchUiState` Holds Mutable State References Inside a `data class`

**Evidence:** `ComposeNavigation.kt` L43–L46

```kotlin
data class SearchUiState(
    val isSearchForced: MutableState<Boolean>,
    val searchFocusTrigger: MutableState<Int>
)
```

`data class` with mutable Compose state references is a well-known anti-pattern. The `copy()` function will copy the reference, not the value. `equals()` / `hashCode()` will reflect reference identity of the state object, not its current value, which will break anything that does state comparison.

**Severity:** Minor (no immediate crash), but violates the principle of least surprise in the data model.

**Fix:** Use a plain class (not `data class`), or replace with a stable holder holding the `MutableState` objects intentionally, documented as such.

**Fix now or after alpha?** After alpha.

---

### ISSUE-A12 · `FullscreenController` is a `data class` Holding Mutable State

**Evidence:** `ComposeNavigation.kt` L55–L69

Same pattern as A11. `FullscreenController` is a `data class` containing `MutableState<Boolean>`, `MutableState<Int>`, and `MutableState<Pair<Int, Int>>`. The generated `equals()` compares the *identity* of the state objects, not their values. More critically, it is rebuilt on every recomposition of `Window { }` in `Main.kt` (L254–L260) because the local `val` references inside the composable body are recreated.

**Severity:** Minor

**Fix:** Extract `FullscreenController` creation into a `remember { }` block, or make it a plain class.

**Fix now or after alpha?** After alpha.

---

### ISSUE-A13 · `DataStore.save()` Writes the Full JSON File Synchronously on Every Key Set

**Evidence:** `DataStore.kt` L29–L57

```kotlin
fun <T> setKey(key: String, value: T) {
    cache[key] = value
    save()  // ← blocks caller, serialises entire map to disk
}
```

`save()` serialises the entire cache map to disk on every single write, from whatever thread calls `setKey`. This is called from composable callbacks, meaning it can block the composition / UI thread for multi-millisecond I/O.

**Android-port context:** On Android, the equivalent was `SharedPreferences.apply()` which is *asynchronous* — it batches and commits on a background thread automatically. The desktop port replaced it with a synchronous `mapper.writeValue()` call but kept the same eager call-on-every-write pattern. The pattern is inherited; the synchronous implementation is the bug.

**Severity:** Major (UI jank, especially as the preferences JSON grows)

**Fix (desktop-only, plugin-safe):** Debounce the disk write. The in-memory `cache` map is still updated synchronously so reads are always consistent — only the flush to disk is deferred. This is safe for plugins because they read/write through the same in-memory cache:
```kotlin
private val saveDebounceJob = AtomicReference<Job?>(null)
fun scheduleSave() {
    saveDebounceJob.getAndSet(null)?.cancel()
    saveDebounceJob.set(ioScope.launch { delay(200); persist() })
}
```
Replace `save()` call with `scheduleSave()` in `setKey`. The `removeKey` paths need the same treatment.

**Fix now or after alpha?** Now — this is an immediate jank source on every setting toggle.

---

### ~~ISSUE-A14~~ · RETRACTED — `DataStore` Android Extension Functions Are Intentional Plugin API Surface

**Evidence:** `DataStore.kt` L60–L218

**Retraction reason:** This was initially flagged as "dead code" but that assessment was wrong. These `android.content.Context` extension functions are **intentional compatibility shims** required by the plugin API contract. Plugins are compiled against the Android SDK and call `Context.getKey()`, `Context.setKey()`, `Context.getSharedPrefs()`, etc. at runtime. The `android-stubs` module provides the `android.content.Context` class on the desktop JVM specifically so these calls resolve. Removing or moving these functions would silently break any plugin that uses the standard CS3 DataStore API from its Android-side code.

> [!CAUTION]
> **Do not touch these functions.** They are part of the plugin API surface, not dead code. Any change must be coordinated with the upstream CS3 plugin API to avoid breaking existing plugins.

**Severity:** N/A — not an issue.

**Fix:** None needed.

---

### ISSUE-A15 · `DesktopAppShell` Runs an Infinite Loop for Plugin Auto-Update

**Evidence:** `DesktopAppShell.kt` L66–L71

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        delay(30 * 60 * 1000L) // 30 minutes
        DesktopRepositoryManager.autoUpdatePlugins()
    }
}
```

This infinite loop is inside a **composable**. `DesktopAppShell` is composed for every top-level screen (`Home`, `Library`, `Extensions`, `Settings`). Each one creates its own `LaunchedEffect(Unit)` loop. With 4 screens in `AnimatedContent`, that's **4 auto-update loops** running simultaneously, each firing `autoUpdatePlugins()` every 30 minutes. Depending on `AnimatedContent`'s state handling, old compositions may linger during transitions.

**Severity:** Major — multiple simultaneous plugin sync jobs, redundant network load.

**Fix:** Hoist this loop to the application level (e.g. in `CloudstreamApp` or `Main.kt`), where it is launched exactly once.

**Fix now or after alpha?** Now.

---

### ISSUE-A16 · `GlobalDetailsCache.cache` is Mutable and Publicly Exposed

**Evidence:** `DetailsRepository.kt` L115

```kotlin
val cache: MutableMap<String, LoadResponse> = Collections.synchronizedMap(...)
```

The internal `LoadResponse` cache is `val` but mutable, and publicly accessible (no `private`). `DetailsViewModel.retry()` calls `GlobalDetailsCache.cache.remove(url)` directly. Any caller anywhere in the codebase can corrupt, clear, or bulk-poison this cache.

**Severity:** Minor (currently only one call site)

**Fix:** Make it private, expose `fun invalidate(url: String)` and `fun get(url: String): LoadResponse?`.

**Fix now or after alpha?** After alpha.

---

## Part 2 — Performance Review

### ISSUE-P01 · `DetailsContent` Calls `getAllWatchHistory()` on Every Recomposition (✅ FIXED)

**Evidence:** `DetailsScreen.kt` L311–L313

```kotlin
val showHistory = remember(data.url, historyUpdatesVal) {
    DesktopDataStore.getAllWatchHistory()
        .filter { it.showUrl == data.url }
        .associateBy { it.episodeId ?: it.parentId }
}
```

`getAllWatchHistory()` is a full table scan every time `historyUpdatesVal` changes (i.e. any episode watched anywhere triggers this). The result is filtered and associated inline. For large watch histories this is O(N) on the UI-relevant recomposition path.

**Severity:** Major

**Fix:** Move this to the ViewModel where it can be computed on `Dispatchers.IO` and cached. Expose a `StateFlow<Map<String?, WatchHistory>>` keyed by show URL.

**Fix now or after alpha?** After alpha (low data volumes in alpha). Design the fix now.

---

### ISSUE-P02 · Duplicate `historyUpdatesVal` Collection in the Same Composable

**Evidence:** `DetailsScreen.kt` L305–L338

`historyUpdatesVal` and `latestHistory` are computed twice in `DetailsContent`: once at the top level of `DetailsContent` (L305–L308), and again inside the `heroAction` lambda (L336–L339). Both call `DesktopDataStore.historyUpdates.collectAsState()` and `getLatestWatchHistoryForShow()`. The `heroAction` lambda is a `@Composable () -> Unit` that is invoked inside the `LazyColumn`, so this produces a second subscription and a second DB query.

**Severity:** Minor (duplicate subscription, small overhead)

**Fix:** Hoist the computation once to the top of `DetailsContent` and pass it down.

**Fix now or after alpha?** After alpha.

---

### ISSUE-P03 · `sampleDominantColor` — Full Image Re-Download and Decode for Color Extraction

**Evidence:** `DetailsViewModel.kt` L86–L141 and `DesktopHomeViewModel.kt` (duplicated implementation)

```kotlin
val bytes = app.get(imageUrl).body.bytes()  // Full image download — bypasses Coil cache
val img = ImageIO.read(bytes.inputStream())  // Full decode into BufferedImage
```

The full resolution image (potentially 1–3 MB backdrop) is downloaded **again** (outside Coil's cache) and decoded into a `BufferedImage` purely for color extraction. The comment says "300 pixels" but the *entire image is still decoded to memory first*. For a 2MB 1920×1080 JPEG, `ImageIO.read` allocates ~8MB of uncompressed pixel data before sampling begins.

**Severity:** Major — redundant network + redundant allocation of multi-MB buffers per detail page open.

**Fix:**
1. Use Coil's already-cached bytes via its `DiskCache` instead of re-downloading.
2. Or use `ImageIO`'s `ImageReadParam.setSourceSubsampling()` to decode at reduced resolution.
3. `sampleDominantColor` is identical in both `DetailsViewModel` and `DesktopHomeViewModel`. Extract to a shared `ImageColorExtractor` utility.

**Fix now or after alpha?** After alpha (functional), but the duplicate code should be extracted now.

---

### ISSUE-P04 · `drawBehind` in `DetailsScreen` Allocates Radial Gradients on Every Draw Frame (✅ FIXED)

**Evidence:** `DetailsScreen.kt` L165–L189

```kotlin
.drawBehind {
    drawRect(animatedHeroColor.copy(alpha = 0.28f))
    val radius1 = size.width.coerceAtLeast(size.height) * 1.5f
    drawRect(brush = Brush.radialGradient(
        colors = listOf(animatedHeroColor.copy(alpha = 0.22f), Color.Transparent),
        ...
    ))
    // another radialGradient
}
```

Every call to `drawBehind` creates new `Color` objects (via `.copy(alpha = ...)`) and new `Brush.radialGradient(...)` instances. These are heap-allocated on every draw frame. When `animatedHeroColor` is animating (800ms tween), this is ~48 allocations per second just for the hero background.

**Severity:** Minor (GC pressure, not a stall)

**Fix:** Hoist the brush creation outside `drawBehind` using `remember(animatedHeroColor)`:
```kotlin
val heroBrush1 = remember(animatedHeroColor) { Brush.radialGradient(...) }
val heroBrush2 = remember(animatedHeroColor) { Brush.radialGradient(...) }
```

**Fix now or after alpha?** After alpha.

---

### ISSUE-P05 · `AnimatedContent` Transition Spec Reads Observable State Directly

**Evidence:** `ComposeNavigation.kt` L144–L225

The `transitionSpec` lambda reads `navController.lastAction`, which is a `mutableStateOf` field. Reading observable state inside a `transitionSpec` lambda causes Compose to re-run the spec on every `lastAction` change, including during the animation itself, which can re-trigger the transition while it's already running.

**Severity:** Minor

**Fix:** Read `lastAction` before the `AnimatedContent` call (in the parent scope) and capture it as a stable `val`, or use `rememberUpdatedState`.

**Fix now or after alpha?** After alpha.

---

### ISSUE-P06 · Ambient Glow `drawBehind` Runs Uncached on Every Frame (✅ FIXED)

**Evidence:** `DesktopAppShell.kt` L85–L120

The ambient glow draws multiple `Brush.radialGradient` calls per frame with `ambientGlowPositions.forEach`. When positions are a set of 4 corners, that's 4 radial gradient shader invocations per draw frame, every frame, even when nothing is changing.

**Severity:** Minor (GPU/Skia pipeline cost, but constant)

**Fix:** Wrap in a `drawWithCache` instead of `drawBehind` to cache the shaders between frames when inputs haven't changed.

**Fix now or after alpha?** After alpha.

---

### ISSUE-P07 · `PosterCard` Calls `AppearanceConfig.gridScale.collectAsState()` Per Card

**Evidence:** `PosterCards.kt` L50

```kotlin
val gridScale by AppearanceConfig.gridScale.collectAsState()
```

Every `PosterCard` in every grid independently subscribes to this `StateFlow`. With 50+ cards in a typical home grid, that's 50+ active `collectAsState` subscriptions on a single global setting. Changing `gridScale` triggers 50 recompositions.

**Severity:** Minor (Compose is efficient at this, but it's wasted work)

**Fix:** Hoist `gridScale` collection to the parent `LazyColumn`/`LazyRow` and pass it down as a plain parameter.

**Fix now or after alpha?** After alpha.

---

### ISSUE-P08 · `LazyColumn` in `DetailsContent` Uses No Stable Keys

**Evidence:** `DetailsScreen.kt` L437–L748

Most `item { }` blocks have no key specified. `LazyColumn` items without keys use their index as an implicit key, meaning any change in item count above a given item causes all items below it to be re-composed. The screenshots `LazyRow` does use `key = { it }` (correct), but the outer `LazyColumn` items do not.

**Severity:** Minor

**Fix:** Add stable keys to `LazyColumn` items: `item(key = "hero") { }`, `item(key = "episodes") { }`, etc.

**Fix now or after alpha?** After alpha.

---

## Part 3 — Maintainability Review

### ISSUE-M01 · `ComposeNativeWebPlayer.kt` is 1,396 Lines of Acknowledged Copy-Paste

**Evidence:** `ComposeNativeWebPlayer.kt` L1–L5

```kotlin
// TODO: Yes, I know this file shares like 40KB of JNA event loops, keyboard hacks,
// and copy-pasted canvas code with BaseMpvPlayer.kt.
// It is an absolute copy-paste crime scene.
```

`ComposeNativeWebPlayer.kt` (82KB, 1,396 lines) and `BaseMpvPlayer.kt` (51KB, 908 lines) share duplicated JNA event-loop logic, keyboard handling, and canvas management. This means any bug fix in one file must manually be replicated in the other. Any new feature (e.g. a new MPV property observer) must be added twice.

**Severity:** Major (maintainability blocker, will cause divergence bugs)

**Fix:** Extract common logic into a shared `MpvEventLoop` class that both composables delegate to.

**Fix now or after alpha?** After alpha. The comment is accurate — touching this incorrectly will break JNI. But plan the refactor now.

---

### ISSUE-M02 · `Main.kt` TODO Is Acknowledged and Not Tracked

**Evidence:** `Main.kt` L5

```kotlin
// TODO: Yeah I know this is a big ball of mud, but let's refactor this later.
```

`Main.kt` (412 lines) handles: crash handler setup, JNA Kernel32 interop, window initialisation, Coil image loader factory, fullscreen toggle logic, AWT component listener for size tracking, DWM dark mode, and Compose application scaffolding. This is 7 distinct responsibilities.

**Severity:** Minor (known, tolerated)

**Fix:** Extract to:
- `CrashHandler.kt`
- `CoilSetup.kt`
- `WindowsWindowManager.kt` (fullscreen + DWM)
- `AppWindowState.kt` (size tracking)

**Fix now or after alpha?** After alpha — but create GitHub issues so it doesn't get forgotten.

---

### ISSUE-M03 · Dock Position Logic Uses String Literals Throughout

**Evidence:** `DesktopAppShell.kt` L123–L133, L173–L177, L216–L219

```kotlin
when (dockPosition) {
    "Right"  -> ...
    "Bottom" -> ...
    "Top"    -> ...
    else     -> ...  // implicitly "Left"
}
```

The string `"Right"`, `"Bottom"`, `"Top"`, `"Left"` appears at least 8 times across `DesktopAppShell.kt` and presumably in `AppearanceConfig`. Changing the serialised name of a position requires a grep-and-replace across multiple files, and a typo silently falls through to the `else` branch without any compiler warning.

**Severity:** Minor

**Fix:** Use a sealed class or enum:
```kotlin
enum class DockPosition { Left, Right, Top, Bottom }
```

**Fix now or after alpha?** After alpha.

---

### ISSUE-M04 · `DetailsScreen.kt` at 1,021 Lines With Deeply Nested Composable Lambdas (✅ FIXED)

**Evidence:** `DetailsScreen.kt` — 1,021 lines.

`DetailsContent` contains inline Composable lambdas (`heroAction`) that themselves contain full business logic (target episode selection, button label computation, play callback). These anonymous `@Composable` lambdas are defined inside a parent composable, making them invisible to the Compose tooling for recomposition analysis, preview, and testing.

**Severity:** Minor (functional), but the `heroAction` block should be a named private composable function.

**Fix:** Extract `heroAction` to `fun HeroActionRow(data, provider, latestHistory, onPlay)` at file scope.

**Fix now or after alpha?** After alpha.

---

### ISSUE-M05 · `PluginSettingsDialog.kt` at 619 Lines for a Single Dialog (✅ FIXED)

**Evidence:** `PluginSettingsDialog.kt` — 619 lines, 46KB.

A dialog that has grown to 619 lines is a clear signal it is doing too much. Setting category rendering, value persistence, slider/toggle/text-field builders, and dialog scaffolding should each be separate components.

**Severity:** Minor

**Fix:** Split into `PluginSettingItem.kt` (per-item renderers) + `PluginSettingsDialog.kt` (orchestration only).

**Fix now or after alpha?** After alpha.

---

### ISSUE-M06 · No Tests Anywhere in the Reviewed Surface Area

**Evidence:** The `test` directory under `desktop-app/src/test` exists but was not populated with any test files covering the reviewed layer.

**Why it's a problem:** `TmdbRateLimiter`, `GlobalDetailsCache`, `DetailsViewModel`, `NavController`, and `DataStore` all have zero test coverage. The race condition in A02 and the mutable model bug in A03 would be trivially caught by unit tests.

**Severity:** Major (for a production trajectory)

**Fix:** At minimum:
- Unit test `NavController` (navigate, goBack, goForward, stack invariants).
- Unit test `DataStore` (set/get round-trip, concurrent access).
- Unit test `TmdbRateLimiter` (prove the race via concurrent coroutine invocation).

**Fix now or after alpha?** After alpha for breadth; before beta for correctness-critical paths.

---

## Issue Classification by Origin

Before using the summary table, understand which category each issue falls into. **Only Category 1 issues are safe to fix without auditing the plugin API boundary.**

### Category 1 — Pure Compose / Desktop Architecture
These have no Android-port justification. They are desktop Compose lifecycle mistakes or concurrency bugs that exist purely in the desktop layer and are safe to fix without touching anything a plugin would call.

| ID | Issue |
|----|-------|
| ✅ A02 | `TmdbRateLimiter` — `@Volatile` does not make compound read-write atomic |
| ✅ A03 | Mutable `LoadResponse` written on IO thread — data race |
| ✅ A04 | `DetailsViewModel` borrows `rememberCoroutineScope` — leaked scope |
| ✅ A10 | `vlcPlayer` file-level singleton — native resource never disposed |
| A11 | `SearchUiState` Compose data class mutability | [✅] |
| A12 | `FullscreenController` Compose data class mutability | [✅] |
| ✅ A15 | Auto-update `LaunchedEffect` loop inside composable — runs 4× simultaneously |
| P04 | `drawBehind` allocates `Brush.radialGradient` on every draw frame |
| P05 | `transitionSpec` lambda captures observable state — can re-fire mid-animation |
| P07 | `collectAsState` called per `PosterCard` for a global setting |
| P08 | `LazyColumn` items have no stable `key =` |

### Category 2 — Android-Port Inherited Patterns (Require Audit Before Fixing)
These issues are real but their root cause comes from porting Android patterns. Fixes must be verified not to break the plugin API surface or the `android-stubs` compatibility layer.

| ID | Issue | Risk if Fixed Incorrectly |
|----|-------|---------------------------|
| A01 | `GlobalDetailsCache` God Object | Low — internal refactor only | [✅] |
| A06 | `Screen.Details` nav backstack leaks | Low — memory optimize only | [✅] | **High** — incorrect fix breaks plugin navigation |
| ✅ A08 | `DesktopRepositoryManager` bypasses proxy | Low — OkHttp swap only |
| ✅ A13 | `DataStore.save()` synchronous write | Low — in-memory cache unchanged, only disk flush deferred |
| P01 | `getAllWatchHistory()` full scan on recomposition | Low — move to ViewModel |
| P03 | Full image re-download for color extraction | Low — Coil cache swap |

### Category 3 — Retracted (Not Issues)
Initially flagged but incorrect given the Android-port context.

| ID | Issue | Why Retracted |
|----|-------|---------------|
| **A14** | `DataStore` Android extension functions | These are **intentional plugin API surface**, not dead code. The `android-stubs` module exists precisely so plugins compiled against the Android SDK can call these. Do not remove or move them. |

---

## Summary Table

| ID  | Area                                                           | Severity     | Fix When     | Category |
|-----|----------------------------------------------------------------|--------------|--------------|----------|
| A01 | `GlobalDetailsCache` God Object                                | Major        | After alpha  | 2        | [✅] |
| ✅ A02 | `TmdbRateLimiter` race condition                               | **Major**    | **Now**      | **1**    |
| ✅ A03 | Mutable `LoadResponse` on IO thread                            | **Critical** | **Now**      | **1**    |
| ✅ A04 | `DetailsViewModel` scope leak                                  | **Major**    | **Now**      | **1**    |
| A05 | `DesktopHomeViewModel` scope never cancelled                   | Minor        | After alpha  | 1        |
| A06 | `Screen.Details` holds live `MainAPI` reference                | Major        | After alpha  | 2        | [✅] |
| A07 | `NavController` non-thread-safe lists                          | Minor        | After alpha  | 1        |
| ✅ A08 | `DesktopRepositoryManager` own OkHttpClients (bypasses proxy)  | **Major**    | **Now**      | 2        |
| ❌ A09 | Hardcoded API key in source                                    | **Major**    | **Now**      | 1        |
| ✅ A10 | `vlcPlayer` file-level singleton, never disposed               | **Major**    | **Now**      | **1**    |
| A11 | `SearchUiState` as `data class` with `MutableState`            | Minor        | After alpha  | 1        | [✅] |
| A12 | `FullscreenController` as `data class` with `MutableState`     | Minor        | After alpha  | 1        | [✅] |
| ✅ A13 | `DataStore.save()` synchronous full-write per key              | **Major**    | **Now**      | 2        |
| A14 | ~~Android extension functions in `DataStore`~~                 | ~~Minor~~    | **RETRACTED**| **3**    |
| ✅ A15 | Auto-update loop inside composable (runs 4×)                   | **Major**    | **Now**      | **1**    |
| A16 | `GlobalDetailsCache.cache` is public mutable                   | Minor        | After alpha  | 1        | [✅] |
| ✅ P01 | `getAllWatchHistory()` full scan on recomposition               | Major        | After alpha  | 2        |
| P02 | Duplicate `historyUpdatesVal` subscription                     | Minor        | After alpha  | 1        |
| P03 | Full image re-download + decode for color extraction           | Major        | After alpha  | 2        |
| ✅ P04 | `drawBehind` allocates gradients every frame                   | Minor        | After alpha  | **1**    |
| P05 | `transitionSpec` reads observable state                        | Minor        | After alpha  | **1**    |
| ✅ P06 | Ambient glow redraws uncached every frame                      | Minor        | After alpha  | 1        |
| P07 | `gridScale` subscribed per poster card                         | Minor        | After alpha  | **1**    |
| P08 | `LazyColumn` items missing stable keys                         | Minor        | After alpha  | **1**    |
| M01 | `ComposeNativeWebPlayer` 1396-line copy-paste                  | Major        | After alpha  | 1        |
| M02 | `Main.kt` acknowledged ball-of-mud                             | Minor        | After alpha  | 1        |
| M03 | Dock position as magic strings                                 | Minor        | After alpha  | 1        |
| ✅ M04 | `DetailsScreen` 1021-line, nested lambdas                      | Minor        | After alpha  | 1        |
| ✅ M05 | `PluginSettingsDialog` 619-line monolith                       | Minor        | After alpha  | 1        |
| M06 | Zero unit tests                                                | Major        | Before beta  | 1        |

---

## Fix-Now Priority Order

1. ~~**A03** — Data race on mutable model (correctness, undefined behavior)~~ ✅
2. ~~**A02** — Rate limiter race condition (operational, TMDB 429 cascades)~~ ✅
3. ~~**A09** — Hardcoded API key in source (operational + ToS violation)~~ ❌ *(Reverted per user request)*
4. ~~**A10** — VLC player never disposed (guaranteed native resource leak)~~ ✅
5. ~~**A08** — Proxy bypass in repository manager (privacy regression)~~ ✅
6. ~~**A13** — DataStore synchronous file write on UI thread (UI jank)~~ ✅
7. ~~**A15** — Auto-update loop ×4 (redundant network, wasted resources)~~ ✅
8. ~~**A04** — DetailsViewModel scope leak (ghost network requests)~~ ✅
