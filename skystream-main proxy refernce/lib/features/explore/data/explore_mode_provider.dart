import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'explore_mode_provider.g.dart';

@riverpod
class ExploreMode extends _$ExploreMode {
  @override
  bool build() => false; // false = Movies & Shows, true = Anime

  void setAnimeMode(bool isAnime) {
    state = isAnime;
  }
}
