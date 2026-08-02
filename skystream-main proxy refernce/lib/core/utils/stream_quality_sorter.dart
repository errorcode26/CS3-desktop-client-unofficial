import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import '../../features/settings/presentation/player_settings_provider.dart';
import '../domain/entity/multimedia_item.dart';

/// Internal quality tier, derived by parsing a source's label string.
/// Order matters — higher index = higher quality.
enum _QualityTier {
  unknown, // no detectable quality info
  q360,
  q480,
  q720,
  q1080,
  q4k,
}

extension on _QualityTier {
  int get rank => index; // unknown=0, q360=1, q480=2, q720=3, q1080=4, q4k=5
}

extension on QualityPreference {
  /// Maps a preference to the equivalent tier rank (-1 = no preference).
  int get rank {
    switch (this) {
      case QualityPreference.any:
        return -1;
      case QualityPreference.q360:
        return _QualityTier.q360.rank;
      case QualityPreference.q480:
        return _QualityTier.q480.rank;
      case QualityPreference.q720:
        return _QualityTier.q720.rank;
      case QualityPreference.q1080:
        return _QualityTier.q1080.rank;
      case QualityPreference.q4k:
        return _QualityTier.q4k.rank;
    }
  }

  String getLabel(AppLocalizations l10n) {
    switch (this) {
      case QualityPreference.any:
        return l10n.anyNoPreference;
      case QualityPreference.q360:
        return '360p';
      case QualityPreference.q480:
        return '480p (SD)';
      case QualityPreference.q720:
        return '720p (HD)';
      case QualityPreference.q1080:
        return '1080p (FHD)';
      case QualityPreference.q4k:
        return '4K (UHD)';
    }
  }
}

String qualityPreferenceLabel(QualityPreference q, AppLocalizations l10n) =>
    q.getLabel(l10n);

/// Detects the quality tier from a source label string.
/// Checks in descending quality order to avoid false matches
/// (e.g. "Full HD" matching "hd" before "fhd").
_QualityTier _detectTier(String sourceLabel) {
  final s = sourceLabel.toLowerCase();

  // 4K tier
  if (s.contains('4k') ||
      s.contains('2160') ||
      s.contains('2160p') ||
      s.contains('uhd') ||
      s.contains('ultra hd') ||
      s.contains('ultrahd') ||
      s.contains('ultra-hd') ||
      s.contains('4096') ||
      s.contains('dci 4k') ||
      s.contains('dci4k')) {
    return _QualityTier.q4k;
  }

  // 1080p / FHD tier — check before generic "hd"
  if (s.contains('1080') ||
      s.contains('1080p') ||
      s.contains('fhd') ||
      s.contains('full hd') ||
      s.contains('fullhd') ||
      s.contains('full-hd') ||
      s.contains('1920x1080') ||
      s.contains('1080i')) {
    return _QualityTier.q1080;
  }

  // 720p / HD tier
  if (s.contains('720') ||
      s.contains('720p') ||
      s.contains(' hd') ||
      s.startsWith('hd') ||
      s.contains('mid hd') ||
      s.contains('hd ') ||
      s.contains('1280x720') ||
      s.contains('hd+') ||
      s.endsWith(' hd')) {
    return _QualityTier.q720;
  }

  // 480p / SD tier
  if (s.contains('480') ||
      s.contains('480p') ||
      s.contains(' sd') ||
      s.startsWith('sd') ||
      s.contains('low hd') ||
      s.contains('854x480') ||
      s.endsWith(' sd') ||
      s.contains('sd+')) {
    return _QualityTier.q480;
  }

  // 360p tier
  if (s.contains('360') ||
      s.contains('360p') ||
      s.contains('640x360') ||
      s.contains('low') ||
      s.contains('lowest')) {
    return _QualityTier.q360;
  }

  return _QualityTier.unknown;
}

