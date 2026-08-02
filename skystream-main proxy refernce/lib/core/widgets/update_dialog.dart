import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/generated/app_localizations.dart';
import '../providers/update_provider.dart';
import '../data/models/github_release.dart';

class UpdateDialog extends ConsumerWidget {
  final GithubRelease release;

  const UpdateDialog({super.key, required this.release});

  static void show(BuildContext context, GithubRelease release) {
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (context) => UpdateDialog(release: release),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final updateState = ref.watch(updateControllerProvider);
    final l10n = AppLocalizations.of(context)!;

    return PopScope(
      canPop: updateState is! UpdateDownloading,
      child: AlertDialog(
        title: Text(l10n.updateAvailableTag(release.tagName)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (updateState is UpdateDownloading) ...[
              Text(l10n.downloadingUpdate),
              const SizedBox(height: 10),
              LinearProgressIndicator(value: updateState.progress),
              const SizedBox(height: 10),
              Text('${(updateState.progress * 100).toStringAsFixed(0)}%'),
            ] else if (updateState is UpdateError) ...[
              Text(
                l10n.errorPrefix(updateState.message),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ] else ...[
              // Truncate body if too long
              ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 200),
                child: SingleChildScrollView(child: Text(release.body)),
              ),
            ],
          ],
        ),
        actions: [
          if (updateState is! UpdateDownloading)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(l10n.later),
            ),
          if (updateState is! UpdateDownloading)
            FilledButton(
              autofocus: true,
              onPressed: () {
                ref
                    .read(updateControllerProvider.notifier)
                    .downloadAndInstall(release);
              },
              child: Text(l10n.updateNow),
            ),
        ],
      ),
    );
  }
}
