# ⚙️ CloudStream Desktop Architecture: Plugin Settings API Catalog

**Document Status:** Final
**Scope:** Architectural analysis of settings API usage across 408 upstream plugins to define the interception layer requirements.

---

## Complete Plugin Settings API Catalog

A total of **428 plugins from 25 repositories** were downloaded and converted. **408 plugins** were successfully processed to JAR with dex2jar and scanned for settings API calls. The following exact APIs must be intercepted:

---

### The Settings API Chain (How Plugins Open & Read Settings)

There are **3 distinct pathways** plugins use to access settings. All 3 funnel down to the same bottom layer:

```
┌─────────────────────────────────────────────────────┐
│  PATHWAY 1: CloudStreamApp.Companion (15 plugins)   │
│  CloudStreamApp.Companion.getKey("key_name")        │
│  CloudStreamApp.Companion.setKey("key_name", value) │
│  CloudStreamApp.Companion.removeKey("key_name")     │
│       ↓ delegates to                                │
│  DataStore.getSharedPrefs(context)                   │
│       ↓ returns                                     │
│  SharedPreferences.getString("key_name", null)      │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  PATHWAY 2: Direct SharedPreferences (104 plugins)  │
│  context.getSharedPreferences("name", MODE_PRIVATE) │
│       ↓ returns                                     │
│  prefs.getString("key_name", defaultValue)          │
│  prefs.getBoolean("key_name", defaultValue)         │
│  prefs.getInt("key_name", defaultValue)             │
│  prefs.edit().putString("key_name", value).apply()  │
│  prefs.edit().putBoolean("key_name", value).apply() │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  PATHWAY 3: SettingsJson (15 plugins)               │
│  MainAPI.getSettingsJson() → SettingsJson object    │
│       ↓ app reads this to build settings UI         │
│       ↓ plugin reads values back via getKey()       │
│  (Ultimately uses same DataStore/SharedPreferences) │
└─────────────────────────────────────────────────────┘
```

---

### Exact API Calls Requiring Interception

#### Layer 1: The Entry Point APIs (what plugins call)

| API Call | Exact JVM Signature | # Plugins |
|----------|-------------------|-----------|
| `Context.getSharedPreferences(String, int)` | `getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;` | **104** |
| `DataStore.getSharedPrefs(Context)` | `getSharedPrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;` | **15** |
| `PreferenceManager.getDefaultSharedPreferences(Context)` | `getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;` | **12** |
| `CloudStreamApp.Companion.getKey(String)` | inline reified — inlines to DataStore.getKey | **15+** |
| `CloudStreamApp.Companion.setKey(String, T)` | inline reified — inlines to DataStore.setKey | **15+** |
| `CloudStreamApp.Companion.removeKey(String)` | delegates to DataStore.removeKey | **few** |
| `CloudStreamApp.Companion.getKey(String, T?)` | with default value | **few** |
| `CloudStreamApp.Companion.getKey(String, String, T?)` | folder + path variant | **few** |
| `CloudStreamApp.Companion.setKey(String, String, T)` | folder + path variant | **few** |
| `MainAPI.getSettingsJson()` | returns SettingsJson? | **15** |

#### Layer 2: The Read APIs (what actually fetches values)

| API Call | Exact JVM Signature | # Plugins |
|----------|-------------------|-----------|
| `SharedPreferences.getString(String, String)` | `getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;` | **~100** |
| `SharedPreferences.getBoolean(String, boolean)` | `getBoolean(Ljava/lang/String;Z)Z` | **~50** |
| `SharedPreferences.getInt(String, int)` | `getInt(Ljava/lang/String;I)I` | **~25** |
| `SharedPreferences.getLong(String, long)` | `getLong(Ljava/lang/String;J)J` | **~8** |
| `SharedPreferences.getFloat(String, float)` | `getFloat(Ljava/lang/String;F)F` | **~2** |
| `SharedPreferences.contains(String)` | `contains(Ljava/lang/String;)Z` | **~5** |
| `SharedPreferences.edit()` | `edit()Landroid/content/SharedPreferences$Editor;` | **~80** |

#### Layer 3: The Write APIs (Editor)

| API Call | Exact JVM Signature | # Plugins |
|----------|-------------------|-----------|
| `Editor.putString(String, String)` | `putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;` | **~70** |
| `Editor.putBoolean(String, boolean)` | `putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;` | **~40** |
| `Editor.putInt(String, int)` | `putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;` | **~20** |
| `Editor.putLong(String, long)` | `putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences$Editor;` | **~5** |
| `Editor.putStringSet(String, Set)` | `putStringSet(Ljava/lang/String;Ljava/util/Set;)Landroid/content/SharedPreferences$Editor;` | **~3** |
| `Editor.remove(String)` | `remove(Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;` | **~10** |
| `Editor.clear()` | `clear()Landroid/content/SharedPreferences$Editor;` | **~3** |
| `Editor.commit()` | `commit()Z` | **~5** |
| `Editor.apply()` | `apply()V` | **~80** |

