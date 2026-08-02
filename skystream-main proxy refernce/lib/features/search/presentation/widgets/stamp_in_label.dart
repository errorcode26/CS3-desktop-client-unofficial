import 'package:flutter/material.dart';

/// A widget that stamps in its child by scaling from 1.2 to 1.0 with an elastic snap curve.
class StampInLabel extends StatefulWidget {
  final Widget child;
  final Duration duration;

  const StampInLabel({
    super.key,
    required this.child,
    this.duration = const Duration(milliseconds: 1000),
  });

  @override
  State<StampInLabel> createState() => _StampInLabelState();
}

class _StampInLabelState extends State<StampInLabel>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.duration);

    // Elastic snap back curve
    _scaleAnimation = Tween<double>(
      begin: 1.25,
      end: 1.0,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.elasticOut));

    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(scale: _scaleAnimation, child: widget.child);
  }
}
