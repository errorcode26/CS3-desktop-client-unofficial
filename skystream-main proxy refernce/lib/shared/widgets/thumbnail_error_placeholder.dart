import 'package:flutter/material.dart';

/// Consistent error/missing-image placeholder for thumbnails and posters.
///
/// Renders entirely on-device — zero network calls.
/// When a [label] is provided, it shows the title text inside the placeholder
/// box so the user knows what content is missing.
class ThumbnailErrorPlaceholder extends StatelessWidget {
  final double? iconSize;
  final String? label;
  final bool isBackdrop;

  const ThumbnailErrorPlaceholder({
    super.key,
    this.iconSize,
    this.label,
    this.isBackdrop = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bg = theme.colorScheme.onPrimaryFixed;
    final fg = theme.colorScheme.onSurface.withValues(alpha: 0.3);
    final size = iconSize ?? 32.0;

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxHeight = constraints.maxHeight;

        // If height is too small, hide the label and show only the icon.
        final showLabel = label != null && label!.isNotEmpty && maxHeight >= 70;

        if (showLabel) {
          final dynamicIconSize = (maxHeight < 100) ? 20.0 : size;
          final fontSize = isBackdrop
              ? (maxHeight < 100 ? 10.0 : 12.0)
              : (maxHeight < 100 ? 8.0 : 10.0);
          final spacing = maxHeight < 100 ? 2.0 : 6.0;

          return Container(
            width: double.infinity,
            height: double.infinity,
            color: bg,
            padding: const EdgeInsets.all(4),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  isBackdrop
                      ? Icons.movie_outlined
                      : Icons.image_not_supported_outlined,
                  size: dynamicIconSize,
                  color: fg,
                ),
                SizedBox(height: spacing),
                Flexible(
                  child: Text(
                    label!,
                    maxLines: isBackdrop ? 1 : 2,
                    textAlign: TextAlign.center,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: fg,
                      fontSize: fontSize,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          );
        }

        // Just icon in the center. Scale down icon size if height is extremely small.
        final smallIconSize = maxHeight < size
            ? (maxHeight > 10 ? maxHeight * 0.8 : 10.0)
            : size;

        return Container(
          color: bg,
          child: Center(
            child: Icon(
              label != null && label!.isNotEmpty
                  ? (isBackdrop
                        ? Icons.movie_outlined
                        : Icons.image_not_supported_outlined)
                  : Icons.broken_image,
              size: smallIconSize,
              color: fg,
            ),
          ),
        );
      },
    );
  }
}
