import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:open_file/open_file.dart';
import '../utils/app_utils.dart';

/// Represents an external video player that can be launched from Skystream.
class ExternalPlayer {
  final String id;
  final String displayName;
  final IconData icon;
  final Set<TargetPlatform> supportedPlatforms;

  // Platform-specific identifiers
  final String? androidPackage; // e.g. 'org.videolan.vlc'
  final String?
  androidAction; // e.g. 'org.videolan.vlc.player.VideoPlayerActivity'
  final String? iosScheme; // e.g. 'vlc://'
  final String? desktopCommand; // e.g. 'vlc'
  final String? macAppName; // e.g. 'VLC' for `open -a VLC`

  const ExternalPlayer({
    required this.id,
    required this.displayName,
    required this.icon,
    required this.supportedPlatforms,
    this.androidPackage,
    this.androidAction,
    this.iosScheme,
    this.desktopCommand,
    this.macAppName,
  });
}

class ExternalPlayerService {
  ExternalPlayerService._();
  static final ExternalPlayerService instance = ExternalPlayerService._();

  /// All known external players across platforms.
  static const List<ExternalPlayer> allPlayers = [
    ExternalPlayer(
      id: 'vlc',
      displayName: 'VLC',
      icon: Icons.play_circle_filled,
      supportedPlatforms: {
        TargetPlatform.android,
        TargetPlatform.iOS,
        TargetPlatform.windows,
        TargetPlatform.macOS,
        TargetPlatform.linux,
      },
      androidPackage: 'org.videolan.vlc',
      iosScheme: 'vlc://',
      desktopCommand: 'vlc',
      macAppName: 'VLC',
    ),
    ExternalPlayer(
      id: 'mx_player',
      displayName: 'MX Player',
      icon: Icons.ondemand_video,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'com.mxtech.videoplayer.ad',
    ),
    ExternalPlayer(
      id: 'mx_player_pro',
      displayName: 'MX Player Pro',
      icon: Icons.ondemand_video,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'com.mxtech.videoplayer.pro',
    ),
    ExternalPlayer(
      id: 'just_player',
      displayName: 'Just Player',
      icon: Icons.smart_display,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'com.brouken.player',
    ),
    ExternalPlayer(
      id: 'mpv_android',
      displayName: 'mpv (Android)',
      icon: Icons.videocam,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'is.xyz.mpv',
    ),
    ExternalPlayer(
      id: 'mpvex',
      displayName: 'mpvEx',
      icon: Icons.videocam_outlined,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'app.marlboroadvance.mpvex',
    ),
    // Web Video Cast — accepts ACTION_VIEW with video/* mime, routes to
    // Chromecast / DLNA / Roku / Fire TV / smart TV. Useful for casting
    // a SkyStream-resolved stream URL to a TV without leaving the phone.
    ExternalPlayer(
      id: 'web_video_cast',
      displayName: 'Web Video Cast',
      icon: Icons.cast,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'com.instantbits.cast.webvideo',
    ),
    ExternalPlayer(
      id: 'web_video_cast_premium',
      displayName: 'Web Video Cast (Premium)',
      icon: Icons.cast,
      supportedPlatforms: {TargetPlatform.android},
      androidPackage: 'com.instantbits.cast.webvideo.premium',
    ),
    ExternalPlayer(
      id: 'mpv',
      displayName: 'mpv',
      icon: Icons.videocam,
      supportedPlatforms: {
        TargetPlatform.windows,
        TargetPlatform.macOS,
        TargetPlatform.linux,
      },
      desktopCommand: 'mpv',
    ),
    ExternalPlayer(
      id: 'iina',
      displayName: 'IINA',
      icon: Icons.play_circle,
      supportedPlatforms: {TargetPlatform.macOS},
      desktopCommand: 'iina',
      macAppName: 'IINA',
    ),
    ExternalPlayer(
      id: 'infuse',
      displayName: 'Infuse',
      icon: Icons.live_tv,
      supportedPlatforms: {TargetPlatform.iOS},
      iosScheme: 'infuse://',
    ),
    ExternalPlayer(
      id: 'nplayer',
      displayName: 'nPlayer',
      icon: Icons.video_library,
      supportedPlatforms: {TargetPlatform.iOS},
      iosScheme: 'nplayer-',
    ),
    ExternalPlayer(
      id: 'potplayer',
      displayName: 'PotPlayer',
      icon: Icons.play_circle_outline,
      supportedPlatforms: {TargetPlatform.windows},
      desktopCommand: 'PotPlayerMini64',
    ),
    ExternalPlayer(
      id: 'mpc_hc',
      displayName: 'MPC-HC',
      icon: Icons.play_circle_outline,
      supportedPlatforms: {TargetPlatform.windows},
      desktopCommand: 'mpc-hc64',
    ),
    ExternalPlayer(
      id: 'mpc_be',
      displayName: 'MPC-BE',
      icon: Icons.play_circle_outline,
      supportedPlatforms: {TargetPlatform.windows},
      desktopCommand: 'mpc-be64',
    ),
    ExternalPlayer(
      id: 'celluloid',
      displayName: 'Celluloid',
      icon: Icons.movie,
      supportedPlatforms: {TargetPlatform.linux},
      desktopCommand: 'celluloid',
    ),
  ];

