#!/bin/bash
set -e

# Directory setup
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
PLUGIN_IOS_DIR="$PROJECT_ROOT/packages/flutter_torrent_server/ios"
TEMP_DIR="$PROJECT_ROOT/temp_build_torrserver"

echo "=== Starting iOS Framework Build ==="

# 1. Install gomobile if missing
if ! command -v gomobile &> /dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    export PATH=$PATH:$(go env GOPATH)/bin
fi

# 2. Init gomobile
echo "Initializing gomobile..."
gomobile init

# 3. Clone Source
echo "Cloning TorrServer source..."
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"
git clone --depth 1 https://github.com/Diegopyl1209/torrentserver-aniyomi "$TEMP_DIR"

# 4. Build Framework
echo "Building TorrServer.xcframework (this may take a while)..."
cd "$TEMP_DIR"
# The package to bind is the root of the repo which contains the exposed methods
gomobile bind -target=ios -o TorrServer.xcframework .

# 5. Move to Plugin
echo "Moving framework to plugin..."
mkdir -p "$PLUGIN_IOS_DIR"
rm -rf "$PLUGIN_IOS_DIR/TorrServer.xcframework"
mv TorrServer.xcframework "$PLUGIN_IOS_DIR/"

# Cleanup
# cd "$PROJECT_ROOT"
# rm -rf "$TEMP_DIR"

echo "=== Build Complete! ==="
echo "Framework located at: $PLUGIN_IOS_DIR/TorrServer.xcframework"
