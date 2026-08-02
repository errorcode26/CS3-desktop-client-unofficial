import 'dart:io';
import 'package:flutter/material.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:skystream/core/domain/entity/multimedia_item.dart';
import 'package:skystream/core/services/download_service.dart';
import 'package:skystream/shared/widgets/custom_widgets.dart';
import 'package:collection/collection.dart';
import '../../../library/presentation/downloads_provider.dart';
import '../playback_launcher.dart';
import '../details_controller.dart';
import '../downloaded_file_provider.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';

class DownloadManagementDialog extends HookConsumerWidget {
  final MultimediaItem item;
  final Episode? episode;
  final File file;

  const DownloadManagementDialog({
    super.key,
    required this.item,
    this.episode,
    required this.file,
  });

  static Future<void> show(
    BuildContext context,
    MultimediaItem item,
    File file, {
    Episode? episode,
  }) async {
    return showDialog<void>(
      context: context,
      builder: (context) =>
          DownloadManagementDialog(item: item, file: file, episode: episode),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    // Try to get fresh details from the controller if available
    final detailsState = ref.watch(detailsControllerProvider(item.url));
    final currentItem = detailsState.item ?? item;

    final title = episode != null
        ? '${currentItem.title} - ${episode!.name}'
        : currentItem.title;

    final downloads = ref.watch(downloadsProvider).value ?? [];
    final matchingItem = downloads.firstWhereOrNull(
      (d) => d.item.url == item.url && d.episode?.url == episode?.url,
    );

    return AlertDialog(
      surfaceTintColor: Colors.transparent,
      title: Text(title),
      content: Text(l10n.videoAlreadyDownloadedPrompt),
      actions: [
        CustomButton(
          isPrimary: false,
          onPressed: () => Navigator.pop(context),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text(l10n.cancel),
          ),
        ),
        CustomButton(
          isPrimary: false,
          isOutlined: true,
          onPressed: () => _showDeleteConfirmation(context, ref, matchingItem),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.delete_outline_rounded,
                  color: Colors.red,
                  size: 20,
                ),
                const SizedBox(width: 8),
                Text(l10n.delete, style: const TextStyle(color: Colors.red)),
              ],
            ),
          ),
        ),
        CustomButton(
          isPrimary: true,
          onPressed: () {
            Navigator.pop(context);
            _playLocalFile(context, ref, currentItem);
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.play_arrow_rounded, size: 20),
                const SizedBox(width: 8),
                Text(l10n.playNow),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _showDeleteConfirmation(
    BuildContext context,
    WidgetRef ref,
    DownloadItem? matchingItem,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        surfaceTintColor: Colors.transparent,
        title: Text(l10n.deleteDownloadPrompt),
        content: Text(l10n.deleteDownloadConfirmation),
        actions: [
          CustomButton(
            isPrimary: false,
            onPressed: () => Navigator.pop(context, false),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(l10n.no),
            ),
          ),
          CustomButton(
            isPrimary: true,
            backgroundColor: Colors.red,
            onPressed: () => Navigator.pop(context, true),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(
                l10n.yesDelete,
                style: const TextStyle(color: Colors.white),
              ),
            ),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      if (matchingItem != null) {
        await ref.read(downloadsProvider.notifier).removeDownload(matchingItem);
      } else {
        await ref.read(downloadServiceProvider).deleteDownloadedFile(file);
      }

      ref
          .read(downloadedFilesProvider.notifier)
          .removeFile(episode?.url ?? item.url);
      if (context.mounted) Navigator.pop(context);
    }
  }

  void _playLocalFile(
    BuildContext context,
    WidgetRef ref,
    MultimediaItem details,
  ) {
    ref
        .read(playbackLauncherProvider)
        .play(context, file.path, baseItem: details, detailedItem: details);
  }
}
