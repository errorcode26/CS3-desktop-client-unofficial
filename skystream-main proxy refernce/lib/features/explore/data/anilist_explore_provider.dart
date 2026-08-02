import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../core/domain/entity/multimedia_item.dart';
import 'anilist_repository.dart';
import 'explore_filter_provider.dart';

part 'anilist_explore_provider.g.dart';

@riverpod
Future<List<MultimediaItem>> trendingAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'trending',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> airedRecentlyAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'airedRecently',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> topSeasonAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'topSeason',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> bestLastSeasonAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'bestLastSeason',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> moviesAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'movies',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> comingSoonAnime(Ref ref) async {
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  return ref
      .watch(anilistRepositoryProvider)
      .fetchSection(
        'comingSoon',
        genre: filter.selectedGenre?.name,
        year: filter.selectedYear,
        minRating: filter.minRating,
        titleLang: titleLang,
      );
}

@riverpod
Future<List<MultimediaItem>> anilistHeroAnime(Ref ref) async {
  final repo = ref.watch(anilistRepositoryProvider);
  final filter = ref.watch(exploreFilterProvider);
  final titleLang = ref.watch(animeTitleLanguageProvider);
  final trending = await repo.fetchSection(
    'trending',
    genre: filter.selectedGenre?.name,
    year: filter.selectedYear,
    minRating: filter.minRating,
    titleLang: titleLang,
  );
  final top5 = trending.take(5).toList();

  return Future.wait(
    top5.map((item) async {
      final anilistId = item.tmdbId;
      if (anilistId == null) return item;
      final images = await repo.getAnimeImages(anilistId);
      final logoUrl = images['logo'];
      final fanartUrl = images['fanart'];
      var updatedItem = item;
      if (logoUrl != null && logoUrl.isNotEmpty) {
        updatedItem = updatedItem.copyWith(logoUrl: logoUrl);
      }
      if (fanartUrl != null && fanartUrl.isNotEmpty) {
        updatedItem = updatedItem.copyWith(bannerUrl: fanartUrl);
      }
      return updatedItem;
    }),
  );
}
