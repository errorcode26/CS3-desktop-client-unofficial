import '../../../../core/domain/entity/multimedia_item.dart';

class SyncProgressItem {
  final int? id; // Playback ID for Trakt removal
  final String? tmdbId;
  final String? imdbId;
  final String title;
  final double progressPercentage;
  final DateTime pausedAt;
  final MultimediaContentType type;
  final int? season;
  final int? episode;
  final String?
  posterUrl; // Optional, some trackers might not provide it directly

  SyncProgressItem({
    this.id,
    this.tmdbId,
    this.imdbId,
    required this.title,
    required this.progressPercentage,
    required this.pausedAt,
    required this.type,
    this.season,
    this.episode,
    this.posterUrl,
  });

  factory SyncProgressItem.fromJson(Map<String, dynamic> json) {
    // Determine type
    final typeStr = json['type'] as String?;
    MultimediaContentType type = MultimediaContentType.movie;
    if (typeStr == 'episode') {
      type = MultimediaContentType.series;
    }

    // Extract title & IDs based on type
    String title = '';
    String? tmdbId;
    String? imdbId;
    int? season;
    int? episode;

    if (type == MultimediaContentType.movie && json['movie'] != null) {
      final movie = json['movie'] as Map<String, dynamic>;
      title = movie['title'] as String? ?? '';
      if (movie['ids'] != null) {
        final ids = movie['ids'] as Map<String, dynamic>;
        tmdbId = ids['tmdb']?.toString();
        imdbId = ids['imdb']?.toString();
      }
    } else if (type == MultimediaContentType.series && json['show'] != null) {
      final show = json['show'] as Map<String, dynamic>;
      title = show['title'] as String? ?? '';
      if (show['ids'] != null) {
        final ids = show['ids'] as Map<String, dynamic>;
        tmdbId = ids['tmdb']?.toString();
        imdbId = ids['imdb']?.toString();
      }
      if (json['episode'] != null) {
        final ep = json['episode'] as Map<String, dynamic>;
        season = ep['season'] as int?;
        episode = ep['number'] as int?;
      }
    }

    return SyncProgressItem(
      id: json['id'] as int?,
      title: title,
      tmdbId: tmdbId,
      imdbId: imdbId,
      progressPercentage: (json['progress'] as num?)?.toDouble() ?? 0.0,
      pausedAt: json['paused_at'] != null
          ? DateTime.parse(json['paused_at'] as String)
          : DateTime.now(),
      type: type,
      season: season,
      episode: episode,
    );
  }

  SyncProgressItem copyWith({
    int? id,
    String? tmdbId,
    String? imdbId,
    String? title,
    double? progressPercentage,
    DateTime? pausedAt,
    MultimediaContentType? type,
    int? season,
    int? episode,
    String? posterUrl,
  }) {
    return SyncProgressItem(
      id: id ?? this.id,
      tmdbId: tmdbId ?? this.tmdbId,
      imdbId: imdbId ?? this.imdbId,
      title: title ?? this.title,
      progressPercentage: progressPercentage ?? this.progressPercentage,
      pausedAt: pausedAt ?? this.pausedAt,
      type: type ?? this.type,
      season: season ?? this.season,
      episode: episode ?? this.episode,
      posterUrl: posterUrl ?? this.posterUrl,
    );
  }
}
