import 'package:flutter/material.dart';
import 'package:expressive_loading_indicator/expressive_loading_indicator.dart';

/// A custom Material 3 Expressive Loading Indicator.
///
/// This widget morphs between different rounded polygon shapes (such as softBurst,
/// pentagon, pill, sunny, etc.) with a smooth, playful spring animation.
class AppLoadingIndicator extends StatelessWidget {
  /// The color of the loading indicator. If null, the theme's primary color is used.
  final Color? color;

  /// Sizing constraints for the indicator. Defaults to 48x48.
  final BoxConstraints? constraints;

  const AppLoadingIndicator({super.key, this.color, this.constraints});

  @override
  Widget build(BuildContext context) {
    if (constraints != null) {
      final double width = constraints!.hasBoundedWidth
          ? constraints!.maxWidth
          : 48.0;
      final double height = constraints!.hasBoundedHeight
          ? constraints!.maxHeight
          : 48.0;

      // Workaround: ExpressiveLoadingIndicator draws at a fixed active size (~38px)
      // and does not scale its internal vector shapes down correctly inside small constraints,
      // resulting in clipped or oversized animations.
      // If the targeted size is smaller than the default 48x48 layout container, we draw at
      // the standard 48x48 size and use FittedBox to scale the vector output cleanly.
      if (width < 48.0 || height < 48.0) {
        return SizedBox(
          width: width,
          height: height,
          child: FittedBox(
            fit: BoxFit.contain,
            child: ExpressiveLoadingIndicator(
              color: color,
              constraints: const BoxConstraints(
                minWidth: 48.0,
                minHeight: 48.0,
                maxWidth: 48.0,
                maxHeight: 48.0,
              ),
            ),
          ),
        );
      }
    }

    return ExpressiveLoadingIndicator(color: color, constraints: constraints);
  }
}
