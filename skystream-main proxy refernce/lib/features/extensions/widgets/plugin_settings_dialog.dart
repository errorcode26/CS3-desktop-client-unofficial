import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/extensions/models/extension_plugin.dart';
import '../../../core/extensions/extension_manager.dart';
import '../../../core/storage/settings_repository.dart';
import '../../../core/storage/extension_repository.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import '../../../shared/widgets/loading_indicator.dart';

class PluginSettingsDialog extends ConsumerStatefulWidget {
  final ExtensionPlugin plugin;

  const PluginSettingsDialog({super.key, required this.plugin});

  @override
  ConsumerState<PluginSettingsDialog> createState() =>
      _PluginSettingsDialogState();
}

class _PluginSettingsDialogState extends ConsumerState<PluginSettingsDialog> {
  late String _selectedDomain;
  late Map<String, bool> _providerEnabled;
  bool _reloading = false;

  @override
  void initState() {
    super.initState();
    final settings = ref.read(settingsRepositoryProvider);
    final storage = ref.read(extensionRepositoryProvider);
    final domains = widget.plugin.domains ?? [];
    final saved = settings.getCustomBaseUrl(widget.plugin.packageName);
    _selectedDomain = saved ?? (domains.isNotEmpty ? domains.first.url : '');

    _providerEnabled = {
      for (final sub in widget.plugin.providers ?? <PluginSubProvider>[])
        sub.id:
            storage.getExtensionData(
              '${widget.plugin.packageName}:_provider_enabled_${sub.id}',
            ) !=
            'false',
    };
  }

  Future<void> _applyDomain(String url) async {
    if (_reloading || url == _selectedDomain) return;
    setState(() {
      _selectedDomain = url;
      _reloading = true;
    });
    await ref
        .read(settingsRepositoryProvider)
        .setCustomBaseUrl(widget.plugin.packageName, url);
    await ref
        .read(extensionManagerProvider.notifier)
        .reloadPlugin(widget.plugin);
    if (mounted) setState(() => _reloading = false);
  }

  Future<void> _toggleProvider(String providerId, bool enabled) async {
    if (_reloading) return;
    setState(() {
      _providerEnabled[providerId] = enabled;
      _reloading = true;
    });
    await ref
        .read(extensionRepositoryProvider)
        .setExtensionData(
          '${widget.plugin.packageName}:_provider_enabled_$providerId',
          enabled ? null : 'false',
        );
    await ref
        .read(extensionManagerProvider.notifier)
        .reloadPlugin(widget.plugin);
    if (mounted) setState(() => _reloading = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final domains = widget.plugin.domains ?? [];
    final providers = widget.plugin.providers ?? <PluginSubProvider>[];

    return Dialog(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.pluginSettings(widget.plugin.name),
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (domains.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Text(
                    'Select Domain',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  RadioGroup<String>(
                    groupValue: _selectedDomain,
                    onChanged: (url) {
                      if (url != null) _applyDomain(url);
                    },
                    child: Column(
                      children: domains.map((d) {
                        return ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          title: Text(d.name),
                          leading: Focus(
                            canRequestFocus: false,
                            // Selection state + change handling come from the
                            // enclosing RadioGroup<String> ancestor; the Radio
                            // only declares its own value (groupValue/onChanged
                            // here are deprecated post-Flutter 3.32).
                            child: Radio<String>(value: d.url),
                          ),
                          onTap: _reloading ? null : () => _applyDomain(d.url),
                        );
                      }).toList(),
                    ),
                  ),
                ],
                if (providers.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Text(
                    'Select Providers',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  ...providers.map(
                    (sub) => CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(sub.name),
                      value: _providerEnabled[sub.id] ?? true,
                      onChanged: _reloading
                          ? null
                          : (val) => _toggleProvider(sub.id, val ?? true),
                    ),
                  ),
                ],
                if (_reloading) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      AppLoadingIndicator(
                        constraints: BoxConstraints.tight(const Size(14, 14)),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Applying…',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 16),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(l10n.close),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
