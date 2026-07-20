# Phase 4: KMP Desktop MVI (Model-View-Intent) Master Refactor Roadmap

## 1. Executive Summary & Architectural Motivation
The CloudStream Desktop Client (`desktop-app`) currently sits on top of a rock-solid, thread-safe, memory-guarded foundation built during Phase 1–3 (`ConcurrentHashMap` data layer, `DesktopPlayerShield` native isolation, Quad HD high-res limits, and resilient IPC bridges).

However, our deep scan of the UI and ViewModel layer revealed significant structural debt inherited from the legacy Android codebase:
1. **Zero ViewModel Inheritance or Lifecycle Ownership**: All 5 "ViewModels" (`DesktopHomeViewModel`, `DetailsViewModel`, `ExtensionsViewModel`, `EmbeddedPlayerViewModel`, `LinksViewModel`) are plain Kotlin classes (`class LinksViewModel { ... }`). They create their own ad-hoc `CoroutineScope` instances and rely on manual `dispose()` calls scattered across Compose UI (`DisposableEffect`) blocks. `DesktopHomeViewModel` has **no `dispose()` method at all**, leaving its `CoroutineScope(Dispatchers.IO)` running indefinitely in memory.
2. **State Explosion & Spaghetti Flows**: Instead of exposing a single immutable UI state (`UiState`), ViewModels expose between **4 and 13 separate `MutableStateFlow` streams**. This forces Compose UI to collect up to 13 distinct flows simultaneously, triggering recomposition storms and allowing impossible/contradictory UI states (`isLoading = true` while showing an old error or stale list).
3. **Frankenstein UI-State/Loose-Variable Hybrids**: In `DetailsViewModel.kt`, an attempt was made to create a `DetailsUiState` object, but 6 loose `MutableStateFlow` variables (`_response`, `_isLoading`, `_fetchFailed`, etc.) were tacked directly underneath it.
4. **Ad-Hoc Direct Repository/DataStore Access in UI**: Screens like `LibraryScreen.kt` collect repository flows directly inside Compose without a ViewModel layer.

### The MVI Solution (Unidirectional Data Flow)
To transform this into a professional, production-grade KMP desktop application, we are systematically migrating every screen from ad-hoc MVVM to **MVI (Model-View-Intent)**:
- **`contract/State.kt` (`UiState`)**: Exactly one immutable data class per screen. Impossible states cannot exist.
- **`contract/Event.kt` (`UiEvent`)**: Explicit user actions (`OnItemClicked`, `OnSearch`, `OnRetry`). UI components never call public ViewModel mutation methods directly.
- **`contract/Effect.kt` (`UiEffect`)**: One-shot side effects (toasts, navigation, dialogs).
- **`BaseMviViewModel<State, Event, Effect>`**: Standardized lifecycle management, structured coroutine scopes, and atomic `StateFlow.update` state reduction.

---

## 2. Baseline Exhaustive UI & ViewModel Inventory (Deep Scan Results)

| Component / Screen | Current ViewModel / State Management | Streams / Complexity | Target MVI Architecture |
| :--- | :--- | :--- | :--- |
| **`BaseMviViewModel`** | *Does not exist (`0` Base Classes)* | Manual `CoroutineScope` in every class | Create `ui/base/BaseMviViewModel.kt` owning lifecycle & state reduction |
| **`LinksScreen.kt`** | `LinksViewModel` (Plain Class, manual `dispose()`) | 4 separate flows (`_links`, `_subtitles`, `_statusText`, `_isScraping`) | `LinksUiState`, `LinksUiEvent`, canonical `LinksViewModel` |
| **`ExtensionsScreen.kt`** | `ExtensionsViewModel` (Plain Class, manual `dispose()`) | 7 internal flows + 3 external repo flows (`_isFetching`, `_plugins`, etc.) | `ExtensionsUiState`, `ExtensionsUiEvent`, canonical `ExtensionsViewModel` |
| **`DetailsScreen.kt`** | `DetailsViewModel` (Plain Class, manual `dispose()`) | 7 flows (Hybrid `_uiState` + 6 loose fields `_response`, `_isLoading`) | `DetailsUiState`, `DetailsUiEvent`, canonical `DetailsViewModel` |
| **`HomeScreen.kt`** | `DesktopHomeViewModel` (Plain Class, **NO `dispose()` method**) | **13 separate flows** (`_providers`, `_searchResultsGrouped`, `_heroMetaMap`, etc.) | `HomeUiState`, `HomeUiEvent`, canonical `DesktopHomeViewModel` |
| **`EmbeddedVideoPlayer.kt`** | `EmbeddedPlayerViewModel` (Plain Class, manual `dispose()`) | 7 separate flows (`_launchData`, `_isLoadingNextEpisode`, etc.) | `PlayerUiState`, `PlayerUiEvent`, canonical `EmbeddedPlayerViewModel` |
| **`LibraryScreen.kt`** | *No ViewModel (`ComposeLibraryScreen`)* | Direct `BookmarksRepository.bookmarksFlow` collection + local `remember` | `LibraryUiState`, `LibraryUiEvent`, canonical `LibraryViewModel` |
| **`CategoryGridScreen.kt`** | Pure stateless composable | Collects `AppearanceConfig.gridScale` | Keep stateless / pass display properties cleanly |
| **`ComposeSettingsScreen.kt`** | Uses `AppearanceConfig` / `DesktopDataStore` | Direct preference updates | Keep simple settings controller or wrap cleanly |

---

## 3. Mandatory Post-Build Cleanup Protocol (Canonical Clean Naming)

> [!IMPORTANT]
> **No `Mvi` in final screen class names or filenames!** Putting architectural pattern names like `Mvi` inside concrete screen classes (`LinksMviViewModel`, `ExtensionsMviViewModel`) is a transitional anti-pattern. Every completed screen must use clean, professional canonical naming (`LinksViewModel`, `ExtensionsViewModel`, `DetailsViewModel`, etc.).

