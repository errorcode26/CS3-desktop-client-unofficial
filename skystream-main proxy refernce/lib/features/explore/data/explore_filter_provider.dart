import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../../../core/models/tmdb_genre.dart';

part 'explore_filter_provider.g.dart';

class FilterState {
  final TmdbGenre? selectedGenre; // {id: 123, name: 'Action'}
  final int? selectedYear;
  final double? minRating;

  const FilterState({this.selectedGenre, this.selectedYear, this.minRating});

  FilterState copyWith({
    TmdbGenre? selectedGenre,
    int? selectedYear,
    double? minRating,
    bool clearGenre = false,
    bool clearYear = false,
    bool clearRating = false,
  }) {
    return FilterState(
      selectedGenre: clearGenre ? null : (selectedGenre ?? this.selectedGenre),
      selectedYear: clearYear ? null : (selectedYear ?? this.selectedYear),
      minRating: clearRating ? null : (minRating ?? this.minRating),
    );
  }
}

@Riverpod(keepAlive: true)
class ExploreFilter extends _$ExploreFilter {
  @override
  FilterState build() => const FilterState();

  void setGenre(TmdbGenre? genre) {
    state = state.copyWith(selectedGenre: genre, clearGenre: genre == null);
  }

  void setYear(int? year) {
    state = state.copyWith(selectedYear: year, clearYear: year == null);
  }

  void setRating(double? rating) {
    state = state.copyWith(minRating: rating, clearRating: rating == null);
  }

  void clearAll() {
    state = const FilterState();
  }
}

@Riverpod(keepAlive: true)
class AnimeTitleLanguage extends _$AnimeTitleLanguage {
  @override
  String build() => 'english';

  void setLanguage(String lang) {
    state = lang;
  }
}
