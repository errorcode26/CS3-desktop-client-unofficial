class GithubRelease {
  final String tagName;
  final String htmlUrl;
  final String body;
  final List<GithubAsset> assets;
  final bool prerelease;
  final DateTime? publishedAt;

  GithubRelease({
    required this.tagName,
    required this.htmlUrl,
    required this.body,
    required this.assets,
    required this.prerelease,
    this.publishedAt,
  });

  factory GithubRelease.fromJson(Map<String, dynamic> json) {
    return GithubRelease(
      tagName: json['tag_name'] as String? ?? '',
      htmlUrl: json['html_url'] as String? ?? '',
      body: json['body'] as String? ?? '',
      prerelease: json['prerelease'] as bool? ?? false,
      publishedAt: json['published_at'] != null
          ? DateTime.tryParse(json['published_at'] as String)
          : null,
      assets:
          (json['assets'] as List<dynamic>?)
              ?.map((e) => GithubAsset.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}

class GithubAsset {
  final String name;
  final String browserDownloadUrl;
  final int size;
  final String contentType;

  GithubAsset({
    required this.name,
    required this.browserDownloadUrl,
    required this.size,
    required this.contentType,
  });

  factory GithubAsset.fromJson(Map<String, dynamic> json) {
    return GithubAsset(
      name: json['name'] as String? ?? '',
      browserDownloadUrl: json['browser_download_url'] as String? ?? '',
      size: json['size'] as int? ?? 0,
      contentType: json['content_type'] as String? ?? '',
    );
  }
}
