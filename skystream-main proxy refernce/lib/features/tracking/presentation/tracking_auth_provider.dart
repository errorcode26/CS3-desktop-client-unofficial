import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../data/simkl_service.dart';
import '../data/trakt_service.dart';
import '../data/mal_service.dart';
import '../data/anilist_service.dart';

part 'tracking_auth_provider.g.dart';

@riverpod
class TrackingAuth extends _$TrackingAuth {
  @override
  FutureOr<Map<String, bool>> build() async {
    final simkl = ref.watch(simklServiceProvider);
    final trakt = ref.watch(traktServiceProvider);
    final mal = ref.watch(malServiceProvider);
    final anilist = ref.watch(aniListServiceProvider);

    return {
      'simkl': await simkl.isLoggedIn,
      'trakt': await trakt.isLoggedIn,
      'mal': await mal.isLoggedIn,
      'anilist': await anilist.isLoggedIn,
    };
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async => await build());
  }
}
