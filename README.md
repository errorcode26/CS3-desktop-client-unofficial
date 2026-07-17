# CloudStream Desktop (Unofficial Client)

> [!IMPORTANT]
> **Pre-Alpha / Closed Testing Phase**  
> This project is in active pre-alpha development. We are not distributing pre-compiled setup executables or installers yet. All development, testing, and debugging are currently run directly from the source code.

Welcome to the CloudStream Desktop project. This is a native **Compose for Desktop** application designed to run CloudStream Android plugins natively in a desktop JVM environment.

---

## 🏗️ Multi-Module Architecture

The project is structured into modular layers to cleanly separate concerns and allow the Android-specific plugin code to execute on the desktop:

*   **`:library` (android-reference)**: A submodule copy of the core Android CloudStream library. It contains the primary data models, scrapers, and extension interfaces.
*   **`:android-stubs`**: Compatibility mock stubs for Android platform APIs (e.g., `Context`, `SharedPreferences`, `ActivityThread`). This allows standard JVM compilation of Android-targeted plugin code.
*   **`:common`**: The persistence and settings layer. It uses **SQLDelight** for local database management and Jackson-based file serialization, remaining completely decoupled from the UI.
*   **`:player-abstraction`**: Abstracts media playback. It hosts native wrappers for **MPV** (JNA bindings) and **VLC** (process wrappers), and embeds a local Ktor Netty proxy (`LocalStreamProxy`) to rewrite HLS segment headers for CDN requests.
*   **`:desktop-app`**: The main entry point. It hosts the Compose for Desktop UI, navigation, theme controls, and window chrome.
*   **`:plugin-runtime` / `:plugin-sandbox`**: Handles isolated plugin loading and basic bytecode sandboxing to protect local files.

---

## ✨ Core Capabilities

*   **Native Compose UI:** Built entirely in Compose for Desktop with rich cinematic header fades, responsive hero layouts, dynamic color extraction, and multi-mode search (persistent overlays & quick-clear controls).
*   **Android Plugin Compatibility:** Runs standard Android plugins directly on the JVM through custom compatibility stubs (`android.*`, `androidx.*`) and isolated class loaders.
*   **Advanced Media Abstraction (`:player-abstraction`):** Native MPV decoding (via JNA `libmpv` bindings) and fallback web/process wrappers, backed by an embedded Ktor Netty proxy (`LocalStreamProxy`) for HLS segment header rewriting and CDN bypasses.
*   **Modular Storage & Synchronization:** SQLDelight local database management decoupled from the presentation layer, with built-in multi-provider watch tracking and history sync.

---

## 🛠️ Setup & Development Workflow

### Prerequisites
Make sure you have all of these installed before you start:
*   **JDK 21** or higher — [Download Temurin](https://adoptium.net/)
*   **Git** — needed for cloning with submodules ([Download](https://git-scm.com/))
*   **MinGW-w64 / g++** — only needed if you plan to modify the C++ JNI bridge (`compile_jni.ps1`). Make sure `g++` is available in your `PATH` ([Download via MSYS2](https://www.msys2.org/))
*   **Inno Setup 6** — only needed if you want to build the `.exe` installer locally ([Download](https://jrsoftware.org/isdl.php))

> [!NOTE]
> You do **not** need Android Studio or any Android SDK. This is a pure JVM/Desktop project.

### 1. Clone the Repository
You **must** use Git clone with recursive submodules so the Android core library references are pulled correctly:
```bash
git clone --recursive https://github.com/errorcode26/cloudstream-desktop-unofficial.git
cd cloudstream-desktop-unofficial
```
> [!WARNING]  
> Do not download this repository as a zip file from GitHub, as submodules will be missing.

### 2. Download Native Binaries
Before running, you need a local copy of the `libmpv` shared library for video decoding:
1. Download the latest `mpv-dev` Windows build (e.g., from SourceForge).
2. Extract and place `libmpv-2.dll` (or `mpv-2.dll`) directly inside the following folder:
   `desktop-app/appResources/windows/mpv/`

### 3. Run Locally
To compile and launch the desktop application in developer mode:
```bash
.\gradlew.bat :desktop-app:run
```
To quickly run only the isolated media player test harness (without starting the entire app UI):
```bash
# For WebView player testing:
.\gradlew.bat :desktop-app:runTestWebViewPlayer

# For native MPV player testing:
.\gradlew.bat :desktop-app:runTestMpvPlayer
```

### 4. Working on the Native C++ Bridge
If you are tweaking the raw C++ code for the WebView2 JNI player bridge (`desktop-app/src/main/cpp`), you do not need to memorize the 15+ GCC compiler/linking flags. Simply run the included PowerShell script to instantly recompile the `.dll`:
```powershell
.\compile_jni.ps1
```

> [!IMPORTANT]
> The CI/CD pipeline does **not** recompile the C++ bridge automatically. After running the script, make sure you **commit the updated `player_bridge.dll`** along with your C++ changes before pushing. Otherwise the CI build will ship the old binary.

> [!NOTE]
> **Want a new UI feature that uses native Windows functionality?** If the feature you want doesn't already exist in the C++ bridge (e.g., a new WebView2 control, a new window event, a new native dialog), you **must** add the corresponding JNI method to `player_bridge.cpp` first and recompile the `.dll`. The Kotlin/Compose UI layer can only call native capabilities that are already exposed through the JNI bridge — there is no other way to add them.

### 5. Build Installer (Optional)
If you need to generate a standalone Windows `.exe` setup installer for testing:
1. Run `compile.bat` to clean and compile the latest executable binaries.
2. Open Inno Setup Compiler and compile [installer/setup.iss](installer/setup.iss).

The compiled setup installer will be generated at `desktop-app\build\outputs\CloudStream-Setup.exe`.

---

## 🧪 Testing & Code Quality
This architecture is built for rapid iteration. We have a lightweight test harness, but our focus is on active developer validation:
*   Use isolated experimental/feature branches for development to keep the `dev` branch clean.
*   To run the standard automated unit test suite (verifies math, updaters, and API parsers):
    ```bash
    .\gradlew.bat :desktop-app:test
    ```
*   To test changes on the video player directly without booting the full app shell, use the isolated harnesses:
    ```bash
    .\gradlew.bat :desktop-app:runTestWebViewPlayer
    .\gradlew.bat :desktop-app:runTestMpvPlayer
    ```

---

## 🤝 Contributing & Issues

### Reporting Issues
Found a bug or got a cool feature idea? Just open an issue! Keep it simple and to the point:
1. **What were you trying to do?** (e.g., "I clicked the play button...")
2. **What actually happened?** (e.g., "...and the app crashed.") If things blew up, drop the error logs or a screenshot.
3. **How can we reproduce it?** (Step-by-step is super helpful so we can see the bug ourselves).

### Pull Requests (PRs)
Want to build a feature yourself? Awesome! We love PRs.
1. Fork the repo and create your own branch off the `dev` branch.
2. Build your feature. Be sure to test it locally using the test commands above!
3. **Important:** Don't touch the version numbers in `gradle.properties` (we handle version bumping when we merge).
4. Open a PR, give it a quick description of what you added and why it's cool, and we'll take a look!

---

## Disclaimer
This repository acts purely as a blank-slate media shell. The application does not ship with any plugins, media files, or pre-configured content sources. The developers hold no responsibility or liability for how users choose to utilize this software.
