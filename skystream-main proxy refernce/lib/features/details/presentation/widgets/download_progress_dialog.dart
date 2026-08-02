import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:background_downloader/background_downloader.dart';
import '../../../../core/services/download_service.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';

class DownloadProgressDialog extends ConsumerWidget {
  final String title;
  final String trackingUrl;

  const DownloadProgressDialog({
    super.key,
    required this.title,
    required this.trackingUrl,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final progressMap = ref.watch(downloadProgressProvider);
    final data = progressMap[trackingUrl];

    if (data == null) {
      // If download finished or disappeared, close dialog safely
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted && ModalRoute.of(context)?.isCurrent == true) {
          Navigator.of(context).pop();
        }
      });
      return const SizedBox.shrink();
    }

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 600),
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                data.status == TaskStatus.paused
                    ? l10n.downloadPaused
                    : l10n.downloading,
                style: Theme.of(
                  context,
                ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Text(
                title,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: LinearProgressIndicator(
                      value: data.progress,
                      borderRadius: BorderRadius.circular(4),
                      minHeight: 8,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Text(
                    '${(data.progress * 100).toInt()}%',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(
                    Icons.data_usage_rounded,
                    size: 16,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    "${data.downloadedSizeString} / ${data.totalSizeString}",
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _buildInfoItem(
                    context,
                    Icons.speed_rounded,
                    l10n.speed,
                    data.speedString,
                  ),
                  _buildInfoItem(
                    context,
                    Icons.timer_outlined,
                    l10n.remaining,
                    data.timeRemainingString,
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (data.progress < 1.0) ...[
                    TextButton(
                      onPressed: () async {
                        final service = ref.read(downloadServiceProvider);
                        await service.cancelDownload(data.taskId, trackingUrl);
                        if (context.mounted) Navigator.of(context).pop();
                      },
                      style: TextButton.styleFrom(
                        foregroundColor: Theme.of(context).colorScheme.error,
                      ),
                      child: Text(l10n.cancel),
                    ),
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () async {
                        final service = ref.read(downloadServiceProvider);
                        if (data.status == TaskStatus.paused) {
                          await service.resumeDownload(data.taskId);
                        } else {
                          await service.pauseDownload(data.taskId);
                        }
                      },
                      child: Text(
                        data.status == TaskStatus.paused
                            ? l10n.resume
                            : l10n.pause,
                      ),
                    ),
                    const SizedBox(width: 8),
                  ],
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: Text(l10n.close),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInfoItem(
    BuildContext context,
    IconData icon,
    String label,
    String value,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 16, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 4),
            Text(
              label,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: Theme.of(
            context,
          ).textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  static void show(BuildContext context, String title, String trackingUrl) {
    showDialog<void>(
      context: context,
      builder: (context) =>
          DownloadProgressDialog(title: title, trackingUrl: trackingUrl),
    );
  }
}
