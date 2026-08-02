import 'dart:io';
import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import '../models/extension_plugin.dart';
import 'dart:convert';

// Args/result types for the `compute()`-offloaded plugin install.
// Kept at file scope so the function passed to compute is top-level.
class _InstallArgs {
  final Uint8List bytes;
  final String pluginsRootPath;
  final String? explicitRepoId;
  _InstallArgs(this.bytes, this.pluginsRootPath, this.explicitRepoId);
}

class _InstallResult {
  final Map<String, dynamic> manifestMap;
  final String repoId;
  _InstallResult(this.manifestMap, this.repoId);
}

Future<_InstallResult> _installPluginIsolate(_InstallArgs args) async {
  final archive = ZipDecoder().decodeBytes(args.bytes);

  final jsonFile = archive.findFile('plugin.json');
  if (jsonFile == null) {
    throw Exception('Invalid .sky: Missing plugin.json (V2 Standard required)');
  }

  final jsonContent = utf8.decode(jsonFile.content as List<int>);
  final Map<String, dynamic> manifestMap;
  try {
    manifestMap = jsonDecode(jsonContent) as Map<String, dynamic>;
  } catch (e) {
    throw Exception('Failed to parse plugin.json: $e');
  }

  if (manifestMap['packageName'] == null && manifestMap['id'] == null) {
    throw Exception("Plugin Manifest (plugin.json) missing 'packageName'");
  }

  final packageName =
      (manifestMap['packageName'] ?? manifestMap['id']) as String;
  final repoId = args.explicitRepoId ?? 'UnknownRepo';
  final targetDir = Directory(p.join(args.pluginsRootPath, packageName));

  // Atomic-ish install: extract to a temp sibling dir, then swap in.
  // If extraction fails partway, the previous install stays intact
  // (audit M14 — old code did `targetDir.delete(...)` BEFORE extraction,
  // so any failure left the user with no plugin at all).
  final stagingDir = Directory(
    p.join(
      args.pluginsRootPath,
      '.tmp-install-$packageName-${DateTime.now().millisecondsSinceEpoch}',
    ),
  );
  if (await stagingDir.exists()) {
    await stagingDir.delete(recursive: true);
  }
  await stagingDir.create(recursive: true);

  try {
    for (final entity in archive) {
      if (entity.isFile) {
        final filename = entity.name;
        // Block zip-slip: refuse paths trying to escape the staging dir.
        if (filename.contains('..')) continue;

        final data = entity.content as List<int>;
        final outFile = File(p.join(stagingDir.path, filename));
        await outFile.parent.create(recursive: true);
        await outFile.writeAsBytes(data);
      }
    }

    // Audit B5 / PR-08c — capture the install-time SHA-256 of the
    // plugin's executable (plugin.js) so the runtime can detect tampering
    // before evaluation. Tamper-evident, not tamper-proof — anyone with
    // filesystem write access can change both the .js and the meta hash,
    // but that's a higher bar than just dropping in a malicious .js.
    final installedJs = File(p.join(stagingDir.path, 'plugin.js'));
    String? installSha256;
    if (await installedJs.exists()) {
      final jsBytes = await installedJs.readAsBytes();
      installSha256 = crypto.sha256.convert(jsBytes).toString();
    }

    final metaFile = File(p.join(stagingDir.path, 'meta.json'));
    final metaData = Map<String, dynamic>.from(manifestMap);
    metaData['repositoryId'] = args.explicitRepoId;
    if (installSha256 != null) {
      metaData['installSha256'] = installSha256;
      metaData['installSha256At'] = DateTime.now().toIso8601String();
    }
    await metaFile.writeAsString(jsonEncode(metaData));

    // Commit: swap staging → target. The brief window between delete and
    // rename is the only point where the old version is gone; if the
    // process is killed inside that window the user can re-install. There
    // is no atomic dir-replace primitive in dart:io on Windows.
    if (await targetDir.exists()) {
      await targetDir.delete(recursive: true);
    }
    await stagingDir.rename(targetDir.path);
  } catch (e) {
    // Best-effort cleanup of the half-written staging dir. The previous
    // install (if any) is still intact at targetDir.
    try {
      if (await stagingDir.exists()) {
        await stagingDir.delete(recursive: true);
      }
    } catch (_) {}
    rethrow;
  }

  return _InstallResult(manifestMap, repoId);
}

