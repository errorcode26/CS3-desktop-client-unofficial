import 'package:flutter/material.dart';

class MarqueeWidget extends StatefulWidget {
  final Widget child;
  final Axis direction;
  final Duration animationDuration, backDuration, pauseDuration;

  const MarqueeWidget({
    super.key,
    required this.child,
    this.direction = Axis.horizontal,
    this.animationDuration = const Duration(seconds: 5),
    this.backDuration = const Duration(seconds: 1),
    this.pauseDuration = const Duration(seconds: 2),
  });

  @override
  MarqueeWidgetState createState() => MarqueeWidgetState();
}

class MarqueeWidgetState extends State<MarqueeWidget> {
  late ScrollController _controller;
  bool _disposed = false;

  @override
  void initState() {
    super.initState();
    _controller = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback(scroll);
  }

  @override
  void dispose() {
    _disposed = true;
    _controller.dispose();
    super.dispose();
  }

  Future<void> scroll(_) async {
    while (!_disposed && _controller.hasClients) {
      await Future<void>.delayed(widget.pauseDuration);
      if (_disposed) return;
      if (_controller.hasClients) {
        await _controller.animateTo(
          _controller.position.maxScrollExtent,
          duration: widget.animationDuration,
          curve: Curves.easeOut,
        );
      }
      if (_disposed) return;
      await Future<void>.delayed(widget.pauseDuration);
      if (_disposed) return;
      if (_controller.hasClients) {
        await _controller.animateTo(
          0.0,
          duration: widget.backDuration,
          curve: Curves.easeOut,
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: widget.direction,
      controller: _controller,
      child: widget.child,
    );
  }
}
