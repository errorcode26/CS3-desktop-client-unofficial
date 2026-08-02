import 'dart:async';
import 'dart:ui' as ui;
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import 'package:skystream/shared/widgets/thumbnail_error_placeholder.dart';
import 'hotstar_player_style.dart';
import 'player_prompt_placement.dart';

class NextEpisodeOverlay extends StatefulWidget {
  final String nextEpisodeTitle;
  final String? nextEpisodePosterUrl;
  final double? nextEpisodeRating;
  final int? nextEpisodeNumber;
  final int? nextEpisodeSeason;

  /// Episode runtime in seconds.
  final int? nextEpisodeRuntime;

  /// Short synopsis / description of the next episode.
  final String? nextEpisodeDescription;

  final VoidCallback onPlayNext;
  final VoidCallback onDismiss;
  final bool isTv;
  final FocusNode? focusNode;
  final bool isPlaying;

  const NextEpisodeOverlay({
    super.key,
    required this.nextEpisodeTitle,
    this.nextEpisodePosterUrl,
    this.nextEpisodeRating,
    this.nextEpisodeNumber,
    this.nextEpisodeSeason,
    this.nextEpisodeRuntime,
    this.nextEpisodeDescription,
    required this.onPlayNext,
    required this.onDismiss,
    this.isTv = false,
    this.focusNode,
    this.isPlaying = true,
  });

  @override
  State<NextEpisodeOverlay> createState() => _NextEpisodeOverlayState();
}

