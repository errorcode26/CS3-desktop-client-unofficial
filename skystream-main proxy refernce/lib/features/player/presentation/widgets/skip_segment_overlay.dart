import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:media_kit/media_kit.dart';
import 'package:video_view/video_view.dart' as vv;
import '../../../../l10n/generated/app_localizations.dart';
import '../../../skip/data/skip_service.dart';
import '../player_controller.dart';
import 'hotstar_player_style.dart';
import 'player_prompt_placement.dart';

/// Displays a contextual "Skip Intro / Skip Recap / Skip Outro" button
/// when the current playback position falls inside a known [SkipSegment].
///
/// Position tracking is fully self-contained via [StreamBuilder] (media_kit)
/// or [ValueListenableBuilder] (video_view) — no parent rebuilds occur.
class SkipSegmentOverlay extends ConsumerStatefulWidget {
  final Player player;
  final vv.VideoController? videoViewController;
  final List<SkipSegment> skipSegments;
  final bool isTv;
  final bool controlsVisible;
  final VoidCallback? onFocusReturned;
  final FocusNode? focusNode;
  final ValueChanged<bool>? onActiveSegmentChanged;

  const SkipSegmentOverlay({
    super.key,
    required this.player,
    required this.skipSegments,
    this.videoViewController,
    this.isTv = false,
    this.controlsVisible = false,
    this.onFocusReturned,
    this.focusNode,
    this.onActiveSegmentChanged,
  });

  @override
  ConsumerState<SkipSegmentOverlay> createState() => _SkipSegmentOverlayState();
}

class _SkipSegmentOverlayState extends ConsumerState<SkipSegmentOverlay> {
  bool _isSkipping = false;
  Timer? _skipDebounceTimer;

  late final FocusNode _focusNode;

  SkipSegment? _activeSegment;
  StreamSubscription<Duration>? _mkPositionSub;

  @override
  void initState() {
    super.initState();
    _focusNode = widget.focusNode ?? FocusNode();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _setupListeners();
    });
  }

  void _setupListeners() {
    final useExoPlayer = ref.read(playerControllerProvider).useExoPlayer;
    if (useExoPlayer && widget.videoViewController != null) {
      widget.videoViewController!.position.addListener(_onVvPositionChanged);
    } else {
      _mkPositionSub = widget.player.stream.position.listen((pos) {
        _checkPosition(pos.inMilliseconds / 1000.0);
      });
    }
  }

  @override
  void didUpdateWidget(SkipSegmentOverlay old) {
    super.didUpdateWidget(old);
    if (old.videoViewController != widget.videoViewController) {
      old.videoViewController?.position.removeListener(_onVvPositionChanged);
      _mkPositionSub?.cancel();
      _setupListeners();
    }
    if (old.skipSegments != widget.skipSegments) {
      _checkPosition(_currentPositionSec);
    }
  }

  @override
  void dispose() {
    _skipDebounceTimer?.cancel();
    if (_focusNode.hasFocus) {
      widget.onFocusReturned?.call();
    }
    if (widget.focusNode == null) {
      _focusNode.dispose();
    }
    widget.videoViewController?.position.removeListener(_onVvPositionChanged);
    _mkPositionSub?.cancel();
    super.dispose();
  }

  double get _currentPositionSec {
    final useExoPlayer = ref.read(playerControllerProvider).useExoPlayer;
    if (useExoPlayer && widget.videoViewController != null) {
      return (widget.videoViewController!.position.value) / 1000.0;
    }
    return widget.player.state.position.inMilliseconds / 1000.0;
  }

  void _onVvPositionChanged() {
    final posSec = (widget.videoViewController?.position.value ?? 0) / 1000.0;
    _checkPosition(posSec);
  }

  void _checkPosition(double positionSec) {
    if (_isSkipping) return;

    SkipSegment? newSegment;
    for (final seg in widget.skipSegments) {
      if (positionSec >= seg.startTime && positionSec < seg.endTime) {
        newSegment = seg;
        break;
      }
    }

    if (newSegment != _activeSegment) {
      if (newSegment == null && _focusNode.hasFocus) {
        widget.onFocusReturned?.call();
      }
      setState(() {
        _activeSegment = newSegment;
      });
      widget.onActiveSegmentChanged?.call(newSegment != null);
    }
  }

  void _handleSkip(SkipSegment segment) {
    ref
        .read(playerControllerProvider.notifier)
        .seekTo(Duration(milliseconds: (segment.endTime * 1000).toInt()));

    if (segment.type == SkipType.outro) {
      ref.read(playerControllerProvider.notifier).forceNextEpisodeOverlay();
    }

    setState(() => _isSkipping = true);
    _skipDebounceTimer?.cancel();
    _skipDebounceTimer = Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() => _isSkipping = false);
    });
  }

  String _labelForType(SkipType type, AppLocalizations l10n) {
    return switch (type) {
      SkipType.intro => l10n.skipIntro,
      SkipType.outro => l10n.skipOutro,
      SkipType.recap => l10n.skipRecap,
      SkipType.unknown => l10n.skip,
    };
  }

  @override
  Widget build(BuildContext context) {
    if (widget.skipSegments.isEmpty) return const SizedBox.shrink();
    return _buildButton(_activeSegment);
  }

  Widget _buildButton(SkipSegment? activeSegment) {
    final size = MediaQuery.sizeOf(context);
    final isCompact = size.shortestSide < 600;

    return PlayerPromptPlacement(
      isTv: widget.isTv,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 300),
        switchInCurve: Curves.easeOut,
        switchOutCurve: Curves.easeIn,
        transitionBuilder: (child, animation) {
          return FadeTransition(
            opacity: animation,
            child: SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0.15, 0),
                end: Offset.zero,
              ).animate(animation),
              child: child,
            ),
          );
        },
        child: activeSegment != null
            ? _SkipPill(
                key: ValueKey('skip_${activeSegment.type.name}'),
                label: _labelForType(
                  activeSegment.type,
                  AppLocalizations.of(context)!,
                ),
                focusNode: _focusNode,
                isTv: widget.isTv,
                isCompact: isCompact,
                controlsVisible: widget.controlsVisible,
                onPressed: () => _handleSkip(activeSegment),
              )
            : const SizedBox.shrink(key: ValueKey('skip_empty')),
      ),
    );
  }
}

