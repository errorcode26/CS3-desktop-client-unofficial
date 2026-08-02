import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'services/plugin_storage_service.dart';
import 'services/repository_service.dart';

import '../network/dio_client_provider.dart';
import '../services/torrent_service.dart';
import '../../features/settings/presentation/general_settings_provider.dart';

part 'providers.g.dart';

// Repository Service Provider
@Riverpod(keepAlive: true)
RepositoryService repositoryService(Ref ref) {
  final generalSettings = ref.watch(generalSettingsProvider);
  return RepositoryService(
    ref.watch(dioClientProvider),
    enableGithubProxy: generalSettings.githubProxyEnabled,
  );
}

// Plugin Storage Service Provider
@Riverpod(keepAlive: true)
PluginStorageService pluginStorageService(Ref ref) {
  return PluginStorageService();
}

// Torrent Service Provider
@Riverpod(keepAlive: true)
TorrentService torrentService(Ref ref) {
  return TorrentService();
}
