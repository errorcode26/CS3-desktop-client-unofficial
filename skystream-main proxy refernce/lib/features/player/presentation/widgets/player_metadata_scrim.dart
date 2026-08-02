import 'dart:async';
import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../../../core/domain/entity/multimedia_item.dart';
import 'hotstar_player_style.dart';

/// Netflix-style left-anchored metadata scrim overlay.
///
/// Sits over the paused video as a fixed-width panel with a horizontal gradient
/// (opaque black on the left → transparent on the right). It is a sibling of the
/// player controls — never inside the center-controls region.
///
/// **Visibility logic (the "when"):**
/// [scheduleMetadataVisibility] is called on every play/pause state change and
/// every auto-hide cycle. When paused → `postDelayed(8 s)` then a 4-guard
/// fade-in. When not paused → fade-out immediately. A monotonic
/// [_visibilityToken] guards against stale timers (resume within 8 s, dialog
/// opened, etc.).
///
/// **Animations:**
/// - *Fade*: alpha 0→1 over 500 ms (decelerate) on show; alpha→0 over 300 ms
///   (accelerateDecelerate) on hide.
/// - *Slide*: translateY synced with the HUD (top bar / bottom bar toggle).
///
/// **Content (per-field graceful degradation):**
/// - Logo (ImageView) / Title (Text) — mutually exclusive; logo hides title.
///   If logo URL is blank or fails to load → falls back to title text.
/// - Meta line: tags • year • season/ep • ⭐ score — joined with " • ". Hidden
///   if all parts are null/blank.
/// - Description: synopsis, max 5 lines. Hidden if null/blank.
///
/// Only shown on TV and desktop — never on phones.
class PlayerMetadataScrim extends StatefulWidget {
  /// The multimedia item whose metadata to display.
  final MultimediaItem? item;

  /// The current episode (nullable for movies).
  final Episode? episode;

  /// Whether this is a series (show season/episode info in the meta line).
  final bool isSeries;

  /// Whether the player is currently paused. Drives the 8-second schedule.
  final bool isPaused;

  /// Whether a dialog is currently open (sources, audio, subtitles, speed,
  /// episodes, content panel). The scrim won't appear while a dialog is open.
  final bool isDialogOpen;

  /// Whether the player chrome (controls) is visible. The scrim slides in
  /// tandem with the chrome hide/show.
  final bool controlsVisible;

  /// True on Android/Fire TV.
  final bool isTv;

  /// Called when the scrim wants the player UI (chrome) hidden so the info
  /// panel has a clean backdrop.
  final VoidCallback? onHidePlayerUI;

  const PlayerMetadataScrim({
    super.key,
    required this.item,
    this.episode,
    this.isSeries = false,
    required this.isPaused,
    this.isDialogOpen = false,
    this.controlsVisible = false,
    this.isTv = false,
    this.onHidePlayerUI,
  });

  @override
  State<PlayerMetadataScrim> createState() => PlayerMetadataScrimState();
}

