import 'package:dio/dio.dart';

class OnlineSubtitle {
  final String id;
  final String name;
  final String language;
  final String source; // e.g., "OpenSubtitles", "SubDL", "SubSource"
  final String downloadUrl;
  final bool isHearingImpaired;
  final Map<String, dynamic>? metadata;

  const OnlineSubtitle({
    required this.id,
    required this.name,
    required this.language,
    required this.source,
    required this.downloadUrl,
    this.isHearingImpaired = false,
    this.metadata,
  });

  factory OnlineSubtitle.fromJson(Map<String, dynamic> json) {
    return OnlineSubtitle(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      language: json['language'] as String? ?? 'English',
      source: json['source'] as String? ?? 'Unknown',
      downloadUrl: json['downloadUrl'] as String? ?? '',
      isHearingImpaired: json['isHearingImpaired'] as bool? ?? false,
      metadata: json['metadata'] as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'language': language,
      'source': source,
      'downloadUrl': downloadUrl,
      'isHearingImpaired': isHearingImpaired,
      'metadata': metadata,
    };
  }
}

abstract class SubtitleProvider {
  String get name;
  String get idPrefix;

  static const Map<String, String> commonHeaders = {
    'User-Agent': 'SkyStream v2.2.1',
    'Accept': 'application/json',
  };

  Future<List<OnlineSubtitle>> search({
    required String query,
    String? imdbId,
    int? tmdbId,
    int? season,
    int? episode,
    String? language,
    CancelToken? cancelToken,
  });

  Future<String?> getDownloadUrl(OnlineSubtitle subtitle);

  /// Returns provider-specific headers needed when downloading
  /// the actual subtitle file from the resolved URL.
  Map<String, String> getDownloadHeaders(OnlineSubtitle subtitle) {
    return commonHeaders;
  }
}
