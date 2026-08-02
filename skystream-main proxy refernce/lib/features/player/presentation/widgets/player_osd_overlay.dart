import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../player_gesture_handler.dart';
import 'hotstar_edge_rail.dart';

/// Rebuilds only when [PlayerGestureHandler] notifies (OSD/volume state).
/// Use this instead of listening to the handler in the full controls to avoid
/// rebuilding the entire player UI on every OSD change.
class PlayerOSDVolumeOverlay extends ConsumerWidget {
  const PlayerOSDVolumeOverlay({
    super.key,
    required this.getDuration,
    required this.formatDuration,
  });

  final Duration Function() getDuration;
  final String Function(Duration) formatDuration;

  String _formatSignedDelta(Duration delta) {
    final sign = delta.isNegative ? '-' : '+';
    return '$sign${formatDuration(delta.abs())}';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerGestureHandlerProvider);
    final handler = ref.read(playerGestureHandlerProvider.notifier);
    final seekDelta = state.swipeSeekValue != null
        ? state.swipeSeekValue! -
              (state.swipeSeekStartValue ?? state.swipeSeekValue!)
        : Duration.zero;
    final size = MediaQuery.sizeOf(context);
    final seekFontSize = size.shortestSide < 600 ? 24.0 : 32.0;

    if (state.swipeSeekValue != null) {
      return Stack(
        children: [
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 120),
                child: Center(
                  key: const ValueKey('swipe-seek-target'),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 6,
                      ),
                      child: Text(
                        '${formatDuration(state.swipeSeekValue!)} [${_formatSignedDelta(seekDelta)}]',
                        maxLines: 1,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: seekFontSize,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 0,
                          shadows: [
                            Shadow(
                              color: Colors.black.withValues(alpha: 0.68),
                              blurRadius: 4,
                              offset: const Offset(0, 1),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      );
    }

    return Stack(
      children: [
        PlayerOsdOverlay(
          showOSD: state.showOSD,
          osdValue: state.osdValue,
          osdLabel: state.osdLabel,
          osdIcon: state.osdIcon,
          osdAlignment: state.osdAlignment,
          supportsVolumeBoost: handler.supportsVolumeBoost,
        ),
      ],
    );
  }
}

class PlayerOsdOverlay extends StatelessWidget {
  final bool showOSD;
  final double? osdValue;
  final String osdLabel;
  final IconData osdIcon;
  final Alignment osdAlignment;
  final bool supportsVolumeBoost;

  const PlayerOsdOverlay({
    super.key,
    required this.showOSD,
    required this.osdValue,
    required this.osdLabel,
    required this.osdIcon,
    required this.osdAlignment,
    required this.supportsVolumeBoost,
  });

  @override
  Widget build(BuildContext context) {
    if (!showOSD) return const SizedBox.shrink();
    final bool showBoostState =
        supportsVolumeBoost &&
        (osdValue ?? 0) > 1.0 &&
        !(osdLabel == "Brightness" || osdLabel == "Auto");

    final isBrightness = osdLabel == "Brightness" || osdLabel == "Auto";
    if (osdValue != null) {
      return Align(
        alignment: isBrightness ? Alignment.centerRight : Alignment.centerLeft,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 42),
          child: HotstarEdgeRail(
            icon: osdIcon,
            value: osdValue!,
            label: osdLabel,
            isBoosted: showBoostState,
            supportsVolumeBoost: supportsVolumeBoost,
          ),
        ),
      );
    }

    return Align(
      alignment: osdAlignment,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.8),
            borderRadius: BorderRadius.circular(30),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(osdIcon, color: Colors.white, size: 24),
              const SizedBox(width: 12),
              Text(
                osdLabel,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
