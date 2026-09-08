# CloudStream Desktop (Unofficial Client)

Desktop-native streaming client built with **Compose Multiplatform** for 64-bit Windows. Runs Android CloudStream extensions natively on a desktop JVM without requiring emulators or compatibility layers.

> [!CAUTION]
> **Active Developer & Experimental Pre-Alpha State**
> * **Developer-Only Environment:** This repository is an active, fast-moving development and experimentation codebase intended strictly for developers and technical testers. It is **not** a stable release and is **not** intended for general or regular end-user consumption.
> * **Zero Feature Stability Guarantees:** Features, internal APIs, storage schemas, and platform behavior undergo rapid iteration and may break or change at any time. Exercise caution when interacting with project files, databases, or local configs.

> [!IMPORTANT]
> **Project Scope & Architecture Directives**
> * **Desktop-Exclusive Hard Fork:** This repository is built exclusively for 64-bit Windows desktop. It is an independent hard fork and does not merge upstream into Android CloudStream.
> * **Zero Affiliation:** This project is independent and unaffiliated with the original Android CloudStream application or its development team. Please do not contact upstream developers regarding this client.
> * **Ad-Free Policy:** Strict ad-free project. Derivative builds and forks must remain clean, free, and open.

---

## Interface Preview

| Home Spotlight & Banner | Media Details & Episode Browser |
| :---: | :---: |
| ![Home Spotlight](screenshots/1.png) | ![Media Details](screenshots/2.png) |
| **Hardware-Accelerated MPV Player** | **Catalog & Discovery** |
| ![Player Controls](screenshots/3.png) | ![Catalog Explorer](screenshots/4.png) |

---

## Architectural Overview

The application is structured into modular subprojects separating platform abstraction, runtime transcompilation, and UI presentation:

| Module | Responsibility |
| :--- | :--- |
| **`:desktop-app`** | Compose Multiplatform presentation layer, Amoled dark theme, local stream proxy, and native desktop window controls. |
| **`:plugin-runtime`** | Transcompilation engine. Converts Dalvik DEX bytecode into JVM bytecode via Dex2jar, applies ASM bytecode instrumentation, and enforces sandbox security policies. |
| **`:player-abstraction`** | JNA bindings to the native `libmpv` C-core for hardware-accelerated video decoding. |
| **`:android-stubs`** | Stubs for Android platform APIs (`Context`, `SharedPreferences`, `Build`, `DisplayMetrics`) allowing Android extension bytecode to run on the JVM. |
| **`:common`** | SQLite persistence layer powered by **SQLDelight** for local history, preferences, and state management. |
| **`:library`** | Base CloudStream contracts and core provider interfaces. |

---

## Developer Setup & Quick Start

