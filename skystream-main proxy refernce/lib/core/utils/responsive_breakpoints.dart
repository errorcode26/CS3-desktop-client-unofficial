import 'dart:io';
import 'package:flutter/material.dart';

enum DeviceScreenType { mobile, tablet, desktop }

class ResponsiveBreakpoints {
  // Common standard breakpoints
  static const double tabletBreakpoint = 600;
  static const double desktopBreakpoint = 900;

  static DeviceScreenType getDeviceType(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;

    if (width >= desktopBreakpoint) {
      return DeviceScreenType.desktop;
    } else if (width >= tabletBreakpoint) {
      return DeviceScreenType.tablet;
    } else {
      return DeviceScreenType.mobile;
    }
  }

  static bool isMobile(BuildContext context) =>
      getDeviceType(context) == DeviceScreenType.mobile;

  static bool isTablet(BuildContext context) =>
      getDeviceType(context) == DeviceScreenType.tablet;

  static bool isDesktop(BuildContext context) =>
      getDeviceType(context) == DeviceScreenType.desktop;

  static bool isTabletOrLarger(BuildContext context) =>
      getDeviceType(context) != DeviceScreenType.mobile;
}

// Extension for easy access from BuildContext
extension ResponsiveContext on BuildContext {
  DeviceScreenType get deviceType => ResponsiveBreakpoints.getDeviceType(this);
  bool get isMobile => ResponsiveBreakpoints.isMobile(this);
  bool get isTablet => ResponsiveBreakpoints.isTablet(this);
  bool get isDesktop => ResponsiveBreakpoints.isDesktop(this);
  bool get isTabletOrLarger => ResponsiveBreakpoints.isTabletOrLarger(this);
  bool get isTv =>
      MediaQuery.maybeNavigationModeOf(this) == NavigationMode.directional ||
      (Platform.isAndroid &&
          MediaQuery.of(this).size.aspectRatio > 1.0 &&
          MediaQuery.of(this).padding.top == 0);
}
