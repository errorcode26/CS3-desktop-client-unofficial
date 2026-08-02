class PluginDomain {
  final String name;
  final String url;

  const PluginDomain({required this.name, required this.url});

  factory PluginDomain.fromJson(Map<String, dynamic> json) {
    return PluginDomain(
      name: json['name'] as String? ?? json['url'] as String? ?? '',
      url: json['url'] as String? ?? '',
    );
  }
}

class PluginSubProvider {
  final String id;
  final String name;

  /// Optional static URL. When null the JS resolves the URL dynamically via manifest.providerId.
  final String? baseUrl;

  const PluginSubProvider({required this.id, required this.name, this.baseUrl});

  factory PluginSubProvider.fromJson(Map<String, dynamic> json) {
    final rawUrl = json['baseUrl'] as String?;
    return PluginSubProvider(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? json['id'] as String? ?? '',
      baseUrl: (rawUrl != null && rawUrl.isNotEmpty) ? rawUrl : null,
    );
  }
}

class ExtensionPlugin {
  final String packageName; // Unique Plugin ID (e.g. "com.hexated.superstream")
  final String name; // Display Name (e.g. "SuperStream")
  final String repositoryId; // "com.hexated" or "LocalAssets"
  final String sourceUrl; // Download Link or Local Path
  final int version;
  final String? iconUrl;

  // Metadata (Native Parity)
  final List<String> authors; // e.g. ["Hexated"]
  final String? description; // e.g. "Watch movies and TV shows"
  final List<String> categories; // e.g. ["Movie", "Anime"]
  final List<String> languages; // e.g. ["en"]
  final int? fileSize; // In bytes
  final int status; // 0: Down, 1: Ok, 2: Slow, 3: Beta
  final Map<String, dynamic> manifest; // Raw JSON manifest
  final String? customBaseUrl; // User-defined override for baseUrl
  final List<PluginDomain>? domains; // Available mirror domains from manifest
  final List<PluginSubProvider>?
  providers; // Sub-providers (one JS, many feeds)

  ExtensionPlugin({
    required this.packageName,
    required this.name,
    required this.repositoryId,
    required this.sourceUrl,
    required this.version,
    this.status = 1,
    this.iconUrl,
    this.authors = const [],
    this.description,
    this.categories = const [],
    this.languages = const [],
    this.fileSize,
    this.manifest = const {},
    this.customBaseUrl,
    this.domains,
    this.providers,
  });

  /// Helper to check if this is a debug/asset plugin
  bool get isDebug => packageName.endsWith('.debug');

  /// Local File Path relative to plugin root
  /// e.g. "plugin/[packageName]/plugin.js"
  String get filePath => "$repositoryId/$packageName/plugin.js";

  static List<String> _readList(Map<String, dynamic> json, List<String> keys) {
    for (final key in keys) {
      final value = json[key];
      if (value is List) {
        return value.map((e) => e.toString()).toList();
      }
      if (value is String && value.trim().isNotEmpty) {
        return [value];
      }
    }
    return const [];
  }

  /// Factory constructor to parse from JSON (SitePlugin format or meta.json)
  factory ExtensionPlugin.fromJson(
    Map<String, dynamic> json,
    String repositoryId,
  ) {
    final String? packageName = json['packageName'] as String?;

    if (packageName == null) {
      throw Exception('Plugin manifest missing mandatory "packageName" field');
    }

    return ExtensionPlugin(
      packageName: packageName,
      name: json['name'] as String? ?? 'Unknown Plugin',
      repositoryId: repositoryId,
      sourceUrl: json['url'] as String? ?? '',
      version: json['version'] as int? ?? 1,
      status: json['status'] as int? ?? 1,
      iconUrl: json['iconUrl'] as String?,
      authors:
          (json['authors'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      description: json['description'] as String?,
      categories: _readList(json, ['categories', 'types', 'tvTypes']),
      languages: _readList(json, ['languages', 'language', 'lang']),
      fileSize: json['fileSize'] as int?,
      manifest: json,
      customBaseUrl: json['customBaseUrl'] as String?,
      domains: (json['domains'] as List<dynamic>?)
          ?.whereType<Map<dynamic, dynamic>>()
          .map((d) => PluginDomain.fromJson(Map<String, dynamic>.from(d)))
          .where((d) => d.url.isNotEmpty)
          .toList(),
      providers: (json['providers'] as List<dynamic>?)
          ?.whereType<Map<dynamic, dynamic>>()
          .map((p) => PluginSubProvider.fromJson(Map<String, dynamic>.from(p)))
          .where((p) => p.id.isNotEmpty)
          .toList(),
    );
  }

  ExtensionPlugin copyWith({String? customBaseUrl}) {
    return ExtensionPlugin(
      packageName: packageName,
      name: name,
      repositoryId: repositoryId,
      sourceUrl: sourceUrl,
      version: version,
      status: status,
      iconUrl: iconUrl,
      authors: authors,
      description: description,
      categories: categories,
      languages: languages,
      fileSize: fileSize,
      manifest: manifest,
      customBaseUrl: customBaseUrl ?? this.customBaseUrl,
      domains: domains,
      providers: providers,
    );
  }
}
