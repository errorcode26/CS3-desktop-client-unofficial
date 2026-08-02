import 'package:flutter/foundation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../../core/domain/entity/multimedia_item.dart';
import '../../../../core/services/tmdb_service.dart';
import '../../explore/data/explore_tmdb_provider.dart';

part 'metadata_resolution_service.g.dart';

class MetadataResolutionService {
  final TmdbService _tmdbService;

  MetadataResolutionService(this._tmdbService);

  Future<MultimediaItem> enrichWithIds(MultimediaItem item) async {
    // If it already has both, skip
    if (item.tmdbId != null && item.imdbId != null) {
      if (kDebugMode) {
        debugPrint(
          'MetadataResolutionService: Both tmdbId (${item.tmdbId}) and imdbId (${item.imdbId}) already present for ${item.title}. Skipping resolution.',
        );
      }
      return item;
    }

    // We only resolve if we have a title
    if (item.title.isEmpty) {
      if (kDebugMode) {
        debugPrint(
          'MetadataResolutionService: Title is empty. Cannot resolve IDs.',
        );
      }
      return item;
    }

    if (kDebugMode) {
      debugPrint(
        'MetadataResolutionService: Resolving IDs for ${item.title}. Current tmdbId: ${item.tmdbId}, imdbId: ${item.imdbId}',
      );
    }

    // Fast path: If we already have tmdbId, just fetch the extra details to get imdbId
    if (item.tmdbId != null && item.imdbId == null) {
      if (kDebugMode) {
        debugPrint(
          'MetadataResolutionService: We have tmdbId (${item.tmdbId}), skipping multiSearch. Fetching extra details directly.',
        );
      }
      String? imdbId;
      if (item.contentType == MultimediaContentType.movie) {
        final extra = await _tmdbService.getMovieExtra(item.tmdbId!);
        if (extra != null) {
          imdbId = _extractImdbId(extra);
        }
      } else {
        final extra = await _tmdbService.getTvExtra(item.tmdbId!);
        if (extra != null) {
          imdbId = _extractImdbId(extra);
        }
      }

      final enriched = item.copyWith(imdbId: imdbId ?? item.imdbId);
      if (kDebugMode) {
        debugPrint(
          'MetadataResolutionService: Fast path resolution complete. Final item: tmdbId: ${enriched.tmdbId}, imdbId: ${enriched.imdbId}',
        );
      }
      return enriched;
    }

    // Use TMDB multiSearch
    // We can append year to the query so the regex in multiSearch picks it up
    String query = item.title;
    if (item.year != null) {
      query += ' ${item.year}';
    }

    final results = await _tmdbService.multiSearch(query: query);

    if (kDebugMode) {
      debugPrint(
        'MetadataResolutionService: multiSearch returned ${results.length} results.',
      );
    }

    if (results.isNotEmpty) {
      final bestMatch = _findBestMatch(item, results);
      if (bestMatch != null) {
        if (kDebugMode) {
          debugPrint(
            'MetadataResolutionService: Found best match: ${bestMatch.title} (tmdbId: ${bestMatch.tmdbId}, imdbId: ${bestMatch.imdbId})',
          );
        }
        String? imdbId = bestMatch.imdbId;
        final int? tmdbId = bestMatch.tmdbId;

        if (tmdbId != null && imdbId == null) {
          if (kDebugMode) {
            debugPrint(
              'MetadataResolutionService: imdbId is missing, fetching extra details for tmdbId: $tmdbId (type: ${bestMatch.contentType})',
            );
          }
          if (bestMatch.contentType == MultimediaContentType.movie) {
            final extra = await _tmdbService.getMovieExtra(tmdbId);
            if (extra != null) {
              imdbId = _extractImdbId(extra);
            }
          } else {
            final extra = await _tmdbService.getTvExtra(tmdbId);
            if (extra != null) {
              imdbId = _extractImdbId(extra);
            }
          }
          if (kDebugMode) {
            debugPrint(
              'MetadataResolutionService: Fetched extra details. Found imdbId: $imdbId',
            );
          }
        }

        final enriched = item.copyWith(
          tmdbId: tmdbId ?? item.tmdbId,
          imdbId: imdbId ?? item.imdbId,
        );
        if (kDebugMode) {
          debugPrint(
            'MetadataResolutionService: Resolution complete. Final item: tmdbId: ${enriched.tmdbId}, imdbId: ${enriched.imdbId}',
          );
        }
        return enriched;
      } else {
        if (kDebugMode) {
          debugPrint(
            'MetadataResolutionService: Could not find a best match among the results.',
          );
        }
      }
    } else {
      if (kDebugMode) {
        debugPrint(
          'MetadataResolutionService: No results found for query: $query',
        );
      }
    }

    return item;
  }

  String? _extractImdbId(Map<String, dynamic> json) {
    String? imdbId = json['imdb_id'] as String?;
    if (imdbId == null || imdbId.isEmpty) {
      final externalIds = json['external_ids'] as Map<String, dynamic>?;
      if (externalIds != null) {
        imdbId = externalIds['imdb_id'] as String?;
      }
    }
    return imdbId;
  }

  MultimediaItem? _findBestMatch(
    MultimediaItem source,
    List<MultimediaItem> results,
  ) {
    // Attempt exact match
    for (final res in results) {
      if (res.title.toLowerCase() == source.title.toLowerCase()) {
        if (source.year != null && res.year != null) {
          if ((source.year! - res.year!).abs() <= 1) {
            return res;
          }
        } else {
          return res; // If no year info, just take exact title match
        }
      }
    }
    // Fallback to the first result
    return results.first;
  }
}

@riverpod
MetadataResolutionService metadataResolutionService(Ref ref) {
  final tmdbService = ref.watch(tmdbServiceProvider);
  return MetadataResolutionService(tmdbService);
}
