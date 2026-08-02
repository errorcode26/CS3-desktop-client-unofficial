import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'flutter_torrent_server_platform_interface.dart';

class FlutterTorrentServerDesktop extends FlutterTorrentServerPlatform {
  /// Registers this class as the default instance of [FlutterTorrentServerPlatform].
  static void registerWith() {
    FlutterTorrentServerPlatform.instance = FlutterTorrentServerDesktop();
  }

  Process? _serverProcess;
  final int _port = 8090;

  @override
  Future<int> start() async {
    if (_serverProcess != null) return _port;

    if (await _checkConnection()) {
      debugPrint("TorrServer is already running on port $_port, reusing.");
      return _port;
    }

    // Forcefully cleanup any lingering instances to ensure a fresh start
    // This prevents "Connection closed" issues from zombie processes.
    try {
      if (!Platform.isWindows) {
        // pkill -f matches full command line
        // Catch and ignore error (exit code 1 if no process found)
        await Process.run('pkill', ['-f', 'TorrServer-']);
        await Future.delayed(const Duration(milliseconds: 500));
      } else {
        await Process.run('taskkill', [
          '/F',
          '/IM',
          'TorrServer-windows-amd64.exe',
        ]);
        await Future.delayed(const Duration(milliseconds: 500));
      }
    } catch (_) {}

    try {
      final binaryName = _getBinaryName();
      if (binaryName == null) {
        throw Exception("Unsupported platform for TorrServer");
      }

      final appDir = await getApplicationSupportDirectory();
      final binaryPath = p.join(appDir.path, binaryName);
      final binaryFile = File(binaryPath);

      // Always copy to ensure we have the latest or if it's missing
      // In production, might want to check version or existence to save time
      final assetPath =
          'packages/flutter_torrent_server/assets/torrserver/$binaryName';
      final byteData = await rootBundle.load(assetPath);

      if (!await binaryFile.exists()) {
        await binaryFile.create(recursive: true);
      }
      await binaryFile.writeAsBytes(
        byteData.buffer.asUint8List(
          byteData.offsetInBytes,
          byteData.lengthInBytes,
        ),
      );

      if (!Platform.isWindows) {
        await Process.run('chmod', ['+x', binaryPath]);
      }

      // As we just killed everything, valid server start is handled below.
      // But just in case, let's proceed.
      debugPrint("Starting TorrServer: $binaryPath");
      _serverProcess = await Process.start(binaryPath, [
        '-p',
        '$_port',
        '-d',
        appDir.path,
      ]);

      _serverProcess!.stdout.transform(utf8.decoder).listen((data) {
        debugPrint("TorrServer: $data");
      });
      _serverProcess!.stderr.transform(utf8.decoder).listen((data) {
        debugPrint("TorrServer Error: $data");
      });

      // Wait for it to be ready
      bool isReady = false;
      for (int i = 0; i < 20; i++) {
        // Increased retries to 10 seconds
        if (await _checkConnection()) {
          isReady = true;
          break;
        }
        await Future.delayed(const Duration(milliseconds: 500));
      }

      if (!isReady) {
        throw Exception(
          "TorrServer failed to start or is not responding on port $_port",
        );
      }

      return _port;
    } catch (e) {
      debugPrint("Failed to start TorrServer: $e");
      rethrow;
    }
  }

  Future<bool> _checkConnection() async {
    try {
      final response = await http.get(
        Uri.parse('http://127.0.0.1:$_port/echo'),
      );
      return response.statusCode ==
          200; // TorrServer usually replies to /echo or just /
    } catch (_) {
      try {
        // Fallback check
        final response = await http.get(
          Uri.parse('http://127.0.0.1:$_port/settings'),
        );
        return response.statusCode == 200;
      } catch (__) {
        return false;
      }
    }
  }

  @override
  Future<void> stop() async {
    _serverProcess?.kill();
    _serverProcess = null;
  }

  @override
  Future<String?> addTorrent(String link) async {
    try {
      final uri = Uri.parse('http://127.0.0.1:$_port/torrents');
      final response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'action': 'add',
          'link': link,
          // 'save_path': ... let it use default
        }),
      );

      if (response.statusCode != 200) {
        throw Exception("Failed to add torrent: ${response.body}");
      }

      // TorrServer usually returns the array of requested torrents or just the one added?
      // Or simply the status object.
      // Assuming it returns a JSON where we can find the hash.
      // If the response is a list, take the first.
      // If it's an object, check for 'hash'.
      // Based on common TorrServer Add behavior (MatriX), it often returns the TorrentStatus list.
      // Use dynamic decoding to be safe.
      final decoded = jsonDecode(response.body);
      if (decoded is List && decoded.isNotEmpty) {
        return decoded[0]['hash'] as String?;
      } else if (decoded is Map) {
        return decoded['hash'] as String?;
      }
      return null;
    } catch (e) {
      throw Exception("Add torrent error: $e");
    }
  }

  @override
  Future<Map<String, dynamic>> getTorrentStatus(String hash) async {
    try {
      final uri = Uri.parse('http://127.0.0.1:$_port/torrents');
      final response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'action': 'get', 'hash': hash}),
      );

      if (response.statusCode == 200) {
        return jsonDecode(response.body) as Map<String, dynamic>;
      } else {
        throw Exception("Failed to get status: ${response.statusCode}");
      }
    } catch (e) {
      throw Exception("Get status error: $e");
    }
  }

  String? _getBinaryName() {
    final version = Platform.version.toLowerCase();
    final isArm64 = version.contains("arm64") || version.contains("aarch64");

    if (Platform.isMacOS) {
      return isArm64 ? "TorrServer-darwin-arm64" : "TorrServer-darwin-amd64";
    }
    if (Platform.isLinux) {
      return isArm64 ? "TorrServer-linux-arm64" : "TorrServer-linux-amd64";
    }
    if (Platform.isWindows) {
      return isArm64
          ? "TorrServer-windows-arm64.exe"
          : "TorrServer-windows-amd64.exe";
    }
    return null;
  }
}
