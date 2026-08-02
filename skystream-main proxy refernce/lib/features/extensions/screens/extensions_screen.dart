import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/utils/layout_constants.dart';
import '../../../core/extensions/models/extension_plugin.dart';
import '../../../core/extensions/models/extension_repository.dart';
import '../../../shared/widgets/custom_widgets.dart';
import '../providers/extensions_controller.dart';
import '../widgets/plugin_settings_dialog.dart';
import '../../../shared/widgets/loading_indicator.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';

class ExtensionsScreen extends ConsumerStatefulWidget {
  const ExtensionsScreen({super.key});

  @override
  ConsumerState<ExtensionsScreen> createState() => _ExtensionsScreenState();
}

class _ExtensionsScreenState extends ConsumerState<ExtensionsScreen> {
  bool _didEnsureInit = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (!_didEnsureInit) {
      _didEnsureInit = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(extensionsControllerProvider.notifier).ensureInitialized();
      });
    }
    // Listen for errors
    ref.listen(extensionsControllerProvider, (previous, next) {
      if (next is ExtensionsError &&
          (previous is! ExtensionsError || previous.message != next.message)) {
        showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(l10n.error),
            content: Text(next.message),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(l10n.ok),
              ),
            ],
          ),
        );
      }
    });

    final state = ref.watch(extensionsControllerProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.extensions)),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 800),
          child: switch (state) {
            ExtensionsLoading(repositories: []) => const Center(
              child: AppLoadingIndicator(),
            ),
            _ => ListView.builder(
              padding: const EdgeInsets.only(bottom: 24),
              addAutomaticKeepAlives:
                  false, // Fixes D-pad focus traversal crash when ExpansionTiles are cached off-screen
              itemCount: _calculateItemCount(state),
              itemBuilder: (context, index) {
                final debugPlugins = state.installedPlugins
                    .where((p) => p.isDebug)
                    .toList();
                final hasDebug = debugPlugins.isNotEmpty;

                final installedPlugins = state.installedPlugins
                    .where((p) => !p.isDebug)
                    .toList();

                final allAvailablePackageNames = state.availablePlugins.values
                    .expand((list) => list)
                    .map((p) => p.packageName)
                    .toSet();
                final installedOnlyPlugins = state.installedPlugins
                    .where(
                      (p) =>
                          !p.isDebug &&
                          !allAvailablePackageNames.contains(p.packageName),
                    )
                    .toList();
                final hasInstalledOnly = installedOnlyPlugins.isNotEmpty;
                final isEmpty =
                    state.repositories.isEmpty &&
                    state.installedPlugins.isEmpty;

                int currentIndex = 0;

                // 1. Debug Section
                if (hasDebug) {
                  if (index == currentIndex) {
                    return _buildDebugSection(context, debugPlugins);
                  }
                  currentIndex++;
                }

                // 1.5. Installed Section (Always present)
                if (index == currentIndex) {
                  return _buildInstalledSection(context, ref, installedPlugins);
                }
                currentIndex++;

                // 2. Installed Only Section
                if (hasInstalledOnly) {
                  if (index == currentIndex) {
                    return _buildInstalledOnlySection(
                      context,
                      ref,
                      installedOnlyPlugins,
                      hasRepos: state.repositories.isNotEmpty,
                    );
                  }
                  currentIndex++;
                }

                // 3. Empty State Text
                if (isEmpty) {
                  if (index == currentIndex) {
                    return Padding(
                      padding: const EdgeInsets.all(LayoutConstants.spacingLg),
                      child: Center(child: Text(l10n.noReposFound)),
                    );
                  }
                  currentIndex++;
                }

                // 4. Repositories
                if (index >= currentIndex &&
                    index < currentIndex + state.repositories.length) {
                  final repoIndex = index - currentIndex;
                  final repo = state.repositories[repoIndex];
                  final plugins = state.availablePlugins[repo.url] ?? [];
                  return _buildRepositoryCard(
                    context,
                    ref,
                    state,
                    repo,
                    plugins,
                    l10n,
                  );
                }
                currentIndex += state.repositories.length;

                // 5. Add Repository Button (Always at the end)
                if (index == currentIndex) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: LayoutConstants.spacingMd,
                      vertical: LayoutConstants.spacingSm,
                    ),
                    child: Card(
                      margin: EdgeInsets.zero,
                      elevation: 0,
                      color: Theme.of(
                        context,
                      ).colorScheme.primary.withValues(alpha: 0.05),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(
                          color: Theme.of(
                            context,
                          ).colorScheme.primary.withValues(alpha: 0.3),
                        ),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: ListTile(
                        leading: Icon(
                          Icons.add_circle_outline,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        title: Text(
                          l10n.addRepo,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        onTap: () => _showAddRepoDialog(context, ref),
                      ),
                    ),
                  );
                }

                return const SizedBox.shrink();
              },
            ),
          },
        ),
      ),
    );
  }

  Widget _buildInstalledSection(
    BuildContext context,
    WidgetRef ref,
    List<ExtensionPlugin> plugins,
  ) {
    return Card(
      margin: const EdgeInsets.all(LayoutConstants.spacingMd),
      color: Theme.of(context).colorScheme.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Theme.of(context).dividerColor),
      ),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        key: const PageStorageKey('installed_extensions'),
        shape: const Border(),
        collapsedShape: const Border(),
        initiallyExpanded: plugins.isNotEmpty,
        backgroundColor: Colors.transparent,
        collapsedBackgroundColor: Colors.transparent,
        tilePadding: const EdgeInsets.symmetric(
          horizontal: LayoutConstants.spacingMd,
          vertical: LayoutConstants.spacingXs,
        ),
        title: Row(
          children: [
            Icon(
              Icons.check_circle_outline,
              color: Theme.of(context).colorScheme.primary,
              size: 24,
            ),
            const SizedBox(width: LayoutConstants.spacingSm),
            Text(
              'Installed Extensions',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        children: plugins.isEmpty
            ? [
                Divider(
                  height: 1,
                  color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                ),
                Padding(
                  padding: const EdgeInsets.all(LayoutConstants.spacingMd),
                  child: Center(
                    child: Text(
                      'No extensions installed',
                      style: TextStyle(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurfaceVariant
                            .withValues(alpha: 0.7),
                        fontSize: 14,
                      ),
                    ),
                  ),
                ),
              ]
            : plugins.asMap().entries.map((entry) {
                final isLast = entry.key == plugins.length - 1;
                return Column(
                  children: [
                    if (entry.key == 0)
                      Divider(
                        height: 1,
                        color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                      ),
                    _PluginTile(plugin: entry.value),
                    if (!isLast)
                      Divider(
                        height: 1,
                        indent: 56,
                        endIndent: 16,
                        color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                      ),
                  ],
                );
              }).toList(),
      ),
    );
  }

  Widget _buildInstalledOnlySection(
    BuildContext context,
    WidgetRef ref,
    List<ExtensionPlugin> plugins, {
    required bool hasRepos,
  }) {
    final l10n = AppLocalizations.of(context)!;
    return Card(
      margin: const EdgeInsets.all(LayoutConstants.spacingMd),
      color: Theme.of(context).colorScheme.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Theme.of(context).dividerColor),
      ),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        key: const PageStorageKey('installed_only_extensions'),
        shape: const Border(),
        collapsedShape: const Border(),
        initiallyExpanded: true,
        backgroundColor: Colors.transparent,
        collapsedBackgroundColor: Colors.transparent,
        tilePadding: const EdgeInsets.symmetric(
          horizontal: LayoutConstants.spacingMd,
          vertical: LayoutConstants.spacingXs,
        ),
        title: Row(
          children: [
            Icon(
              Icons.extension,
              color: Theme.of(context).colorScheme.primary,
              size: 24,
            ),
            const SizedBox(width: LayoutConstants.spacingSm),
            Text(
              l10n.extensionsNotInRepos,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        subtitle: Text(
          hasRepos ? l10n.noLongerInRepo : l10n.addRepoToBrowse,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        children: plugins.asMap().entries.map((entry) {
          final isLast = entry.key == plugins.length - 1;
          return Column(
            children: [
              if (entry.key == 0)
                Divider(
                  height: 1,
                  color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                ),
              _PluginTile(plugin: entry.value),
              if (!isLast)
                Divider(
                  height: 1,
                  indent: 56,
                  endIndent: 16,
                  color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                ),
            ],
          );
        }).toList(),
      ),
    );
  }

  Widget _buildDebugSection(
    BuildContext context,
    List<ExtensionPlugin> debugPlugins,
  ) {
    final l10n = AppLocalizations.of(context)!;
    return Card(
      margin: const EdgeInsets.all(LayoutConstants.spacingMd),
      color: Theme.of(context).colorScheme.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: Theme.of(context).colorScheme.tertiary.withValues(alpha: 0.5),
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        key: const PageStorageKey('debug_extensions'),
        shape: const Border(),
        collapsedShape: const Border(),
        initiallyExpanded: true,
        backgroundColor: Colors.transparent,
        collapsedBackgroundColor: Colors.transparent,
        tilePadding: const EdgeInsets.symmetric(
          horizontal: LayoutConstants.spacingMd,
          vertical: LayoutConstants.spacingXs,
        ),
        title: Text(
          l10n.debugExtensions,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: Theme.of(context).colorScheme.tertiary,
            fontWeight: FontWeight.bold,
          ),
        ),
        children: debugPlugins.asMap().entries.map((entry) {
          final isLast = entry.key == debugPlugins.length - 1;
          return Column(
            children: [
              _PluginTile(plugin: entry.value, isDebugSection: true),
              if (!isLast)
                Divider(
                  height: 1,
                  indent: 16,
                  endIndent: 16,
                  color: Theme.of(context).dividerColor.withValues(alpha: 0.5),
                ),
            ],
          );
        }).toList(),
      ),
    );
  }

  int _calculateItemCount(ExtensionsState state) {
    final hasDebug = state.installedPlugins.any((p) => p.isDebug);
    final allAvailablePackageNames = state.availablePlugins.values
        .expand((list) => list)
        .map((p) => p.packageName)
        .toSet();
    final hasInstalledOnly = state.installedPlugins.any(
      (p) => !p.isDebug && !allAvailablePackageNames.contains(p.packageName),
    );
    final isEmpty =
        state.repositories.isEmpty && state.installedPlugins.isEmpty;

    return (hasDebug ? 1 : 0) +
        1 + // Always show Installed Extensions tile
        (hasInstalledOnly ? 1 : 0) +
        (isEmpty ? 1 : 0) + // Empty state text
        1 + // Add Repository tile
        state.repositories.length;
  }

  Widget _buildRepositoryCard(
    BuildContext context,
    WidgetRef ref,
    ExtensionsState state,
    ExtensionRepository repo,
    List<ExtensionPlugin> plugins,
    AppLocalizations l10n,
  ) {
    // True when every plugin in this repo is already installed (non-debug).
    final allInstalled =
        plugins.isNotEmpty &&
        plugins.every(
          (p) => state.installedPlugins.any(
            (i) => !i.isDebug && i.packageName == p.packageName,
          ),
        );

    final isRepoInstalling = plugins.any(
      (p) => state.installingPlugins.contains(p.packageName),
    );

    return Card(
      margin: const EdgeInsets.only(
        bottom: LayoutConstants.spacingMd,
        left: LayoutConstants.spacingMd,
        right: LayoutConstants.spacingMd,
      ),
      color: Theme.of(context).colorScheme.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Theme.of(context).dividerColor),
      ),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        key: PageStorageKey('repo_${repo.url}'),
        shape: const Border(),
        collapsedShape: const Border(),
        // Start collapsed — repos can be individually expanded.
        initiallyExpanded: false,
        backgroundColor: Colors.transparent,
        collapsedBackgroundColor: Colors.transparent,
        tilePadding: const EdgeInsets.symmetric(
          horizontal: LayoutConstants.spacingMd,
          vertical: LayoutConstants.spacingXs,
        ),
        // Embed description directly in the title so the buttons stay
        // vertically centred with the whole block and there is no extra
        // gap that the ExpansionTile subtitle property introduces.
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              repo.name,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
            if (repo.description?.isNotEmpty ?? false) ...[
              const SizedBox(height: 2),
              Text(
                repo.description!,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ],
        ),
        children: [
          // Repository Actions (Download All / Delete) moved inside children
          // to prevent D-pad focus conflicts with the ExpansionTile header.
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: LayoutConstants.spacingMd,
              vertical: LayoutConstants.spacingXs,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (isRepoInstalling)
                  const Padding(
                    padding: EdgeInsets.all(12),
                    child: AppLoadingIndicator(
                      constraints: BoxConstraints(
                        minWidth: 24,
                        minHeight: 24,
                        maxWidth: 24,
                        maxHeight: 24,
                      ),
                    ),
                  )
                else ...[
                  // Download-all / all-installed indicator.
                  TextButton.icon(
                    icon: Icon(
                      allInstalled
                          ? Icons.check_circle_outline
                          : Icons.download,
                      color: allInstalled
                          ? Theme.of(context).colorScheme.primary
                          : null,
                    ),
                    label: Text(
                      allInstalled
                          ? 'All installed'
                          : l10n.downloadAllProviders,
                    ),
                    onPressed: allInstalled || plugins.isEmpty
                        ? null
                        : () {
                            final pluginsToInstall = plugins.where((p) {
                              final installed = state.installedPlugins
                                  .cast<ExtensionPlugin?>()
                                  .firstWhere(
                                    (inst) =>
                                        inst?.packageName == p.packageName,
                                    orElse: () => null,
                                  );
                              // Only install if it's missing or if we have a newer version
                              return installed == null ||
                                  p.version > installed.version;
                            }).toList();

                            if (pluginsToInstall.isNotEmpty) {
                              ref
                                  .read(extensionsControllerProvider.notifier)
                                  .installPlugins(pluginsToInstall);
                            }
                          },
                  ),
                  const SizedBox(width: LayoutConstants.spacingSm),
                  TextButton.icon(
                    icon: const Icon(Icons.delete_outline),
                    style: TextButton.styleFrom(foregroundColor: Colors.red),
                    label: Text(l10n.delete),
                    onPressed: () => _confirmDeleteRepo(context, ref, repo),
                  ),
                ],
              ],
            ),
          ),
          const Divider(height: 1),
          ...plugins.asMap().entries.map((entry) {
            final isLast = entry.key == plugins.length - 1;
            return Column(
              children: [
                _PluginTile(plugin: entry.value),
                if (!isLast)
                  Divider(
                    height: 1,
                    indent: 56,
                    endIndent: 16,
                    color: Theme.of(
                      context,
                    ).dividerColor.withValues(alpha: 0.5),
                  ),
              ],
            );
          }),
        ],
      ),
    );
  }

  void _confirmDeleteRepo(
    BuildContext context,
    WidgetRef ref,
    ExtensionRepository repo,
  ) {
    final l10n = AppLocalizations.of(context)!;
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.removeRepoConfirm(repo.name)),
        content: Text(l10n.removeRepoWarning),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () {
              ref
                  .read(extensionsControllerProvider.notifier)
                  .removeRepository(repo.url);
              Navigator.of(context).pop();
            },
            child: Text(
              l10n.delete,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        ],
      ),
    ).then((_) {
      if (context.mounted) {
        // Automatically handled by framework
      }
    });
  }

  void _showAddRepoDialog(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final controller = TextEditingController();

    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        surfaceTintColor: Colors.transparent,
        title: Text(l10n.addRepository),
        content: CustomTextField(
          controller: controller,
          hintText: l10n.repoUrlOrShortcode,
          autofocus: true,
          textInputAction: TextInputAction.done,
          onSubmitted: (value) {
            if (value.isNotEmpty) {
              ref
                  .read(extensionsControllerProvider.notifier)
                  .addRepository(value);
              Navigator.pop(context);
            }
          },
        ),
        actions: [
          CustomButton(
            onPressed: () => Navigator.pop(context),
            child: Text(
              l10n.cancel,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: LayoutConstants.spacingXs),
          CustomButton(
            isPrimary: true,
            onPressed: () {
              if (controller.text.isNotEmpty) {
                ref
                    .read(extensionsControllerProvider.notifier)
                    .addRepository(controller.text);
                Navigator.pop(context);
              }
            },
            child: Text(l10n.addRepo),
          ),
        ],
      ),
    ).then((_) {
      if (context.mounted) {
        // Automatically handled by framework
      }
    });
  }
}

