# 🔄 Android to Desktop: Core Migration Log & Synchronization Strategy

**Document Status:** Active
**Scope:** Defines the migration boundaries between the official Android CloudStream 3 codebase and the CloudStream Desktop Client, detailing how to sync upstream updates.

---

## 🏗️ Project Module Boundaries

To prevent maintenance hell and massive merge conflicts, the CloudStream Desktop port explicitly isolates the UI from the Core Engine. 

### Copied/Upstream Modules (Core Engine)
The following modules were **cloned directly** from the official Android repository. They contain the heavy-lifting logic (networking, parsing, data models) and **must be kept in sync with upstream**.

- **`common/`**: Contains core data models (`SearchResponse`, `LoadResponse`), basic utilities, and abstract definitions.
- **`library/`**: The heart of the application. Contains the plugin SDK, `NiceHttp` (network interceptors), `CloudflareKiller`, the plugin parser, and the core extractor engines.

*Note: Changes inside these modules should be strictly limited. Any desktop-specific overrides are handled via injection or stubbing from the UI layer to prevent merge conflicts during upstream syncs.*

### Desktop-Specific Modules (Do Not Sync)
The following modules are **100% custom to the Desktop port** and replace their Android equivalents.

- **`desktop-app/`**: Replaces the Android `app/` module. Contains all Compose for Desktop UI logic, desktop-specific storage implementations, and the desktop `Main.kt` entry point.
- **`android-stubs/`**: A custom JVM module containing mock implementations (stubs) for Android-specific APIs (like `android.content.Context` or `android.util.Log`) required by the plugins, bridging them to standard JVM alternatives (like `SLF4J`).
- **`plugin-runtime/` & `plugin-sandbox/`**: Custom desktop JVM classloaders and sandbox rules to safely execute Android `.dex`/`.jar` plugins on a Windows/Linux/Mac JVM.

---

## 🔄 Keeping Core Synchronized

When the official Android team pushes critical updates (such as fixing a broken Cloudflare interceptor or updating the DoH implementation), we do **not** manually copy-paste the code. 

We utilize a Git **Upstream Merge** strategy.

### 1. Setting up the Upstream Remote (One-Time Setup)
```bash
git remote add upstream https://github.com/recloudstream/cloudstream.git
```

### 2. Pulling Core Updates
When an update drops, fetch the upstream commits and selectively merge them:
```bash
# Fetch the latest official code
git fetch upstream

# Merge the official master branch into our desktop master branch
git merge upstream/master
```

### 3. Handling Conflicts
Because we deleted their `app/` module and created `desktop-app/`, Git will cleanly merge updates to `library/` and `common/` while ignoring UI differences. If merge conflicts arise in `library/` or `common/`, it means we made custom desktop modifications to those files. Resolve conflicts by keeping our desktop overrides where necessary (e.g., our custom Player hooks), while accepting their network/parsing fixes.

---

## 🚧 Future Migration Goals (KMP)

The long-term goal of this project is to eliminate the need for the Upstream Merge strategy entirely. Once the Desktop client reaches maturity, we aim to upstream our JVM compatibility fixes to the official repository, allowing `common` and `library` to be compiled as a standalone **Kotlin Multiplatform (KMP)** dependency.

Once published to Maven, the desktop port will simply depend on:
`implementation("com.lagradost.cloudstream:core:X.Y.Z")`