/// The actual pill-shaped skip button.
class _SkipPill extends StatelessWidget {
  final String label;
  final FocusNode focusNode;
  final bool isTv;
  final bool isCompact;
  final bool controlsVisible;
  final VoidCallback onPressed;

  const _SkipPill({
    super.key,
    required this.label,
    required this.focusNode,
    required this.isTv,
    required this.isCompact,
    required this.controlsVisible,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final borderRadius = BorderRadius.circular(isCompact ? 8 : 10);
    final buttonHeight = isCompact ? 46.0 : 52.0;

    return FocusTraversalGroup(
      child: Focus(
        focusNode: focusNode,
        autofocus: isTv && controlsVisible,
        canRequestFocus: controlsVisible,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          if (event.logicalKey == LogicalKeyboardKey.select ||
              event.logicalKey == LogicalKeyboardKey.enter ||
              event.logicalKey == LogicalKeyboardKey.space) {
            onPressed();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: Builder(
          builder: (context) {
            final isFocused = Focus.of(context).hasFocus;
            return AnimatedScale(
              scale: isFocused ? 1.04 : 1.0,
              duration: const Duration(milliseconds: 150),
              curve: Curves.easeOut,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                decoration: BoxDecoration(
                  borderRadius: borderRadius,
                  boxShadow: isFocused
                      ? [
                          BoxShadow(
                            color: HotstarPlayerStyle.accent.withValues(
                              alpha: 0.55,
                            ),
                            blurRadius: 16,
                            spreadRadius: 1,
                          ),
                        ]
                      : null,
                ),
                child: Material(
                  color: Colors.transparent,
                  child: Ink(
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.52),
                      borderRadius: borderRadius,
                      border: Border.all(
                        color: isFocused
                            ? HotstarPlayerStyle.accent
                            : Colors.white.withValues(alpha: 0.22),
                        width: isFocused ? 2 : 1,
                      ),
                    ),
                    child: InkWell(
                      borderRadius: borderRadius,
                      onTap: onPressed,
                      focusColor: HotstarPlayerStyle.accent.withValues(
                        alpha: 0.24,
                      ),
                      child: SizedBox(
                        height: buttonHeight,
                        child: Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: isCompact ? 14 : 18,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(
                                Icons.skip_next_rounded,
                                color: Colors.white,
                                size: 20,
                              ),
                              SizedBox(width: isCompact ? 6 : 8),
                              Text(
                                label,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: isCompact ? 13 : 15,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
