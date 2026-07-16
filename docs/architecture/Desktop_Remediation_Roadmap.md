# 🚀 CloudStream Desktop: Deep Dive Architecture & Remediation Roadmap

This document outlines the most critical vulnerabilities and architectural bottlenecks in the CloudStream Desktop client. To ensure long-term maintainability, actionable solutions and implementation strategies are provided directly beneath each issue.

---

## 🚨 Tier 1: The "Survival" Tier (Security & Future-Proofing)
These issues represent active vulnerabilities or reliance on deprecated APIs that will prevent the app from functioning on upcoming Java versions.

### 1. The Death of the Plugin Sandbox (`SecurityManager`)
**The Problem:**
The application relies on `java.lang.SecurityManager` in `PluginSecurityManager.kt` to sandbox untrusted Android plugins. Oracle deprecated `SecurityManager` in JEP 411 (Java 17) and it will be completely removed in Java 24+. Once removed, any loaded plugin gains full, unrestricted admin-level access to the host's filesystem and shell.

**The Pragmatic Solution: JVM Bytecode Firewall (ClassLoader + ASM)**
OS-level sandboxing (IPC/gRPC) is unnecessary overhead and cross-platform maintenance hell for simple web scrapers. Instead, implement a strict application-level sandbox.
*   **Architecture:** Confine the plugin entirely within the JVM by severing its access to dangerous APIs before it even loads.
*   **Implementation Steps:**
    1.  **ClassLoader Whitelisting:** Modify `SafePluginClassLoader`. Override `loadClass()`. Explicitly block execution and filesystem packages (e.g., `java.io.*`, `java.nio.*`, `java.lang.Runtime`, `java.lang.ProcessBuilder`, `java.lang.reflect.*`) by throwing a `SecurityException`. Only allow safe packages (`java.net.*`, `java.util.*`, `java.lang.*`).
    2.  **ASM Static Verification:** Since plugins pass through ASM anyway (see Issue #3), use an ASM `MethodVisitor` to scan the raw bytecode. If the visitor detects a direct method call to `java/lang/Runtime` or `java/io/File`, refuse to load the plugin.
    3.  **Result:** The plugin retains full ability to scrape the web and parse JSON, but is physically blocked from touching the hard drive or executing terminal commands. Network trust remains the user's responsibility.

### 2. TLS/SSL Verification Bypassed Globally
**The Problem:**
In `NetworkConfig.kt`, the app uses `insecureBuilder.ignoreAllSSLErrors()`. This disables X.509 certificate validation globally for the OkHttp/NiceHttp clients, leaving the application entirely vulnerable to Man-In-The-Middle (MITM) attacks.

**The Deep Dive Solution: Whitelisted `X509TrustManager`**
*   **Implementation Steps:**
    1.  Remove `ignoreAllSSLErrors()`.
    2.  If specific video CDNs have broken or self-signed certificates, implement a custom `X509TrustManager`.
    3.  In the `checkServerTrusted` method, check the domain of the incoming request. If it matches a known, hardcoded list of broken CDNs (e.g., `listOf("broken-cdn.example.com")`), allow it. For all other domains (TMDB, GitHub, Trakt), delegate to the standard JVM `TrustManager` to enforce strict SSL validation.

---

## 📉 Tier 2: The "Stability & Integrity" Tier
These issues cause silent memory leaks, corrupted bytecode, and eventual application crashes.

### 3. Bytecode Sabotage (`dex2jar` + `JarPatcher`)
**The Problem:**
`dex2jar` successfully translates Dalvik math, but corrupts Kotlin metadata and inline class names. `JarPatcher.kt` attempts to fix this by treating compiled `.class` files as text documents, doing raw binary search-and-replace (e.g., changing `constructor_impl` to `constructor-impl`). This corrupts the Constant Pool and will permanently break the moment Kotlin changes its compiler output.

**The Deep Dive Solution: ASM Bytecode Instrumentation**
*   **Architecture:** Keep the `de.femtopedia.dex2jar` fork, but delete `JarPatcher.kt` entirely. Replace it with `org.ow2.asm`.
*   **Implementation Steps:**
    1.  Add `org.ow2.asm:asm` and `org.ow2.asm:asm-tree` to your dependencies.
    2.  In `ExtensionLoader.kt`, after `dex2jar` generates the JAR, pass the bytes through an ASM `ClassReader`.
    3.  Create an ASM `ClassVisitor` and `MethodVisitor`.
    4.  Override `visitMethod`: Safely rename methods containing `_` back to `-` for known Kotlin inline signatures.
    5.  Override `visitAnnotation`: Intercept the `@kotlin.Metadata` annotation. Instead of corrupting it (the `Xetadata` hack), simply instruct ASM to drop the annotation entirely, or parse and rewrite it correctly.
    6.  Use `ClassWriter` to generate structurally perfect, valid `.class` bytes.

### 4. Unmanaged Concurrency Leaks (`GlobalScope`)
**The Problem:**
`GlobalScope.launch` is used extensively for background tasks (Repository syncing, MpvPlayer/VlcPlayer initialization). Because `GlobalScope` has no lifecycle, a hanging network request or a frozen JNA call will leave a "zombie" thread in memory forever, eventually leading to UI freezes or `OutOfMemoryError`.

**The Deep Dive Solution: Structured Concurrency**
*   **Implementation Steps:**
    1.  **Application Level:** In `Main.kt`, define a global application scope: `val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Use this for tasks that must outlive UI screens but still need to be managed.
    2.  **Player Level:** Inside `MpvPlayer.kt` and `VlcPlayer.kt`, implement `CoroutineScope` by delegating to a `Job()`. 
    3.  When the `destroy()` or `stop()` method is called on the player, call `job.cancel()`. This ensures that if the user closes the video window, any pending network requests or background processing threads for that video are instantly killed.

### 5. Silent Disk Exhaustion (`File.createTempFile`)
**The Problem:**
In `PlayerLinkHandler.kt`, streaming configuration files (`.m3u`, `.edl`, `.conf`) are created using `File.createTempFile()` and rely on `.deleteOnExit()`. If the JNA video player faults and crashes the JVM, the files are orphaned, slowly filling the user's disk.

**The Deep Dive Solution: Startup Cache Sanitization**
*   **Implementation Steps:**
    1.  Stop using `File.createTempFile()`.
    2.  Define a specific, predictable directory: `val streamCache = File(PlatformPaths.appDataDir, "stream_cache")`.
    3.  Write all `.m3u` and `.edl` files directly into this folder.
    4.  In `Main.kt`, immediately upon application boot, execute: `streamCache.deleteRecursively(); streamCache.mkdirs()`. This guarantees the slate is wiped clean every time the app starts, preventing infinite bloat.

---

## 🛠️ Tier 3: Maintainability & Developer Sanity
These are architectural technical debts that make the codebase difficult to work with and prone to unexpected crashes.

### 6. Manual Android Stubs (`android-stubs`)
**The Problem:**
The desktop app uses 38 manually written `.java` files to fake Android APIs. If a plugin updates and uses an Android class you haven't manually stubbed (like a specific `android.graphics` class), the desktop app crashes with a `ClassNotFoundException`. Writing manual stubs does not scale.

**The Deep Dive Solution: Dynamic Proxy Generation ("Ghost Stubs")**
*   **Implementation Steps:**
    1.  Delete the vast majority of the `android-stubs` folder.
    2.  Implement a custom `ClassLoader` (extending `URLClassLoader`) for the plugins.
    3.  Override `findClass(String name)`. If the `name` starts with `android.`, and you don't have a real implementation for it, dynamically generate a stub in memory using JVM Reflection (`java.lang.reflect.Proxy`) or byte-buddy.
    4.  Configure these "Ghost Stubs" to log a warning and return `null` or empty strings when called, rather than crashing the JVM.
    5.  Only retain manual stubs for APIs that are absolutely critical to the plugin's logic (like `android.net.Uri`, which should just be routed to `java.net.URI`).

### 7. Brittle Native Linkage (`libmpv-2`)
**The Problem:**
`Native.load("libmpv-2")` is hardcoded. It causes immediate startup crashes if the user's OS has the library named differently (e.g., `libmpv.so.1` on Linux, or `mpv-3.dll` on Windows).

**The Deep Dive Solution: Dynamic Native Resolution**
*   **Implementation Steps:**
    1.  Create a fallback array of possible library names: `val targets = listOf("mpv", "libmpv", "libmpv-2", "mpv-1")`.
    2.  Iterate through the array and wrap `Native.load()` in a `try/catch(UnsatisfiedLinkError)`.
    3.  If all targets fail, do not crash the app. Instead, set a flag and show a graceful UI dialog directing the user to install MPV.

### 8. The Legacy Local Proxy (`com.sun.net.httpserver`)
**The Problem:**
`LocalStreamProxy.kt` uses an undocumented Java 6 server to intercept HLS streams. It is synchronous and blocks threads, which struggles under high-bandwidth video segment rewriting.

**The Deep Dive Solution: Bypass via MPV Headers**
*   **Implementation Steps:**
    1.  Ideally, eliminate the proxy entirely. 
    2.  MPV supports passing authentication headers directly to the demuxer via the command line: `--http-header-fields=User-Agent: CS3, Referer: https://example.com`.
    3.  If segment URL rewriting is strictly required for the HLS manifest, migrate the local server from `com.sun.net.httpserver` to an embedded **Ktor Netty** server, which is fully asynchronous and natively supports coroutines.