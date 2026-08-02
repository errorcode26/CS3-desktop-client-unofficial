import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:skystream/core/utils/layout_constants.dart';
import 'package:skystream/core/extensions/extension_manager.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import 'package:skystream/shared/widgets/cards_wrapper.dart';
import 'package:skystream/features/home/presentation/delegates/home_search_delegate.dart';
import 'package:skystream/features/home/presentation/home_provider.dart';
import 'package:skystream/features/explore/presentation/widgets/hover_border_gradient.dart';
import 'dart:async';

/// A custom header bar for the widescreen dashboard layout.
///
/// Contains: carousel prev/next arrows, capsule search, provider chip.
class DashboardHeaderBar extends ConsumerWidget {
  final FocusNode searchFocusNode;
  final VoidCallback onShowProviderSelector;

  /// Called when the user taps the left arrow (carousel previous).
  final VoidCallback? onPrevious;

  /// Called when the user taps the right arrow (carousel next).
  final VoidCallback? onNext;

  const DashboardHeaderBar({
    super.key,
    required this.searchFocusNode,
    required this.onShowProviderSelector,
    this.onPrevious,
    this.onNext,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context)!;
    final activeProvider = ref.watch(activeProviderProvider);

    final hasCarousel = onPrevious != null && onNext != null;

    return Container(
      height: LayoutConstants.dashboardHeaderHeight,
      padding: const EdgeInsets.symmetric(
        horizontal: LayoutConstants.dashboardContentPadding,
      ),
      child: Row(
        children: [
          // Carousel prev / next arrows
          CardsWrapper(
            scaleFactor: 1.01,
            onTap: () => onPrevious?.call(),
            borderRadius: BorderRadius.circular(8),
            child: Container(
              width: 32,
              height: 32,
              alignment: Alignment.center,
              child: Icon(
                Icons.arrow_back_ios_new,
                size: 14,
                color: hasCarousel
                    ? theme.colorScheme.onSurface
                    : theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
              ),
            ),
          ),
          const SizedBox(width: 4),
          CardsWrapper(
            scaleFactor: 1.01,
            onTap: () => onNext?.call(),
            borderRadius: BorderRadius.circular(8),
            child: Container(
              width: 32,
              height: 32,
              alignment: Alignment.center,
              child: Icon(
                Icons.arrow_forward_ios,
                size: 14,
                color: hasCarousel
                    ? theme.colorScheme.onSurface
                    : theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
              ),
            ),
          ),

          const SizedBox(width: 16),

          // Capsule search bar
          Expanded(
            child: CardsWrapper(
              scaleFactor: 1.01,
              focusNode: searchFocusNode,
              onTap: () {
                unawaited(
                  showSearch<void>(
                    context: context,
                    delegate: HomeSearchDelegate(),
                    useRootNavigator: false,
                    maintainState: true,
                  ),
                );
              },
              borderRadius: BorderRadius.circular(LayoutConstants.radiusPill),
              child: Container(
                height: 38,
                constraints: const BoxConstraints(maxWidth: 500),
                padding: const EdgeInsets.symmetric(horizontal: 16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withValues(
                    alpha: 0.5,
                  ),
                  borderRadius: BorderRadius.circular(
                    LayoutConstants.radiusPill,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.search,
                      size: 18,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '${l10n.search}...',
                        style: TextStyle(
                          fontSize: 13,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),

          const SizedBox(width: 16),

          // Refresh button
          CardsWrapper(
            scaleFactor: 1.01,
            onTap: () => ref.read(homeDataProvider.notifier).fetch(),
            borderRadius: BorderRadius.circular(50),
            child: Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: theme.colorScheme.surfaceContainerHighest.withValues(
                  alpha: 0.3,
                ),
              ),
              child: Icon(
                Icons.refresh,
                color: theme.colorScheme.onSurfaceVariant,
                size: 18,
              ),
            ),
          ),

          const SizedBox(width: 12),

          HoverBorderGradient(
            onTap: onShowProviderSelector,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.extension,
                  color: Theme.of(context).brightness == Brightness.dark
                      ? Colors.white
                      : Colors.black,
                  size: 16,
                ),
                const SizedBox(width: 8),
                Text(
                  activeProvider?.name ?? l10n.none,
                  style: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark
                        ? Colors.white
                        : Colors.black,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
