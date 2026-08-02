import 'dart:async';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../data/explore_tmdb_provider.dart';
import '../../data/explore_mode_provider.dart';
import '../../data/anilist_repository.dart';
import '../../data/explore_filter_provider.dart';
import '../../../../core/domain/entity/multimedia_item.dart';

part 'explore_search_controller.g.dart';

class ExploreSearchState {
  final List<MultimediaItem> suggestions;
  final List<MultimediaItem> results;
  final bool isLoading;
  final String query;
  final int page;
  final bool hasMore;

  const ExploreSearchState({
    this.suggestions = const [],
    this.results = const [],
    this.isLoading = false,
    this.query = '',
    this.page = 1,
    this.hasMore = true,
  });

  ExploreSearchState copyWith({
    List<MultimediaItem>? suggestions,
    List<MultimediaItem>? results,
    bool? isLoading,
    String? query,
    int? page,
    bool? hasMore,
  }) {
    return ExploreSearchState(
      suggestions: suggestions ?? this.suggestions,
      results: results ?? this.results,
      isLoading: isLoading ?? this.isLoading,
      query: query ?? this.query,
      page: page ?? this.page,
      hasMore: hasMore ?? this.hasMore,
    );
  }
}

@riverpod
class ExploreSearchController extends _$ExploreSearchController {
  Timer? _debounce;

  @override
  ExploreSearchState build() {
    ref.onDispose(() {
      _debounce?.cancel();
    });
    return const ExploreSearchState();
  }

  void onQueryChanged(String query) {
    if (query == state.query) return;

    if (query.trim().isEmpty) {
      _debounce?.cancel();
      state = state.copyWith(query: query, suggestions: [], isLoading: false);
      return;
    }

    state = state.copyWith(query: query, isLoading: true);

    if (_debounce?.isActive ?? false) _debounce!.cancel();

    _debounce = Timer(const Duration(milliseconds: 500), () async {
      try {
        final isAnime = ref.read(exploreModeProvider);
        final List<MultimediaItem> results;
        if (isAnime) {
          final anilist = ref.read(anilistRepositoryProvider);
          final titleLang = ref.read(animeTitleLanguageProvider);
          results = await anilist.searchAnime(query, titleLang: titleLang);
        } else {
          final tmdb = ref.read(tmdbServiceProvider);
          results = await tmdb.multiSearch(query: query, language: 'en-US');
        }

        if (state.query == query) {
          state = state.copyWith(
            suggestions: results.take(10).toList(),
            isLoading: false,
          );
        }
      } catch (e) {
        if (state.query == query) {
          state = state.copyWith(isLoading: false);
        }
      }
    });
  }

  Future<void> fetchResults(String query) async {
    if (query == state.query && state.results.isNotEmpty) return;

    state = state.copyWith(
      query: query,
      isLoading: true,
      page: 1,
      hasMore: true,
    );

    try {
      final isAnime = ref.read(exploreModeProvider);
      final List<MultimediaItem> results;
      if (isAnime) {
        final anilist = ref.read(anilistRepositoryProvider);
        final titleLang = ref.read(animeTitleLanguageProvider);
        results = await anilist.searchAnime(
          query,
          page: 1,
          titleLang: titleLang,
        );
      } else {
        final tmdb = ref.read(tmdbServiceProvider);
        results = await tmdb.multiSearch(
          query: query,
          language: 'en-US',
          page: 1,
        );
      }

      if (state.query == query) {
        state = state.copyWith(
          results: results,
          isLoading: false,
          hasMore: results.isNotEmpty,
        );
      }
    } catch (e) {
      if (state.query == query) {
        state = state.copyWith(isLoading: false);
      }
    }
  }

  Future<void> fetchNextPage() async {
    if (state.isLoading || !state.hasMore) return;

    state = state.copyWith(isLoading: true);

    try {
      final isAnime = ref.read(exploreModeProvider);
      final nextPage = state.page + 1;
      final List<MultimediaItem> results;
      if (isAnime) {
        final anilist = ref.read(anilistRepositoryProvider);
        final titleLang = ref.read(animeTitleLanguageProvider);
        results = await anilist.searchAnime(
          state.query,
          page: nextPage,
          titleLang: titleLang,
        );
      } else {
        final tmdb = ref.read(tmdbServiceProvider);
        results = await tmdb.multiSearch(
          query: state.query,
          language: 'en-US',
          page: nextPage,
        );
      }

      if (results.isEmpty) {
        state = state.copyWith(hasMore: false, isLoading: false);
      } else {
        state = state.copyWith(
          results: [...state.results, ...results],
          page: nextPage,
          isLoading: false,
        );
      }
    } catch (e) {
      state = state.copyWith(isLoading: false);
    }
  }

  void clearSearch() {
    _debounce?.cancel();
    state = const ExploreSearchState();
  }
}
