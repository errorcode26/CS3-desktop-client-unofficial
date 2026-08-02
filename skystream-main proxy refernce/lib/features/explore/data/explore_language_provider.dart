import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../core/storage/settings_repository.dart';

part 'explore_language_provider.g.dart';

@Riverpod(keepAlive: true)
class Language extends _$Language {
  @override
  String build() {
    final settings = ref.read(settingsRepositoryProvider);
    return settings.getExploreLanguage();
  }

  Future<void> setLanguage(String language) async {
    await ref.read(settingsRepositoryProvider).setExploreLanguage(language);
    state = language;
  }
}

class LanguageOption {
  final String code;
  final String name;
  final String nativeName;

  const LanguageOption(this.code, this.name, this.nativeName);
}

@Riverpod(keepAlive: true)
List<LanguageOption> languageList(Ref ref) {
  return const [
    LanguageOption('en-US', 'English', 'English'),
    LanguageOption('ar-SA', 'Arabic', 'عربي'),
    LanguageOption('ar-AE', 'Levantine Arabic', 'عربي (الشام)'),
    LanguageOption('as-IN', 'Assamese', 'অসমীয়া'),
    LanguageOption('be-BY', 'Belarusian', 'беларуская'),
    LanguageOption('bg-BG', 'Bulgarian', 'български'),
    LanguageOption('bn-IN', 'Bengali', 'বাংলা'),
    LanguageOption('cs-CZ', 'Czech', 'čeština'),
    LanguageOption('de-DE', 'German', 'Deutsch'),
    LanguageOption('el-GR', 'Greek', 'Ελληνικά'),
    LanguageOption('es-ES', 'Spanish', 'Español'),
    LanguageOption('fr-FR', 'French', 'Français'),
    LanguageOption('gu-IN', 'Gujarati', 'ગુજરાતી'),
    LanguageOption('he-IL', 'Hebrew', 'עברית'),
    LanguageOption('hi-IN', 'Hindi', 'हिन्दी'),
    LanguageOption('hr-HR', 'Croatian', 'Hrvatski'),
    LanguageOption('hu-HU', 'Hungarian', 'Magyar'),
    LanguageOption('id-ID', 'Indonesian', 'Bahasa Indonesia'),
    LanguageOption('it-IT', 'Italian', 'Italiano'),
    LanguageOption('ja-JP', 'Japanese', '日本語'),
    LanguageOption('kn-IN', 'Kannada', 'ಕನ್ನಡ'),
    LanguageOption('ko-KR', 'Korean', '한국어'),
    LanguageOption('lv-LV', 'Latvian', 'latviešu'),
    LanguageOption('mk-MK', 'Macedonian', 'македонски'),
    LanguageOption('ml-IN', 'Malayalam', 'മലയാളം'),
    LanguageOption('mr-IN', 'Marathi', 'मराठी'),
    LanguageOption('nl-NL', 'Dutch', 'Nederlands'),
    LanguageOption('pa-IN', 'Punjabi', 'ਪੰਜਾਬੀ'),
    LanguageOption('pl-PL', 'Polish', 'Polski'),
    LanguageOption('pt-PT', 'Portuguese', 'Português'),
    LanguageOption('pt-BR', 'Portuguese (Brazil)', 'Português (Brasil)'),
    LanguageOption('ro-RO', 'Romanian', 'Română'),
    LanguageOption('ru-RU', 'Russian', 'Русский'),
    LanguageOption('sv-SE', 'Swedish', 'Svenska'),
    LanguageOption('ta-IN', 'Tamil', 'தமிழ்'),
    LanguageOption('te-IN', 'Telugu', 'తెలుగు'),
    LanguageOption('tr-TR', 'Turkish', 'Türkçe'),
    LanguageOption('uk-UA', 'Ukrainian', 'Українська'),
    LanguageOption('ur-PK', 'Urdu', 'اردو'),
    LanguageOption('vi-VN', 'Vietnamese', 'Tiếng Việt'),
    LanguageOption('zh-CN', 'Chinese', '中文'),
    LanguageOption('zh-TW', 'Chinese (Traditional)', '繁體中文'),
  ];
}
