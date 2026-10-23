# CloudStream Desktop (Unofficial Client)

[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2?logo=discord&logoColor=white)](https://discord.gg/JA7rts5GH)

Desktop-native streaming client built with **Compose Multiplatform** and native **MPV** for 64-bit Windows (Linux planned). Runs Android CloudStream extensions natively on a desktop JVM without requiring emulators or compatibility layers.

> [!CAUTION]
> **EXPERIMENTAL PROTOTYPE — DEVELOPERS & TESTERS ONLY**
>
> * **Not for Regular / Daily Usage:** This repository is an early-stage, experimental engineering prototype. Features, local databases, and runtime behaviors undergo rapid, breaking iteration. Things can and will break, behave erratically, or crash unexpectedly.
> * **AI-Assisted & Retrofitted Codebase:** Substantial portions of this codebase have been written, retrofitted, or ported using AI agents. Implementation patterns may not conform to industry best practices, standard conventions, or clean software architecture.
> * **Zero Liability / As-Is Disclaimer:** Use strictly at your own risk. The authors, maintainers, and contributors assume zero responsibility or liability for crashes, system instability, data loss, or any unexpected behavior.
>
> **Security Notice (No Sandboxing):** There is currently **no plugin sandboxing** or security isolation. Extensions run with direct host JVM privileges. Only install extensions and repositories from sources you trust. Distributed strictly "as is" under GPLv3 (derivative works and ports must attribute [`errorcode26/CS3-desktop-client-unofficial`](https://github.com/errorcode26/CS3-desktop-client-unofficial)).

## Quick Install (Windows)

Download the latest installer (`CloudStream-Setup.exe`) from the [**Releases Page**](https://github.com/errorcode26/CS3-desktop-client-unofficial/releases).
* **Requirements:** 64-bit Windows 10 / 11 and [Microsoft Edge WebView2 Runtime](https://developer.microsoft.com/en-us/microsoft-edge/webview2/).
* **Community & Support:** Join the [CloudStream Desktop Discord](https://discord.gg/JA7rts5GH).

---

## Modules

| Module | Description |
| :--- | :--- |
| **`:desktop-app`** | Compose Desktop UI, player view, and desktop window controls |
| **`:plugin-runtime`** | DEX-to-JVM bytecode transcompiler (Dex2jar) and plugin loader (no sandbox yet) |
| **`:player-abstraction`** | JNA bindings for `libmpv` |
| **`:android-stubs`** | Android API stubs (`Context`, `SharedPreferences`) for JVM plugin compatibility |
| **`:common`** | SQLite persistence via SQLDelight |
| **`:library`** | Upstream CloudStream core interfaces and provider contracts |

---

## Developer Setup

### Prerequisites
* Windows 10 / 11 (64-bit)
* Microsoft Edge WebView2 Runtime
* JDK 21+
* Git

### 1. Clone With Submodules
```bash
git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
cd CS3-desktop-client-unofficial
```

### 2. Download MPV Core
Download `mpv-dev-x86_64-*.7z` from [shinchiro/mpv-winbuild-cmake releases](https://github.com/shinchiro/mpv-winbuild-cmake/releases) (e.g. build 20260610), extract `libmpv-2.dll`, and place it in:
```text
desktop-app/appResources/windows/mpv/libmpv-2.dll
```

### 3. Run & Build
Use the launcher script:
```bat
.\launch.bat dev      # Start with Live LogCat (F12)
.\launch.bat release  # Run compiled standalone EXE
.\launch.bat build    # Compile distribution package
.\launch.bat test     # Run test suites
```

Or execute directly via Gradle:
```bat
.\gradlew.bat :desktop-app:run --args="--dev"
.\gradlew.bat :desktop-app:createDistributable
```

---

## Player Architecture

MPV renders video directly into a native Win32 window (`HWND`), with a transparent Microsoft WebView2 overlay hosting the player UI. High-frequency seek and volume commands are processed directly inside C++ (`desktop-app/src/main/cpp/player_bridge.cpp`) to bypass JVM GC latency.

To recompile the C++ bridge (optional; pre-compiled DLL is already tracked):
```powershell
cd desktop-app\src\main\cpp
.\build_jni.ps1
```

---

## Disclaimer & Nominative Fair Use

CloudStream Desktop is an empty, media-neutral desktop player shell and runtime harness (no plugin sandboxing currently implemented). It does not host, scrape, index, cache, or distribute any media content or video streams. All extensions and scrapers are third-party, decentralized, and user-installed. All product names, company names, logos, rating badges, and brands belong to their respective owners and are used strictly for nominative and descriptive identification. Use of these names, logos, and brands does not imply endorsement, affiliation, sponsorship, or partnership. The software is provided "as is" under GPLv3 without warranty.

---

## Credits & Licensing

* **Inspirations & Prior Art:** UX patterns, addon models, and layout ideas inspired by open-source media applications including Stremio and Nuvio.
* **Upstream Core:** [CloudStream-3](https://github.com/Lagradost/CloudStream-3) by Lagradost & contributors (`:library` interfaces).
* **Desktop Implementation:** [errorcode26](https://github.com/errorcode26) & CloudStream Desktop contributors (Compose UI, C++ bridge, runtime engine, desktop DB).
* **Downstream Attribution:** Any fork, port (e.g., Linux/macOS), or derivative work **must** credit [`errorcode26/CS3-desktop-client-unofficial`](https://github.com/errorcode26/CS3-desktop-client-unofficial) under GPLv3.
* **Third-Party Libraries:** mpv, Compose Multiplatform, Dex2jar, SQLDelight, Microsoft Edge WebView2.
* **License:** [GNU General Public License v3.0](LICENSE).
