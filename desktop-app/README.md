# CloudStream Desktop App

This module contains the primary CloudStream Desktop client, built using Compose for Desktop and Kotlin Multiplatform.

## Overview

Unlike the Android application, this module operates in a standard JVM desktop environment. To run plugins designed for Android, the client integrates with `:plugin-runtime` for Dalvik DEX-to-JVM transpilation and `:android-stubs` for Android platform compatibility.

## Architecture Guidelines

- **UI Framework:** All UI is written in Compose Multiplatform following an MVI architecture with reactive StateFlows.
- **Unified Dialog System:** All popups and dialogs MUST use `CloudstreamAlertDialog` or `CloudstreamCustomDialog` from `com.lagradost.cloudstream3.desktop.ui.components.CloudstreamDialogs` to maintain visual consistency and Amoled Pure Black theme support.
- **Thread Safety:** Database writes and file I/O must always run on background dispatchers (`Dispatchers.IO`).
- **Compilation:** Use `launch.bat` (or `launch.bat dev` / `launch.bat build`) in the root directory for development and packaging.
