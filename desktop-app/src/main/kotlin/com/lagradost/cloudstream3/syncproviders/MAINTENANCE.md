# 🚨 ARCHITECTURAL HARDENING: THE "SINKING SHIPS" REPORT

**Status:** ASCENDED MADNESS (Critical Risk for Future-Proofing)  
**Objective:** Replace legacy/deprecated "Necromancy" with modern, high-tier engineering.

---

## 1. THE SECURITY APOCALYPSE: `SecurityManager`
*   **Location:** `plugin-runtime/src/main/kotlin/com/lagradost/runtime/security/PluginSecurityManager.kt`
*   **The Rot:** Oracle officially deprecated the `SecurityManager` for removal in **JEP 411** (Java 17). It is currently being held together by the `-Djava.security.manager=allow` flag. In future JVM versions (Java 24+), this code will simply cease to exist.
*   **The Problem:** Once removed, your "Police State" sandbox disappears, and plugins gain full access to the user's filesystem.
*   **The Modern Fix:** **Multi-Process Isolation.** 
    *   Spawn the Plugin Engine in a separate, low-privilege OS process.
    *   Use OS-native sandboxing (Windows AppContainer / Linux Bubblewrap).
    *   Communicate via IPC (Inter-Process Communication).

---

## 2. THE BYTECODE ROT: `dex2jar` & `JarPatcher`
*   **Location:** `plugin-runtime/src/main/kotlin/com/lagradost/runtime/loader/ExtensionLoader.kt` & `JarPatcher.kt`
*   **The Rot:** `dex2jar` is essentially abandonware. It does not understand modern Kotlin metadata or K2 compiler optimizations. The `JarPatcher` is a "band-aid on a gunshot wound"—manually fixing binary strings because the transpiler is broken.
*   **The Problem:** A single update to the Kotlin compiler naming convention will brick the `JarPatcher` and all new plugins.
*   **The Modern Fix:** **Dexlib2 + ASM Transformation.**
    *   Use `dexlib2` (the gold standard for DEX reading) to ingest plugins.
    *   Use `ASM` to perform **Surgical Bytecode Transformation** during loading.
    *   Rename mangled Kotlin methods (`_` to `-`) and redirect `Log` calls at the instruction level, not via binary search-and-replace.

---

## 3. THE "SMUGGLER'S" PROXY: `com.sun.net.httpserver`
*   **Location:** `player-abstraction/src/main/kotlin/com/lagradost/player/impl/proxy/LocalStreamProxy.kt`
*   **The Rot:** Using the internal JDK `HttpServer` from Java 6. It is synchronous, clunky, and has zero protection against local exploits. It’s a "utility" server being forced to handle high-bandwidth 4K video streams.
*   **The Problem:** High latency, memory overhead, and potential for "Socket Exhaustion" during heavy HLS segment rewriting.
*   **The Modern Fix:** **Netty or Ktor-Server.**
    *   Use a non-blocking, asynchronous engine.
    *   Implement **OkHttp Interceptors** or **MPV Protocol Handlers** to inject headers directly into the player memory, bypassing the need for a local web server entirely.

---

## 4. THE MANUAL LABOR: `android-stubs`
*   **Location:** `android-stubs/src/main/java/android/*`
*   **The Rot:** Every time a plugin uses a new Android class, a human has to manually write a `.java` file. This is "Wizard Toil." It doesn't scale.
*   **The Problem:** If a popular plugin starts using `android.media.*` or `android.graphics.drawable.VectorDrawable`, the app crashes until you manually stub it.
*   **The Modern Fix:** **Dynamic Reflection Proxies.**
    *   Create a **"Universal Ghost Stub"** using Java's `Proxy` class.
    *   When a plugin calls a missing class, the engine generates a fake version in memory on-the-fly. 
    *   Log the call so you only implement the stubs that actually need logic (like Storage or Networking).

---

## 5. THE CLASSLOADER LEAK: `URLClassLoader`
*   **Location:** `ExtensionLoader.kt`
*   **The Rot:** `URLClassLoader` is "Old Guard" Java. It is notoriously bad at "unloading" code. Every time you reload a plugin, a small piece of memory stays behind forever.
*   **The Problem:** Users who keep the app open for days and refresh their extensions will eventually hit an `OutOfMemoryError`.
*   **The Modern Fix:** **Java Modules (JPMS) or Custom Bytecode Injection.**
    *   Define strict module boundaries.
    *   Ensure each plugin lives in a truly disposable `Layer`.

---

## SUMMARY OF NECROMANCY
| Component | The "Zombie" Code | The "Modern" Solution |
| :--- | :--- | :--- |
| **Security** | `SecurityManager` | **OS-Level Process Jail** |
| **Transpilation** | `dex2jar` + `JarPatcher` | **Dexlib2 + ASM Transformation** |
| **Proxy** | `com.sun.net.httpserver` | **Ktor / Custom Protocol Handler** |
| **Mocking** | Manual `.java` stubs | **Dynamic Runtime Proxies** |

---

## 6. THE LEGACY JSON BOMB: `org.json`
*   **Location:** `desktop-app/build.gradle.kts` & `android-stubs`
*   **The Rot:** The `org.json` library is a relic from 2002. It is bundled with Android, which is the only reason it's here. It is slow, mutation-heavy, and non-idiomatic in Kotlin.
*   **The Problem:** Modern plugins often mix this with Jackson, leading to "JSON Identity Crisis" bugs where data is parsed twice or incorrectly.
*   **The Modern Fix:** **KotlinX Serialization.**
    *   It's compiler-safe, 10x faster, and designed for multiplatform use.

