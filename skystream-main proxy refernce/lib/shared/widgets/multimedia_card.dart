import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../core/utils/responsive_breakpoints.dart';
import 'cards_wrapper.dart';
import 'shimmer_placeholder.dart';
import 'thumbnail_error_placeholder.dart';

class MultimediaCard extends StatelessWidget {
  final String? imageUrl;
  final String title;
  final VoidCallback onTap;
  final String heroTag;
  final bool isPortrait;
  final FocusNode? focusNode;

  const MultimediaCard({
    super.key,
    required this.imageUrl,
    required this.title,
    required this.onTap,
    required this.heroTag,
    this.isPortrait = true,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) {
    final isDesktop = context.isDesktop;
    final cardWidth = isDesktop
        ? (isPortrait ? 200.0 : 300.0)
        : (isPortrait ? 130.0 : 200.0);
    // No explicit memCacheWidth here. The TMDB source is w500 which is
    // already close to displayed width × DPR (e.g. 200 dp × 3 DPR = 600 px,
    // 300 dp × 3 = 900 px). Forcing a smaller memCacheWidth blurs the image
    // on hi-DPR phones; letting CNI decode at the source size keeps it crisp
    // without bloating the cache (the cache cap in main.dart bounds total
    // memory regardless).

    return RepaintBoundary(
      child: CardsWrapper(
        onTap: onTap,
        focusNode: focusNode,
        scaleFactor: 1.05,
        child: SizedBox(
          width: cardWidth,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Hero(
                  tag: heroTag,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: CachedNetworkImage(
                      imageUrl: imageUrl ?? '',
                      fit: BoxFit.cover,
                      width: double.infinity,
                      placeholder: (context, url) =>
                          ShimmerPlaceholder(borderRadius: 12),
                      errorWidget: (_, _, _) =>
                          ThumbnailErrorPlaceholder(label: title),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: Theme.of(
                    context,
                  ).colorScheme.onSurface.withValues(alpha: 0.8),
                  fontSize: isDesktop ? 22 : 14,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
