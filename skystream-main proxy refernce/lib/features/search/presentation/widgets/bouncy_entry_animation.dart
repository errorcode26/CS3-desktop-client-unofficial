import 'package:flutter/material.dart';

/// A widget that animates its child's entrance using a spring-overshoot (bouncy) curve.
///
/// Supports delayed trigger (for staggering cards) and implements slide-and-scale animations.
class BouncyEntryAnimation extends StatefulWidget {
  final Widget child;
  final Duration delay;
  final Duration duration;
  final bool animateScale;
  final double slideOffset;

  const BouncyEntryAnimation({
    super.key,
    required this.child,
    this.delay = Duration.zero,
    this.duration = const Duration(milliseconds: 550),
    this.animateScale = true,
    this.slideOffset = 40.0, // Slide distance in logical pixels
  });

  @override
  State<BouncyEntryAnimation> createState() => _BouncyEntryAnimationState();
}

class _BouncyEntryAnimationState extends State<BouncyEntryAnimation>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;
  late final Animation<double> _slideAnimation;
  late final Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration);

    // Bouncy easeOutBack overshoot curve
    final curve = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutBack,
    );

    _scaleAnimation = Tween<double>(
      begin: widget.animateScale ? 0.75 : 1.0,
      end: 1.0,
    ).animate(curve);
    _slideAnimation = Tween<double>(
      begin: widget.slideOffset,
      end: 0.0,
    ).animate(curve);
    _fadeAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));

    if (widget.delay == Duration.zero) {
      _controller.forward();
    } else {
      Future.delayed(widget.delay, () {
        if (mounted) _controller.forward();
      });
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Opacity(
          opacity: _fadeAnimation.value,
          child: Transform.translate(
            offset: Offset(0.0, _slideAnimation.value),
            child: Transform.scale(scale: _scaleAnimation.value, child: child),
          ),
        );
      },
      child: widget.child,
    );
  }
}
