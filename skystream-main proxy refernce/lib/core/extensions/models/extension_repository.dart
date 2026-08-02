import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'extension_plugin.dart';

class ExtensionRepository {
  final String name;
  final String url;
  final String? description;
  final String? iconUrl;
  final int manifestVersion;
  final List<String> pluginLists;
  final List<String> includedRepos;
  final List<ExtensionPlugin> plugins; // NEW: Direct plugin list
  final String? _explicitId;

  ExtensionRepository({
    required this.name,
    required this.url,
    required this.pluginLists,
    this.includedRepos = const [],
    this.plugins = const [],
    this.description,
    this.iconUrl,
    this.manifestVersion = 1,
    String? explicitId,
  }) : _explicitId = explicitId;

  /// The Package Namespace.
  /// Returns explicit Package Name or falls back to Hash(Url).
  String get packageName =>
      _explicitId ??
      sha256.convert(utf8.encode(url)).toString().substring(0, 10);

  /// Factory constructor to parse from JSON
  factory ExtensionRepository.fromJson(Map<String, dynamic> json, String url) {
    final repoId = json['packageName'] as String? ?? 'Unknown';
    return ExtensionRepository(
      name: json['name'] as String? ?? 'Unknown Repository',
      url: url,
      pluginLists:
          (json['pluginLists'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      includedRepos:
          (json['repos'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      plugins:
          (json['plugins'] as List<dynamic>?)
              ?.map(
                (e) =>
                    ExtensionPlugin.fromJson(e as Map<String, dynamic>, repoId),
              )
              .toList() ??
          [],
      description: json['description'] as String?,
      iconUrl: json['iconUrl'] as String?,
      manifestVersion: json['manifestVersion'] as int? ?? 1,
      explicitId: json['packageName'] as String?,
    );
  }
}
