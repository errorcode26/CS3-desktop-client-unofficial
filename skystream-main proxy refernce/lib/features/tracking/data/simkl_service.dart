import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'tracking_service.dart';
import '../domain/sync_progress_item.dart';
import '../../../../core/domain/entity/multimedia_item.dart';
import '../../../../core/logger/app_logger.dart';
import '../../../../core/network/dio_client_provider.dart';
import '../../../../core/storage/secure_token_storage.dart';
import '../../../../core/config/sync_config.dart';

part 'simkl_service.g.dart';

class SimklService implements TrackingService {
  final Dio _dio;
  final SecureTokenStorage _storage;

  static const String _clientId = SyncConfig.simklClientId;
  static const String _kAccessTokenKey = 'simkl_access_token';

  String? _accessToken;
  Future<void>? _initFuture;

  SimklService(this._dio, this._storage) {
    _initFuture = _initToken();
  }

  Future<void> _initToken() async {
    _accessToken = await _storage.read(_kAccessTokenKey);
  }

  Future<void> _ensureInit() async {
    if (_initFuture != null) {
      await _initFuture;
    }
  }

  @override
  String get name => 'Simkl';

  @override
  String get idPrefix => 'simkl';

  @override
  String get mainUrl => 'https://simkl.com';

  @override
  Future<bool> get isLoggedIn async {
    await _ensureInit();
    return _accessToken != null;
  }

  @override
  Future<bool> login({
    Future<void> Function(String url, String code)? onDeviceCodeGenerated,
    Future<void> Function(String url)? onWebViewRequested,
    bool Function()? isCancelled,
  }) async {
    try {
      talker.debug('SimklService: Initiating Device PIN Flow...');
      final response = await _dio.get<dynamic>(
        'https://api.simkl.com/oauth/pin',
        queryParameters: {'client_id': _clientId},
      );

      final userCode = response.data['user_code'] as String;
      final verificationUrl = response.data['verification_url'] as String;
      final interval = (response.data['interval'] as num?)?.toInt() ?? 5;

      talker.debug(
        'SIMKL DEVICE LOGIN — go to $verificationUrl and enter code $userCode',
      );

      if (onDeviceCodeGenerated != null) {
        await onDeviceCodeGenerated(verificationUrl, userCode);
      }

      // Polling
      int attempts = 0;
      while (attempts < 60) {
        // Timeout after ~5 mins (assuming 5s interval)
        if (isCancelled != null && isCancelled()) {
          talker.debug('SimklService: Polling cancelled by user.');
          return false;
        }
        await Future<void>.delayed(Duration(seconds: interval));
        if (isCancelled != null && isCancelled()) return false;

        try {
          talker.debug('SimklService: Polling for token...');
          final tokenResponse = await _dio.get<dynamic>(
            'https://api.simkl.com/oauth/pin/$userCode',
            queryParameters: {'client_id': _clientId},
          );

          final data = tokenResponse.data;
          if (data is Map &&
              data['result'] == 'OK' &&
              data['access_token'] != null) {
            _accessToken = data['access_token'].toString();
            await _storage.write(_kAccessTokenKey, _accessToken!);
            talker.debug('SimklService: Login successful!');
            return true;
          }
        } on DioException catch (e) {
          if (e.response?.statusCode != 400) {
            talker.debug('SimklService: Polling error: ${e.message}');
          }
        }
        attempts++;
      }
      talker.debug('SimklService: Login timed out.');
      return false;
    } catch (e) {
      talker.error('SimklService: Login failed', e);
      return false;
    }
  }

  @override
  Future<void> logout() async {
    talker.debug('SimklService: Logging out...');
    _accessToken = null;
    await _storage.delete(_kAccessTokenKey);
  }

  @override
  Future<List<MultimediaItem>> search(String query) async {
    if (_accessToken == null) return [];

    // Search implementation
    return [];
  }

  @override
  Future<Map<String, String>> syncIds(MultimediaItem item) async {
    final Map<String, String> resolvedIds = {};

    try {
      final queryParams = <String, String>{'client_id': _clientId};

      if (item.imdbId != null) {
        queryParams['imdb'] = item.imdbId!;
      } else if (item.tmdbId != null) {
        queryParams['tmdb'] = item.tmdbId.toString();
      } else {
        talker.debug(
          'SimklService.syncIds: No imdbId or tmdbId found on item: ${item.title}',
        );
        return {};
      }

      talker.debug(
        'SimklService.syncIds: Querying Simkl for ${item.title} with params: $queryParams',
      );

      final response = await _dio.get<List<dynamic>>(
        'https://api.simkl.com/search/id',
        queryParameters: queryParams,
      );

      talker.debug(
        'SimklService.syncIds: Response code: ${response.statusCode}, data: ${response.data}',
      );

      if (response.statusCode == 200 &&
          response.data != null &&
          response.data!.isNotEmpty) {
        final result = response.data!.first as Map<String, dynamic>;
        final type = result['type'] as String?;
        final ids = result['ids'] as Map<String, dynamic>?;

        if (ids != null && ids['simkl'] != null) {
          final simklId = ids['simkl'].toString();
          resolvedIds['simkl'] = simklId;

          // Determine correct details endpoint path
          String detailsPath;
          if (type == 'movie') {
            detailsPath = 'movies';
          } else if (type == 'tv') {
            detailsPath = 'tv';
          } else if (type == 'anime') {
            detailsPath = 'anime';
          } else {
            detailsPath = item.contentType == MultimediaContentType.movie
                ? 'movies'
                : 'tv';
          }

          talker.debug(
            'SimklService.syncIds: Fetching full details from /$detailsPath/$simklId',
          );

          final detailsResponse = await _dio.get<Map<String, dynamic>>(
            'https://api.simkl.com/$detailsPath/$simklId',
            queryParameters: {'client_id': _clientId},
          );

          if (detailsResponse.statusCode == 200 &&
              detailsResponse.data != null) {
            final detailsIds =
                detailsResponse.data!['ids'] as Map<String, dynamic>?;
            if (detailsIds != null) {
              if (detailsIds['simkl'] != null) {
                resolvedIds['simkl'] = detailsIds['simkl'].toString();
              }
              if (detailsIds['mal'] != null) {
                resolvedIds['mal'] = detailsIds['mal'].toString();
              }
              if (detailsIds['anilist'] != null) {
                resolvedIds['anilist'] = detailsIds['anilist'].toString();
              }
              if (detailsIds['tmdb'] != null) {
                resolvedIds['tmdb'] = detailsIds['tmdb'].toString();
              }
              if (detailsIds['imdb'] != null) {
                resolvedIds['imdb'] = detailsIds['imdb'].toString();
              }
            }
          }
        }
        talker.debug('SimklService.syncIds: Resolved ids: $resolvedIds');
      } else {
        talker.debug('SimklService.syncIds: Empty or non-200 response.');
      }
    } catch (e, st) {
      talker.error('SimklService.syncIds error for item: ${item.title}', e, st);
    }

    return resolvedIds;
  }

