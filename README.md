# CloudStream Desktop (Unofficial Client)

> [!CAUTION]
> **PURELY EXPERIMENTAL - NOT FOR REGULAR USERS**  
> This project is a desktop JVM port and developer playground designed to run Android plugins natively on the desktop. It is actively evolving and intended for developers and contributors exploring Kotlin Multiplatform (KMP), Compose for Desktop, and bytecode transpilation.

Welcome to the CloudStream Desktop project. This is a native **Compose for Desktop** application designed to run CloudStream Android plugins natively in a desktop JVM environment without an Android emulator.

---

## 🏗️ Multi-Module Architecture

The project is structured into modular layers to cleanly separate concerns and allow Android-targeted plugins to execute seamlessly on the desktop:

*   **`:library` (android-reference)**: A submodule copy of the core Android CloudStream library containing primary data models, scrapers, and extension interfaces.
*   **`:android-stubs`**: Compatibility mock stubs for Android platform APIs (e.g., `Context`, `SharedPreferences`, `ActivityThread`). This allows standard JVM compilation and runtime execution of Android-targeted plugin code.
*   **`:common`**: The persistence and shared settings layer. Uses **SQLDelight** for local database management, watch history, settings persistence, and update records.
*   **`:player-abstraction`**: Abstracts media playback across engines. Hosts native wrappers for **MPV** (via JNA `libmpv` bindings), **WebView2** (via C++ JNI bridge), and embeds a local Ktor Netty proxy (`LocalStreamProxy`) to handle HLS segment header rewriting for CDN requests.
*   **`:plugin-runtime`**: The plugin execution and security engine. Handles:
    *   **Dalvik DEX $\rightarrow$ JVM Transpilation:** Real-time DEX translation (`Dex2jar`) allowing Android `.cs3` and `.jar` extensions to run on JVM.
    *   **Plugin Security Policy:** Enforces a strict Default Deny whitelist policy ([`PluginSecurityPolicy.kt`](plugin-runtime/src/main/kotlin/com/lagradost/runtime/security/PluginSecurityPolicy.kt)) and blocks unauthorized reflection or desktop system calls.
    *   **Bytecode Rewriting:** Automatic ASM instruction transformation ([`PluginBytecodeTransformer.kt`](plugin-runtime/src/main/kotlin/com/lagradost/runtime/loader/PluginBytecodeTransformer.kt)) replacing dangerous system calls with safe runtime stubs.
    *   **JavaScript Security:** Global Rhino `ClassShutter` ([`RhinoSecurity.kt`](plugin-runtime/src/main/kotlin/com/lagradost/runtime/security/RhinoSecurity.kt)) preventing embedded JavaScript scrapers from reflecting into host JVM classes.
    *   **Crash Immunity:** `SafePluginInvoker` supervisor coroutine wrappers with strict execution timeouts to ensure third-party plugins cannot crash the main application.
*   **`:desktop-app`**: The primary Compose for Desktop application. Houses the MVI presentation layer, Unified Dialog System, Amoled Pure Black theme engine, Dev Studio LogCat, and the Global Floating Toast notification system.

---

## ✨ Core Capabilities

*   **Native Compose for Desktop UI:** Rich cinematic hero banners, responsive media grids, multi-mode search with persistent overlays, and custom Amoled Pure Black dark mode.
*   **Unified Dialog Architecture:** Strictly encapsulated dialog system (`CloudstreamAlertDialog` and `CloudstreamCustomDialog`) ensuring desktop-first sizing and consistent elevation across all dialogs and popups.
*   **In-Process Fault Tolerance & Crash Shielding:** All plugin API calls (search, load, link extraction) are fully shielded by supervisor scopes and execution timeouts to prevent UI freezes or crashes.
*   **Automatic Plugin Management & Update Diagnostics:** Background update checks, visual success/failure badges in the Update History tab, and automated cleanup of unverified files.
*   **Global Toast Overlay:** Floating glassmorphic toast notification system with spam debouncing and detailed failure diagnostics for plugin operations and background workers.
*   **Advanced Media Playback:** Native MPV hardware-accelerated video decoding with subtitle formatting, custom keybindings, and fallback WebView2 player support.

