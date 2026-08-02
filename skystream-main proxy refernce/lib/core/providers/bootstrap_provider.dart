import 'dart:io';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:flutter_displaymode/flutter_displaymode.dart';
import '../storage/storage_service.dart';
import '../network/doh_service.dart';
import '../logger/app_logger.dart';

part 'bootstrap_provider.g.dart';

@Riverpod(keepAlive: true)
class Bootstrap extends _$Bootstrap {
  @override
  Future<void> build() async {
    talker.info('Bootstrap: Starting initialization...');

    final storageService = ref.read(storageServiceProvider);

    try {
      await Future.wait([
        storageService.init().then(
          (_) => talker.info('Bootstrap: Storage initialized'),
        ),
        DohService.instance.init().then(
          (_) => talker.info('Bootstrap: DoH initialized'),
        ),
        if (Platform.isAndroid)
          FlutterDisplayMode.setHighRefreshRate().catchError((Object e) {
            talker.error('Bootstrap: Error setting high refresh rate', e);
          }),
      ]);

      talker.info('Bootstrap: Initialization complete');
    } catch (e, st) {
      talker.handle(e, st, 'Bootstrap: Critical initialization error');
      rethrow;
    }
  }
}
