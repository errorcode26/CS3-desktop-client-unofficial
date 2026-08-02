import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../shared/widgets/thumbnail_error_placeholder.dart';
import '../../../../shared/widgets/cards_wrapper.dart';
import '../../../../shared/widgets/desktop_scroll_wrapper.dart';
import '../../../../core/utils/responsive_breakpoints.dart';
import '../../../../core/models/tmdb_details.dart';

class MovieCastList extends StatefulWidget {
  final List<TmdbCast> cast;
  final Color? textColor;
  final Color? textSecondary;

  final bool isLoading;

  const MovieCastList({
    super.key,
    required this.cast,
    this.isLoading = false,
    this.textColor,
    this.textSecondary,
  });

  @override
  State<MovieCastList> createState() => _MovieCastListState();
}

class _MovieCastListState extends State<MovieCastList> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.isLoading && widget.cast.isEmpty) {
      return const SizedBox.shrink();
    }

    final isDesktop = context.isDesktop;
    final displayCount = widget.isLoading ? 6 : widget.cast.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (isDesktop) ...[
          Text(
            "Cast",
            style: TextStyle(
              color: widget.textColor,
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 140, // Height for Cast Cards
            child: DesktopScrollWrapper(
              controller: _scrollController,
              child: ListView.builder(
                clipBehavior: Clip.none,
                controller: _scrollController,
                scrollDirection: Axis.horizontal,
                itemCount: displayCount,
                // Fixed item width (80) + spacing (16) baked in via padding.
                itemExtent: 96,
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
          Text(
            "Cast",
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 160,
            child: ListView.builder(
              clipBehavior: Clip.none,
              scrollDirection: Axis.horizontal,
              itemCount: displayCount,
              // Item is width:90 + margin-right:16 inside _buildMobileItem.
              itemExtent: 106,
              itemBuilder: (context, index) => RepaintBoundary(
                child: widget.isLoading
                    ? _buildShimmerItem(context, isDesktop: false)
                    : _buildMobileItem(context, index),
              ),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ],
    );
  }

  Widget _buildDesktopItem(BuildContext context, int index) {
    final actor = widget.cast[index];
    return CardsWrapper(
      onTap: () {},
      borderRadius: BorderRadius.circular(40),
      child: Column(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(40),
            child: CachedNetworkImage(
              imageUrl: actor.profileImageUrl ?? '',
              width: 80,
              height: 80,
              // No memCacheWidth — TMDB profile source is already w185 which
              // matches 80 dp × 3 DPR. Forcing smaller blurs on hi-DPR.
              fit: BoxFit.cover,
              errorWidget: (_, _, _) =>
                  ThumbnailErrorPlaceholder(label: actor.name, iconSize: 30),
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            width: 80,
            child: Text(
              actor.name,
              style: TextStyle(color: widget.textColor, fontSize: 12),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ),
          SizedBox(
            width: 80,
            child: Text(
              actor.character,
              style: TextStyle(color: widget.textSecondary, fontSize: 10),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMobileItem(BuildContext context, int index) {
    final member = widget.cast[index];
    return CardsWrapper(
      onTap: () {},
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: 90,
        margin: const EdgeInsets.only(right: 16),
        child: Column(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(35),
              child: CachedNetworkImage(
                imageUrl: member.profileImageUrl ?? '',
                width: 70,
                height: 70,
                // No memCacheWidth — see desktop branch above.
                fit: BoxFit.cover,
                errorWidget: (_, _, _) =>
                    ThumbnailErrorPlaceholder(label: member.name, iconSize: 30),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              member.name,
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurface,
                fontSize: 13,
                fontWeight: FontWeight.bold,
              ),
            ),
            Text(
              member.character,
              maxLines: 1,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.6),
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildShimmerItem(BuildContext context, {required bool isDesktop}) {
    return Container(
      width: isDesktop ? 80 : 90,
      margin: isDesktop ? EdgeInsets.zero : const EdgeInsets.only(right: 16),
      child: Column(
        children: [
          Container(
            width: isDesktop ? 80 : 70,
            height: isDesktop ? 80 : 70,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(height: 8),
          Container(
            width: 60,
            height: 12,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(height: 4),
          Container(
            width: 40,
            height: 10,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
        ],
      ),
    );
  }
}
