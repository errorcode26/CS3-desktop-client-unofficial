import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'flutter_torrent_server_method_channel.dart';

abstract class FlutterTorrentServerPlatform extends PlatformInterface {
  /// Constructs a FlutterTorrentServerPlatform.
  FlutterTorrentServerPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterTorrentServerPlatform _instance =
      MethodChannelFlutterTorrentServer();

  /// The default instance of [FlutterTorrentServerPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterTorrentServer].
  static FlutterTorrentServerPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterTorrentServerPlatform] when
  /// they register themselves.
  static set instance(FlutterTorrentServerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Starts the embedded Torrent Server.
  /// Returns the port number it is listening on, or -1 on error.
  Future<int> start() {
    throw UnimplementedError('start() has not been implemented.');
  }

  /// Stops the embedded Torrent Server.
  Future<void> stop() {
    throw UnimplementedError('stop() has not been implemented.');
  }

  /// Adds a torrent/magnet link to the server.
  /// Returns the hash of the added torrent.
  Future<String?> addTorrent(String link) {
    throw UnimplementedError('addTorrent() has not been implemented.');
  }

  /// Gets the status of a specific torrent by hash.
  Future<Map<String, dynamic>?> getTorrentStatus(String hash) {
    throw UnimplementedError('getTorrentStatus() has not been implemented.');
  }
}