/// Returns a sort key for [tier] given [prefRank].
/// - Preferred tier  → 0
/// - Lower tiers     → 1, 2, … (closer lower = smaller key)
/// - Higher tiers    → rank (always > any lower-tier key)
/// - Unknown/auto    → 100 (always last)
int _sortKey(_QualityTier tier, int prefRank) {
  if (tier == _QualityTier.unknown) return 100;
  final r = tier.rank;
  if (r == prefRank) return 0;
  if (r < prefRank) return prefRank - r; // 1 … prefRank
  return r; // r > prefRank; always > any lower-tier key
}

/// Detects whether the device is currently on Wi-Fi.
/// Returns `true` for Wi-Fi, `false` for everything else (mobile, none, unknown).
/// Falls back to `false` on any error so mobile quality preference is used.
Future<bool> isOnWifi() async {
  try {
    final results = await Connectivity().checkConnectivity();
    return results.contains(ConnectivityResult.wifi);
  } catch (_) {
    return false;
  }
}

/// Sorts [streams] by quality preference without changing the original list.
///
/// If [preference] is [QualityPreference.any] the list is returned unchanged.
/// Within the same quality tier the original relative order is preserved
/// (stable sort).
///
/// Sort order for a given preference P (example: 1080p):
///   1080p → 720p → 480p → 360p → 4K → unknown/auto
List<StreamResult> sortStreamsByQuality(
  List<StreamResult> streams,
  QualityPreference preference,
) {
  if (preference == QualityPreference.any || streams.length <= 1) {
    return streams;
  }

  final prefRank = preference.rank;
  final indexed = streams
      .asMap()
      .entries
      .map(
        (e) =>
            (index: e.key, stream: e.value, tier: _detectTier(e.value.source)),
      )
      .toList();

  indexed.sort((a, b) {
    final ka = _sortKey(a.tier, prefRank);
    final kb = _sortKey(b.tier, prefRank);
    if (ka != kb) return ka.compareTo(kb);
    return a.index.compareTo(b.index); // stable: preserve original order
  });

  return indexed.map((e) => e.stream).toList();
}

/// Filters [streams] to only those satisfying [mode] relative to [preference].
///
/// - [QualityFilterMode.atOrAbove]: keeps streams at or above the preferred tier.
/// - [QualityFilterMode.atOrBelow]: keeps streams at or below the preferred tier.
/// - [QualityFilterMode.any]: returns [streams] unchanged.
///
/// If the filter would produce an empty list the original [streams] list is
/// returned unchanged and [didFallback] (if provided) is set to `true`. This
/// prevents showing an empty Sources tab when the plugin returns no source that
/// matches the preference.
List<StreamResult> filterStreamsByQuality(
  List<StreamResult> streams,
  QualityPreference preference,
  QualityFilterMode mode, {
  void Function(bool)? onFallback,
}) {
  if (mode == QualityFilterMode.any ||
      preference == QualityPreference.any ||
      streams.isEmpty) {
    onFallback?.call(false);
    return streams;
  }

  final prefRank = preference.rank;

  final filtered = streams.where((s) {
    final tier = _detectTier(s.source);
    if (tier == _QualityTier.unknown) return true; // always keep auto/unknown
    final r = tier.rank;
    if (mode == QualityFilterMode.atOrAbove) return r >= prefRank;
    if (mode == QualityFilterMode.atOrBelow) return r <= prefRank;
    return true;
  }).toList();

  if (filtered.isEmpty) {
    onFallback?.call(true);
    return streams; // fallback: show all rather than empty list
  }

  onFallback?.call(false);
  return filtered;
}

/// Returns a short human-readable quality label for [stream] suitable for
/// display as a badge in the Sources tab (e.g. "1080p", "720p", "Auto").
String qualityBadgeLabel(StreamResult stream) {
  switch (_detectTier(stream.source)) {
    case _QualityTier.q4k:
      return '4K';
    case _QualityTier.q1080:
      return '1080p';
    case _QualityTier.q720:
      return '720p';
    case _QualityTier.q480:
      return '480p';
    case _QualityTier.q360:
      return '360p';
    case _QualityTier.unknown:
      return 'Auto';
  }
}