class PlayerMetadataScrimState extends State<PlayerMetadataScrim>
    with TickerProviderStateMixin {
  /// Monotonically increasing token to guard against stale postDelayed
  /// callbacks. Incremented on every pause/resume/autoHide/uiReset so that
  /// a timer scheduled 8 s ago bails if the world has changed.
  int _visibilityToken = 0;

  /// Whether the scrim content is logically visible (controls the animation
  /// target; actual visibility waits for the fade to finish).
  bool _isVisible = false;

  late final AnimationController _fadeController;
  late final AnimationController _slideController;

  Timer? _scheduleTimer;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 500), // fade-in
      reverseDuration: const Duration(milliseconds: 300), // fade-out
    );
    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
      value: 1.0, // start off-screen (slid down)
    );

    _fadeController.addStatusListener(_onFadeStatus);

    // Schedule initial visibility check.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduleMetadataVisibility();
    });
  }

  @override
  void didUpdateWidget(covariant PlayerMetadataScrim oldWidget) {
    super.didUpdateWidget(oldWidget);

    // Play/pause changed → reschedule.
    if (widget.isPaused != oldWidget.isPaused) {
      _scheduleMetadataVisibility();
    }

    // Dialog state changed → reschedule (dialog opening cancels, closing re-evaluates).
    if (widget.isDialogOpen != oldWidget.isDialogOpen) {
      _scheduleMetadataVisibility();
    }

    // Controls visibility changed.
    if (widget.controlsVisible != oldWidget.controlsVisible) {
      if (widget.controlsVisible) {
        // Controls just became visible:
        // If the scrim was currently visible, hide it immediately.
        if (_isVisible) {
          _hideScrim();
        }
        _slideController.animateTo(1.0, curve: Curves.easeOut); // slide down (off)
      } else {
        // Controls just became hidden:
        // Reschedule the pause timer (will show scrim after 8s).
        _scheduleMetadataVisibility();
        _slideController.animateTo(0.0, curve: Curves.easeOut); // slide up (on)
      }
    }
  }

  @override
  void dispose() {
    _scheduleTimer?.cancel();
    _fadeController.removeStatusListener(_onFadeStatus);
    _fadeController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  void _onFadeStatus(AnimationStatus status) {
    // When the hide animation completes, fully remove from the tree.
    if (status == AnimationStatus.dismissed && _isVisible == false) {
      if (mounted) setState(() {});
    }
  }

  /// The core scheduling routine. Called on every play/pause transition,
  /// autoHide, and uiReset.
  void _scheduleMetadataVisibility() {
    _scheduleTimer?.cancel();
    _visibilityToken++;

    if (!widget.isPaused || widget.isDialogOpen) {
      // Not paused OR dialog is open → hide immediately.
      _hideScrim();
      return;
    }

    // If already visible, no need to schedule a timer to show it.
    if (_isVisible) return;

    // Paused and hidden → schedule the 8-second delayed show.
    final token = _visibilityToken;
    _scheduleTimer = Timer(const Duration(seconds: 8), () {
      if (!mounted) return;

      // 4-guard check:
      // 1. Token must match (no state change since scheduling).
      if (_visibilityToken != token) return;
      // 2. Must not already be visible.
      if (_isVisible) return;
      // 3. Must still be paused.
      if (!widget.isPaused) return;
      // 4. No dialog must be open.
      if (widget.isDialogOpen) return;

      _showScrim();
    });
  }

  /// Public method so the parent can force a reschedule (e.g. on autoHide).
  void resetSchedule() {
    _scheduleMetadataVisibility();
  }

  /// Public method so the parent can force-hide the scrim (e.g. on uiReset).
  void forceHide() {
    _visibilityToken++;
    _hideScrim();
  }

  void _showScrim() {
    if (!mounted || _isVisible) return;
    setState(() => _isVisible = true);

    // Fade in with DecelerateInterpolator.
    _fadeController.animateTo(1.0, curve: Curves.decelerate);

    // Slide in.
    _slideController.animateTo(0.0, curve: Curves.easeOut);

    // Hide the player chrome so the info panel has a clean backdrop.
    widget.onHidePlayerUI?.call();
  }

  void _hideScrim() {
    if (!mounted || !_isVisible) return;
    setState(() => _isVisible = false);

    // Fade out with AccelerateDecelerateInterpolator.
    _fadeController.animateTo(0.0, curve: Curves.easeInOut);
  }

  @override
  Widget build(BuildContext context) {
    // Don't show on phones — only TV and desktop.
    final isDesktop =
        Platform.isMacOS || Platform.isWindows || Platform.isLinux;
    if (!widget.isTv && !isDesktop) return const SizedBox.shrink();

    // Nothing to show if there's no item data.
    if (widget.item == null) return const SizedBox.shrink();

    // Not visible and animation fully dismissed → remove from tree.
    if (!_isVisible && !_fadeController.isAnimating) {
      return const SizedBox.shrink();
    }

    final item = widget.item!;
    final screenWidth = MediaQuery.sizeOf(context).width;
    // Fixed width: ~40% of screen on desktop, ~45% on TV, clamped.
    final panelWidth = (screenWidth * (widget.isTv ? 0.45 : 0.40))
        .clamp(320.0, 600.0);

    return Positioned(
      left: 0,
      top: 0,
      bottom: 0,
      width: panelWidth,
      child: AnimatedBuilder(
        animation: Listenable.merge([_fadeController, _slideController]),
        builder: (context, child) {
          return Opacity(
            opacity: _fadeController.value,
            child: Transform.translate(
              offset: Offset(0, _slideController.value * 20),
              child: child,
            ),
          );
        },
        child: IgnorePointer(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [
                  Colors.black.withValues(alpha: 0.85),
                  Colors.black.withValues(alpha: 0.65),
                  Colors.black.withValues(alpha: 0.30),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.45, 0.75, 1.0],
              ),
            ),
            child: SafeArea(
              right: false,
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  widget.isTv
                      ? HotstarPlayerStyle.tvEdgeInset
                      : HotstarPlayerStyle.edgeInset + 8,
                  widget.isTv ? 80 : 60,
                  40,
                  widget.isTv ? 80 : 60,
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // --- Logo / Title (mutually exclusive) ---
                    _buildLogoOrTitle(item),

                    const SizedBox(height: 20),

                    // --- Meta line ---
                    _buildMetaLine(item),

                    const SizedBox(height: 16),

                    // --- Description ---
                    _buildDescription(item),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// Logo (if available) with text-title fallback. If the logo URL is blank
  /// or fails to load, the title text is shown instead.
  /// Sizing matches the details desktop hero pattern (height: 200) but scaled
  /// proportionally for the narrower scrim panel.
  Widget _buildLogoOrTitle(MultimediaItem item) {
    final logoUrl = item.logoUrl;
    final hasLogo = logoUrl != null && logoUrl.isNotEmpty;

    if (!hasLogo) return _titleText(item.title);

    final double logoHeight = widget.isTv ? 120 : 100;

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: logoHeight,
        maxWidth: double.infinity,
      ),
      child: CachedNetworkImage(
        imageUrl: logoUrl,
        height: logoHeight,
        alignment: Alignment.centerLeft,
        fit: BoxFit.contain,
        placeholder: (_, _) => _titleText(item.title),
        errorWidget: (_, _, _) => _titleText(item.title),
      ),
    );
  }

  Widget _titleText(String title) {
    return Text(
      title,
      style: TextStyle(
        color: HotstarPlayerStyle.primaryText,
        fontSize: widget.isTv ? 32 : 28,
        fontWeight: FontWeight.w800,
        height: 1.15,
        letterSpacing: -0.3,
      ),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// Meta line: tags • year • S1:E3 • ⭐ 8.5
  Widget _buildMetaLine(MultimediaItem item) {
    final parts = <String>[];

    // Tags / genres (first 6).
    final tags = item.tags;
    if (tags != null && tags.isNotEmpty) {
      parts.add(tags.take(6).join(', '));
    }

    // Year.
    if (item.year != null) {
      parts.add(item.year.toString());
    }

    // Season/Episode (series only).
    if (widget.isSeries && widget.episode != null) {
      final ep = widget.episode!;
      parts.add('S${ep.season}:E${ep.episode}');
    }

    // Score / Rating.
    if (item.score != null && item.score! > 0) {
      parts.add('⭐ ${item.score!.toStringAsFixed(1)}');
    }

    if (parts.isEmpty) return const SizedBox.shrink();

    return Text(
      parts.join('  •  '),
      style: TextStyle(
        color: HotstarPlayerStyle.secondaryText,
        fontSize: widget.isTv ? 16 : 14,
        fontWeight: FontWeight.w500,
        height: 1.4,
      ),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// Synopsis / description, max 5 lines.
  Widget _buildDescription(MultimediaItem item) {
    final plot = item.description;
    if (plot == null || plot.trim().isEmpty) return const SizedBox.shrink();

    return Text(
      plot.trim(),
      style: TextStyle(
        color: HotstarPlayerStyle.mutedText,
        fontSize: widget.isTv ? 15 : 13,
        fontWeight: FontWeight.w400,
        height: 1.55,
      ),
      maxLines: 5,
      overflow: TextOverflow.ellipsis,
    );
  }
}
