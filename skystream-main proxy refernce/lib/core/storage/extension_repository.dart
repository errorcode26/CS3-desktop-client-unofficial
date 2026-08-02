import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'storage_service.dart';

part 'extension_repository.g.dart';

@Riverpod(keepAlive: true)
ExtensionRepository extensionRepository(Ref ref) {
  return ExtensionRepository(ref.watch(storageServiceProvider));
}

class ExtensionRepository {
  final StorageService _storageService;

  ExtensionRepository(this._storageService);

  Future<void> setExtensionData(String key, String? value) async {
    await _storageService.setExtensionData(key, value);
  }

  String? getExtensionData(String key) {
    return _storageService.getExtensionData(key);
  }
}