class PluginStorageService {
  /// Root directory for extensions: app_doc_dir/extensions/plugin/
  Future<Directory> get _pluginsDir async {
    final appDocDir = await getApplicationSupportDirectory();
    final dir = Directory(p.join(appDocDir.path, 'extensions', 'plugin'));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  /// Installs a plugin from a .sky (Zip) file.
  ///
  /// 1. Reads the zip.
  /// 2. Extracts `plugin.json`.
  /// 3. Parses the JSON for metadata (ID, Version, etc).
  /// 4. Installs to: plugin/[ID]/
  /// 5. Generates `meta.json` for caching.
  ///
  /// The ZIP decode + extraction is delegated to a background isolate via
  /// `compute()` so a multi-MB plugin doesn't freeze the UI thread during
  /// install (audit B11).
  Future<ExtensionPlugin?> installPlugin(
    String filePath,
    String? explicitRepoId,
  ) async {
    if (kDebugMode) {
      debugPrint("PluginStorageService: Installing .sky from $filePath");
    }
    final file = File(filePath);
    if (!await file.exists()) throw Exception("Plugin file not found");

    final bytes = await file.readAsBytes();
    final rootDir = await _pluginsDir;

    final result = await compute(
      _installPluginIsolate,
      _InstallArgs(bytes, rootDir.path, explicitRepoId),
    );

    final plugin = ExtensionPlugin.fromJson(result.manifestMap, result.repoId);

    if (kDebugMode) {
      debugPrint(
        "PluginStorageService: Installation complete for ${plugin.packageName}",
      );
    }
    return plugin;
  }

  /// Deletes a plugin directory (by Package Name)
  Future<void> deletePlugin(ExtensionPlugin plugin) async {
    final rootDir = await _pluginsDir;
    final pluginDir = Directory(p.join(rootDir.path, plugin.packageName));
    if (await pluginDir.exists()) {
      await pluginDir.delete(recursive: true);
    }
  }

  /// Deletes an entire repository folder
  Future<void> deleteRepository(String repoId) async {
    final rootDir = await _pluginsDir;
    final repoDir = Directory(p.join(rootDir.path, repoId));

    if (await repoDir.exists()) {
      await repoDir.delete(recursive: true);
    }
  }

  /// List all installed plugins
  Future<List<ExtensionPlugin>> listInstalledPlugins() async {
    final plugins = <ExtensionPlugin>[];
    final rootDir = await _pluginsDir;

    if (!await rootDir.exists()) return [];

    // Async list avoids blocking the UI thread on slow scoped storage on
    // Android. Audit H25.
    final children = await rootDir.list().toList();

    for (final entity in children) {
      if (entity is Directory) {
        try {
          // Check for meta.json (New System)
          final metaFile = File(p.join(entity.path, 'meta.json'));
          if (await metaFile.exists()) {
            final content = await metaFile.readAsString();
            final json = jsonDecode(content) as Map<String, dynamic>;
            final repoId = json['repositoryId'] as String? ?? 'Local';
            plugins.add(ExtensionPlugin.fromJson(json, repoId));
            continue;
          }

          // Check for plugin.json (Plugin v2 Standard)
          final jsonFile = File(p.join(entity.path, 'plugin.json'));
          if (await jsonFile.exists()) {
            final content = await jsonFile.readAsString();
            final manifest = jsonDecode(content) as Map<String, dynamic>;
            // Auto-generate meta.json if missing
            const repoId = 'Local';
            manifest['repositoryId'] = repoId;
            await metaFile.writeAsString(jsonEncode(manifest));
            plugins.add(ExtensionPlugin.fromJson(manifest, repoId));
            continue;
          }

          // Legacy Folder Structure Loop (Optional: if we still want to see old plugins?)
          // User said "no backward compatibility", so we can ignore nested repo dirs if they don't follow new structure.
        } catch (e) {
          if (kDebugMode) {
            debugPrint("Error reading plugin at ${entity.path}: $e");
          }
        }
      }
    }
    return plugins;
  }

  /// Get full path to the JS file for a plugin
  Future<String> getPluginJsPath(ExtensionPlugin plugin) async {
    if (plugin.repositoryId == 'LocalAssets') {
      return plugin.sourceUrl;
    }

    final rootDir = await _pluginsDir;
    // New Path: plugin/[packageName]/plugin.js
    final jsFile = File(p.join(rootDir.path, plugin.packageName, 'plugin.js'));
    return jsFile.path;
  }

  /// Verifies the on-disk `plugin.js` matches the SHA-256 captured at
  /// install time (stored in meta.json's `installSha256` field). Returns:
  ///
  /// - `true` — hash matches, OR the manifest has no `installSha256`
  ///   (legacy installs without a hash get a pass — they were installed
  ///   before PR-08c so we have nothing to compare against).
  /// - `false` — hash mismatch; refuse to load the plugin.
  ///
  /// Asset-bundled plugins (`repositoryId == 'LocalAssets'`) always pass
  /// — they're shipped inside the signed APK / IPA.
  /// Audit B5 / PR-08c.
  Future<bool> verifyIntegrity(ExtensionPlugin plugin) async {
    if (plugin.repositoryId == 'LocalAssets') return true;

    final rootDir = await _pluginsDir;
    final pluginDir = Directory(p.join(rootDir.path, plugin.packageName));
    final metaFile = File(p.join(pluginDir.path, 'meta.json'));
    if (!await metaFile.exists()) {
      if (kDebugMode) {
        debugPrint(
          'verifyIntegrity(${plugin.packageName}): meta.json missing — '
          'treating as legacy install (pass).',
        );
      }
      return true;
    }

    final metaJson =
        jsonDecode(await metaFile.readAsString()) as Map<String, dynamic>;
    final expected = metaJson['installSha256'] as String?;
    if (expected == null || expected.isEmpty) {
      // Legacy install pre-PR-08c — no recorded hash. Pass with a
      // debug log so we know who's still on the older format.
      if (kDebugMode) {
        debugPrint(
          'verifyIntegrity(${plugin.packageName}): no installSha256 in '
          'meta.json — legacy install (pass).',
        );
      }
      return true;
    }

    final jsFile = File(p.join(pluginDir.path, 'plugin.js'));
    if (!await jsFile.exists()) return false;

    final actual = crypto.sha256.convert(await jsFile.readAsBytes()).toString();
    if (actual != expected) {
      if (kDebugMode) {
        debugPrint(
          'verifyIntegrity(${plugin.packageName}): HASH MISMATCH. '
          'expected=$expected actual=$actual',
        );
      }
      return false;
    }
    return true;
  }
}
