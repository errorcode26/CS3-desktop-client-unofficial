import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter_js/flutter_js.dart';

import '../../logger/app_logger.dart';

/// Compiles fully-wrapped IIFE scripts to QuickJS `.qbc` bytecode files.
///
/// A single [JavascriptRuntime] is reused for all compilations and disposed
/// after [_idleTimeout] of inactivity. This avoids creating a fresh native
/// QuickJS instance (~20–50 ms on Android) per plugin.
///
/// Only supported on platforms that use QuickJS (Android, Windows, Linux).
/// On iOS/macOS all calls are no-ops and return false — text eval is used.
class JsBytecodeCompiler {
  JsBytecodeCompiler._();

  static bool get supported =>
      !kIsWeb && (Platform.isAndroid || Platform.isWindows || Platform.isLinux);

  // ---------------------------------------------------------------------------
  // Singleton compile runtime
  // ---------------------------------------------------------------------------

  static JavascriptRuntime? _rt;
  static Timer? _idleTimer;
  static const _idleTimeout = Duration(seconds: 30);

  static JavascriptRuntime _runtime() {
    _idleTimer?.cancel();
    _rt ??= getJavascriptRuntime();
    _idleTimer = Timer(_idleTimeout, _releaseRuntime);
    return _rt!;
  }

  static void _releaseRuntime() {
    _idleTimer?.cancel();
    _idleTimer = null;
    try {
      _rt?.dispose();
    } catch (_) {}
    _rt = null;
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /// Compile [wrappedScript] and write the result to [qbcPath].
  /// Returns true on success, false if unsupported or on any error.
  static Future<bool> compile(String wrappedScript, String qbcPath) async {
    if (!supported) return false;

    // Yield before the synchronous FFI work so concurrent compile() calls
    // each get their own event-loop turn and frame callbacks can run between.
    await Future<void>.delayed(Duration.zero);

    try {
      final rt = _runtime();
      if (!rt.supportsBytecode) return false;

      final bytecode = rt.compileToBytes(wrappedScript);
      if (bytecode == null || bytecode.isEmpty) return false;

      await File(qbcPath).writeAsBytes(bytecode, flush: true);
      return true;
    } catch (e) {
      talker.error('JsBytecodeCompiler: compile failed → $qbcPath: $e');
      // Drop the runtime on error — it may be in a bad state.
      _releaseRuntime();
      return false;
    }
  }

  /// True if [qbcPath] is missing or older than [scriptPath].
  ///
  /// Async to avoid blocking the UI isolate on filesystem stat calls during
  /// plugin warm-up — Android scoped storage can be slow on cold cache
  /// (audit H26).
  static Future<bool> isStale(String scriptPath, String qbcPath) async {
    final qbc = File(qbcPath);
    if (!await qbc.exists()) return true;
    final script = File(scriptPath);
    if (!await script.exists()) return false;
    final scriptMtime = await script.lastModified();
    final qbcMtime = await qbc.lastModified();
    return scriptMtime.isAfter(qbcMtime);
  }
}