  /// Returns players available on the current platform.
  List<ExternalPlayer> getPlayersForPlatform() {
    final platform = defaultTargetPlatform;
    return allPlayers
        .where((p) => p.supportedPlatforms.contains(platform))
        .toList();
  }

  /// Finds a player by its ID.
  ExternalPlayer? getPlayerById(String id) {
    try {
      return allPlayers.firstWhere((p) => p.id == id);
    } catch (_) {
      return null;
    }
  }

  /// Launches a video URL in the specified external player.
  ///
  /// [videoUrl] — direct video stream URL (not the episode data blob)
  /// [headers] — optional HTTP headers for the stream
  /// [playerId] — the external player ID to use
  /// [title] — optional video title for players that support it
  Future<bool> launch(
    String videoUrl, {
    Map<String, String>? headers,
    required String playerId,
    String? title,
  }) async {
    final player = getPlayerById(playerId);
    if (player == null) return false;

    try {
      final normalizedUrl = AppUtils.normalizeUrl(videoUrl);

      if (Platform.isAndroid) {
        return await _launchAndroid(
          normalizedUrl,
          player,
          headers: headers,
          title: title,
        );
      } else if (Platform.isIOS) {
        return await _launchIOS(normalizedUrl, player);
      } else if (Platform.isMacOS) {
        return await _launchMacOS(normalizedUrl, player);
      } else if (Platform.isWindows) {
        return await _launchWindows(normalizedUrl, player);
      } else if (Platform.isLinux) {
        return await _launchLinux(normalizedUrl, player);
      }
    } catch (e) {
      if (kDebugMode) debugPrint('ExternalPlayer launch error: $e');
    }
    return false;
  }

  // -- Android: Native Intent via platform channel --

  static const _playerChannel = MethodChannel(
    'dev.akash.skystream/external_player',
  );

