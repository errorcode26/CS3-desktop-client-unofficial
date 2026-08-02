import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../shared/widgets/thumbnail_error_placeholder.dart';
import '../../../../shared/widgets/cards_wrapper.dart';
import '../../../../shared/widgets/desktop_scroll_wrapper.dart';
import '../../../../core/utils/responsive_breakpoints.dart';
import '../../../../core/models/tmdb_details.dart';

class MovieTrailersCarousel extends StatefulWidget {
  final List<TmdbVideo> trailers;
  final Color? textColor;
  final bool isLoading;

  const MovieTrailersCarousel({
    super.key,
    required this.trailers,
    this.isLoading = false,
    this.textColor,
  });

  @override
  State<MovieTrailersCarousel> createState() => _MovieTrailersCarouselState();
}

class _MovieTrailersCarouselState extends State<MovieTrailersCarousel> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.isLoading && widget.trailers.isEmpty) {
      return const SizedBox.shrink();
    }

    final isDesktop = context.isDesktop;
    final displayCount = widget.isLoading ? 3 : widget.trailers.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (isDesktop) ...[
          Text(
            "Trailers & Clips",
            style: TextStyle(
              color: widget.textColor,
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 160,
            child: DesktopScrollWrapper(
              controller: _scrollController,
              child: ListView.builder(
                clipBehavior: Clip.none,
                controller: _scrollController,
                scrollDirection: Axis.horizontal,
                itemCount: displayCount,
                // 16:9 at height 160 → width ≈ 284, +16 spacing baked in.
                itemExtent: 300,
                itemBuilder: (context, index) {
                  final child = widget.isLoading
                      ? _buildShimmerItem(context, isDesktop: true)
                      : _buildDesktopItem(context, index);
                  return RepaintBoundary(
                    child: Padding(
                      padding: const EdgeInsets.only(right: 16),
                      child: child,
                    ),
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 50),
        ] else ...[
          Row(
            children: [
              Text(
                "Trailers",
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurface,
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(width: 12),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  border: Border.all(
                    color: Theme.of(
                      context,
                    ).colorScheme.onSurface.withValues(alpha: 0.3),
                  ),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  children: [
                    Text(
                      "Official Trailers",
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurface,
                        fontSize: 12,
                      ),
                    ),
                    Icon(
                      Icons.keyboard_arrow_down,
                      color: Theme.of(context).colorScheme.onSurface,
                      size: 16,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 150,
            child: ListView.builder(
              clipBehavior: Clip.none,
              scrollDirection: Axis.horizontal,
              itemCount: displayCount,
              // Mobile item is width:200 + margin-right:16 inside _buildMobileItem.
              itemExtent: 216,
              itemBuilder: (context, index) => RepaintBoundary(
                child: widget.isLoading
                    ? _buildShimmerItem(context, isDesktop: false)
                    : _buildMobileItem(context, index),
              ),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ],
    );
  }

  Widget _buildDesktopItem(BuildContext context, int index) {
    final video = widget.trailers[index];
    final key = video.key;
    return CardsWrapper(
      onTap: () async {
        final uri = Uri.parse('https://www.youtube.com/watch?v=$key');
        if (await canLaunchUrl(uri)) {
          await launchUrl(uri);
        }
      },
      child: SizedBox(
        // Matches itemExtent: 16:9 at row height 160 = 284 logical px.
        width: 284,
        height: 160,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Stack(
            fit: StackFit.expand,
            children: [
              CachedNetworkImage(
                imageUrl: 'https://img.youtube.com/vi/$key/mqdefault.jpg',
                fit: BoxFit.cover,
                // YouTube mqdefault is 320 px native; that's the source ceiling,
                // memCacheWidth would only force downscale below that. Skip.
                errorWidget: (_, _, _) => const ThumbnailErrorPlaceholder(),
              ),
              Container(color: Colors.black26),
              const Center(
                child: Icon(
                  Icons.play_circle_outline,
                  color: Colors.white,
                  size: 40,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMobileItem(BuildContext context, int index) {
    final video = widget.trailers[index];
    final videoKey = video.key;
    final thumbUrl = 'https://img.youtube.com/vi/$videoKey/0.jpg';
    return CardsWrapper(
      onTap: () async {
        final uri = Uri.parse('https://www.youtube.com/watch?v=$videoKey');
        if (await canLaunchUrl(uri)) {
          await launchUrl(uri, mode: LaunchMode.externalApplication);
        }
      },
      child: Container(
        width: 200,
        margin: const EdgeInsets.only(right: 16),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Stack(
            fit: StackFit.expand,
            children: [
              CachedNetworkImage(
                imageUrl: thumbUrl,
                fit: BoxFit.cover,
                // /0.jpg is 480 px native; let CNI decode at source.
                errorWidget: (_, _, _) =>
                    ThumbnailErrorPlaceholder(label: video.name),
              ),
              Container(color: Colors.black26),
              Center(
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: const BoxDecoration(
                    color: Colors.black54,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.play_arrow, color: Colors.white),
                ),
              ),
              Positioned(
                bottom: 8,
                left: 8,
                right: 8,
                child: Text(
                  video.name,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    shadows: [Shadow(color: Colors.black, blurRadius: 4)],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildShimmerItem(BuildContext context, {required bool isDesktop}) {
    return Container(
      width: isDesktop ? 284 : 200,
      margin: isDesktop ? EdgeInsets.zero : const EdgeInsets.only(right: 16),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );
  }
}
