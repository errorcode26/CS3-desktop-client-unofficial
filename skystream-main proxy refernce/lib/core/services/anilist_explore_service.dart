import 'package:dio/dio.dart';
import '../../core/logger/app_logger.dart';

class _CacheEntry {
  final Map<String, dynamic> data;
  final DateTime expiry;

  _CacheEntry(this.data, this.expiry);

  bool get isExpired => DateTime.now().isAfter(expiry);
}

class AnilistExploreService {
  final Dio _dio;
  final Map<String, _CacheEntry> _cache = {};

  AnilistExploreService(Dio baseDio)
    : _dio = Dio(
        baseDio.options.copyWith(baseUrl: 'https://graphql.anilist.co'),
      ) {
    _dio.interceptors.addAll(baseDio.interceptors);
  }

  String _getCacheKey(String query, Map<String, dynamic>? variables) {
    return '$query|${variables?.toString()}';
  }

  Future<Map<String, dynamic>?> postGraphQL(
    String query, {
    Map<String, dynamic>? variables,
    int retryCount = 0,
    bool forceRefresh = false,
  }) async {
    final cacheKey = _getCacheKey(query, variables);
    if (!forceRefresh && _cache.containsKey(cacheKey)) {
      final entry = _cache[cacheKey]!;
      if (!entry.isExpired) {
        return entry.data;
      } else {
        _cache.remove(cacheKey);
      }
    }

    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '',
        data: {'query': query, 'variables': ?variables},
        options: Options(
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          },
        ),
      );
      if (response.statusCode == 200 && response.data != null) {
        final body = response.data!;
        if (body.containsKey('errors') &&
            body['errors'] is List &&
            (body['errors'] as List).isNotEmpty) {
          talker.error(
            'AnilistExploreService: GraphQL errors: ${body['errors']}',
          );
        }
        // Cache for 5 minutes
        _cache[cacheKey] = _CacheEntry(
          body,
          DateTime.now().add(const Duration(minutes: 5)),
        );
        return body;
      }
    } catch (e, st) {
      if (e is DioException) {
        final response = e.response;
        if (response != null && response.statusCode == 429 && retryCount < 3) {
          final retryAfterStr = response.headers.value('retry-after');
          int retryAfterSeconds = int.tryParse(retryAfterStr ?? '') ?? 2;
          if (retryAfterSeconds <= 0) retryAfterSeconds = 2;
          talker.warning(
            'AnilistExploreService: 429 Rate Limit. Retrying in $retryAfterSeconds seconds (retry $retryCount/3)...',
          );
          await Future<void>.delayed(Duration(seconds: retryAfterSeconds));
          return postGraphQL(
            query,
            variables: variables,
            retryCount: retryCount + 1,
          );
        }
      }
      talker.error('AnilistExploreService: HTTP/GraphQL request failed', e, st);
    }
    return null;
  }
}
