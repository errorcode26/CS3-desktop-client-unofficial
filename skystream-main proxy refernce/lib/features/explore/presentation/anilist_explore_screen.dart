import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/anilist_explore_provider.dart';
import 'widgets/explore_carousel.dart';
import 'widgets/media_horizontal_list.dart';
import '../../../../core/utils/layout_constants.dart';
import '../../../../core/utils/responsive_breakpoints.dart';
import '../../../../shared/widgets/shimmer_placeholder.dart';
import '../../../../core/domain/entity/multimedia_item.dart';
import 'view_all_screen.dart';

class AnilistExploreScreen extends ConsumerStatefulWidget {
  final ScrollController scrollController;
  final FocusNode firstActionFocusNode;
  final ValueChanged<HeroCarouselController>? onControllerReady;

  const AnilistExploreScreen({
    super.key,
    required this.scrollController,
    required this.firstActionFocusNode,
    this.onControllerReady,
  });

  @override
  ConsumerState<AnilistExploreScreen> createState() =>
      _AnilistExploreScreenState();
}

class _AnilistExploreScreenState extends ConsumerState<AnilistExploreScreen> {
  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      controller: widget.scrollController,
      slivers: [
        ..._buildContentSlivers(context),
        const SliverToBoxAdapter(child: SizedBox(height: 100)),
      ],
    );
  }

  List<Widget> _buildContentSlivers(BuildContext context) {
    return [
      SliverToBoxAdapter(
        child: Consumer(
          builder: (context, ref, _) {
            final heroAnimeAsync = ref.watch(anilistHeroAnimeProvider);
            return switch (heroAnimeAsync) {
              AsyncData(:final value) =>
                value.isEmpty
                    ? const SizedBox.shrink()
                    : ExploreCarousel(
                        movies: value,
                        scrollController: widget.scrollController,
                        onNavigateUp: () =>
                            widget.firstActionFocusNode.requestFocus(),
                        onControllerReady: widget.onControllerReady,
                      ),
              AsyncLoading() => _buildCarouselShimmer(context),
              AsyncError() => Container(
                height: 500,
                margin: const EdgeInsets.only(
                  bottom: LayoutConstants.spacingLg,
                ),
                decoration: BoxDecoration(
                  color: Theme.of(
                    context,
                  ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.error_outline_rounded,
                        size: 48,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      const SizedBox(height: 16),
                      const Text(
                        "Could not load trending anime",
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextButton.icon(
                        onPressed: () =>
                            ref.invalidate(anilistHeroAnimeProvider),
                        icon: const Icon(Icons.refresh),
                        label: const Text("Retry"),
                      ),
                    ],
                  ),
                ),
              ),
            };
          },
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(trendingAnimeProvider),
          'Trending right now',
          ViewAllCategory.providerContent,
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(airedRecentlyAnimeProvider),
          'Aired recently',
          ViewAllCategory.providerContent,
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(topSeasonAnimeProvider),
          'Top of the season',
          ViewAllCategory.providerContent,
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(bestLastSeasonAnimeProvider),
          'Best of last season',
          ViewAllCategory.providerContent,
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(moviesAnimeProvider),
          'Movies',
          ViewAllCategory.providerContent,
        ),
      ),

      SliverToBoxAdapter(
        child: _buildSection(
          context,
          ref.watch(comingSoonAnimeProvider),
          'Coming soon',
          ViewAllCategory.providerContent,
        ),
      ),
    ];
  }

  Widget _buildSection(
    BuildContext context,
    AsyncValue<List<MultimediaItem>> asyncValue,
    String title,
    ViewAllCategory category,
  ) {
    return switch (asyncValue) {
      AsyncData(:final value) =>
        value.isEmpty
            ? const SizedBox.shrink()
            : MediaHorizontalList(
                title: title,
                mediaList: value,
                category: category,
                heroTagPrefix: 'explore_anilist',
              ),
      AsyncLoading() => _buildListShimmer(context),
      AsyncError() => const SizedBox.shrink(),
    };
  }

  Widget _buildCarouselShimmer(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final heroHeight = size.height * 0.60;
    final isDesktop =
        size.width > LayoutConstants.exploreCarouselDesktopBreakpoint;

    if (isDesktop) {
      return Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: LayoutConstants.dashboardContentPadding,
          vertical: LayoutConstants.spacingSm,
        ),
        child: SizedBox(
          height: heroHeight,
          width: double.infinity,
          child: ShimmerPlaceholder(borderRadius: 18),
        ),
      );
    } else {
      return SizedBox(
        height: heroHeight,
        width: double.infinity,
        child: ShimmerPlaceholder.rectangular(
          width: double.infinity,
          height: heroHeight,
          borderRadius: 0,
        ),
      );
    }
  }

  Widget _buildListShimmer(BuildContext context) {
    final isDesktop = context.isDesktop;
    final cardWidth = isDesktop ? 200.0 : 130.0;
    final imageHeight = cardWidth / (2 / 3);
    final listHeight = imageHeight + 40.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Title Placeholder
        Padding(
          padding: EdgeInsets.fromLTRB(
            isDesktop
                ? LayoutConstants.dashboardContentPadding
                : LayoutConstants.spacingMd,
            LayoutConstants.spacingLg,
            isDesktop
                ? LayoutConstants.dashboardContentPadding
                : LayoutConstants.spacingMd,
            LayoutConstants.spacingSm,
          ),
          child: ShimmerPlaceholder.rectangular(
            width: 150,
            height: 24,
            borderRadius: 4,
          ),
        ),
        const SizedBox(height: LayoutConstants.spacingMd),
        // List Placeholder
        SizedBox(
          height: listHeight,
          child: ListView.separated(
            padding: EdgeInsets.symmetric(
              horizontal: isDesktop
                  ? LayoutConstants.dashboardContentPadding
                  : LayoutConstants.spacingMd,
            ),
            scrollDirection: Axis.horizontal,
            itemCount: 10,
            separatorBuilder: (_, _) => SizedBox(
              width: isDesktop
                  ? LayoutConstants.spacingLg
                  : LayoutConstants.spacingSm,
            ),
            itemBuilder: (context, index) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ShimmerPlaceholder.rectangular(
                    width: cardWidth,
                    height: imageHeight,
                    borderRadius: 12,
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}
