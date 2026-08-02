import 'dart:async';
import 'dart:io';
import 'package:skystream/l10n/generated/app_localizations.dart';

class AppErrorMessages {
  AppErrorMessages._();

  static String from(Object error, AppLocalizations l10n) {
    final s = error.toString();
    if (error is SocketException || s.contains('SocketException')) {
      return l10n.noInternetError;
    }
    if (error is TimeoutException ||
        s.contains('TimeoutException') ||
        s.contains('Connection timed out')) {
      return l10n.timeoutError;
    }
    if (error is HttpException || s.contains('HttpException')) {
      return l10n.serverError;
    }
    if (s.contains('404')) return l10n.contentNotFoundError;
    if (s.contains('403') || s.contains('401')) {
      return l10n.accessDeniedError;
    }
    if (s.contains('500') || s.contains('502') || s.contains('503')) {
      return l10n.serviceUnavailableError;
    }
    return l10n.generalError;
  }
}