#### Layer 4: The Settings UI APIs (5 plugins — can be no-op stubs)

| API Call | # Plugins | Example |
|----------|-----------|---------|
| `PreferenceFragmentCompat.onCreatePreferences()` | **5** | YouTube, Anime3rb |
| `PreferenceScreen.addPreference()` | **6** | YouTube, Anime3rb |
| `PreferenceCategory.<init>()` | **5** | YouTube, Anime3rb |
| `EditTextPreference.<init>()` | **3** | YouTube, Anime3rb |
| `SwitchPreferenceCompat.<init>()` | **3** | YouTube, FullMatchShows |
| `ListPreference.<init>()` | **1** | YouTube |
| `SeekBarPreference.<init>()` | **1** | YouTube |
| `MainAPI.getSettingsFragment()` | **0** (in SDK, none override) | — |

---

### Actual Settings Key Names Found in CineStream

CineStream is the most complex plugin. Its settings keys (stored via `getKey`/`setKey` which goes through DataStore → SharedPreferences):

| Key Name | Purpose | Type |
|----------|---------|------|
| `ProviderCineStream` | Whether CineStream provider is enabled | Boolean |
| `ScrapeConcurrency` | Number of parallel scrapers | Int |
| `DownloadEnable` | Whether to include download links | Boolean |
| `provider_order` | JSON array of provider names in order | String (JSON) |
| `seen_providers` | List of providers the user has seen | String (JSON) |
| `stremio_addons` | JSON array of Stremio addon URLs | String (JSON) |
| `showbox_ui_token` | ShowBox/Febbox API token | String |
| `gramcinema_bearer_token` | GramCinema auth token | String |
| `wyzie_subs_api_key` | Wyzie Subs API key | String |
| `new_provider_default_on` | Auto-enable new providers | Boolean |

### Settings Key Names Across All 408 Plugins (Top Impactful Ones)

| Key Name | # Plugins | Category |
|----------|-----------|----------|
| `last_domain` / `last_domain_update` | **59** | Domain cache (kraptor family) |
| `DomainListesi` | **59** | Domain list (Turkish plugins) |
| `CACHE_DURATION` | **59** | Cache TTL |
| `cf_clearance` | **19** | Cloudflare bypass cookie |
| `tmdbApiKey` / `api_key` | **22** | TMDB API key |
| `token` / `usertoken` | **63** | Auth tokens |
| `Cookie` (as pref key) | **74** | HTTP cookies |
| `providerName` / `provider_order` | **62** | Provider config |

---

### Required Interceptor Implementation

**Exactly 2 Java classes** are required to intercept all settings for all 408 plugins:

**1. `android.content.SharedPreferences`** — JSON-backed implementation with all 6 read methods + Editor with all 9 write methods. Every plugin that uses settings calls this.

**2. `com.lagradost.cloudstream3.utils.DataStore`** — Static singleton that:
   - Has `getSharedPrefs(Context)` → returns the same SharedPreferences
   - Has `getKey(path)` → delegates to `getSharedPrefs().getString(path, null)` + JSON deserialize
   - Has `setKey(path, value)` → delegates to `getSharedPrefs().edit().putString(path, json).apply()`
   - Has `removeKey(path)` → delegates to `.edit().remove(path).apply()`
   - Has `getKeys(folder)` → lists keys with a prefix
   - Has `editor(Context)` → returns a DataStore.Editor wrapper

**3. `com.lagradost.cloudstream3.CloudStreamApp.Companion`** — Static methods:
   - `getKey(String path)` → delegates to DataStore
   - `setKey(String path, T value)` → delegates to DataStore
   - `removeKey(String path)` → delegates to DataStore
   - `getKeys(String folder)` → delegates to DataStore

**4. `android.content.Context.getSharedPreferences(String, int)`** → route to per-name JSON files

**5. `androidx.preference.PreferenceManager.getDefaultSharedPreferences(Context)`** → route to `global.json`

**6. `com.lagradost.cloudstream3.SettingsJson`** → simple data class, can be no-op

The PreferenceFragment/UI classes (5 plugins) can be implemented as no-op stubs — they only render the settings dialog, which the desktop port replaces with JSON config files or a CLI command.
All scan data has been saved to the reporting directory:
- `settings_api_complete_results.json` — per-API plugin counts
- `plugin_settings_complete.json` — per-plugin API usage
- `settings_key_names_complete.json` — all discovered settings key names
- `all_plugins.json` — full plugin metadata






---

## 📦 Extended Analysis: Phisher CloudStream Extensions Repository

---

## 📦 Repository Structure Overview

The repo.json points to a `plugins.json` containing **75 plugins**. Each plugin is distributed as **two file types**: `.cs3` (CloudStream 3 plugin package) and optionally `.jar` (JVM bytecode archive).

| Metric | Count |
|--------|-------|
| Total Plugins | 75 |
| Plugins WITH `.jar` files | **42** |
| Plugins WITHOUT `.jar` files | **33** |

