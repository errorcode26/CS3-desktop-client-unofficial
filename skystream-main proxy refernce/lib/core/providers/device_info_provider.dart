import 'dart:ui' as ui;
import 'dart:io';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'device_info_provider.g.dart';

class DeviceProfile {
  final bool isTv;
  final bool isTablet;

  /// Indicates if running on a desktop operating system (macOS, Windows, Linux)
  /// Use this for capability checks (e.g. window controls, mouse hovers),
  /// NOT for layout sizing. Use [ResponsiveBreakpoints] for layout sizing.
  final bool isDesktopOS;

  const DeviceProfile({
    this.isTv = false,
    this.isTablet = false,
    this.isDesktopOS = false,
  });

  bool get isLargeScreen => isTv || isTablet || isDesktopOS;
}

@riverpod
Future<DeviceProfile> deviceProfile(Ref ref) async {
  bool isTv = false;
  bool isTablet = false;
  bool isDesktopOS = false;

  if (!kIsWeb) {
    if (Platform.isAndroid || Platform.isIOS) {
      final view = ui.PlatformDispatcher.instance.views.first;
      final size = view.physicalSize / view.devicePixelRatio;
      if (size.shortestSide >= 600) {
        isTablet = true;
      }
      if (Platform.isAndroid) {
        final deviceInfo = DeviceInfoPlugin();
        final androidInfo = await deviceInfo.androidInfo;
        isTv = androidInfo.systemFeatures.contains('android.software.leanback');
      }
    }

    if (Platform.isMacOS || Platform.isWindows || Platform.isLinux) {
      isDesktopOS = true;
    }
  }

  return DeviceProfile(
    isTv: isTv,
    isTablet: isTablet,
    isDesktopOS: isDesktopOS,
  );
}
