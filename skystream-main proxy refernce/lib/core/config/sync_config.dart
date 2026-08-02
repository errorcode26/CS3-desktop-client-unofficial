/// Compile-time configuration for third-party sync / tracking services.
///
/// **Security model:** every value below is read from `--dart-define` flags
/// at build time and embedded as a string constant in the compiled binary.
/// Anyone with an APK / IPA can extract them with `strings` or a decompiler.
/// **Do not treat these as secrets.**
///
/// For sideload-only distribution this is the standard pattern — the OAuth
/// providers above already assume that public client IDs are public, and
/// they tolerate the "client secret" being shared with the redirect URL for
/// device / PKCE / implicit flows. Rotate the keys if abuse is detected.
class SyncConfig {
  static const String animeSkipClientId = String.fromEnvironment(
    'ANIMESKIP_CLIENT_ID',
  );

  static const String traktClientId = String.fromEnvironment('TRAKT_CLIENT_ID');
  static const String traktClientSecret = String.fromEnvironment(
    'TRAKT_CLIENT_SECRET',
  );

  static const String anilistClientId = String.fromEnvironment(
    'ANILIST_CLIENT_ID',
  );

  static const String malClientId = String.fromEnvironment('MAL_CLIENT_ID');
  static const String malClientSecret = String.fromEnvironment(
    'MAL_CLIENT_SECRET',
  );
  // MAL OAuth redirect URI. The webview dialog matches incoming redirects
  // against this with a *host* equality check (see WebViewAuthDialog), not a
  // string prefix — keep it as a parseable URI, not a fragment.
  static const String malRedirectUri = 'http://localhost';

  static const String simklClientId = String.fromEnvironment('SIMKL_CLIENT_ID');
  static const String simklClientSecret = String.fromEnvironment(
    'SIMKL_CLIENT_SECRET',
  );
}
