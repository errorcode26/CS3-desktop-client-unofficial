import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../l10n/generated/app_localizations.dart';
import '../../../../core/router/app_router.dart';
import '../../../../core/utils/image_fallbacks.dart';
import '../../../../core/utils/layout_constants.dart';
import '../../../../core/utils/responsive_breakpoints.dart';
import '../../../../shared/widgets/multimedia_card.dart';
import '../library_provider.dart';

import '../library_state.dart';
import '../../../../shared/widgets/loading_indicator.dart';

class BookmarksTab extends ConsumerStatefulWidget {
  const BookmarksTab({super.key});

  @override
  ConsumerState<BookmarksTab> createState() => _BookmarksTabState();
}

class _BookmarksTabState extends ConsumerState<BookmarksTab>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final libraryState = ref.watch(libraryProvider);
    final isLarge = context.isTabletOrLarger;
    final double totalHeight = isLarge ? 180.0 : 150.0;

    return switch (libraryState) {
      LibraryLoading() => const Center(child: AppLoadingIndicator()),
      LibraryError(message: final msg) => Center(child: Text(msg)),
      LibraryEmpty() => _buildEmpty(context),
      LibrarySuccess(items: final items) => GridView.builder(
        padding: const EdgeInsets.all(LayoutConstants.spacingMd),
        gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: totalHeight,
          childAspectRatio: 2 / 3.4,
          crossAxisSpacing: LayoutConstants.spacingMd,
          mainAxisSpacing: LayoutConstants.spacingMd,
        ),
        itemCount: items.length,
        itemBuilder: (context, index) {
          final item = items[index];
          return MultimediaCard(
            key: ValueKey(item.url),
            imageUrl:
                AppImageFallbacks.poster(item.posterUrl, label: item.title) ??
                '',
            title: item.title,
            heroTag: 'lib_bookmark_${item.url}_$index',
            onTap: () => DetailsRoute(
              $extra: DetailsRouteExtra(item: item),
            ).push<void>(context),
          );
        },
      ),
    };
  }

  Widget _buildEmpty(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.bookmark_outline_rounded,
            size: 64,
            color: Theme.of(context).dividerColor,
          ),
          const SizedBox(height: 16),
          Text(
            AppLocalizations.of(context)!.libraryEmpty,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ],
      ),
    );
  }
}
