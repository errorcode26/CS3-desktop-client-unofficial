import 'package:flutter/material.dart';
import 'hotstar_player_style.dart';

/// Where the prompt should sit vertically.
enum PromptVerticalAlignment {
  /// Above the bottom chrome (default for resume / skip).
  bottom,

  /// Vertically centered on the right side (for post-play overlays).
  center,
}

/// Shared anchor for player prompts (resume / next-episode / skip) that should
/// sit just above the bottom chrome, right-aligned. Derives its inset from the
/// single [HotstarPlayerStyle.bottomChromeHeight] token so it stays in sync
/// with the bottom bar instead of repeating its magic numbers.
class PlayerPromptPlacement extends StatelessWidget {
  const PlayerPromptPlacement({
    super.key,
    required this.child,
    this.isTv = false,
    this.alignment = PromptVerticalAlignment.bottom,
  });

  final Widget child;
  final bool isTv;
  final PromptVerticalAlignment alignment;

  static const double _gapAboveChrome = 12;

  /// On compact screens (phones) the bottom chrome is much shorter than the
  /// desktop/TV estimate of 132px — use a smaller gap so the card doesn't
  /// overflow the top of the screen.
  static double _bottomOffset(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final isCompact = size.shortestSide < 600;
    final chromeHeight = isCompact
        ? 60.0
        : HotstarPlayerStyle.bottomChromeHeight;
    final padding = MediaQuery.viewPaddingOf(context);
    return chromeHeight + _gapAboveChrome + padding.bottom;
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.viewPaddingOf(context);
    final edge = isTv
        ? HotstarPlayerStyle.tvEdgeInset
        : HotstarPlayerStyle.edgeInset;
    final double rightPadding = isTv
        ? edge
        : (padding.right > edge ? padding.right : edge);

    if (alignment == PromptVerticalAlignment.center) {
      return Positioned(
        right: rightPadding,
        top: 0,
        bottom: 0,
        child: Center(child: child),
      );
    }

    return Positioned(
      right: rightPadding,
      bottom: _bottomOffset(context),
      child: child,
    );
  }
}
