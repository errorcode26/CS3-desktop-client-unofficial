import 'package:flutter/material.dart';
import '../../../../core/models/torrent_status.dart';
import '../../../../core/widgets/marquee_widget.dart';
import '../widgets/hotstar_player_style.dart';

/// Compact, responsive torrent download-stats card. Self-contained: it sizes
/// its own typography/spacing down on small screens and never assumes a fixed
/// width (the caller constrains the width and places it). Purely informational
/// — wrap in an IgnorePointer at the call site so it never blocks the player.
class TorrentInfoWidget extends StatelessWidget {
  final TorrentStatus? status;

  const TorrentInfoWidget({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    final s = status;
    if (s == null) return const SizedBox.shrink();

    final compact = MediaQuery.sizeOf(context).shortestSide < 600;
    final readMb = s.bytesRead / 1024 / 1024;
    final totalMb = s.totalSize / 1024 / 1024;
    final pct = (s.progress.clamp(0.0, 1.0) * 100).toStringAsFixed(0);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: HotstarPlayerStyle.background.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.45),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Padding(
        padding: EdgeInsets.all(compact ? 10 : 13),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(
                  Icons.downloading_rounded,
                  color: HotstarPlayerStyle.accent,
                  size: 16,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: SizedBox(
                    height: compact ? 16 : 18,
                    child: MarqueeWidget(
                      child: Text(
                        s.title,
                        style: TextStyle(
                          color: HotstarPlayerStyle.primaryText,
                          fontWeight: FontWeight.w800,
                          fontSize: compact ? 12.5 : 14,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            SizedBox(height: compact ? 10 : 12),
            Row(
              children: [
                Expanded(
                  child: _Stat(
                    icon: Icons.download_rounded,
                    value: s.speedString,
                    label: 'Speed',
                    compact: compact,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _Stat(
                    icon: Icons.people_alt_rounded,
                    value: '${s.seeds} / ${s.peers}',
                    label: 'Seeds / Peers',
                    compact: compact,
                  ),
                ),
              ],
            ),
            SizedBox(height: compact ? 10 : 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: s.progress.clamp(0.0, 1.0),
                minHeight: 4,
                backgroundColor: HotstarPlayerStyle.trackInactive,
                valueColor: const AlwaysStoppedAnimation<Color>(
                  HotstarPlayerStyle.accent,
                ),
              ),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${readMb.toStringAsFixed(1)} / ${totalMb.toStringAsFixed(1)} MB',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: HotstarPlayerStyle.secondaryText,
                      fontSize: 10.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '$pct% · ${s.status}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: HotstarPlayerStyle.mutedText,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final bool compact;

  const _Stat({
    required this.icon,
    required this.value,
    required this.label,
    required this.compact,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: HotstarPlayerStyle.secondaryText, size: 16),
        const SizedBox(width: 6),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: HotstarPlayerStyle.primaryText,
                  fontSize: compact ? 12 : 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: HotstarPlayerStyle.mutedText,
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
