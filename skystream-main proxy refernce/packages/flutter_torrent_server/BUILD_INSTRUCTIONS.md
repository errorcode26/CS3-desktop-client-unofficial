# Building the Native Torrent Server

Since `go` is not installed on the system where the core planning was done, you must compile the Go binaries yourself to jumpstart the Torrent Server integration.

## Prerequisites

1.  **Install Go**: [Download Go](https://go.dev/dl/) (version 1.20+ recommended).
2.  **Install Gomobile** (for Android/iOS):
    ```bash
    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
    ```
3.  **Android NDK** (for Android): Ensure you have the NDK installed via Android Studio.

## Build Script

Run the provided `build_libs.sh` script to generate the necessary libraries.

```bash
cd packages/flutter_torrent_server
chmod +x build_libs.sh
./build_libs.sh
```

## What this does
1.  **Android**: Generates `android/libs/torrentserver.aar` (contains Java bindings + native libs).
2.  **iOS**: Generates `ios/Frameworks/TorrServer.xcframework`.
3.  **Desktop (Executables)**: Generates standalone binaries for multiple architectures in `assets/torrserver/`:
    *   `TorrServer-darwin-amd64` (macOS Intel)
    *   `TorrServer-darwin-arm64` (macOS Apple Silicon)
    *   `TorrServer-linux-amd64` (Linux x86_64)
    *   `TorrServer-linux-arm64` (Linux ARM64/aarch64)
    *   `TorrServer-windows-amd64.exe` (Windows x86_64)
    *   `TorrServer-windows-arm64.exe` (Windows ARM64/aarch64)
4.  **Desktop (Shared Libs)**: Generates legacy shared libraries (`.dylib`, `.dll`, `.so`) in `assets/`.

## Post-Build Setup
After running the script, the Flutter plugin will automatically pick the correct binary based on the host architecture during initialization.
