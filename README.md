# CloudStream Desktop

[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2?logo=discord&logoColor=white)](https://discord.gg/JA7rts5GH)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-0078D4?logo=windows&logoColor=white)](https://github.com/errorcode26/CS3-desktop-client-unofficial/releases)

A dedicated, unaffiliated desktop-native fork (Windows only) of the original [CloudStream-3](https://github.com/Lagradost/CloudStream-3) project. Built with **Compose Multiplatform** and native **MPV**, featuring a dual runtime architecture that supports both Android **CloudStream extensions** (executed on a desktop JVM without emulators) and native **Stremio addons**.

> [!CAUTION]
> **ALPHA — Testers & Developers Only. Not for Daily Use.**
>
> - **Every update can break existing features or make the app entirely non-functional.** There are no stability guarantees between releases.
> - **This is a testing ground, not a finished product.** It is not suitable for regular or daily use.
> - **AI agents are heavily used in the development of this repository.** Code may not always conform to conventional patterns or best practices. Review critically.
> - **Zero liability.** The authors assume no responsibility for crashes, data loss, system instability, or any unexpected behavior. Use strictly at your own risk.

---

## Install (Windows)

Download the latest release from the [**Releases Page**](https://github.com/errorcode26/CS3-desktop-client-unofficial/releases).

| Package | Description |
| :--- | :--- |
| `CloudStream-Setup.exe` | Recommended. Standard installer with auto-update support. |
| `CloudStream-Portable-*.zip` | True portable build. Extract anywhere — USB drive included. Stores all data in the app folder. No registry writes. |

**Requirements:** 64-bit Windows 10 / 11 · [Microsoft Edge WebView2 Runtime](https://developer.microsoft.com/en-us/microsoft-edge/webview2/)

**Community & Support:** [CloudStream Desktop Discord](https://discord.gg/JA7rts5GH)

---

## Reporting Bugs

Before opening an issue:

1. Check if the issue already exists in [Issues](https://github.com/errorcode26/CS3-desktop-client-unofficial/issues).
2. Reproduce with the latest release if possible.
3. Open a new issue and include:
   - App version (shown in Settings)
   - Windows version
   - Steps to reproduce
   - What you expected vs. what happened
   - Logs if available (press **F12** in dev mode for the debug console)

Feature requests are welcome but low priority given the alpha stage of the project.

---

## Contributing

Contributions are welcome. Please keep the following in mind:

- This repository targets **Windows desktop only**. Do not open PRs for Android, mobile, or web targets.
- Open an issue or discuss on Discord before starting significant work — to avoid duplicate effort.
- Keep commits clean and scoped. One logical change per PR.
- Do not commit local paths, credentials, API keys, or platform-specific machine configurations.
- PRs that break the existing build without a clear justification will not be merged.

There is no formal contributor agreement. All contributions fall under the project's [GPLv3 license](LICENSE).

---

## Developer Setup

### Prerequisites

- Windows 10 / 11 (64-bit)
- JDK 21+
- Git
- Microsoft Edge WebView2 Runtime

### 1. Clone with Submodules

```bash
git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
cd CS3-desktop-client-unofficial
```

### 2. Download MPV Core

Download `mpv-dev-x86_64-*.7z` from [shinchiro/mpv-winbuild-cmake releases](https://github.com/shinchiro/mpv-winbuild-cmake/releases), extract `libmpv-2.dll`, and place it at:

```
desktop-app/appResources/windows/mpv/libmpv-2.dll
```

### 3. Run & Build

```bat
.\launch.bat dev      # Run with live debug console (F12)
.\launch.bat release  # Run compiled standalone
.\launch.bat build    # Build distribution package
.\launch.bat test     # Run test suites
```

Or via Gradle directly:

```bat
.\gradlew.bat :desktop-app:run --args="--dev"
.\gradlew.bat :desktop-app:createDistributable
.\gradlew.bat :desktop-app:packageMsi
.\gradlew.bat :desktop-app:packagePortableZip
```

---

## Modules

| Module | Description |
| :--- | :--- |
| `:desktop-app` | Compose Desktop UI, window management, player integration, and Stremio addon client |
| `:plugin-runtime` | DEX-to-JVM transpiler (Dex2jar), plugin loader, bytecode security transformer |
| `:sandbox` | Windows AppContainer sandbox host and worker protocol |
| `:player-abstraction` | JNA bindings for `libmpv` |
| `:android-stubs` | Android API stubs (`Context`, `SharedPreferences`) for JVM plugin compatibility |
| `:common` | Cross-platform persistence layer (SQLDelight / SQLite) |
| `:library` | Upstream CloudStream core interfaces and provider contracts |

---

## Extension & Addon Architecture

The application implements a dual-runtime architecture supporting two independent extension ecosystems:

- **CloudStream Extensions (`.cs3` / `.jar`):** Android CloudStream plugins are transpiled to JVM bytecode at runtime and executed inside an isolated low-privilege **Windows AppContainer** worker process. The sandbox is enforced by the Windows kernel through Win32 Job Objects (`ACTIVE_PROCESS = 1`, 70% CPU cap, 2 GB memory limit) and explicit filesystem ACLs. Plugins cannot access the host JVM heap, sensitive user folders, or loopback network interfaces.
- **Stremio Addons:** Built-in client protocol support enables adding standard HTTP/HTTPS Stremio addon manifests, seamlessly integrating remote catalogs, stream resolvers, and subtitles into the desktop player.

Install third-party extensions and addons only from sources and repositories you trust.

---

## Player Architecture

MPV renders video into a native Win32 `HWND`. A transparent Microsoft WebView2 overlay hosts the player controls UI. High-frequency commands (seek, volume) are processed inside a thin C++ bridge (`desktop-app/src/main/cpp/player_bridge.cpp`) to avoid JVM GC latency on the media pipeline.

To recompile the C++ bridge (the pre-compiled DLL is already tracked in the repository):

```powershell
cd desktop-app\src\main\cpp
.\build_jni.ps1
```

---

## Legal & Disclaimer

**1. Content-Neutral Media Shell & Runtime.** CloudStream Desktop is distributed strictly as an empty, media-neutral application shell and plugin runtime environment. The software contains no pre-bundled media sources, streaming links, or remote scraping routines. The authors, maintainers, and contributors do not host, store, index, cache, scrape, or transmit any audio/video streams or copyrighted materials, nor do they operate any media hosting infrastructure or delivery networks.

**2. Sole User Responsibility & Third-Party Extensions.** All extensions, plugins, and repository URLs are installed, managed, and executed entirely at the discretion and initiative of the end user. The project authors maintain no affiliation with, ownership of, or control over any third-party extensions, external repositories, or third-party web endpoints. End users are solely and independently responsible for ensuring that their usage of this software and any installed extensions complies with all applicable local, national, and international laws, copyright regulations, and licensing agreements.

**3. Copyright Infringement & Intellectual Property Inquiries.** Because CloudStream Desktop neither hosts nor distributes any multimedia content or third-party extension packages, the maintainers cannot remove, modify, or restrict access to media hosted on external servers. Any copyright infringement claims, DMCA notices, or intellectual property inquiries must be directed to the respective third-party web hosts, domain owners, or extension authors originating the content.

**4. Third-Party Metadata.** This application does not directly query third-party metadata databases. Any metadata, posters, descriptions, or unique identifiers displayed in the user interface are supplied at runtime by user-installed extensions. The operation of any third-party API within an extension is governed independently by that extension's code and the respective API provider's terms of service.

**5. Nominative Fair Use & Trademarks.** All product names, registered trademarks, service marks, logos, brand names, and rating symbols mentioned in the application or documentation belong to their respective owners. Reference to any third-party names or marks is conducted strictly for nominative identification and descriptive purposes. No endorsement, sponsorship, affiliation, or commercial relationship is claimed or implied.

**6. Warranty & Limitation of Liability.** This software is provided under the [GNU General Public License v3.0](LICENSE) "AS IS", without warranty of any kind, express or implied, including but not limited to warranties of merchantability, fitness for a particular purpose, and non-infringement. Under no circumstances shall the authors, maintainers, or contributors be held liable for any direct, indirect, incidental, special, exemplary, or consequential damages, data loss, legal liability, or system instability arising from the installation, use, or misuse of this software.

---

## Credits & Licensing

### Upstream Project & Original Authors
Full credit, recognition, and deep gratitude go to the original creators and contributors of [CloudStream-3](https://github.com/Lagradost/CloudStream-3):
- **Original Project:** [CloudStream-3 on GitHub](https://github.com/Lagradost/CloudStream-3)
- **Original Author & Lead:** [Lagradost](https://github.com/Lagradost) and the CloudStream open-source contributors
- **Upstream Core:** Architecture, provider contracts, and interface definitions originate from the upstream `:library` project.
- **Independence & Affiliation:** This repository is an **independent, unaffiliated desktop-native fork** dedicated exclusively to Windows desktop. It is not maintained, endorsed, or affiliated with the upstream CloudStream team. For the official Android application, please visit the upstream repository linked above.

### Desktop Implementation
- **Desktop Maintainer:** [errorcode26](https://github.com/errorcode26) & CloudStream Desktop contributors (Compose UI, C++ player bridge, Windows AppContainer sandbox, desktop runtime engine, and SQLite persistence)
- **Protocol Inspiration:** Addon client compatibility inspired by the open [Stremio Addon Specification](https://github.com/Stremio/stremio-addon-sdk).
- **Third-Party Libraries:** [mpv](https://mpv.io/) · [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) · [Dex2jar](https://github.com/pxb1988/dex2jar) · [SQLDelight](https://github.com/cashapp/sqldelight) · [Microsoft Edge WebView2](https://developer.microsoft.com/en-us/microsoft-edge/webview2/) · [OkHttp](https://square.github.io/okhttp/)
- **License:** [GNU General Public License v3.0](LICENSE)

### Forks & References

This repository is open source under GPLv3. Forks and derivative works must comply with GPLv3 terms. If you use this project as a reference, base, or starting point — even partially — a credit back to [`errorcode26/CS3-desktop-client-unofficial`](https://github.com/errorcode26/CS3-desktop-client-unofficial) is appreciated. It takes minimal effort and means a lot to the people who built this.
