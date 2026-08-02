#include "include/flutter_torrent_server/flutter_torrent_server_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "flutter_torrent_server_plugin.h"

void FlutterTorrentServerPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  flutter_torrent_server::FlutterTorrentServerPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