// ---------------------------------------------------------------------------
// Plugin tile
// ---------------------------------------------------------------------------

class _PluginTile extends ConsumerStatefulWidget {
  final ExtensionPlugin plugin;
  final bool isDebugSection;

  const _PluginTile({required this.plugin, this.isDebugSection = false});

  @override
  ConsumerState<_PluginTile> createState() => _PluginTileState();
}

class _PluginTileState extends ConsumerState<_PluginTile> {
  final FocusNode _settingsFocusNode = FocusNode();

  @override
  void dispose() {
    _settingsFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    if (widget.isDebugSection) {
      return ListTile(
        leading: Container(
          padding: const EdgeInsets.all(LayoutConstants.spacingXs),
          decoration: BoxDecoration(
            color: Theme.of(
              context,
            ).colorScheme.tertiary.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(
            Icons.bug_report,
            color: Theme.of(context).colorScheme.tertiary,
            size: 20,
          ),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(
                widget.plugin.name,
                style: const TextStyle(fontWeight: FontWeight.w500),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: LayoutConstants.spacingXs),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.red,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                l10n.debug,
                style: const TextStyle(
                  fontSize: 10,
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ],
        ),
        subtitle: Text(
          "v${widget.plugin.version} • ${l10n.assetPlugin}",
          style: TextStyle(color: Theme.of(context).textTheme.bodySmall?.color),
        ),
      );
    }

    final state = ref.watch(extensionsControllerProvider);

    final installedPlugin = state.installedPlugins
        .cast<ExtensionPlugin?>()
        .firstWhere((p) {
          if (p == null) return false;
          if (p.isDebug) return false;
          return p.packageName == widget.plugin.packageName;
        }, orElse: () => null);

    final isInstalled = installedPlugin != null;
    final updateAvailable = state.availableUpdates[widget.plugin.packageName];

    final isInstalling = state.installingPlugins.contains(
      widget.plugin.packageName,
    );

    return ListTile(
      leading: Container(
        padding: const EdgeInsets.all(LayoutConstants.spacingXs),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(
          Icons.extension_outlined,
          color: Theme.of(context).colorScheme.primary,
          size: 20,
        ),
      ),
      title: Text(
        widget.plugin.name,
        style: const TextStyle(fontWeight: FontWeight.w500),
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: _buildSubtitle(context, isInstalled, installedPlugin),
      trailing: isInstalling
          ? const Padding(
              padding: EdgeInsets.all(12),
              child: AppLoadingIndicator(
                constraints: BoxConstraints(
                  minWidth: 24,
                  minHeight: 24,
                  maxWidth: 24,
                  maxHeight: 24,
                ),
              ),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Update button
                if (isInstalled && updateAvailable != null)
                  IconButton(
                    icon: const Icon(Icons.download, color: Colors.green),
                    tooltip: l10n.updateTo(updateAvailable.version.toString()),
                    onPressed: () {
                      ref
                          .read(extensionsControllerProvider.notifier)
                          .updatePlugin(updateAvailable);
                    },
                  ),

                // Settings button (shown when plugin declares domains or providers)
                if (isInstalled &&
                    ((installedPlugin.domains?.isNotEmpty ?? false) ||
                        (installedPlugin.providers?.isNotEmpty ?? false)))
                  IconButton(
                    focusNode: _settingsFocusNode,
                    icon: const Icon(Icons.settings),
                    tooltip: l10n.settings,
                    onPressed: () {
                      showDialog<void>(
                        context: context,
                        builder: (context) =>
                            PluginSettingsDialog(plugin: installedPlugin),
                      ).then((_) {
                        if (context.mounted) {
                          _settingsFocusNode.requestFocus();
                        }
                      });
                    },
                  ),

                // Install / delete button
                if (isInstalled)
                  IconButton(
                    icon: Icon(
                      Icons.delete,
                      color: Theme.of(context).colorScheme.error,
                    ),
                    tooltip: l10n.delete,
                    onPressed: () {
                      ref
                          .read(extensionsControllerProvider.notifier)
                          .uninstallPlugin(installedPlugin);
                    },
                  )
                else
                  IconButton(
                    icon: const Icon(Icons.download),
                    tooltip: l10n.install,
                    onPressed: () {
                      ref
                          .read(extensionsControllerProvider.notifier)
                          .installPlugin(widget.plugin);
                    },
                  ),
              ],
            ),
    );
  }

  /// Subtitle widget: description · version/authors line · language chips.
  Widget _buildSubtitle(
    BuildContext context,
    bool isInstalled,
    ExtensionPlugin? installedPlugin,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    // Description uses bodyMedium so it's comfortably readable.
    final descStyle = textTheme.bodyMedium?.copyWith(
      color: colorScheme.onSurfaceVariant,
    );
    // Version + authors line uses bodySmall.
    final metaStyle = textTheme.bodySmall?.copyWith(
      color: colorScheme.onSurfaceVariant,
    );

    // Version from installed copy if present, otherwise from the catalog.
    final version =
        'v${isInstalled ? installedPlugin!.version : widget.plugin.version}';

    // Authors: up to 2, prefixed with "By".
    final authors = widget.plugin.authors.take(2).join(', ');

    final metaParts = [version, if (authors.isNotEmpty) 'By $authors'];
    final metaLine = metaParts.join(' • ');

    final desc = widget.plugin.description;
    final hasDesc = desc != null && desc.isNotEmpty;
    final hasLanguages = widget.plugin.languages.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (hasDesc)
          Text(
            desc,
            style: descStyle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        const SizedBox(height: 2),
        Text(
          metaLine,
          style: metaStyle,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        if (hasLanguages) ...[
          const SizedBox(height: 4),
          Wrap(
            spacing: 4,
            runSpacing: 4,
            children: widget.plugin.languages.take(5).map((lang) {
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: colorScheme.secondaryContainer,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  lang.toUpperCase(),
                  style: TextStyle(
                    fontSize: 11,
                    color: colorScheme.onSecondaryContainer,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.3,
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ],
    );
  }
}
