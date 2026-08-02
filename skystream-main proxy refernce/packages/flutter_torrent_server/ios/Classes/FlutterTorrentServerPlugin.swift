import Flutter
import UIKit
import TorrServer // Import the generated framework

public class FlutterTorrentServerPlugin: NSObject, FlutterPlugin {
  private var serverPort: Int = 0
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_torrent_server", binaryMessenger: registrar.messenger())
    let instance = FlutterTorrentServerPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "start":
        startServer(result: result)
    case "stop":
        // TorrServer doesn't expose a clean stop in this binding typically.
        // We rely on OS cleanup or simple ignore.
        result(nil)
    case "addTorrent":
        if let args = call.arguments as? [String: Any], let link = args["link"] as? String {
            addTorrent(link: link, result: result)
        } else {
            result(FlutterError(code: "INVALID_ARGS", message: "Link is required", details: nil))
        }
    case "getTorrentStatus":
        if let args = call.arguments as? [String: Any], let hash = args["hash"] as? String {
            getTorrentStatus(hash: hash, result: result)
        } else {
            result(FlutterError(code: "INVALID_ARGS", message: "Hash is required", details: nil))
        }
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func startServer(result: @escaping FlutterResult) {
    DispatchQueue.global(qos: .userInitiated).async {
        if self.serverPort == 0 {
            let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!.path + "/torrent_tmp"
            do {
                try FileManager.default.createDirectory(atPath: cacheDir, withIntermediateDirectories: true, attributes: nil)
                // Call the Go binding: ServerStart(pathdb, port, roSets, searchWA)
                ServerStart(cacheDir, "8090", false, false)
                self.serverPort = 8090 // Keep consistent
                
                DispatchQueue.main.async {
                    result(self.serverPort)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(code: "START_ERROR", message: error.localizedDescription, details: nil))
                }
            }
        } else {
            DispatchQueue.main.async {
                result(self.serverPort)
            }
        }
    }
  }

  private func addTorrent(link: String, result: @escaping FlutterResult) {
      guard let url = URL(string: "http://127.0.0.1:\(serverPort)/torrents") else {
          result(FlutterError(code: "URL_ERROR", message: "Invalid server URL", details: nil))
          return
      }
      
      var request = URLRequest(url: url)
      request.httpMethod = "POST"
      request.setValue("application/json", forHTTPHeaderField: "Content-Type")
      
      let body: [String: Any] = ["action": "add", "link": link]
      request.httpBody = try? JSONSerialization.data(withJSONObject: body)
      
      let task = URLSession.shared.dataTask(with: request) { data, response, error in
          if let error = error {
              DispatchQueue.main.async {
                  result(FlutterError(code: "ADD_ERROR", message: error.localizedDescription, details: nil))
              }
              return
          }
          
          if let data = data {
              do {
                   // Parse response to find hash. Response can be List or Object.
                   // Usually it returns the added torrent status/info.
                   if let jsonArray = try JSONSerialization.jsonObject(with: data, options: []) as? [[String: Any]],
                      let first = jsonArray.first,
                      let hash = first["hash"] as? String {
                       DispatchQueue.main.async { result(hash) }
                   } else if let jsonObject = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                             let hash = jsonObject["hash"] as? String {
                       DispatchQueue.main.async { result(hash) }
                   } else {
                       DispatchQueue.main.async { result(FlutterError(code: "PARSE_ERROR", message: "Could not extract hash", details: String(data: data, encoding: .utf8))) }
                   }
              } catch {
                  DispatchQueue.main.async {
                      result(FlutterError(code: "JSON_ERROR", message: error.localizedDescription, details: nil))
                  }
              }
          }
      }
      task.resume()
  }

  private func getTorrentStatus(hash: String, result: @escaping FlutterResult) {
      guard let url = URL(string: "http://127.0.0.1:\(serverPort)/torrents") else {
          result(FlutterError(code: "URL_ERROR", message: "Invalid server URL", details: nil))
          return
      }
      
      var request = URLRequest(url: url)
      request.httpMethod = "POST"
      request.setValue("application/json", forHTTPHeaderField: "Content-Type")
      
      let body: [String: Any] = ["action": "get", "hash": hash]
      request.httpBody = try? JSONSerialization.data(withJSONObject: body)
      
      let task = URLSession.shared.dataTask(with: request) { data, response, error in
          if let error = error {
              DispatchQueue.main.async {
                  result(FlutterError(code: "STATUS_ERROR", message: error.localizedDescription, details: nil))
              }
              return
          }
          
          if let data = data {
              do {
                   if let jsonObject = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                       DispatchQueue.main.async { result(jsonObject) }
                   } else {
                       DispatchQueue.main.async { result(nil) }
                   }
              } catch {
                   DispatchQueue.main.async { result(nil) }
              }
          }
      }
      task.resume()
  }
}
