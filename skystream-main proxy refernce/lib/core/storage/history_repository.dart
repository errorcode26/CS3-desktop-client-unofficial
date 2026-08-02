import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../domain/entity/multimedia_item.dart';
import 'storage_service.dart';

part 'history_repository.g.dart';

class HistoryItem {
  final MultimediaItem item;
  final int position;
  final int duration;
  final String? lastStreamUrl;
  final String? lastEpisodeUrl;
  final int? season;
  final int? episode;
  final String? episodeTitle;
  final String? episodePosterUrl;
  final int timestamp;

  HistoryItem({
    required this.item,
    required this.position,
    required this.duration,
    this.lastStreamUrl,
    this.lastEpisodeUrl,
    this.season,
    this.episode,
    this.episodeTitle,
    this.episodePosterUrl,
    required this.timestamp,
  });

  double get progress => duration > 0 ? position / duration : 0;

  factory HistoryItem.fromMap(Map<String, dynamic> map) {
    return HistoryItem(
      item: MultimediaItem(
        title: (map['title'] as String?) ?? '',
        url: (map['url'] as String?) ?? '',
        posterUrl: (map['posterUrl'] as String?) ?? '',
        bannerUrl: map['bannerUrl'] as String?,
        description: map['description'] as String?,
        contentType: MultimediaItem.parseContentType(
          (map['type'] as String?) ??
              (map['contentType'] as String?) ??
              'movie',
        ),
        provider: map['provider'] as String?,
        tmdbId: map['tmdbId'] as int?,
        imdbId: map['imdbId'] as String?,
      ),
      position: (map['position'] as int?) ?? 0,
      duration: (map['duration'] as int?) ?? 0,
      lastStreamUrl: map['lastStreamUrl'] as String?,
      lastEpisodeUrl: map['lastEpisodeUrl'] as String?,
      season: map['season'] as int?,
      episode: map['episode'] as int?,
      episodeTitle: map['episodeTitle'] as String?,
      episodePosterUrl: map['episodePosterUrl'] as String?,
      timestamp: (map['timestamp'] as int?) ?? 0,
    );
  }
}

@Riverpod(keepAlive: true)
HistoryRepository historyRepository(Ref ref) {
  return HistoryRepository(ref.watch(storageServiceProvider));
}

class HistoryRepository {
  final StorageService _storageService;

  HistoryRepository(this._storageService);

  Future<void> saveProgress(
    MultimediaItem item,
    int position,
    int duration, {
    String? lastStreamUrl,
    String? lastEpisodeUrl,
    int? season,
    int? episode,
    String? episodeTitle,
    String? episodePosterUrl,
  }) async {
    await _storageService.saveProgress(
      item,
      position,
      duration,
      lastStreamUrl: lastStreamUrl,
      lastEpisodeUrl: lastEpisodeUrl,
      season: season,
      episode: episode,
      episodeTitle: episodeTitle,
      episodePosterUrl: episodePosterUrl,
    );
  }

  Future<void> removeFromHistory(String url) async {
    await _storageService.removeFromHistory(url);
  }

  Future<void> updateHistoryItemTimestampAndPosition(
    HistoryItem item,
    int timestamp,
    int position,
  ) async {
    await _storageService.updateHistoryItemTimestampAndPosition(
      item.item.url,
      item.lastEpisodeUrl,
      timestamp,
      position,
    );
  }

  Future<void> clearAllHistory() async {
    await _storageService.clearAllHistory();
  }

  List<HistoryItem> getWatchHistory() {
    final rawItems = _storageService.getWatchHistory();
    return rawItems.map((map) => HistoryItem.fromMap(map)).toList();
  }

  int getPosition(String url) {
    return _storageService.getPosition(url);
  }

  int getEpisodePosition(
    String url, {
    String? mainUrl,
    int? season,
    int? episode,
  }) {
    return _storageService.getEpisodePosition(
      url,
      mainUrl: mainUrl,
      season: season,
      episode: episode,
    );
  }

  int getDuration(String url) {
    return _storageService.getDuration(url);
  }

  int getEpisodeDuration(
    String url, {
    String? mainUrl,
    int? season,
    int? episode,
  }) {
    return _storageService.getEpisodeDuration(
      url,
      mainUrl: mainUrl,
      season: season,
      episode: episode,
    );
  }

  String? getLastStreamUrl(String url) {
    return _storageService.getLastStreamUrl(url);
  }

  String? getLastEpisodeUrl(String url) {
    return _storageService.getLastEpisodeUrl(url);
  }
}
