import 'dart:async';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/models/tmdb_details.dart';
import '../../../core/services/tmdb_service.dart';
import '../../explore/data/explore_language_provider.dart';
import '../../explore/data/explore_tmdb_provider.dart';
import './tmdb_details_provider.dart';

import '../../explore/data/anilist_repository.dart';

part 'lightweight_details_provider.g.dart';

@riverpod
Future<TmdbDetails?> lightweightDetails(
  Ref ref,
  MovieDetailsParams params,
) async {
  if (params.source == 'anilist') {
    return ref.watch(anilistRepositoryProvider).getAnimeDetails(params.id);
  }
  final service = ref.watch(tmdbServiceProvider);
  final language = ref.watch(languageProvider);

  try {
    final data = await service
        .getDetailsForCarousel(params.id, params.type, language: language)
        .timeout(
          const Duration(seconds: 10),
          onTimeout: () => throw TimeoutException('Fast-load timed out'),
        );

    if (data == null) return null;

    // Process logoUrl
    String? logoUrl;
    final images = data['images'] as Map<String, dynamic>?;
    if (images != null) {
      final logos = List<Map<String, dynamic>>.from(
        (images['logos'] as List?) ?? const <dynamic>[],
      );
      logoUrl = TmdbService.pickBestLogo(logos, language);
    }
    data['logo_url'] = logoUrl;

    return TmdbDetails.fromJson(data, language);
  } catch (_) {
    return null; // Fallback to heavy provider if fast-path fails
  }
}