---

## 7. THE "LEAKY" CONCURRENCY: `GlobalScope`
*   **Location:** `desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/Main.kt`
*   **The Rot:** `GlobalScope` is a "delicate" API in Kotlin Coroutines. It creates "orphan" tasks that aren't tied to any lifecycle. It is effectively "unmanaged concurrency."
*   **The Problem:** If a startup task (like a repository sync) hangs, it will stay in memory forever, even if the app UI crashes or restarts. It’s a "Memory/Thread Leak" waiting to happen.
*   **The Modern Fix:** **Structured Concurrency.**
    *   Use a dedicated `CoroutineScope` tied to the `application` lifecycle or the main Window.

---

## 8. THE BINARY SABOTAGE: `Xetadata` Patching
*   **Location:** `plugin-runtime/src/main/kotlin/com/lagradost/runtime/loader/JarPatcher.kt`
*   **The Rot:** The dev is manually renaming the `kotlin/Metadata` annotation to `kotlin/Xetadata` at the binary level for certain classes.
*   **The Problem:** This is "Security through Obscurity" for bytecode. It bypasses Kotlin's internal version checks, but it leaves the class in a technically corrupted state. It is a "Hack of Last Resort" that will break if the metadata format ever changes.
*   **The Modern Fix:** **Proper ProGuard/R8 rules or ASM Metadata Removal.**
    *   Instead of renaming it, use a real tool to strip or fix the metadata during loading.

---

## 9. THE BRITTLE NATIVE LINKAGE: Hardcoded `libmpv-2`
*   **Location:** `desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/player/MpvLibrary.kt`
*   **The Rot:** The app uses JNA to load `libmpv-2` by name. It does not check for other versions or provide a fallback.
*   **The Problem:** If a user has `libmpv-1` or `mpv-3` installed, or if the DLL is named differently on their system, the app crashes instantly on startup.
*   **The Modern Fix:** **Dynamic Native Loading.**
    *   Search for multiple library names (mpv, libmpv, libmpv-2, etc.) and provide a clear error message/prompt to install the correct version if missing.

---

## 10. THE CRYPTO TAKEOVER: BouncyCastle Priority 1
*   **Location:** `desktop-app/src/main/kotlin/com/lagradost/cloudstream3/desktop/Main.kt`
*   **The Rot:** Manually inserting `BouncyCastleProvider` at position #1 in the JVM Security list.
*   **The Problem:** This overrides the entire JVM's cryptography stack. It may cause unintended side effects or performance hits for standard libraries that expect the default SunJCE provider.
*   **The Modern Fix:** **Algorithm-Specific Resolution.**
    *   Explicitly request the "BC" provider only when doing Android-specific crypto operations, rather than hijacking the global JVM state.

---

## 11. THE STUB PANDEMIC: Broken Sync Authentication
*   **Location:** `desktop-app/.../com/lagradost/cloudstream3/syncproviders/AuthAPI.kt`
*   **The Rot:** Hardcoded `"stub"` and `emptyMap()` returns for critical OAuth2 utility methods (`generateCodeVerifier`, `splitRedirectUrl`) because the original Android `android.util.Base64` and `android.net.Uri` were missing on Desktop.
*   **The Problem:** All external tracker and sync logins (MAL, AniList, etc.) silently fail or refuse to parse redirects. It is broken out-of-the-box.
*   **The Modern Fix:** **Standard JVM Replacements.**
    *   Replace the missing Android imports with native Java 17+ libraries (`java.security.SecureRandom`, `java.util.Base64`, `java.net.URI`).

---

## 12. BRITTLE REFLECTION BOMBS: `AudioFile` Instantiation
*   **Location:** `player-abstraction/.../PlayerLinkHandler.kt`
*   **The Rot:** Using `.getDeclaredConstructor(String::class.java, Map::class.java).apply { isAccessible = true }.newInstance(...)` to force open internal Android library classes.
*   **The Problem:** If upstream CloudStream updates the `AudioFile` data class (adding a parameter or changing obfuscation rules), this reflection call instantly throws `NoSuchMethodException` and bricks the desktop player.
*   **The Modern Fix:** **Proper Bridge Abstractions.**
    *   Expose a proper builder or factory method in the `library` bridge rather than resorting to hostile reflection.

---

## 13. THE "GHOST FILES" LEAK: Temp Disk Exhaustion
*   **Location:** `player-abstraction/.../PlayerLinkHandler.kt` (and similar video proxy files)
*   **The Rot:** Creating M3U, EDL, and MPV config files using `File.createTempFile()` and trusting `deleteOnExit()`.
*   **The Problem:** `deleteOnExit()` only triggers during a *graceful* JVM shutdown. If the Desktop app crashes or the JNA video player freezes (which happens frequently), the files are orphaned. Over time, the user's disk will be filled with thousands of stale streaming temp files.
*   **The Modern Fix:** **Startup Cache Sanitization.**
    *   Use a dedicated, managed `.cache/stream_data` directory that gets unconditionally wiped clean every time the application engine boots.

**Final Verdict:** The current "Frankenstein" build is a masterpiece of pragmatic hacking. But if the goal is to survive **Java 25** and **Kotlin 2.1**, the "Guerilla" tactics must be upgraded to **Platform Engineering.**
