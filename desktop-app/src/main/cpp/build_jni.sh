#!/usr/bin/env bash
# A shell script to compile the JNI native bridge for CloudStream Desktop on Linux.
# Requirements: g++ (or clang++), and JAVA_HOME environment variable set.

set -e

SO_DIR="../../../appResources/linux/jni"
mkdir -p "$SO_DIR"
SO_OUTPUT="$SO_DIR/libplayer_bridge.so"

if [ -z "$JAVA_HOME" ]; then
    echo "ERROR: JAVA_HOME environment variable is not set. Please set it to your JDK path."
    exit 1
fi

JAVA_INCLUDE="$JAVA_HOME/include"
JAVA_INCLUDE_LINUX="$JAVA_HOME/include/linux"
COMMON_INCLUDE="include"

echo "Compiling libplayer_bridge.so for Linux..."

g++ -shared -fPIC -std=c++17 -O2 \
    -o "$SO_OUTPUT" \
    common/mpv_core.cpp \
    linux/surface_linux.cpp \
    -I"$COMMON_INCLUDE" \
    -I"$JAVA_INCLUDE" \
    -I"$JAVA_INCLUDE_LINUX" \
    -lpthread -ldl

echo "Compilation successful! SO output to: $SO_OUTPUT"
