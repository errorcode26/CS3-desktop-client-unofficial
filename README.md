# CloudStream Desktop (Unofficial Client)

> [!CAUTION]
> **PROCEED AT YOUR OWN RISK (OR DON'T)**
> 
> * **Cursed Code Warning:** If you are a professional software engineer, browsing this source code may cause severe emotional damage. Large chunks were iterated using context-blind AI models hallucinating shortcuts while I argued with them.
> * **Not Claiming It's Good:** It is not a textbook software engineering masterpiece. It has tech debt, weird workarounds, and pragmatic duct tape. But it compiles, launches, plays video smoothly with `libmpv`, and runs on my PC.
> * **Windows Only & Hard Fork:** Exclusively built and tested for 64-bit Windows desktop. It is a permanent hard fork and will never merge into upstream Android CloudStream.
> * **Zero Affiliation (Completely Different App):** Full respect to the original CloudStream creators, but this client is completely unaffiliated and uses an entirely different desktop architecture. **DO NOT contact or bother the official developers** about anything related to this project.
> * **Zero Monetization:** No donations accepted. No feature requests taken. No support provided.
> * **The Only Non-Negotiable Rule for Forks:** **ABSOLUTELY NO ADS.** Don't be that person. Keep it free, clean, and open.

---

## What Is This Thing & How Does It Actually Work?

This is a standalone desktop media client that runs Android CloudStream extensions natively on a desktop JVM without needing an Android emulator. 

The codebase is split into modules to pull off this trick:

* **`:library`**: Core data models, scrapers, and provider contracts inherited from CloudStream to maintain extension compatibility.
* **`:android-stubs`**: Fake mock implementations of Android platform classes (`Context`, `SharedPreferences`, `ActivityThread`) so plugin bytecode doesn't immediately crash standard JVM.
* **`:plugin-runtime`**: The transpilation engine. Converts Dalvik DEX bytecode into JVM bytecode on the fly (`Dex2jar`), transforms instructions via ASM (`PluginBytecodeTransformer`), and sandboxes reflection calls.
* **`:player-abstraction`**: Direct JNA bindings to the native `libmpv` C-core for hardware-accelerated video decoding, plus a local Ktor Netty proxy (`LocalStreamProxy`) to handle custom stream headers.
* **`:common`**: Shared persistence using **SQLDelight** for local SQLite storage, settings, and watch history.
* **`:desktop-app`**: The desktop presentation layer:
  * **Compose Multiplatform UI** with custom Amoled dark theme and desktop window management.
  * **Offline Turbo Downloader:** Multi-threaded chunked downloader (HTTP Range) and HLS segment downloader with local queue management.
  * **TMDB Metadata Enrichment:** Call-sheet indexing, accurate billing order, dual actor/crew role handling, and per-season cast switching.

---

## Quick Start (Windows)

### Requirements
* **JDK 21** or higher (e.g. [Eclipse Temurin](https://adoptium.net/))
* **Git**

### 1. Clone With Submodules
You must clone recursively so submodules are pulled properly:
```bash
git clone --recursive https://github.com/errorcode26/CS3-desktop-client-unofficial.git
cd CS3-desktop-client-unofficial
```

### 2. Run
Native `libmpv` 64-bit Windows binaries are pre-bundled in the repository.

```bat
# Launch in dev mode with live LogCat (F12)
.\launch.bat dev

# Launch standard release mode
.\launch.bat release

# Compile standalone release .exe
.\launch.bat build
```

---

## Contributing & Forking Policy

* **Ideas & Bugs:** Feel free to suggest ideas or report bugs. If something looks fun, I might tinker with it, but there are **zero guarantees, timelines, or obligations**.
* **Want Specific Changes?** Fork the repository and build it yourself.
* **Strict Reminder:** **ABSOLUTELY NO ADS** on any forks or derivative builds.

---

## Disclaimer

This is a blank-slate media shell and runtime harness. It does not ship with, host, distribute, or pre-configure any plugins, streaming links, or copyrighted media files. 

Run at your own risk; I take zero responsibility if your CPU fans launch your PC into low Earth orbit.
