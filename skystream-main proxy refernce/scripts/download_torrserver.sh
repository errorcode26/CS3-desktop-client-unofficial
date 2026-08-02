#!/bin/bash
# scripts/download_torrserver.sh
# Downloads TorrServer binaries for Desktop platforms

set -e

# Define version
VERSION="MatriX.133" # Using a known stable version tag or "latest" if verified
BASE_URL="https://github.com/YouROK/TorrServer/releases/download/$VERSION"

# Target Directory
# Use path relative to this script to be safe
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TARGET_DIR="$SCRIPT_DIR/../packages/flutter_torrent_server/assets/torrserver"
mkdir -p "$TARGET_DIR"

echo "Downloading TorrServer binaries to $TARGET_DIR..."

# macOS (Darwin) - AMD64 (Intel) & ARM64 (Apple Silicon)
# Note: Matrix release naming convention might vary. Checking...
# Actually, YouROK/TorrServer releases usually look like: TorrServer-darwin-amd64, TorrServer-darwin-arm64
echo "Downloading macOS binaries..."
curl -L -o "$TARGET_DIR/TorrServer-darwin-amd64" "$BASE_URL/TorrServer-darwin-amd64"
chmod +x "$TARGET_DIR/TorrServer-darwin-amd64"
curl -L -o "$TARGET_DIR/TorrServer-darwin-arm64" "$BASE_URL/TorrServer-darwin-arm64"
chmod +x "$TARGET_DIR/TorrServer-darwin-arm64"

# Windows - AMD64
echo "Downloading Windows binary..."
curl -L -o "$TARGET_DIR/TorrServer-windows-amd64.exe" "$BASE_URL/TorrServer-windows-amd64.exe"

# Linux - AMD64
echo "Downloading Linux binary..."
curl -L -o "$TARGET_DIR/TorrServer-linux-amd64" "$BASE_URL/TorrServer-linux-amd64"
chmod +x "$TARGET_DIR/TorrServer-linux-amd64"

echo "Download complete."
ls -l "$TARGET_DIR"