### Protocol at the end of each screen migration phase:
1. **Transition Wiring & Local Build**: Build and verify the screen using the contract (`UiState`, `UiEvent`, `UiEffect`) and ensure zero regressions.
2. **Immediate Post-Build Cleanup (NEVER SKIP)**:
   - **Delete** the obsolete/dead old `*ViewModel.kt` file.
   - **Rename** the new implementation back to canonical clean naming (`*ViewModel.kt` with class name `*ViewModel`).
   - **Update UI Screen** to instantiate canonical `*ViewModel()`.
3. **Final Local Verification**: Run `./gradlew :desktop-app:compileKotlin` to verify the canonical cleanup compiles perfectly before checking off the phase.

---

## 4. Screen-By-Screen Migration Roadmap & Status Checklist

### Phase 4.1: Base MVI Core Infrastructure
- [x] **4.1.1** Create `ui/base/BaseMviViewModel.kt` (`State`, `Event`, `Effect` generic contract, atomic `update` reducer, and `dispose()` clean-up).
- [x] **4.1.2** Verify project compiles locally (`./gradlew :desktop-app:compileKotlin`).

### Phase 4.2: Small & Self-Contained Screen Migrations
- [x] **4.2.1 `LinksScreen` & `LinksViewModel` Migration**
  - Create `ui/screens/contract/LinksState.kt`, `LinksEvent.kt`, `LinksEffect.kt`.
  - Migrate `LinksViewModel` -> `LinksMviViewModel` inheriting `BaseMviViewModel`.
  - Update `LinksScreen.kt` to collect single `uiState` and emit `onEvent`.
  - Verify local compilation & zero behavior regression.
- [x] **4.2.2 `ExtensionsScreen` & `ExtensionsViewModel` Migration**
  - Create `ui/screens/extensions/contract/` (`State`, `Event`, `Effect`).
  - Migrate `ExtensionsViewModel` -> `ExtensionsMviViewModel`.
  - Update `ExtensionsScreen.kt`, `BrowseTab.kt`, `InstalledTab.kt`, and `RepositoriesTab.kt` wiring.
  - Verify local compilation (`./gradlew :desktop-app:compileKotlin`).

### Phase 4.3: Medium Core Navigation Screen Migrations
- [x] **4.3.1 `DetailsScreen` & `DetailsViewModel` Migration**
  - Create `ui/screens/details/contract/` (`DetailsUiState`, `DetailsUiEvent`, `DetailsUiEffect`).
  - Eliminate the 6 loose Frankenstein variables (`_response`, `_fakeData`, `_fetchFailed`, etc.) into `DetailsUiState`.
  - Update `DetailsScreen.kt`, `DetailsHeader.kt`, and `DetailsEpisodeList.kt` wiring.
  - Verify local compilation (`./gradlew :desktop-app:compileKotlin`).
- [x] **4.3.2 `HomeScreen` & `DesktopHomeViewModel` Migration**
  - Create `ui/screens/home/contract/` (`HomeUiState`, `HomeUiEvent`, `HomeUiEffect`).
  - Eliminate the 13-flow spaghetti class (`_providers`, `_searchResultsGrouped`, etc.) into single `HomeUiState`.
  - Ensure formal lifecycle (`dispose()` / scope cancellation) is introduced.
  - Update `HomeScreen.kt` and `HomeHeroCarousel.kt` wiring.
  - Verify local compilation (`./gradlew :desktop-app:compileKotlin`).

### Phase 4.4: Complex Player & Subcompose Screen Migrations
- [x] **4.4.1 `EmbeddedVideoPlayer` & `EmbeddedPlayerViewModel` Migration**
  - Create `ui/screens/player/contract/` (`PlayerUiState`, `PlayerUiEvent`, `PlayerUiEffect`).
  - Migrate `EmbeddedPlayerViewModel` -> `PlayerMviViewModel`.
  - Update `EmbeddedVideoPlayer.kt`, `EpisodesOverlay.kt`, `PlayerLoadingOverlay.kt`, and `PausedDetailsOverlay.kt` wiring.
  - Verify local compilation (`./gradlew :desktop-app:compileKotlin`).
- [x] **4.4.2 `LibraryScreen` MVI Formalization**
  - Create `ui/screens/library/contract/` (`LibraryUiState`, `LibraryUiEvent`).
  - Create `LibraryMviViewModel` to encapsulate `BookmarksRepository` and `DesktopWatchType` filtering instead of raw UI flow collection.
  - Update `LibraryScreen.kt` wiring and verify local compilation.

### Phase 4.5: Final Verification & Branch Synchronization
- [x] **4.5.1 Full Build & Verification on `refactor/mvi-architecture`**
  - Execute clean compile & check (`./gradlew :desktop-app:compileKotlin`).
- [ ] **4.5.2 Local Branch Sync Protocol**
  - Fast-forward merge `refactor/mvi-architecture` -> `work` -> `refactor/critical-fixes` -> `refactor/high-risk-fixes`.
  - **Strict Rule Enforcement**: `main` is completely untouched. Zero `git push` executed without pre-push audit and explicit approval.

---

## 4. Architectural Rules & Guardrails
1. **Zero Visual Regressions**: Layout components, Quad HD (`2560x1440`) / Logo (`1600x800`) limits, fonts, colors, and animations remain untouched. Only state collection wiring and action emission change.
2. **One Brick at a Time**: We migrate exactly one screen per step, run verification locally, and confirm status before proceeding to the next.
3. **No Silent Actions**: All commands, file edits, and branch merges are explicitly reviewed and approved in chat before execution.