### Prerequisites
* **Operating System:** Windows 10 / 11 (64-bit)
* **Web Runtime:** **Microsoft Edge WebView2 Runtime** (pre-installed by default on Windows 11 and modern Windows 10; [Evergreen Bootstrapper](https://developer.microsoft.com/en-us/microsoft-edge/webview2/) available if omitted on custom OS builds). Note: Google Chrome is not used.
* **Java Development Kit:** **JDK 21** or higher (e.g. [Eclipse Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21))
* **Git:** Installed and available in PATH

---

### Step 1: Clone With Submodules
This repository relies on internal submodules. You **must** clone recursively:

```bash
git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
cd CS3-desktop-client-unofficial
```

*(If you already cloned without `--recursive`, run `git submodule update --init --recursive` inside the repository).*

---

### Step 2: Native Binaries Setup (MPV)
The video player requires the 64-bit native `libmpv-2.dll` placed in `desktop-app/appResources/windows/mpv/`.

Because `libmpv-2.dll` (~112 MB) exceeds GitHub's 100 MB single-file repository limit, it is not bundled directly in git.

| Binary | Distribution | Tracked in Git? | Purpose |
| :--- | :--- | :---: | :--- |
| **`libmpv-2.dll`** (~112 MB) | Downloaded manually | No | Native MPV media playback core |
| **`player_bridge.dll`** (~180 KB) | Pre-bundled | Yes | Win32 Airspace HWND compositor & low-latency fast-path |
| **`WebView2Loader.dll`** (~160 KB) | Pre-bundled | Yes | Microsoft WebView2 runtime dynamic loader |

**How to get `libmpv-2.dll`:**
1. Download the `mpv-dev-x86_64-*.7z` development package from [shinchiro/mpv-winbuild-cmake releases](https://github.com/shinchiro/mpv-winbuild-cmake/releases) (such as pinned build [20260610](https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20260610)).
2. Extract `libmpv-2.dll` from the downloaded archive.
3. Place `libmpv-2.dll` into:

```text
desktop-app/
└── appResources/
    └── windows/
        ├── mpv/
        │   ├── libmpv-2.dll          <-- Place extracted DLL here
        │   └── portable_config/
        │       └── mpv.conf
        └── jni/
            ├── player_bridge.dll     <-- Pre-bundled in repository
            └── WebView2Loader.dll    <-- Pre-bundled in repository
```

---

### Step 3: Run & Build

Use the interactive launcher script:

```bat
.\launch.bat
```

The launcher provides quick shortcuts:
* `.\launch.bat dev` — Start the client with Live LogCat (F12) enabled.
* `.\launch.bat release` — Launch the compiled standalone executable.
* `.\launch.bat build` — Compile the standalone distribution EXE via Gradle.
* `.\launch.bat test` — Run all module test suites and compile checks.

Alternatively, execute tasks directly via Gradle:
```bat
# Run in dev mode
.\gradlew.bat :desktop-app:run --args="--dev"

# Compile standalone distributable
.\gradlew.bat :desktop-app:createDistributable
```

---

## Native Bridge & Player Architecture

### The Win32 Airspace & Fast-Path Architecture
Directly overlaying Java Swing / Compose Multiplatform components onto a native video window handle (`HWND`) causes severe visual occlusion and flicker (the Win32 "Airspace problem"). Furthermore, routing high-frequency seekbar scrubs through JVM garbage collection and JNI layers introduces 10–50ms latency delays that cause scrubber rubber-banding.

CloudStream Desktop resolves this through a hybrid native architecture in `desktop-app/src/main/cpp/player_bridge.cpp`:
1. **HWND Composition:** A unified native Win32 container (`g_containerHwnd`) hosts MPV's render context as the base layer, with a transparent Microsoft WebView2 Chromium instance layered directly on top.
2. **C++ Native Fast-Path:** Timeline scrubbing, volume changes, and state events are intercepted and processed directly inside C++ in sub-millisecond time (`g_mpv_command_string`), completely bypassing JVM overhead.
3. **High-Frequency Polling:** A native timer polls MPV playback positions (`time-pos`, `bufferPos`) and pushes updates via `PostWebMessageAsJson` without allocating JVM objects.
4. **Cloudflare CDP Resolution:** WebView2 is also leveraged headlessly by `CloudflareKiller.kt` via Chrome DevTools Protocol to solve Cloudflare Turnstile / anti-bot challenges that Android extensions expect an Android OS WebView to handle.

### Optional: Recompiling the Native C++ Bridge
Developers only need a C++ compiler if modifying `desktop-app/src/main/cpp/player_bridge.cpp`. The pre-compiled DLLs are already tracked in git.

To recompile:
* **MinGW-w64 (GCC):** Ensure `g++` and `JAVA_HOME` are set, then execute:
  ```powershell
  cd desktop-app\src\main\cpp
  .\build_jni.ps1
  ```
  *(Statically links `libstdc++` and `libwinpthread` to eliminate external runtime dependencies).*
* **CMake (MSVC / CLion):** A standard `CMakeLists.txt` is provided in `desktop-app/src/main/cpp/` linking the self-contained WebView2 headers and libraries in `webview2/`.

---

## Disclaimer

This software is an empty media player shell and runtime harness. It does not host, distribute, or bundle any media content, streams, or scrapers. Users are solely responsible for extensions they choose to install.
