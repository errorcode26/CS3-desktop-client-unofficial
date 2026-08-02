import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class AppUtils {
  // Registered by main() before first runApp call.
  static void Function()? _restartImpl;

  static void setRestartFunction(void Function() fn) {
    _restartImpl = fn;
  }

  /// Two-phase restart:
  /// Phase 1 — replace the widget tree with an empty widget so Flutter
  ///            disposes the old ProviderScope, GoRouter GlobalKey, and all
  ///            stream subscriptions in the current frame.
  /// Phase 2 — after one addPostFrameCallback (disposal frame complete),
  ///            call the registered rebuild function to mount a fresh tree.
  static Future<void> restartApp(BuildContext? context) async {
    final fn = _restartImpl;
    if (fn == null) {
      if (kDebugMode) {
        debugPrint("AppUtils.restartApp: no restart function registered");
      }
      return;
    }

    // Phase 1: clear the entire widget tree.
    runApp(const SizedBox.shrink());

    // Phase 2: wait for the disposal frame to complete, then rebuild.
    final completer = Completer<void>();
    WidgetsBinding.instance.addPostFrameCallback((_) => completer.complete());
    await completer.future;

    fn();
  }

  static bool isLocalFile(String path) {
    if (path.isEmpty) return false;
    // Android/Linux/macOS absolute
    if (path.startsWith('/')) return true;
    // Windows absolute (C:\ or D:/)
    if (RegExp(r'^[a-zA-Z]:[\\/]').hasMatch(path)) return true;
    // File URL
    if (path.startsWith('file:')) return true;
    return false;
  }

  static String normalizeUrl(String url) {
    if (url.isEmpty) return url;
    if (Platform.isAndroid) return url;

    if (isLocalFile(url) && !url.startsWith('file:')) {
      if (url.startsWith('/')) {
        return 'file://$url';
      }
      // Windows absolute path like C:\
      // Standardize on forward slashes for valid file:/// URIs
      final standardizedPath = url.replaceAll('\\', '/');
      return 'file:///$standardizedPath';
    }
    return url;
  }
}
