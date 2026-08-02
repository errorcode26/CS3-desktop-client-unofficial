import 'package:flutter/material.dart';

/// Visual + layout tokens for the player chrome.
///
/// This is the single source of truth for player colors, motion, gradients,
/// and the layout metrics that the chrome, subtitle offset, and floating
/// prompts all share — so nothing relies on magic numbers scattered across
/// widgets.
class HotstarPlayerStyle {
  // --- Colors ---
  static const Color background = Color(0xFF000000);
  static const Color panel = Color(0xFF05070B);
  static const Color panelElevated = Color(0xFF090D14);
  static const Color accent = Color(0xFF0A84FF);
  static const Color hotstar = Color(0xFF1F80E0);
  static const Color accentAlt = Color(0xFFDD3EFF);
  static const Color primaryText = Color(0xF2FFFFFF);
  static const Color secondaryText = Color(0xA6FFFFFF);
  static const Color mutedText = Color(0x73FFFFFF);
  static const Color divider = Color(0x1FFFFFFF);
  static const Color track = Color(0x55FFFFFF);
  static const Color trackInactive = Color(0x35FFFFFF);
  static const Color focus = Color(0x660A84FF);
  static const Color liveRed = Color(0xFFE53935);

  /// Marker on the scrubber for skip segments (intro / recap / outro). A warm
  /// amber so it reads clearly against the blue progress and grey track.
  static const Color skipSegment = Color(0xFFFFC107);

  // --- Motion ---
  static const Duration controlFadeDuration = Duration(milliseconds: 220);
  static const Duration fastMotionDuration = Duration(milliseconds: 160);
  static const Duration panelMotionDuration = Duration(milliseconds: 240);

  // --- Layout tokens ---
  /// Horizontal edge inset for the chrome on touch/desktop.
  static const double edgeInset = 20;

  /// Larger inset on TV to clear the overscan-unsafe border (~5% of edges
  /// is clipped on many TVs). Keeps controls and focus rings fully visible.
  static const double tvEdgeInset = 48;

  /// Approximate height of the bottom chrome (scrubber row + controls row +
  /// internal padding), excluding the safe-area bottom inset. Used to offset
  /// subtitles and to anchor floating prompts above the scrubber. The bottom
  /// bar itself is content-sized; this is a layout estimate, not a clamp.
  static const double bottomChromeHeight = 132;

  /// Focus-ring treatment shared by every focusable control so play/pause,
  /// seek, scrubber, action, and utility buttons look identical when focused.
  static const double focusScale = 1.04;

  // --- Gradients (const scrims, dark at the edge → transparent at center) ---
  static const LinearGradient topGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0xCC000000), Color(0x66000000), Color(0x00000000)],
    stops: [0.0, 0.55, 1.0],
  );

  static const LinearGradient bottomGradient = LinearGradient(
    begin: Alignment.bottomCenter,
    end: Alignment.topCenter,
    colors: [Color(0xE6000000), Color(0x99000000), Color(0x00000000)],
    stops: [0.0, 0.5, 1.0],
  );
}
