import 'package:flutter/material.dart';
import 'hotstar_player_style.dart';

class HotstarEdgeRail extends StatelessWidget {
  final IconData icon;
  final double value;
  final String label;
  final bool isBoosted;
  final bool supportsVolumeBoost;

  const HotstarEdgeRail({
    super.key,
    required this.icon,
    required this.value,
    required this.label,
    required this.isBoosted,
    required this.supportsVolumeBoost,
  });

  @override
  Widget build(BuildContext context) {
    final isBrightness = label == "Brightness" || label == "Auto";
    final baseValue = value.clamp(0.0, 1.0);
    final railValue = isBrightness || !supportsVolumeBoost
        ? baseValue
        : value.clamp(0.0, 1.0);
    final size = MediaQuery.sizeOf(context);
    final isCompact = size.shortestSide < 600;
    final railWidth = isCompact ? 34.0 : 48.0;
    final railHeight = isCompact ? 176.0 : 248.0;
    final iconSize = isCompact ? 22.0 : 28.0;
    final gap = isCompact ? 8.0 : 12.0;
    final fillWidth = isCompact ? 3.5 : 4.5;
    final fillRadius = isCompact ? 4.0 : 6.0;

    return SizedBox(
      width: railWidth,
      height: railHeight,
      child: Column(
        children: [
          Icon(icon, color: Colors.white, size: iconSize),
          SizedBox(height: gap),
          Expanded(
            child: Center(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(fillRadius),
                child: SizedBox(
                  width: fillWidth,
                  child: Stack(
                    alignment: Alignment.bottomCenter,
                    children: [
                      Container(
                        color: HotstarPlayerStyle.track.withValues(alpha: 0.36),
                      ),
                      FractionallySizedBox(
                        heightFactor: railValue,
                        child: Container(color: Colors.white),
                      ),
                      if (isBoosted)
                        FractionallySizedBox(
                          heightFactor: (value - 1.0).clamp(0.0, 1.0),
                          child: Container(color: Colors.orange),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
