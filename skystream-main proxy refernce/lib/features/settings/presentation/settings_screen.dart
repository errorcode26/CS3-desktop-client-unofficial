import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/utils/layout_constants.dart';
import '../../../core/utils/responsive_breakpoints.dart';
import '../../../core/providers/device_info_provider.dart';
import '../../../core/theme/theme_provider.dart';

import '../../../core/utils/stream_quality_sorter.dart';
import 'widgets/settings_widgets.dart';
import 'widgets/settings_dialogs.dart';
import 'player_settings_provider.dart';
import 'general_settings_provider.dart';
import 'app_version_provider.dart';
import 'account_settings_screen.dart';

import 'package:skystream/l10n/generated/app_localizations.dart';
import '../../../core/providers/locale_provider.dart';
import '../../../core/network/doh_service.dart';
import '../../../core/router/app_router.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(deviceProfileProvider).asData?.value;
    final isTv = profile?.isTv == true || context.isTv;
    final isWidescreen = isTv || context.isTabletOrLarger;

    if (isWidescreen) {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: Column(
          children: [
            // Inline header matching other widescreen screens
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Container(
                height: LayoutConstants.dashboardHeaderHeight,
                padding: const EdgeInsets.symmetric(
                  horizontal: LayoutConstants.dashboardContentPadding,
                ),
                alignment: Alignment.centerLeft,
                child: Text(
                  AppLocalizations.of(context)!.settings,
                  style: Theme.of(
                    context,
                  ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                ),
              ),
            ),
            Expanded(child: _buildSettingsList(context, ref, isTv)),
          ],
        ),
      );
    }

    // Mobile layout
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.settings)),
      body: _buildSettingsList(context, ref, isTv),
    );
  }

  Widget _buildSettingsList(BuildContext context, WidgetRef ref, bool isTv) {
    final versionAsync = ref.watch(appVersionProvider);
    final themeMode = ref.watch(appThemeModeProvider);
    final generalSettings = ref.watch(generalSettingsProvider);

    final playerSettings =
        ref.watch(playerSettingsProvider).asData?.value ??
        const PlayerSettings();

    final l10n = AppLocalizations.of(context)!;

    final platform = Theme.of(context).platform;
    final isDesktopOS =
        platform == TargetPlatform.windows ||
        platform == TargetPlatform.macOS ||
        platform == TargetPlatform.linux;
    final isTouchDevice = !isTv && !isDesktopOS;

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 800),
        child: ListView(
          padding: const EdgeInsets.only(bottom: LayoutConstants.spacingLg),
          children: [
            const SizedBox(height: LayoutConstants.spacingXs),
            SettingsGroup(
              title: l10n.general,
              children: [
                SettingsTile(
                  icon: Icons.dark_mode_rounded,
                  title: l10n.appTheme,
                  subtitle: themeMode == ThemeMode.system
                      ? l10n.system
                      : (themeMode == ThemeMode.dark ? l10n.dark : l10n.light),
                  onTap: () => showThemeDialog(context, ref, themeMode),
                ),
                SettingsTile(
                  icon: Icons.history_rounded,
                  title: l10n.recordWatchHistory,
                  subtitle: generalSettings.watchHistoryEnabled
                      ? l10n.enabled
                      : l10n.disabled,
                  trailing: Switch(
                    value: generalSettings.watchHistoryEnabled,
                    onChanged: (val) => ref
                        .read(generalSettingsProvider.notifier)
                        .setWatchHistoryEnabled(val),
                  ),
                  onTap: () => ref
                      .read(generalSettingsProvider.notifier)
                      .setWatchHistoryEnabled(
                        !generalSettings.watchHistoryEnabled,
                      ),
                ),
                SettingsTile(
                  icon: Icons.home_rounded,
                  title: l10n.defaultHomeScreen,
                  subtitle: getHomeScreenLabel(
                    generalSettings.defaultHomeScreen,
                    l10n,
                  ),
                  onTap: () => showDefaultHomeScreenDialog(
                    context,
                    ref,
                    generalSettings.defaultHomeScreen,
                  ),
                ),
                SettingsTile(
                  icon: Icons.translate_rounded,
                  title: l10n.language,
                  subtitle: l10n.languageName,
                  isLast: true,
                  onTap: () => showLanguageDialog(
                    context,
                    ref,
                    ref.read(localeProvider),
                  ),
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.player,
              children: [
                SettingsTile(
                  icon: Icons.smart_display_rounded,
                  title: l10n.defaultPlayer,
                  subtitle: getPlayerDisplayName(
                    playerSettings.preferredPlayer,
                    l10n,
                  ),
                  onTap: () => showDefaultPlayerDialog(
                    context,
                    ref,
                    playerSettings.preferredPlayer,
                  ),
                ),
                if (isTouchDevice) ...[
                  SettingsTile(
                    icon: Icons.swipe_vertical_rounded,
                    title: l10n.leftGesture,
                    subtitle: getGestureLabel(playerSettings.leftGesture, l10n),
                    onTap: () => showGestureDialog(
                      context,
                      ref,
                      true,
                      playerSettings.leftGesture,
                    ),
                  ),
                  SettingsTile(
                    icon: Icons.swipe_vertical_rounded,
                    title: l10n.rightGesture,
                    subtitle: getGestureLabel(
                      playerSettings.rightGesture,
                      l10n,
                    ),
                    onTap: () => showGestureDialog(
                      context,
                      ref,
                      false,
                      playerSettings.rightGesture,
                    ),
                  ),
                  SettingsTile(
                    icon: Icons.touch_app_rounded,
                    title: l10n.doubleTapToSeek,
                    subtitle: playerSettings.doubleTapEnabled
                        ? l10n.enabled
                        : l10n.disabled,
                    trailing: Switch(
                      value: playerSettings.doubleTapEnabled,
                      onChanged: (val) => ref
                          .read(playerSettingsProvider.notifier)
                          .setDoubleTapEnabled(val),
                    ),
                    onTap: () => ref
                        .read(playerSettingsProvider.notifier)
                        .setDoubleTapEnabled(!playerSettings.doubleTapEnabled),
                  ),
                  SettingsTile(
                    icon: Icons.swipe_rounded,
                    title: l10n.swipeToSeek,
                    subtitle: playerSettings.swipeSeekEnabled
                        ? l10n.enabled
                        : l10n.disabled,
                    trailing: Switch(
                      value: playerSettings.swipeSeekEnabled,
                      onChanged: (val) => ref
                          .read(playerSettingsProvider.notifier)
                          .setSwipeSeekEnabled(val),
                    ),
                    onTap: () => ref
                        .read(playerSettingsProvider.notifier)
                        .setSwipeSeekEnabled(!playerSettings.swipeSeekEnabled),
                  ),
                ],
                SettingsTile(
                  icon: Icons.av_timer_rounded,
                  title: l10n.seekDuration,
                  subtitle: formatSeekDuration(
                    playerSettings.seekDuration,
                    l10n,
                  ),
                  onTap: () => showDurationDialog(
                    context,
                    ref,
                    playerSettings.seekDuration,
                  ),
                ),
                SettingsTile(
                  icon: Icons.timer_outlined,
                  title: l10n.bufferDepth,
                  subtitle: formatReadahead(
                    playerSettings.readaheadSeconds,
                    l10n,
                  ),
                  onTap: () => showReadaheadDialog(
                    context,
                    ref,
                    playerSettings.readaheadSeconds,
                  ),
                ),
                SettingsTile(
                  icon: Icons.aspect_ratio_rounded,
                  title: l10n.defaultResizeMode,
                  subtitle: getResizeModeLabel(
                    playerSettings.defaultResizeMode,
                    l10n,
                  ),
                  onTap: () => showResizeDialog(
                    context,
                    ref,
                    playerSettings.defaultResizeMode,
                  ),
                ),
                SettingsTile(
                  icon: Icons.high_quality_rounded,
                  title: l10n.hardwareDecoding,
                  subtitle: playerSettings.hardwareDecoding
                      ? '${l10n.enabled} (${l10n.recommended})'
                      : l10n.disabled,
                  trailing: Switch(
                    value: playerSettings.hardwareDecoding,
                    onChanged: (val) => ref
                        .read(playerSettingsProvider.notifier)
                        .setHardwareDecoding(val),
                  ),
                  onTap: () => ref
                      .read(playerSettingsProvider.notifier)
                      .setHardwareDecoding(!playerSettings.hardwareDecoding),
                ),
                SettingsTile(
                  icon: Icons.wifi_rounded,
                  title: l10n.wifiQualityPreference,
                  subtitle: qualityPreferenceLabel(
                    playerSettings.wifiQuality,
                    l10n,
                  ),
                  onTap: () => showQualityDialog(
                    context,
                    ref,
                    title: l10n.wifiQualityPreference,
                    current: playerSettings.wifiQuality,
                    onChanged: ref
                        .read(playerSettingsProvider.notifier)
                        .setWifiQuality,
                  ),
                ),
                SettingsTile(
                  icon: Icons.signal_cellular_alt_rounded,
                  title: l10n.mobileQualityPreference,
                  subtitle: qualityPreferenceLabel(
                    playerSettings.mobileQuality,
                    l10n,
                  ),
                  onTap: () => showQualityDialog(
                    context,
                    ref,
                    title: l10n.mobileQualityPreference,
                    current: playerSettings.mobileQuality,
                    onChanged: ref
                        .read(playerSettingsProvider.notifier)
                        .setMobileQuality,
                  ),
                ),
                SettingsTile(
                  icon: Icons.filter_list_rounded,
                  title: 'Quality Filter Mode',
                  subtitle: _qualityFilterModeLabel(
                    playerSettings.qualityFilterMode,
                  ),
                  isLast: true,
                  onTap: () => showQualityFilterModeDialog(
                    context,
                    ref,
                    current: playerSettings.qualityFilterMode,
                    onChanged: ref
                        .read(playerSettingsProvider.notifier)
                        .setQualityFilterMode,
                  ),
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.accounts,
              children: [
                SettingsTile(
                  icon: Icons.account_circle_rounded,
                  title: 'Manage Accounts',
                  subtitle: 'Configure Subtitles and Tracking Services',
                  isLast: true,
                  onTap: () {
                    Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => const AccountSettingsScreen(),
                      ),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            Builder(
              builder: (context) {
                final dohState =
                    ref.watch(dohSettingsProvider).asData?.value ??
                    const DohSettings();
                return SettingsGroup(
                  title: l10n.network,
                  children: [
                    SettingsTile(
                      icon: Icons.dns_rounded,
                      title: l10n.dnsOverHttps,
                      subtitle: dohState.enabled
                          ? '${l10n.on} (${getDohProviderLabel(dohState.provider, dohState.customUrl, l10n)})'
                          : l10n.off,
                      trailing: Switch(
                        value: dohState.enabled,
                        onChanged: (val) {
                          ref
                              .read(dohSettingsProvider.notifier)
                              .setEnabled(val);
                        },
                      ),
                      onTap: () {
                        ref
                            .read(dohSettingsProvider.notifier)
                            .setEnabled(!dohState.enabled);
                      },
                    ),
                    if (dohState.enabled)
                      SettingsTile(
                        icon: Icons.cloud_rounded,
                        title: l10n.dohProvider,
                        subtitle: getDohProviderLabel(
                          dohState.provider,
                          dohState.customUrl,
                          l10n,
                        ),
                        onTap: () => showDohProviderDialog(context, ref),
                      ),

                    SettingsTile(
                      icon: Icons.alt_route_rounded,
                      title: l10n.githubProxy,
                      subtitle: l10n.githubProxySubtitle,
                      trailing: Switch(
                        value: generalSettings.githubProxyEnabled,
                        onChanged: (val) {
                          ref
                              .read(generalSettingsProvider.notifier)
                              .setGithubProxyEnabled(val);
                        },
                      ),
                      onTap: () {
                        ref
                            .read(generalSettingsProvider.notifier)
                            .setGithubProxyEnabled(
                              !generalSettings.githubProxyEnabled,
                            );
                      },
                      isLast: true,
                    ),
                  ],
                );
              },
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.extensions,
              children: [
                SettingsTile(
                  icon: Icons.extension_rounded,
                  title: l10n.manageExtensions,
                  subtitle: l10n.installRemoveProviders,
                  isLast: true,
                  onTap: () => const ExtensionsRoute().go(context),
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.appData,
              children: [
                SettingsTile(
                  icon: Icons.restore_rounded,
                  title: l10n.resetDataKeepExtensions,
                  subtitle: l10n.resetDataSubtitle,
                  onTap: () => showResetDataDialog(context, ref),
                ),
                SettingsTile(
                  icon: Icons.delete_forever_rounded,
                  title: l10n.factoryReset,
                  subtitle: l10n.factoryResetSubtitle,
                  isLast: true,
                  onTap: () => showFactoryResetDialog(context, ref),
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.developer,
              children: [
                SettingsTile(
                  icon: Icons.developer_mode_rounded,
                  title: l10n.developerOptions,
                  subtitle: l10n.developerOptionsSubtitle,
                  isLast: true,
                  onTap: () => const DeveloperOptionsRoute().go(context),
                ),
              ],
            ),
            const SizedBox(height: LayoutConstants.spacingLg),
            SettingsGroup(
              title: l10n.about,
              children: [
                SettingsTile(
                  icon: Icons.person_outline_rounded,
                  title: l10n.developer,
                  subtitle: l10n.developedBy('Akash'),
                  onTap: () => showDeveloperDialog(context),
                ),
                SettingsTile(
                  icon: Icons.forum_outlined,
                  title: l10n.discord,
                  subtitle: l10n.discordSubtitle,
                  onTap: () => launchUrl(
                    Uri.parse('https://discord.gg/73XGA8Mxn9'),
                    mode: LaunchMode.externalApplication,
                  ),
                ),
                SettingsTile(
                  icon: Icons.send_rounded,
                  title: l10n.telegram,
                  subtitle: l10n.telegramSubtitle,
                  onTap: () => launchUrl(
                    Uri.parse('https://t.me/+Ez5Vsv2pUUFjZmNl'),
                    mode: LaunchMode.externalApplication,
                  ),
                ),
                SettingsTile(
                  icon: Icons.info_outline_rounded,
                  title: l10n.version,
                  subtitle: versionAsync.when(
                    data: (v) => v,
                    loading: () => l10n.loading,
                    error: (err, stack) => l10n.unknown,
                  ),
                  trailing: const SizedBox.shrink(),
                  isLast: true,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

String _qualityFilterModeLabel(QualityFilterMode mode) {
  switch (mode) {
    case QualityFilterMode.any:
      return 'Show all (sort only)';
    case QualityFilterMode.atOrAbove:
      return 'Hide sources below preference';
    case QualityFilterMode.atOrBelow:
      return 'Hide sources above preference';
  }
}
