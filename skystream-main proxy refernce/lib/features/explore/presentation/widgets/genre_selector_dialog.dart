import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/explore_filter_provider.dart';
import '../../data/explore_tmdb_provider.dart';
import '../../../../shared/widgets/loading_indicator.dart';

class GenreSelectorDialog extends ConsumerWidget {
  const GenreSelectorDialog({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final genresAsync = ref.watch(genresProvider);
    final selectedGenre = ref.watch(exploreFilterProvider).selectedGenre;

    return BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
      child: Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.all(20),
        child: Container(
          width: double.infinity,
          constraints: const BoxConstraints(maxHeight: 600, maxWidth: 500),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: theme.dividerColor, width: 1),
            boxShadow: [
              BoxShadow(
                color: theme.shadowColor,
                blurRadius: 20,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(24.0),
                child: Row(
                  children: [
                    Icon(
                      Icons.category_outlined,
                      color: theme.colorScheme.primary,
                      size: 28,
                    ),
                    const SizedBox(width: 12),
                    Text(
                      "Select Genre",
                      style: TextStyle(
                        color: theme.colorScheme.onSurface,
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const Spacer(),
                    if (selectedGenre != null)
                      TextButton(
                        onPressed: () {
                          ref
                              .read(exploreFilterProvider.notifier)
                              .setGenre(null);
                          Navigator.of(context).pop();
                        },
                        child: Text(
                          "Clear",
                          style: TextStyle(color: theme.colorScheme.error),
                        ),
                      ),
                  ],
                ),
              ),
              Divider(height: 1, color: theme.dividerColor),
              Expanded(
                child: genresAsync.when(
                  data: (genres) => ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: genres.length,
                    itemBuilder: (context, index) {
                      final genre = genres[index];
                      final isSelected =
                          selectedGenre != null && selectedGenre.id == genre.id;
                      return ListTile(
                        onTap: () {
                          ref
                              .read(exploreFilterProvider.notifier)
                              .setGenre(genre);
                          Navigator.of(context).pop();
                        },
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        tileColor: isSelected
                            ? theme.colorScheme.primary.withValues(alpha: 0.2)
                            : null,
                        leading: Icon(
                          isSelected
                              ? Icons.check_circle
                              : Icons.circle_outlined,
                          color: isSelected
                              ? theme.colorScheme.primary
                              : theme.colorScheme.outline,
                        ),
                        title: Text(
                          genre.name,
                          style: TextStyle(
                            color: isSelected
                                ? theme.colorScheme.onSurface
                                : theme.colorScheme.onSurfaceVariant,
                            fontWeight: isSelected
                                ? FontWeight.bold
                                : FontWeight.normal,
                          ),
                        ),
                      );
                    },
                  ),
                  loading: () => const Center(child: AppLoadingIndicator()),
                  error: (_, _) => Center(
                    child: Text(
                      "Failed to load genres",
                      style: TextStyle(color: theme.colorScheme.onSurface),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
