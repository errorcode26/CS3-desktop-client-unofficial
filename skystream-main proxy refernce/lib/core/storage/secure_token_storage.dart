import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import '../logger/app_logger.dart';
import 'storage_service.dart';

part 'secure_token_storage.g.dart';

/// Platform-secure storage for OAuth credentials (Trakt/Simkl/MAL/AniList
/// access + refresh tokens). Backed by Keychain (iOS/macOS), Keystore (Android),
/// Credentials API (Windows), and libsecret (Linux). Falls back to the
/// pre-existing Hive-backed [StorageService] on a per-key read if the secure
/// backend throws — so a missing libsecret install on Linux degrades gracefully
/// rather than locking the user out.
///
/// On first read of a given key, if the secure backend returns null but the
/// legacy [StorageService] still has a value (users who installed the app
/// before this change), the value is migrated into the secure backend and
/// removed from the legacy box. Existing OAuth sessions therefore survive the
/// upgrade.
class SecureTokenStorage {
  SecureTokenStorage(this._legacy);

  final StorageService _legacy;
  final FlutterSecureStorage _secure = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  Future<String?> read(String key) async {
    try {
      final v = await _secure.read(key: key);
      if (v != null) return v;
    } catch (e) {
      talker.error('SecureTokenStorage.read failed for $key', e);
    }
    // Legacy migration (one-shot): if a value exists in plain Hive, move it.
    final legacy = _legacy.getString(key);
    if (legacy != null && legacy.isNotEmpty) {
      try {
        await _secure.write(key: key, value: legacy);
        await _legacy.remove(key);
        talker.debug('SecureTokenStorage: migrated $key from legacy storage');
      } catch (e) {
        talker.error('SecureTokenStorage migration failed for $key', e);
        // Fall through and return the legacy value so the user stays logged in
        // even if the secure backend is unavailable on this platform.
        return legacy;
      }
      return legacy;
    }
    return null;
  }

  Future<void> write(String key, String value) async {
    try {
      await _secure.write(key: key, value: value);
      // Clear any stale legacy copy.
      await _legacy.remove(key);
    } catch (e) {
      talker.error('SecureTokenStorage.write failed for $key', e);
      // Last-resort fallback: keep the user logged in even on a broken
      // secure backend. Less ideal than secure storage but better than
      // dropping the session entirely.
      await _legacy.setString(key, value);
    }
  }

  Future<void> delete(String key) async {
    try {
      await _secure.delete(key: key);
    } catch (e) {
      talker.error('SecureTokenStorage.delete failed for $key', e);
    }
    await _legacy.remove(key);
  }
}

@Riverpod(keepAlive: true)
SecureTokenStorage secureTokenStorage(Ref ref) {
  return SecureTokenStorage(ref.watch(storageServiceProvider));
}