---

## 🛠️ Setup & Development Workflow

### Prerequisites
Make sure you have the following installed:
*   **JDK 21** or higher — [Download Temurin](https://adoptium.net/)
*   **Git** — needed for cloning with recursive submodules ([Download](https://git-scm.com/))
*   **MinGW-w64 / g++** — only needed if modifying the C++ JNI bridge (`compile_jni.ps1`) ([Download via MSYS2](https://www.msys2.org/))
*   **Inno Setup 6** — only needed for building the `.exe` setup installer locally ([Download](https://jrsoftware.org/isdl.php))

> [!NOTE]
> You do **not** need Android Studio or the Android SDK. This is a pure JVM/Desktop project.

### 1. Clone the Repository
You **must** use Git clone with recursive submodules so the core library references are pulled correctly:
```bash
git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
cd CS3-desktop-client-unofficial
```
> [!WARNING]  
> Do not download this repository as a zip file from GitHub, as submodules will be missing.

### 2. Download Native Binaries
Before running, you need a local copy of the `libmpv` shared library for video decoding:
1. Download the latest `mpv-dev` Windows build (e.g., from SourceForge or official mpv builds).
2. Extract and place `libmpv-2.dll` (or `mpv-2.dll`) directly inside:
   `desktop-app/appResources/windows/mpv/`

### 3. Run Locally
To launch the desktop application, run the launcher script:
```bat
.\launch.bat
```
Or specify direct command-line targets:
```bat
.\launch.bat dev       # Launches with Dev Studio & Live LogCat (F12)
.\launch.bat release   # Launches packaged standalone .exe (builds if needed)
.\launch.bat build     # Compiles standalone release EXE
.\launch.bat test      # Runs all unit test suites & verification checks
```

> [!TIP]
> Press **F12** anywhere inside the app to toggle the live Dev Studio LogCat console.

To run only the isolated media player test harness (without starting the entire app UI):
```bash
# For WebView player testing:
.\gradlew.bat :desktop-app:runTestWebViewPlayer

# For native MPV player testing:
.\gradlew.bat :desktop-app:runTestMpvPlayer
```

### 4. Working on the Native C++ Bridge
If you modify the C++ code for the WebView2 JNI player bridge (`desktop-app/src/main/cpp`), recompile the `.dll` using the PowerShell script:
```powershell
.\compile_jni.ps1
```

> [!IMPORTANT]
> The CI/CD pipeline does **not** recompile the C++ bridge automatically. After running the script, make sure you **commit the updated `player_bridge.dll`** along with your C++ changes.

### 5. Build Standalone Installer
To generate a standalone Windows `.exe` installer:
1. Run `.\launch.bat build` to compile the release executable.
2. Open Inno Setup Compiler and compile [installer/setup.iss](installer/setup.iss).
3. The setup installer will be generated at `desktop-app\build\outputs\CloudStream-Setup.exe`.

---

## 🧪 Testing & Code Quality

*   **Kotlin Compilation Check:**
    ```bash
    .\gradlew.bat compileKotlin
    ```
*   **Automated Unit Test Suite:**
    ```bash
    .\gradlew.bat :desktop-app:test
    ```

---

## 🤝 Contributing & Pull Requests

1. Fork the repo and create your feature branch off `work` or create a new branch.
2. Ensure code builds locally with `.\gradlew.bat compileKotlin`.
3. Follow the project's **Unified Dialog Protocol** (`CloudstreamAlertDialog` / `CloudstreamCustomDialog`) and **MVI architecture** patterns.
4. Open a Pull Request with a clear description of the changes.

---

## Disclaimer
This repository acts purely as a blank-slate media shell and plugin runtime environment. The application does not ship with any plugins, media files, or pre-configured content sources. The developers hold no responsibility or liability for how users choose to utilize this software.