  Future<bool> _syncList(
    MultimediaItem item,
    Episode? episode,
    String listType,
    Map<String, String>? resolvedIds,
  ) async {
    if (_accessToken == null) return false;

    final simklId = resolvedIds?['simkl'];
    // We need simkl id or other ids. We'll send TMDB/IMDB directly if available.
    if (simklId == null && item.tmdbId == null && item.imdbId == null) {
      return false;
    }

    try {
      final payload = <String, dynamic>{};
      final ids = <String, dynamic>{
        'simkl': ?simklId,
        if (simklId == null && item.tmdbId != null) 'tmdb': item.tmdbId,
        if (simklId == null && item.imdbId != null) 'imdb': item.imdbId,
      };

      if (item.contentType == MultimediaContentType.movie) {
        payload['movies'] = [
          {'to': listType, 'ids': ids},
        ];
      } else {
        payload['shows'] = [
          {'to': listType, 'ids': ids},
        ];
      }

      final response = await _dio.post<dynamic>(
        'https://api.simkl.com/sync/add-to-list',
        data: payload,
        options: Options(
          headers: {
            'Authorization': 'Bearer $_accessToken',
            'simkl-api-key': _clientId,
          },
        ),
      );
      talker.debug(
        'SimklService: Add to list $listType success: ${response.statusCode}',
      );
      return response.statusCode == 200 || response.statusCode == 201;
    } catch (e) {
      talker.error('SimklService: Add to list $listType failed', e);
      return false;
    }
  }

  @override
  Future<bool> markWatched(
    MultimediaItem item,
    Episode? episode, {
    Map<String, String>? resolvedIds,
  }) async {
    if (_accessToken == null) return false;
    final simklId = resolvedIds?['simkl'];
    if (simklId == null && item.tmdbId == null && item.imdbId == null) {
      return false;
    }

    try {
      final payload = <String, dynamic>{};
      final ids = <String, dynamic>{
        'simkl': ?simklId,
        if (simklId == null && item.tmdbId != null) 'tmdb': item.tmdbId,
        if (simklId == null && item.imdbId != null) 'imdb': item.imdbId,
      };

      if (item.contentType == MultimediaContentType.movie) {
        payload['movies'] = [
          {'ids': ids},
        ];
      } else {
        if (episode == null) return false;
        payload['shows'] = [
          {
            'ids': ids,
            'episodes': [
              {'season': episode.season, 'number': episode.episode},
            ],
          },
        ];
      }

      final response = await _dio.post<dynamic>(
        'https://api.simkl.com/sync/history',
        data: payload,
        options: Options(
          headers: {
            'Authorization': 'Bearer $_accessToken',
            'simkl-api-key': _clientId,
          },
        ),
      );
      talker.debug(
        'SimklService: Mark watched success: ${response.statusCode}',
      );
      return response.statusCode == 200 || response.statusCode == 201;
    } catch (e) {
      talker.error('SimklService: Mark watched failed', e);
      return false;
    }
  }

  @override
  Future<bool> scrobbleStart(
    MultimediaItem item,
    Episode? episode,
    double progress, {
    Map<String, String>? resolvedIds,
  }) async {
    // Simkl doesn't have real-time scrobble, so we add to watching list
    return _syncList(item, episode, 'watching', resolvedIds);
  }

  @override
  Future<bool> scrobblePause(
    MultimediaItem item,
    Episode? episode,
    double progress, {
    Map<String, String>? resolvedIds,
  }) async {
    // No-op for Simkl
    return true;
  }

  @override
  Future<bool> scrobbleStop(
    MultimediaItem item,
    Episode? episode,
    double progress, {
    Map<String, String>? resolvedIds,
  }) async {
    // No-op for Simkl. Actual completion is handled by markWatched.
    return true;
  }

  @override
  Future<bool> addToPlanToWatch(
    MultimediaItem item, {
    Map<String, String>? resolvedIds,
  }) async {
    return _syncList(item, null, 'plantowatch', resolvedIds);
  }

  @override
  Future<List<SyncProgressItem>> pullPlaybackProgress() async {
    return [];
  }

  @override
  Future<bool> removePlaybackProgress(String id) async {
    return false;
  }
}

@riverpod
SimklService simklService(Ref ref) {
  return SimklService(
    ref.watch(dioClientProvider),
    ref.watch(secureTokenStorageProvider),
  );
}
