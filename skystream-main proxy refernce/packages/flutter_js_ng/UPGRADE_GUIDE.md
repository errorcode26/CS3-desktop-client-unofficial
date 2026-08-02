# QuickJS-NG Upgrade Guide for flutter_js_ng

## Architecture Overview

This forked package uses a **dual-engine** strategy (inherited from the upstream `flutter_js`):

| Platform | JS Engine | Source |
| :--- | :--- | :--- |
| Android | **QuickJS-NG** (upgraded) | Compiled from source via CMake/NDK |
| Windows | **QuickJS-NG** (upgraded) | Pre-built `quickjs_c_bridge.dll` |
| Linux | **QuickJS-NG** (upgraded) | Pre-built `libquickjs_c_bridge_plugin.so` |
| iOS | JavaScriptCore (Apple) | System framework (no change) |
| macOS | JavaScriptCore (Apple) | System framework (no change) |

## How to Upgrade the QuickJS-NG Source

### Android (Compiled from Source)
The original `flutter_js` plugin stripped the C compiler components from Android to use a precompiled JitPack library (`fastdev-jsruntimes-quickjs:0.3.6`).

This forked version has re-enabled `externalNativeBuild` inside `android/build.gradle` and completely bypasses the external JitPack dependency so QuickJS-NG compiles directly alongside your Flutter Android app.

To upgrade QuickJS-NG on Android:
1. Run the `Build QuickJS-NG Natives` GitHub Action.
2. The workflow will automatically compile and commit all 16 QuickJS-NG source files into `packages/flutter_js_ng/android/src/main/c/quickjs/`.
3. Run `flutter build apk` (Android CMake will automatically build it into `libfastdev_quickjs_runtime.so`).

### Windows and Linux (Pre-built Binaries)
The GitHub Action natively cross-compiles both x64 and ARM64 shared libraries and automatically commits them to your repository:

- **Windows x64 & ARM64**: Auto-committed to `windows/shared/<arch>/quickjs_c_bridge.dll`.
- **Linux x64 & ARM64**: Auto-committed to `linux/shared/<arch>/libquickjs_c_bridge_plugin.so`.

## GitHub Actions Workflow

Run the `Build QuickJS-NG Natives` workflow to rebuild all pre-built binaries.
The workflow:
1. Clones the QuickJS-NG source
2. Clones the flutter_js C bridge source
3. Cross-compiles for Windows (MSVC) and Linux (GCC)
4. Generates ZIP artifacts containing the pre-built binaries and Android sources

## Important Notes

- The C bridge layer (`libfastdev_quickjs_runtime.cpp`) wraps the QuickJS C API
  with Flutter-compatible function signatures. QuickJS-NG maintains API compatibility
  with the original QuickJS, so this bridge works without modifications.
- iOS and macOS are unaffected — they use Apple's JavaScriptCore, which is already
  one of the fastest JS engines available.