  Future<bool> _launchAndroid(
    String videoUrl,
    ExternalPlayer player, {
    Map<String, String>? headers,
    String? title,
  }) async {
    try {
      // Use the native Kotlin channel which constructs a proper Android Intent.
      // This avoids url_launcher's Uri.parse() which breaks on video URLs
      // containing query parameters (?key=value&...).
      final result = await _playerChannel
          .invokeMethod<bool>('launchVideoInPlayer', {
            'url': videoUrl,
            'package': player.androidPackage,
            'mimeType': 'video/*',
            'title': ?title,
          });
      return result ?? false;
    } on PlatformException catch (e) {
      if (kDebugMode) debugPrint('Android external player error: ${e.message}');
    } catch (e) {
      if (kDebugMode) debugPrint('Android intent launch failed: $e');
    }

    // Fallback: plain ACTION_VIEW without a package target
    try {
      final uri = Uri.parse(videoUrl);
      return await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (e) {
      if (kDebugMode) debugPrint('Android fallback launch failed: $e');
      return false;
    }
  }

  // -- iOS: Custom URL scheme --

  Future<bool> _launchIOS(String videoUrl, ExternalPlayer player) async {
    if (AppUtils.isLocalFile(videoUrl)) {
      // Local files on iOS cannot be easily shared via URL schemes
      // due to sandbox restrictions. Use Open-In which is the standard mechanism.
      final path = videoUrl.replaceFirst('file://', '');
      final result = await OpenFile.open(path);
      return result.type == ResultType.done;
    }

    if (player.iosScheme != null) {
      // VLC: vlc://url
      // Infuse: infuse://x-callback-url/play?url=...
      // nPlayer: nplayer-http://url or nplayer-https://url
      String launchUrl;

      if (player.id == 'vlc') {
        launchUrl = 'vlc://${Uri.encodeFull(videoUrl)}';
      } else if (player.id == 'infuse') {
        launchUrl =
            'infuse://x-callback-url/play?url=${Uri.encodeComponent(videoUrl)}';
      } else if (player.id == 'nplayer') {
        // nPlayer replaces the URL scheme: http→nplayer-http, https→nplayer-https
        launchUrl = videoUrl
            .replaceFirst(RegExp(r'^https://'), 'nplayer-https://')
            .replaceFirst(RegExp(r'^http://'), 'nplayer-http://');
      } else {
        launchUrl = '${player.iosScheme}${Uri.encodeFull(videoUrl)}';
      }

      final uri = Uri.parse(launchUrl);
      if (await canLaunchUrl(uri)) {
        return await launchUrl_(uri);
      }
    }
    return false;
  }

  // Wrapper to avoid name collision with url_launcher's launchUrl
  Future<bool> launchUrl_(Uri uri) async {
    return await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  // -- macOS: open -a or CLI --

  Future<bool> _launchMacOS(String videoUrl, ExternalPlayer player) async {
    try {
      if (player.macAppName != null) {
        // 'open -a' is a launcher that returns immediately after starting the app.
        // Process.run is perfect here to catch "App not found" without blocking.
        final result = await Process.run('open', [
          '-a',
          player.macAppName!,
          videoUrl,
        ]);
        return result.exitCode == 0;
      }
      if (player.desktopCommand != null) {
        if (await _isCommandAvailable(player.desktopCommand!)) {
          await Process.start(player.desktopCommand!, [
            videoUrl,
          ], mode: ProcessStartMode.detached);
          return true;
        }
      }
    } catch (e) {
      if (kDebugMode) debugPrint('macOS launch error: $e');
    }
    return false;
  }

  // -- Windows: Process.run with command --

  // Common installation paths for popular Windows players
  static const _windowsPlayerPaths = <String, List<String>>{
    'vlc': [
      r'C:\Program Files\VideoLAN\VLC\vlc.exe',
      r'C:\Program Files (x86)\VideoLAN\VLC\vlc.exe',
    ],
    'PotPlayerMini64': [
      r'C:\Program Files\DAUM\PotPlayer\PotPlayerMini64.exe',
      r'C:\Program Files (x86)\DAUM\PotPlayer\PotPlayerMini64.exe',
    ],
    'mpv': [
      r'C:\Program Files\mpv\mpv.exe',
      r'C:\Program Files (x86)\mpv\mpv.exe',
    ],
    'mpc-hc64': [
      r'C:\Program Files\MPC-HC\mpc-hc64.exe',
      r'C:\Program Files (x86)\MPC-HC\mpc-hc64.exe',
    ],
    'mpc-be64': [r'C:\Program Files\MPC-BE x64\mpc-be64.exe'],
  };

  Future<bool> _launchWindows(String videoUrl, ExternalPlayer player) async {
    final command = player.desktopCommand;
    if (command == null) return false;
    try {
      // 1. Try running by command name (works if it's in PATH)
      try {
        if (await _isCommandAvailable(command)) {
          await Process.start(command, [
            videoUrl,
          ], mode: ProcessStartMode.detached);
          return true;
        }
      } catch (_) {
        // Not in PATH — try common install directories
      }

      // 2. Try known install paths
      final knownPaths = _windowsPlayerPaths[command] ?? [];
      for (final exePath in knownPaths) {
        try {
          final f = File(exePath);
          if (await f.exists()) {
            await Process.start(exePath, [
              videoUrl,
            ], mode: ProcessStartMode.detached);
            return true;
          }
        } catch (_) {
          continue;
        }
      }

      // 3. Last resort: use Windows shell `start` to open with default handler
      try {
        // We use Process.run here as `start` is a cmd internal and returning
        // quickly is already handled by start /b or similar if needed,
        // but for safety with playUrl, run is fine for the fallback.
        final result = await Process.run('cmd', [
          '/c',
          'start',
          '',
          '"$videoUrl"',
        ], runInShell: true);
        return result.exitCode == 0;
      } catch (_) {}
    } catch (e) {
      if (kDebugMode) debugPrint('Windows launch error: $e');
    }
    return false;
  }

  // -- Linux: Process.run with CLI --

  Future<bool> _launchLinux(String videoUrl, ExternalPlayer player) async {
    try {
      if (player.desktopCommand != null) {
        try {
          if (await _isCommandAvailable(player.desktopCommand!)) {
            await Process.start(player.desktopCommand!, [
              videoUrl,
            ], mode: ProcessStartMode.detached);
            return true;
          }
        } catch (_) {
          // Command not recognized or not in PATH
        }
      }
    } catch (e) {
      if (kDebugMode) debugPrint('Linux launch error: $e');
    }
    return false;
  }

  Future<bool> _isCommandAvailable(String command) async {
    try {
      final executable = Platform.isWindows ? 'where' : 'which';
      final result = await Process.run(executable, [command]);
      return result.exitCode == 0;
    } catch (_) {
      return false;
    }
  }
}
