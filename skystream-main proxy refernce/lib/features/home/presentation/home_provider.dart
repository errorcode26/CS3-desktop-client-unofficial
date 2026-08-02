import 'dart:io';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../../core/extensions/extension_manager.dart';
import '../../../../core/storage/storage_service.dart';
import '../../../../core/extensions/base_provider.dart';

import './home_state.dart';

part 'home_provider.g.dart';

@riverpod
class HomeData extends _$HomeData {
  @override
  HomeState build() {
    final activeProvider = ref.watch(activeProviderProvider);
    if (activeProvider == null) {
      return const HomeNoProvider();
    }

    // Start initial fetch
    Future.microtask(() => fetch());
    return const HomeLoading();
  }

  Future<void> fetch() async {
    state = const HomeLoading();

    final activeProvider = ref.read(activeProviderProvider);
    if (activeProvider == null) {
      state = const HomeNoProvider();
      return;
    }

    // Fast connectivity check
    try {
      final result = await InternetAddress.lookup(
        'dns.google',
      ).timeout(const Duration(seconds: 2));
      if (result.isEmpty || result[0].rawAddress.isEmpty) {
        state = const HomeOffline();
        return;
      }
    } catch (_) {
      state = const HomeOffline();
      return;
    }

    try {
      final items = await activeProvider.getHome();
      if (items.isEmpty) {
        state = const HomeSuccess({});
      } else {
        state = HomeSuccess(items);
      }
    } catch (e) {
      state = HomeError(e.toString());
    }
  }
}

@riverpod
class HomeFilter extends _$HomeFilter {
  @override
  ProviderType? build() {
    final storage = ref.read(storageServiceProvider);
    final saved = storage.getHomeCategory();
    if (saved != null) {
      try {
        return ProviderType.values.firstWhere((e) => e.name == saved);
      } catch (_) {}
    }
    return null;
  }

  Future<void> setFilter(ProviderType? type) async {
    state = type;
    final storage = ref.read(storageServiceProvider);
    await storage.setHomeCategory(type?.name);
  }
}