class _NextEpisodeOverlayState extends State<NextEpisodeOverlay>
    with TickerProviderStateMixin {
  late final AnimationController _countdownController;
  late final AnimationController _entranceController;
  late final Animation<Offset> _slideAnimation;
  late final Animation<double> _fadeAnimation;
  late final FocusNode _cancelFocusNode;
  Timer? _timer;
  bool _completed = false;
  bool _timerPaused = false;
  double _elapsedFraction = 0.0;

  static const _countdownSecs = 15;

  @override
  void initState() {
    super.initState();
    _cancelFocusNode = FocusNode(debugLabel: 'next_ep_cancel');

    _entranceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    )..forward();
    _entranceController.addStatusListener((status) {
      if (status == AnimationStatus.dismissed && _completed) {
        widget.onDismiss();
      }
    });

    _slideAnimation =
        Tween<Offset>(begin: const Offset(0.12, 0.0), end: Offset.zero).animate(
          CurvedAnimation(
            parent: _entranceController,
            curve: Curves.easeOutCubic,
          ),
        );

    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _entranceController,
        curve: const Cubic(0.0, 0.0, 0.3, 1.0),
      ),
    );

    _countdownController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: _countdownSecs),
    );

    if (widget.isPlaying) {
      _startCountdown();
    } else {
      _timerPaused = true;
    }
  }

  void _startCountdown() {
    _timerPaused = false;
    _countdownController.forward(from: _elapsedFraction);
    final remaining = _countdownSecs * (1.0 - _elapsedFraction);
    _timer = Timer(
      Duration(milliseconds: (remaining * 1000).round()),
      _handleTimeout,
    );
  }

  void _pauseCountdown() {
    if (_timerPaused || _completed) return;
    _timerPaused = true;
    _elapsedFraction = _countdownController.value;
    _countdownController.stop();
    _timer?.cancel();
  }

  void _resumeCountdown() {
    if (!_timerPaused || _completed) return;
    _startCountdown();
  }

  @override
  void didUpdateWidget(NextEpisodeOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isPlaying != oldWidget.isPlaying) {
      if (widget.isPlaying) {
        _resumeCountdown();
      } else {
        _pauseCountdown();
      }
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _countdownController.dispose();
    _entranceController.dispose();
    _cancelFocusNode.dispose();
    super.dispose();
  }

  void _handlePressed() {
    if (_completed) return;
    _completed = true;
    _timer?.cancel();
    _countdownController.stop();
    widget.onPlayNext();
  }

  void _handleTimeout() {
    if (_completed) return;
    _completed = true;
    widget.onPlayNext();
  }

  void _handleDismiss() {
    if (_completed) return;
    _completed = true;
    _timer?.cancel();
    _countdownController.stop();
    _entranceController.reverse();
  }

  String _formatRuntime(int? seconds) {
    if (seconds == null || seconds < 60) return '';
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) return '${h}h ${m}m';
    return '${m}m';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final size = MediaQuery.sizeOf(context);
    final isCompact = size.shortestSide < 600;
    final cardWidth = isCompact ? 280.0 : (widget.isTv ? 440.0 : 360.0);
    final thumbHeight = cardWidth * 9 / 16;

    final cardContent = ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Stack(
        children: [
          Positioned.fill(
            child: BackdropFilter(
              filter: ui.ImageFilter.blur(sigmaX: 6, sigmaY: 6),
              child: Container(color: Colors.black.withValues(alpha: 0.45)),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildThumbnail(thumbHeight, cardWidth, isCompact),
              _buildInfo(isCompact),
            ],
          ),
        ],
      ),
    );

    final card = isCompact
        ? Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.circular(12),
            clipBehavior: Clip.antiAlias,
            child: InkWell(onTap: _handlePressed, child: cardContent),
          )
        : cardContent;

    return PlayerPromptPlacement(
      isTv: widget.isTv,
      alignment: widget.isTv
          ? PromptVerticalAlignment.center
          : PromptVerticalAlignment.bottom,
      child: FadeTransition(
        opacity: _fadeAnimation,
        child: SlideTransition(
          position: _slideAnimation,
          child: SizedBox(
            width: cardWidth,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (isCompact) const SizedBox(height: 48),
                    card,
                    if (!isCompact) _buildButtons(isCompact, l10n),
                  ],
                ),
                if (isCompact)
                  Positioned(
                    top: 8,
                    left: 0,
                    right: 0,
                    child: Center(
                      child: GestureDetector(
                        onTap: _handleDismiss,
                        behavior: HitTestBehavior.opaque,
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.6),
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Colors.white.withValues(alpha: 0.25),
                              width: 1,
                            ),
                          ),
                          child: const Icon(
                            Icons.close,
                            color: Colors.white,
                            size: 18,
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildThumbnail(double height, double cardWidth, bool isCompact) {
    final imageUrl = widget.nextEpisodePosterUrl?.isNotEmpty == true
        ? widget.nextEpisodePosterUrl!
        : null;

    return SizedBox(
      height: height,
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (imageUrl != null)
              CachedNetworkImage(
                imageUrl: imageUrl,
                fit: BoxFit.cover,
                memCacheWidth: (cardWidth * 2).round(),
                memCacheHeight: (height * 2).round(),
                errorWidget: (context, url, error) =>
                    const ThumbnailErrorPlaceholder(),
                placeholder: (context, url) => Container(
                  color: Colors.white.withValues(alpha: 0.05),
                  child: const Center(
                    child: SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  ),
                ),
              )
            else
              const ThumbnailErrorPlaceholder(),

            // Gradient fade overlay on the top of the thumbnail container
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: height * 0.35,
              child: IgnorePointer(
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.45),
                        Colors.black.withValues(alpha: 0.0),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            if (widget.nextEpisodeRuntime != null &&
                widget.nextEpisodeRuntime! >= 60)
              Positioned(
                top: 6,
                right: 6,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.65),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    _formatRuntime(widget.nextEpisodeRuntime),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfo(bool isCompact) {
    final hasRating =
        widget.nextEpisodeRating != null && widget.nextEpisodeRating! > 0;
    final hasBadge =
        widget.nextEpisodeSeason != null || widget.nextEpisodeNumber != null;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        isCompact ? 12 : 16,
        isCompact ? 12 : 14,
        isCompact ? 12 : 16,
        isCompact ? 8 : 10,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 20),
            child: Row(
              children: [
                if (hasBadge)
                  Container(
                    padding: EdgeInsets.symmetric(
                      horizontal: isCompact ? 6 : 8,
                      vertical: isCompact ? 2 : 2.5,
                    ),
                    decoration: BoxDecoration(
                      color: HotstarPlayerStyle.accent.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(100),
                      border: Border.all(
                        color: HotstarPlayerStyle.accent.withValues(
                          alpha: 0.35,
                        ),
                        width: 0.8,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.25),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Text.rich(
                      TextSpan(
                        children: [
                          if (widget.nextEpisodeSeason != null)
                            TextSpan(
                              text: 'S${widget.nextEpisodeSeason}',
                              style: TextStyle(
                                fontSize: isCompact ? 8.5 : 9.5,
                                fontWeight: FontWeight.w400,
                                color: Colors.white.withValues(alpha: 0.7),
                                letterSpacing: 0.4,
                              ),
                            ),
                          if (widget.nextEpisodeSeason != null &&
                              widget.nextEpisodeNumber != null)
                            TextSpan(
                              text: ' • ',
                              style: TextStyle(
                                fontSize: isCompact ? 8 : 9,
                                fontWeight: FontWeight.bold,
                                color: HotstarPlayerStyle.accent.withValues(
                                  alpha: 0.55,
                                ),
                              ),
                            ),
                          if (widget.nextEpisodeNumber != null)
                            TextSpan(
                              text: 'E${widget.nextEpisodeNumber}',
                              style: TextStyle(
                                fontSize: isCompact ? 9.5 : 10.5,
                                fontWeight: FontWeight.w700,
                                color: Colors.white,
                                letterSpacing: 0.4,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                if (hasRating) ...[const Spacer(), _buildRating(isCompact)],
              ],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            widget.nextEpisodeTitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: HotstarPlayerStyle.primaryText,
              fontSize: isCompact ? 14 : 16,
              fontWeight: FontWeight.w700,
              height: 1.25,
            ),
          ),
          if (widget.nextEpisodeDescription != null &&
              widget.nextEpisodeDescription!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                widget.nextEpisodeDescription!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: HotstarPlayerStyle.secondaryText,
                  fontSize: isCompact ? 11 : 12,
                  fontWeight: FontWeight.w400,
                  height: 1.4,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildRating(bool isCompact) {
    final rating = widget.nextEpisodeRating ?? 0.0;
    final fullStars = rating ~/ 2;
    final halfStar = rating % 2 >= 1;
    final numeric = rating.toStringAsFixed(1);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        ...List.generate(5, (i) {
          IconData icon;
          final color = i < fullStars
              ? const Color(0xFFFFC107)
              : i == fullStars && halfStar
              ? const Color(0xFFFFC107)
              : Colors.white.withValues(alpha: 0.35);
          if (i < fullStars) {
            icon = Icons.star_rounded;
          } else if (i == fullStars && halfStar) {
            icon = Icons.star_half_rounded;
          } else {
            icon = Icons.star_outline_rounded;
          }
          return Padding(
            padding: EdgeInsets.only(right: isCompact ? 1 : 1.5),
            child: Icon(icon, size: isCompact ? 13 : 14, color: color),
          );
        }),
        const SizedBox(width: 4),
        Text(
          numeric,
          style: TextStyle(
            color: HotstarPlayerStyle.secondaryText,
            fontSize: isCompact ? 10 : 11,
            fontWeight: FontWeight.w700,
          ),
        ),
        Text(
          '/10',
          style: TextStyle(
            color: HotstarPlayerStyle.mutedText,
            fontSize: isCompact ? 9 : 10,
            fontWeight: FontWeight.w500,
          ),
        ),
      ],
    );
  }

  Widget _buildButtons(bool isCompact, AppLocalizations l10n) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        isCompact ? 12 : 16,
        isCompact ? 8 : 10,
        isCompact ? 12 : 16,
        isCompact ? 8 : 10,
      ),
      child: Row(
        children: [
          Expanded(
            child: _PlayNowButton(
              onPressed: _handlePressed,
              isTv: widget.isTv,
              isCompact: isCompact,
              label: l10n.playNow,
              isCompleted: _completed,
              focusNode: widget.focusNode,
            ),
          ),
          SizedBox(width: isCompact ? 8 : 10),
          Expanded(
            child: _CancelButton(
              onPressed: _handleDismiss,
              isTv: widget.isTv,
              isCompact: isCompact,
              label: MaterialLocalizations.of(context).closeButtonTooltip,
              isCompleted: _completed,
              focusNode: _cancelFocusNode,
            ),
          ),
        ],
      ),
    );
  }
}

class _PlayNowButton extends StatefulWidget {
  final VoidCallback onPressed;
  final bool isTv;
  final bool isCompact;
  final String label;
  final bool isCompleted;
  final FocusNode? focusNode;

  const _PlayNowButton({
    required this.onPressed,
    required this.isTv,
    required this.isCompact,
    required this.label,
    required this.isCompleted,
    this.focusNode,
  });

  @override
  State<_PlayNowButton> createState() => _PlayNowButtonState();
}

class _PlayNowButtonState extends State<_PlayNowButton>
    with SingleTickerProviderStateMixin {
  FocusNode? _focusNode;
  bool _isFocused = false;
  bool _isHovered = false;
  late AnimationController _shineController;

  static const double _skew = -0.21;

  bool get _isActive => _isFocused || _isHovered;

  @override
  void initState() {
    super.initState();
    _shineController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    );
    if (widget.isTv) {
      _focusNode = widget.focusNode ?? FocusNode();
      _focusNode!.addListener(() {
        if (mounted) setState(() => _isFocused = _focusNode!.hasFocus);
      });
    }
  }

  @override
  void dispose() {
    _shineController.dispose();
    if (widget.focusNode == null) _focusNode?.dispose();
    super.dispose();
  }

  void _onHover(bool hovering) {
    if (!mounted) return;
    setState(() => _isHovered = hovering);
    if (hovering) {
      _shineController.forward(from: 0);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isCompact = widget.isCompact;
    final button = Transform(
      alignment: Alignment.center,
      transform: Matrix4.skewX(_skew),
      child: AnimatedScale(
        scale: _isActive ? 1.03 : 1.0,
        duration: const Duration(milliseconds: 150),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: isCompact ? 40 : null,
          decoration: BoxDecoration(
            color: HotstarPlayerStyle.accent,
            boxShadow: _isActive
                ? [
                    BoxShadow(
                      color: HotstarPlayerStyle.accent.withValues(alpha: 0.5),
                      blurRadius: 16,
                      spreadRadius: 1,
                    ),
                  ]
                : null,
          ),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              // Shine overlay
              Positioned.fill(
                child: ClipRect(
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final w = constraints.maxWidth;
                      return AnimatedBuilder(
                        animation: _shineController,
                        builder: (context, _) {
                          final streakWidth = w * 0.4;
                          return Transform.translate(
                            offset: Offset(
                              (-0.6 + _shineController.value * 2.1) * w,
                              0,
                            ),
                            child: Container(
                              width: streakWidth,
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  colors: [
                                    Colors.transparent,
                                    Colors.white.withValues(alpha: 0.4),
                                    Colors.transparent,
                                  ],
                                ),
                              ),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
              ),
              // Content (counter-skewed)
              Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: widget.isCompleted ? null : widget.onPressed,
                  onHover: widget.isTv ? null : _onHover,
                  splashColor: Colors.white.withValues(alpha: 0.15),
                  highlightColor: Colors.white.withValues(alpha: 0.05),
                  child: Transform(
                    alignment: Alignment.center,
                    transform: Matrix4.skewX(-_skew),
                    child: Center(
                      widthFactor: isCompact ? null : 1.0,
                      heightFactor: isCompact ? null : 1.0,
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: isCompact ? 16 : 32,
                          vertical: isCompact ? 0 : 12,
                        ),
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.play_arrow_rounded,
                                color: Colors.white,
                                size: isCompact ? 18 : 20,
                              ),
                              SizedBox(width: isCompact ? 6 : 8),
                              Text(
                                widget.label.toUpperCase(),
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: isCompact ? 11 : 13,
                                  fontWeight: FontWeight.w900,
                                  letterSpacing: 2.6,
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
            ],
          ),
        ),
      ),
    );

    if (widget.isTv) {
      return Focus(
        focusNode: _focusNode,
        autofocus: true,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.enter ||
              key == LogicalKeyboardKey.space) {
            widget.onPressed();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: button,
      );
    }
    return button;
  }
}

class _CancelButton extends StatefulWidget {
  final VoidCallback onPressed;
  final bool isTv;
  final bool isCompact;
  final String label;
  final bool isCompleted;
  final FocusNode? focusNode;

  const _CancelButton({
    required this.onPressed,
    required this.isTv,
    required this.isCompact,
    required this.label,
    required this.isCompleted,
    this.focusNode,
  });

  @override
  State<_CancelButton> createState() => _CancelButtonState();
}

class _CancelButtonState extends State<_CancelButton> {
  FocusNode? _focusNode;
  bool _isFocused = false;
  bool _isHovered = false;

  bool get _isActive => _isFocused || _isHovered;
  static const double _skew = -0.21;

  @override
  void initState() {
    super.initState();
    if (widget.isTv) {
      _focusNode = widget.focusNode ?? FocusNode();
      _focusNode!.addListener(() {
        if (mounted) setState(() => _isFocused = _focusNode!.hasFocus);
      });
    }
  }

  @override
  void dispose() {
    if (widget.focusNode == null) _focusNode?.dispose();
    super.dispose();
  }

  void _onHover(bool hovering) {
    if (!mounted) return;
    setState(() => _isHovered = hovering);
  }

  @override
  Widget build(BuildContext context) {
    final isCompact = widget.isCompact;
    final primary = Theme.of(context).colorScheme.primary;
    final button = Transform(
      alignment: Alignment.center,
      transform: Matrix4.skewX(_skew),
      child: AnimatedScale(
        scale: _isActive ? 1.03 : 1.0,
        duration: const Duration(milliseconds: 150),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          height: isCompact ? 40 : null,
          decoration: BoxDecoration(
            color: _isActive
                ? Colors.black.withValues(alpha: 0.8)
                : Colors.black.withValues(alpha: 0.6),
            border: Border.all(
              color: _isActive
                  ? primary.withValues(alpha: 0.8)
                  : Colors.white.withValues(alpha: 0.1),
              width: 1,
            ),
          ),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              // Right edge vertical line
              Positioned(
                top: 0,
                right: 0,
                bottom: 0,
                width: 1,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  color: _isActive
                      ? primary.withValues(alpha: 0.5)
                      : Colors.white.withValues(alpha: 0.1),
                ),
              ),
              // Gradient overlay (fades in on hover)
              Positioned.fill(
                child: AnimatedOpacity(
                  opacity: _isActive ? 1.0 : 0.0,
                  duration: const Duration(milliseconds: 300),
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.centerLeft,
                        end: Alignment.centerRight,
                        colors: [
                          primary.withValues(alpha: 0.0),
                          primary.withValues(alpha: 0.15),
                          primary.withValues(alpha: 0.0),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              // Content (counter-skewed)
              Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: widget.isCompleted ? null : widget.onPressed,
                  onHover: widget.isTv ? null : _onHover,
                  splashColor: Colors.white.withValues(alpha: 0.1),
                  highlightColor: Colors.white.withValues(alpha: 0.03),
                  child: Transform(
                    alignment: Alignment.center,
                    transform: Matrix4.skewX(-_skew),
                    child: Center(
                      widthFactor: isCompact ? null : 1.0,
                      heightFactor: isCompact ? null : 1.0,
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: isCompact ? 16 : 32,
                          vertical: isCompact ? 0 : 12,
                        ),
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              AnimatedRotation(
                                turns: _isActive ? 0.25 : 0.0,
                                duration: const Duration(milliseconds: 300),
                                child: Icon(
                                  Icons.close_rounded,
                                  color: Colors.white,
                                  size: isCompact ? 18 : 20,
                                ),
                              ),
                              SizedBox(width: isCompact ? 6 : 8),
                              Text(
                                widget.label.toUpperCase(),
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: isCompact ? 11 : 13,
                                  fontWeight: FontWeight.w900,
                                  letterSpacing: 2.6,
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
            ],
          ),
        ),
      ),
    );

    if (widget.isTv) {
      return Focus(
        focusNode: _focusNode,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.enter ||
              key == LogicalKeyboardKey.space) {
            widget.onPressed();
            return KeyEventResult.handled;
          }
          if (key == LogicalKeyboardKey.escape ||
              key == LogicalKeyboardKey.goBack) {
            widget.onPressed();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: button,
      );
    }
    return button;
  }
}
