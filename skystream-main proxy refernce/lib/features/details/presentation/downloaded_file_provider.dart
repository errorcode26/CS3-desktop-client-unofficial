import 'dart:io';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../core/domain/entity/multimedia_item.dart';
import '../../../core/services/download_service.dart';

part 'downloaded_file_provider.g.dart';

/// Provider that tracks existing downloaded files on disk.
/// Maps URL strings to File objects if they exist.
@Riverpod(keepAlive: true)
class DownloadedFiles extends _$DownloadedFiles {
  @override
  Map<String, File?> build() {
    return const <String, File?>{};
  }

  Future<void> checkFile(MultimediaItem item, {Episode? episode}) async {
    final key = episode?.url ?? item.url;
    final downloadService = ref.read(downloadServiceProvider);
    final file = await downloadService.getDownloadedFile(
      item,
      episode: episode,
    );

    state = {...state, key: file};
  }

  void removeFile(String key) {
    state = {...state, key: null};
  }
}
