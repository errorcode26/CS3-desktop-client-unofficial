#ifndef FLUTTER_PLUGIN_FLUTTER_TORRENT_SERVER_PLUGIN_H_
#define FLUTTER_PLUGIN_FLUTTER_TORRENT_SERVER_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace flutter_torrent_server {

class FlutterTorrentServerPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  FlutterTorrentServerPlugin();

  virtual ~FlutterTorrentServerPlugin();

  // Disallow copy and assign.
  FlutterTorrentServerPlugin(const FlutterTorrentServerPlugin&) = delete;
  FlutterTorrentServerPlugin& operator=(const FlutterTorrentServerPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace flutter_torrent_server

#endif  // FLUTTER_PLUGIN_FLUTTER_TORRENT_SERVER_PLUGIN_H_
