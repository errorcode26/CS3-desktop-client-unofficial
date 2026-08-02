import 'package:flutter/material.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../storage/storage_service.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';

part 'locale_provider.g.dart';

@Riverpod(keepAlive: true)
class LocaleNotifier extends _$LocaleNotifier {
  late final StorageService _storage;

  @override
  Locale build() {
    _storage = ref.read(storageServiceProvider);
    return _resolveLocale(_storage.getLanguage());
  }

  static Locale _resolveLocale(String langTag) {
    final parsed = langTag.contains('-')
        ? Locale(langTag.split('-')[0], langTag.split('-')[1])
        : Locale(langTag);
    // Exact match first
    if (AppLocalizations.supportedLocales.contains(parsed)) return parsed;
    // Fall back to language-code-only match (e.g. 'en-US' → 'en')
    final languageOnly = Locale(parsed.languageCode);
    if (AppLocalizations.supportedLocales.contains(languageOnly)) {
      return languageOnly;
    }
    return parsed;
  }

  Future<void> setLocale(Locale locale) async {
    final langTag = locale.countryCode != null
        ? '${locale.languageCode}-${locale.countryCode}'
        : locale.languageCode;
    await _storage.setLanguage(langTag);
    state = locale;
  }
}
