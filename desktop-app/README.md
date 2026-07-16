# CloudStream Desktop App

This module contains the primary CloudStream Desktop client, built using Compose Desktop.

## Overview

Unlike the Android application, this module operates in a JVM desktop environment. To run plugins that were designed exclusively for Android, this module includes an extension loader and custom Android API stubs (`android.*`, `androidx.*`) that mimic the Android framework on Windows.

## Development

- All UI is written in Compose Multiplatform.
- Android APIs utilized by plugins (such as `android.view.View` and `android.content.Context`) must be manually stubbed here if they do not yet exist.
- Always use `compile.bat` in the root workspace to compile the `.exe`.