---

## 🗂️ CS3 File Structure (ALL 75 plugins have these)

The `.cs3` files are **ZIP archives** containing **Android DEX (Dalvik Executable) bytecode**, designed to run on the **Android runtime (ART/Dalvik)**:

```
┌── manifest.json        ← Plugin metadata (name, version, pluginClassName, requiresResources)
├── classes.dex          ← Dalvik bytecode (DEX format v035) — the actual Android-executable code
├── res/                 ← (optional) Android resources
│   ├── drawable/        ← Icons, vector drawables (XML)
│   └── layout/          ← UI layouts (XML)
└── resources.arsc       ← (optional) Compiled Android resource table
```

- **DEX magic**: `dex\n035\0` — standard Android Dalvik format
- **manifest.json** contains the plugin entry class name, e.g., `"pluginClassName": "com.phisher98.AllMovieLandProviderPlugin"`
- Plugins with `requiresResources: true` (like AnimePahe) include `res/` and `resources.arsc`

---

## 🏗️ JAR File Structure (42 of 75 plugins)

The `.jar` files are also **ZIP archives** but contain **standard JVM `.class` files** (Java/Kotlin bytecode):

```
┌── META-INF/
│   └── <PluginName>.kotlin_module   ← Kotlin compiler metadata (binary format)
├── com/<package>/
│   ├── <Plugin>.class               ← Main plugin class (Kotlin compiled)
│   ├── <Plugin>Plugin.class         ← Plugin entry point (extends CloudstreamPlugin)
│   └── <Plugin>$Inner.class         ← Inner classes, lambdas, closures
└── (NO MANIFEST.MF)                 ← ⚠️ Missing standard JAR manifest
```

### JAR Bytecode Verification:
| Check | Result |
|-------|--------|
| Class file magic | `0xCAFEBABE` ✅ Valid JVM |
| Bytecode version | `52.0` (Java 8 target) ✅ |
| Language | Kotlin (compiled to JVM .class) |
| Has `MANIFEST.MF` | ❌ **None of the JARs have it** |
| Has `Main-Class` | ❌ **Not executable JARs** |
| CloudStream3 dependency | ✅ All reference `com.lagradost.cloudstream3.*` |

---

## 🔍 Are the JAR Files Ready for JVM?

**Partially YES, but with important caveats:**

### ✅ What's JVM-Compatible:
1. **Valid `.class` format** — All class files have the correct `0xCAFEBABE` magic number
2. **Java 8 target** — Bytecode version 52.0, compatible with any Java 8+ JVM
3. **No DEX inside JARs** — The `.jar` files contain standard JVM `.class` files, NOT `.dex` files
4. **Most JARs have no direct Android SDK references** — Plugins like `AllMovieLandProvider` and `Fivemovierulz` don't reference `android.*` packages

### ⚠️ Issues / Not Fully JVM-Ready:
1. **Missing `MANIFEST.MF`** — No standard JAR manifest. These cannot be used as standalone executable JARs or standard library JARs without adding a manifest first.

2. **CloudStream3 dependency** — Every JAR depends on `com.lagradost.cloudstream3.*` (MainAPI, CloudstreamPlugin, ExtractorApi, etc.). These classes are **NOT bundled** inside the JAR. They must be provided at runtime by the CloudStream3 host app. **The JARs will fail to load on a bare JVM without the CloudStream3 classpath.**

3. **Some JARs reference Android SDK** — e.g., `Anichi.jar` references `android.annotation.SuppressLint`. While this is only an annotation (not functional code), it still creates a classpath dependency on the Android SDK.

4. **33 plugins have NO JAR files at all** — Plugins like `AnimePahe`, `Cinemacity`, `HDhub4u`, `Jellyfin`, etc. only exist as `.cs3` (DEX-only). Their JARs simply don't exist on the server (HTTP 404).

---

## 📊 Summary Table

| Aspect | CS3 Files | JAR Files |
|--------|-----------|-----------|
| Format | ZIP containing DEX | ZIP containing .class |
| Runtime Target | Android ART/Dalvik | JVM (Java 8+) |
| Available For | All 75 plugins | Only 42 plugins |
| Executable Standalone | No (needs CloudStream app) | No (missing MANIFEST.MF + deps) |
| Contains DEX | ✅ `classes.dex` | ❌ |
| Contains .class | ❌ | ✅ JVM bytecode |
| Android Resources | Sometimes (`res/`, `.arsc`) | ❌ |
| Primary Use | Loaded by CloudStream Android app | Likely used for dev/reflection/dex2jar |

### Bottom Line:
The **JAR files contain valid JVM `.class` bytecode** (Java 8 / Kotlin compiled), but they are **NOT ready for standalone JVM execution** because they:
- Lack `MANIFEST.MF`
- Depend on the CloudStream3 runtime library (`com.lagradost.cloudstream3.*`)
- Some reference Android SDK annotations
- Are essentially **plugin libraries** meant to be loaded by the CloudStream3 Android app's classloader, not run independently