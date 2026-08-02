import 'dart:collection';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'skip_service.dart';
import '../../../../core/network/dio_client_provider.dart';
import '../../../../core/config/sync_config.dart';

part 'anime_skip_service.g.dart';

class AnimeSkipService implements SkipService {
  final Dio _dio;

  static const String _clientId = SyncConfig.animeSkipClientId;

  // Cache the three-step GraphQL chain (show → episodes → timestamps)
  // for an hour, keyed by anilistId+episode. Without this, fast-skipping
  // through anime episodes fires 3 GraphQL queries per seek.
  static const int _cacheMax = 500;
  static const Duration _cacheTtl = Duration(hours: 1);
  static final LinkedHashMap<String, _CachedSegments> _cache =
      LinkedHashMap<String, _CachedSegments>();

  String _key(int anilistId, int episode) => '$anilistId:$episode';

  List<SkipSegment>? _lookupCached(String key) {
    final entry = _cache[key];
    if (entry == null) return null;
    if (DateTime.now().isAfter(entry.expiresAt)) {
      _cache.remove(key);
      return null;
    }
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

  AnimeSkipService(this._dio);

  @override
  String get name => 'AnimeSkip';

  @override
  Future<List<SkipSegment>> getSkipSegments({
    int? tmdbId,
    String? imdbId,
    int? anilistId,
    required int season,
    required int episode,
    int? duration,
  }) async {
    if (anilistId == null) return [];

    final key = _key(anilistId, episode);
    final cached = _lookupCached(key);
    if (cached != null) return cached;

    try {
      const String serviceEnum = 'ANILIST';
      final String serviceId = anilistId.toString();

      // STEP 1: Find the internal Show ID from the external ID
      final showQuery =
          '''
      query {
        findShowsByExternalId(service: $serviceEnum, serviceId: "$serviceId") {
          id
        }
      }
      ''';

      var response = await _dio.post<Map<String, dynamic>>(
        'https://api.anime-skip.com/graphql',
        options: Options(headers: {'X-Client-ID': _clientId}),
        data: {'query': showQuery},
      );

      final data = response.data?['data'];
      if (data == null ||
          data['findShowsByExternalId'] == null ||
          (data['findShowsByExternalId'] as List).isEmpty) {
        if (kDebugMode) {
          debugPrint('AnimeSkip: Could not find show for anilistId $anilistId');
        }
        _store(key, const []);
        return [];
      }

      final showId = data['findShowsByExternalId'][0]['id'];

      // STEP 2: Find the internal Episode ID from the Show ID
      final episodeQuery =
          '''
      query {
        findEpisodesByShowId(showId: "$showId") {
          id
          number
        }
      }
      ''';

      response = await _dio.post<Map<String, dynamic>>(
        'https://api.anime-skip.com/graphql',
        options: Options(headers: {'X-Client-ID': _clientId}),
        data: {'query': episodeQuery},
      );

      final episodeData = response.data?['data'];
      if (episodeData == null ||
          episodeData['findEpisodesByShowId'] == null ||
          (episodeData['findEpisodesByShowId'] as List).isEmpty) {
        if (kDebugMode) {
          debugPrint('AnimeSkip: Could not find episodes for showId $showId');
        }
        _store(key, const []);
        return [];
      }

      final episodes = episodeData['findEpisodesByShowId'] as List;
      if (kDebugMode) {
        debugPrint('AnimeSkip: Found ${episodes.length} episodes');
      }
      final targetEpisode = episodes.firstWhere(
        (dynamic ep) =>
            ep['number'] == episode ||
            ep['number'].toString() == episode.toString(),
        orElse: () => null,
      );

      if (targetEpisode == null) {
        if (kDebugMode) {
          debugPrint(
            'AnimeSkip: Could not find episode $episode for showId $showId',
          );
        }
        _store(key, const []);
        return [];
      }

      final episodeId = targetEpisode['id'];

      // STEP 3: Fetch the timestamps for this Episode ID
      final timestampQuery =
          '''
      query {
        findTimestampsByEpisodeId(episodeId: "$episodeId") {
          at
          type { name }
        }
      }
      ''';

      response = await _dio.post<Map<String, dynamic>>(
        'https://api.anime-skip.com/graphql',
        options: Options(headers: {'X-Client-ID': _clientId}),
        data: {'query': timestampQuery},
      );

      final timestampData = response.data?['data'];
      if (timestampData == null ||
          timestampData['findTimestampsByEpisodeId'] == null) {
        if (kDebugMode) {
          debugPrint(
            'AnimeSkip: Could not find timestamps for episodeId $episodeId',
          );
        }
        _store(key, const []);
        return [];
      }

      final timestamps = timestampData['findTimestampsByEpisodeId'] as List;
      if (timestamps.isEmpty) {
        if (kDebugMode) {
          debugPrint(
            'AnimeSkip: Timestamps array is empty for episodeId $episodeId',
          );
        }
        _store(key, const []);
        return [];
      }

      if (kDebugMode) debugPrint('AnimeSkip Raw Timestamps: $timestamps');

      // Sort timestamps by 'at' ascending
      timestamps.sort((a, b) => (a['at'] as num).compareTo(b['at'] as num));

      final List<SkipSegment> segments = [];

      final skippableTypes = [
        'intro',
        'new intro',
        'credits',
        'ending',
        'new credits',
        'mixed intro',
        'mixed credits',
        'preview',
        'recap',
        'transition',
      ];

      for (int i = 0; i < timestamps.length; i++) {
        final current = timestamps[i];
        final typeName =
            current['type']?['name']?.toString().toLowerCase() ?? '';

        if (skippableTypes.contains(typeName)) {
          final start = (current['at'] as num).toDouble();

          double end =
              start +
              85.0; // Default fallback duration (85s) for anime if no next timestamp

          if (i + 1 < timestamps.length) {
            end = (timestamps[i + 1]['at'] as num).toDouble();
          } else if (duration != null) {
            end = duration.toDouble();
          }

          SkipType skipType = SkipType.intro;
          if (typeName.contains('credits') ||
              typeName.contains('ending') ||
              typeName.contains('outro')) {
            skipType = SkipType.outro;
          } else if (typeName.contains('preview') ||
              typeName.contains('recap')) {
            skipType = SkipType.recap;
          } else if (typeName.contains('transition')) {
            skipType = SkipType.unknown;
          }

          segments.add(
            SkipSegment(startTime: start, endTime: end, type: skipType),
          );
        }
      }

      // Sanitize: the AnimeSkip data set is crowdsourced and routinely
      // contains backwards / zero-length entries (typo in start vs end).
      final cleaned = SkipSegment.sanitize(
        segments,
        durationSec: duration?.toDouble(),
      );
      _store(key, cleaned);
      return cleaned;
    } catch (e) {
      if (e is DioException && e.response != null) {
        if (kDebugMode) {
          debugPrint(
            'AnimeSkip Caught Error: ${e.response?.statusCode} - ${e.response?.data}',
          );
        }
      } else {
        if (kDebugMode) {
          debugPrint('AnimeSkip Caught Error: $e');
        }
      }
    }

    // Cache empty result so we don't repeat the 3-query chain on every seek.
    _store(key, const []);
    return [];
  }
}

class _CachedSegments {
  _CachedSegments(this.segments, this.expiresAt);
  final List<SkipSegment> segments;
  final DateTime expiresAt;
}

@riverpod
AnimeSkipService animeSkipService(Ref ref) {
  return AnimeSkipService(ref.watch(dioClientProvider));
}
