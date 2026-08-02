import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/extensions/extension_manager.dart';
import '../providers/extensions_controller.dart';

/// Listens to [extensionsControllerProvider] and syncs installed plugins into
/// [ExtensionManager]. Keeps core independent of the feature; sync is driven
/// from here.
///
/// Debounced (500 ms) so a rapid sequence of install/uninstall/update events
/// (which arrive as several distinct state updates) results in **one** sync
/// pass instead of N — installing 5 plugins one-after-the-other previously
/// fired the heavy sync 5 times. Audit M15.
class ExtensionsSyncBridge extends ConsumerStatefulWidget {
  const ExtensionsSyncBridge({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<ExtensionsSyncBridge> createState() =>
      _ExtensionsSyncBridgeState();
}

class _ExtensionsSyncBridgeState extends ConsumerState<ExtensionsSyncBridge> {
  Timer? _debounce;
  static const _debounceWindow = Duration(milliseconds: 500);

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(extensionsControllerProvider, (previous, next) {
      if (!listEquals(previous?.installedPlugins, next.installedPlugins)) {
        // Coalesce bursts of install/uninstall events into one sync.
        _debounce?.cancel();
        _debounce = Timer(_debounceWindow, () {
          if (!mounted) return;
          // Re-read state at fire time in case more changes arrived during
          // the debounce window.
          final latest = ref
              .read(extensionsControllerProvider)
              .installedPlugins;
          ref.read(extensionManagerProvider.notifier).syncFromPlugins(latest);
        });
      }
    });
    return widget.child;
  }
}
