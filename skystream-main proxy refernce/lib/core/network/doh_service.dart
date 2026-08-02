import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'doh_service.g.dart';

/// DNS over HTTPS provider options.
enum DohProvider {
  cloudflare, // https://cloudflare-dns.com/dns-query
  google, // https://dns.google/dns-query
  adguard, // https://dns.adguard.com/dns-query
  dnsWatch, // https://resolver2.dns.watch/dns-query
  quad9, // https://dns.quad9.net/dns-query
  dnsSb, // https://doh.dns.sb/dns-query
  canadianShield, // https://private.canadianshield.cira.ca/dns-query
  custom, // User-defined URL
}

/// Riverpod state for DoH settings — replaces ChangeNotifier for UI reactivity.
class DohSettings {
  final bool enabled;
  final DohProvider provider;
  final String customUrl;

  const DohSettings({
    this.enabled = false,
    this.provider = DohProvider.cloudflare,
    this.customUrl = '',
  });

  DohSettings copyWith({
    bool? enabled,
    DohProvider? provider,
    String? customUrl,
  }) {
    return DohSettings(
      enabled: enabled ?? this.enabled,
      provider: provider ?? this.provider,
      customUrl: customUrl ?? this.customUrl,
    );
  }
}

@riverpod
class DohSettingsNotifier extends _$DohSettingsNotifier {
  static const _kEnabledKey = 'doh_enabled';
  static const _kProviderKey = 'doh_provider';
  static const _kCustomUrlKey = 'doh_custom_url';

  @override
  Future<DohSettings> build() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final enabled = prefs.getBool(_kEnabledKey) ?? false;
      final providerName = prefs.getString(_kProviderKey);
      final customUrl = prefs.getString(_kCustomUrlKey) ?? '';
      final provider = DohProvider.values.firstWhere(
        (p) => p.name == providerName,
        orElse: () => DohProvider.cloudflare,
      );
      final settings = DohSettings(
        enabled: enabled,
        provider: provider,
        customUrl: customUrl,
      );
      // Wait for instance init if not already done, though it should be
      await DohService.instance.init();
      DohService.instance._syncFromSettings(settings);
      return settings;
    } catch (_) {
      return const DohSettings();
    }
  }

  Future<void> setEnabled(bool value) async {
    final current = state.asData?.value ?? const DohSettings();
    final updated = current.copyWith(enabled: value);
    state = AsyncData(updated);
    DohService.instance._syncFromSettings(updated);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_kEnabledKey, value);
  }

  Future<void> setProvider(DohProvider p) async {
    final current = state.asData?.value ?? const DohSettings();
    final updated = current.copyWith(provider: p);
    state = AsyncData(updated);
    DohService.instance._syncFromSettings(updated);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kProviderKey, p.name);
  }

  Future<void> setCustomUrl(String url) async {
    final current = state.asData?.value ?? const DohSettings();
    final updated = current.copyWith(customUrl: url);
    state = AsyncData(updated);
    DohService.instance._syncFromSettings(updated);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kCustomUrlKey, url);
  }

  void clearCache() => DohService.instance.clearCache();
}

/// A DNS-over-HTTPS resolver that queries Cloudflare or Google for DNS records.
///
/// Settings state is managed by [dohSettingsProvider] (Riverpod).
/// This class is a stateless singleton for DNS resolution only.
class DohService {
  DohService._();
  static final DohService instance = DohService._();

  final Dio _dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 5),
      receiveTimeout: const Duration(seconds: 5),
    ),
  );

  // In-memory cache: domain -> (ip, expiry)
  final Map<String, _DohCacheEntry> _cache = {};

  bool _initialized = false;
  bool _enabled = false;
  DohProvider _provider = DohProvider.cloudflare;
  String _customUrl = '';

  bool get enabled => _enabled;
  DohProvider get provider => _provider;
  String get customUrl => _customUrl;

  /// Initialize from SharedPreferences. Should be called exactly once at boot.
  Future<void> init() async {
    if (_initialized) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      _enabled = prefs.getBool(DohSettingsNotifier._kEnabledKey) ?? false;
      final providerName = prefs.getString(DohSettingsNotifier._kProviderKey);
      _customUrl = prefs.getString(DohSettingsNotifier._kCustomUrlKey) ?? '';
      _provider = DohProvider.values.firstWhere(
        (p) => p.name == providerName,
        orElse: () => DohProvider.cloudflare,
      );
      _initialized = true;
      if (kDebugMode) {
        debugPrint(
          '[DoH] Eagerly initialized settings -> enabled: $_enabled, provider: ${_provider.name}',
        );
      }
    } catch (e) {
      if (kDebugMode) debugPrint('[DoH] Failed to eager init settings: $e');
    }
  }

  /// Called by [DohSettingsNotifier] to sync state.
  void _syncFromSettings(DohSettings settings) {
    _enabled = settings.enabled;
    _provider = settings.provider;
    _customUrl = settings.customUrl;
  }

  String get _endpoint {
    switch (_provider) {
      case DohProvider.cloudflare:
        return 'https://cloudflare-dns.com/dns-query';
      case DohProvider.google:
        return 'https://dns.google/dns-query';
      case DohProvider.adguard:
        return 'https://dns.adguard.com/dns-query';
      case DohProvider.dnsWatch:
        return 'https://resolver2.dns.watch/dns-query';
      case DohProvider.quad9:
        return 'https://dns.quad9.net/dns-query';
      case DohProvider.dnsSb:
        return 'https://doh.dns.sb/dns-query';
      case DohProvider.canadianShield:
        return 'https://private.canadianshield.cira.ca/dns-query';
      case DohProvider.custom:
        return _customUrl;
    }
  }

  /// Resolves a domain to an IP address using DNS over HTTPS.
  /// Returns null if resolution fails (caller should fall back to normal DNS).
  Future<String?> resolve(String domain) async {
    if (!_enabled) return null;

    // Check cache first
    final cached = _cache[domain];
    if (cached != null && cached.expiry.isAfter(DateTime.now())) {
      return cached.ip;
    }

    try {
      final response = await _dio.get<dynamic>(
        _endpoint,
        queryParameters: {
          'name': domain,
          'type': 'A', // IPv4
        },
        options: Options(
          headers: {'Accept': 'application/dns-json'},
          responseType: ResponseType.json,
        ),
      );

      if (response.statusCode == 200) {
        final data = response.data is String
            ? jsonDecode(response.data as String)
            : response.data;

        if (data['Status'] == 0 && data['Answer'] != null) {
          final answers = data['Answer'] as List;
          // Find A record (type 1)
          for (final answer in answers) {
            if (answer['type'] == 1) {
              final ip = answer['data'] as String;
              final ttl = answer['TTL'] as int? ?? 300;
              // Cache with TTL
              _cache[domain] = _DohCacheEntry(
                ip: ip,
                expiry: DateTime.now().add(Duration(seconds: ttl)),
              );
              if (kDebugMode) {
                debugPrint('[DoH] $domain -> $ip (TTL: ${ttl}s)');
              }
              return ip;
            }
          }
        }
      }
    } catch (e) {
      if (kDebugMode) debugPrint('[DoH] Failed to resolve $domain: $e');
    }

    return null; // Fall back to normal DNS
  }

  /// Clears the DNS cache.
  void clearCache() => _cache.clear();
}

class _DohCacheEntry {
  final String ip;
  final DateTime expiry;
  _DohCacheEntry({required this.ip, required this.expiry});
}
