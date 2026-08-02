import '../config/tmdb_config.dart';

/// Utility for normalizing image URLs.
///
/// All methods return `null` when the image is missing — callers should use
/// their local [ThumbnailErrorPlaceholder] / [ShimmerPlaceholder] widgets
/// instead of fetching a network placeholder.
class AppImageFallbacks {
  // ---------------------------------------------------------------------------
  // Provider / plugin image URLs
  // ---------------------------------------------------------------------------

  /// Returns the normalized URL, or null if missing/blank.
  static String? poster(String? imageUrl, {String? label}) {
    return _normalize(imageUrl);
  }

  /// Returns the URL as-is if non-empty, or null.
  static String? optional(String? imageUrl) => _normalize(imageUrl);

  // ---------------------------------------------------------------------------
  // TMDB image URLs
  // ---------------------------------------------------------------------------

  static String? tmdbPoster(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.posterSizeUrl);
  }

  static String? tmdbThumbnail(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.profileSizeUrl);
  }

  static String? tmdbBackdrop(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.backdropSizeUrl);
  }

  static String? tmdbProfile(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.profileSizeUrl);
  }

  static String? tmdbLogo(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.imageBaseUrl);
  }

  static String? tmdbStill(String? path, {String? label}) {
    return _tmdb(path, baseUrl: TmdbConfig.imageBaseUrl);
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  static String? _tmdb(String? path, {required String baseUrl}) {
    final normalized = _normalize(path);
    if (normalized == null) return null;
    if (normalized.startsWith('http')) return normalized;
    return '$baseUrl$normalized';
  }

  static String? _normalize(String? value) {
    final trimmed = value?.trim();
    return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
  }
}
