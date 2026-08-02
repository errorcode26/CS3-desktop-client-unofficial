import 'dart:collection';

import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'skip_service.dart';
import '../../../../core/logger/app_logger.dart';
import '../../../../core/network/dio_client_provider.dart';

part 'intro_db_service.g.dart';

class IntroDbService implements SkipService {
  final Dio _dio;

  IntroDbService(this._dio);

  @override
  String get name => 'IntroDB';

  // Cache results for an hour. Power-users fast-skipping through episodes
  // would otherwise hammer the public API on every seek. LRU-capped so an
  // all-day binge doesn't accumulate unbounded entries.
  static const int _cacheMax = 500;
  static const Duration _cacheTtl = Duration(hours: 1);
  static final LinkedHashMap<String, _CachedSegments> _cache =
      LinkedHashMap<String, _CachedSegments>();

  // When the server returns 429, hold off for the Retry-After period
  // (capped at 5 min) before issuing any further requests across all
  // instances. Avoids amplifying rate-limits.
  static DateTime? _rateLimitUntil;

  String _key(String imdbId, int season, int episode) =>
      '$imdbId:$season:$episode';

  List<SkipSegment>? _lookupCached(String key) {
    final entry = _cache[key];
    if (entry == null) return null;
    if (DateTime.now().isAfter(entry.expiresAt)) {
      _cache.remove(key);
      return null;
    }
    // LRU touch.
    _cache.remove(key);
    _cache[key] = entry;
    return entry.segments;
  }

  void _store(String key, List<SkipSegment> segments) {
    _cache.remove(key);
    _cache[key] = _CachedSegments(segments, DateTime.now().add(_cacheTtl));
    while (_cache.length > _cacheMax) {
      _cache.remove(_cache.keys.first);
    }
  }

  @override
  Future<List<SkipSegment>> getSkipSegments({
    int? tmdbId,
    String? imdbId,
    int? anilistId,
    required int season,
    required int episode,
    int? duration,
  }) async {
    // The new API seems to only support imdb_id
    if (imdbId == null) {
      return [];
    }

    final key = _key(imdbId, season, episode);
    final cached = _lookupCached(key);
    if (cached != null) return cached;

    final now = DateTime.now();
    final until = _rateLimitUntil;
    if (until != null && now.isBefore(until)) {
      return [];
    }

    try {
      final queryParams = <String, dynamic>{
        'season': season,
        'episode': episode,
        'imdb_id': imdbId,
      };

      final response = await _dio.get<Map<String, dynamic>>(
        'https://api.introdb.app/segments',
        queryParameters: queryParams,
      );

      if (response.statusCode == 200 && response.data != null) {
        final data = response.data!;
        final segments = <SkipSegment>[];

        void addSegment(String k, SkipType type) {
          final segmentData = data[k];
          // If we use 'is Map' it's safer for dynamic JSON maps
          if (segmentData != null && segmentData is Map) {
            segments.add(
              SkipSegment(
                startTime: (segmentData['start_sec'] as num).toDouble(),
                endTime: (segmentData['end_sec'] as num).toDouble(),
                type: type,
              ),
            );
          }
        }

        addSegment('intro', SkipType.intro);
        addSegment('recap', SkipType.recap);
        addSegment('outro', SkipType.outro);

        // Sanitize before caching — IntroDB occasionally returns
        // zero-length or out-of-range entries.
        final cleaned = SkipSegment.sanitize(
          segments,
          durationSec: duration?.toDouble(),
        );
        _store(key, cleaned);
        return cleaned;
      }
    } on DioException catch (e) {
      if (e.response?.statusCode == 429) {
        final retryAfter = _parseRetryAfter(
          e.response?.headers.value('retry-after'),
        );
        _rateLimitUntil = DateTime.now().add(retryAfter);
        talker.debug(
          'IntroDB rate-limited; holding off ${retryAfter.inSeconds}s',
        );
      }
      // Other errors (404 no segments, network blips) are not interesting.
    } catch (_) {
      // Ignore — caller treats empty list as "no skip data".
    }

    // Cache empty result too, so we don't re-query a missing episode every seek.
    _store(key, const []);
    return [];
  }

  Duration _parseRetryAfter(String? header) {
    if (header == null) return const Duration(seconds: 60);
    final seconds = int.tryParse(header.trim());
    if (seconds == null || seconds < 0) return const Duration(seconds: 60);
    if (seconds > 300) return const Duration(minutes: 5);
    return Duration(seconds: seconds);
  }
}

class _CachedSegments {
  _CachedSegments(this.segments, this.expiresAt);
  final List<SkipSegment> segments;
  final DateTime expiresAt;
}

@riverpod
IntroDbService introDbService(Ref ref) {
  return IntroDbService(ref.watch(dioClientProvider));
}
