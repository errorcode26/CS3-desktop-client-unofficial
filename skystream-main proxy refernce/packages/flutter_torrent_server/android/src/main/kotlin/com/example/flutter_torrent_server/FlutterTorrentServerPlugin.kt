package com.example.flutter_torrent_server

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import torrServer.TorrServer
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** FlutterTorrentServerPlugin */
class FlutterTorrentServerPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var cacheDir: String
    private var serverPort: Long = 0

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_torrent_server")
        channel.setMethodCallHandler(this)
        cacheDir = flutterPluginBinding.applicationContext.cacheDir.absolutePath + "/torrent_tmp"
        File(cacheDir).mkdirs()
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "start" -> {
                thread {
                    try {
                        if (serverPort == 0L) {
                            TorrServer.startTorrentServer(cacheDir)
                            serverPort = 8090 // Default port for this AAR version apparently
                        }
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            result.success(serverPort)
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            result.error("START_ERROR", e.message, null)
                        }
                    }
                }
            }
            "stop" -> {
                // TorrServer currently doesn't have a clean stop exposed easily or it crashes, 
                // but we can try just clearing referencing.
                // For now, doing nothing is safer as per Android app comments.
                result.success(null)
            }
            "addTorrent" -> {
                val link = call.argument<String>("link")
                if (link == null) {
                    result.error("INVALID_ARGS", "Link is null", null)
                    return
                }

                thread {
                    try {
                        val url = URL("http://127.0.0.1:$serverPort/torrents")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")

                        val jsonBody = JSONObject()
                        jsonBody.put("action", "add")
                        jsonBody.put("link", link)
                        // Android app sets save_path to cacheDir temporarily? Or lets it handle it.
                        // We will let default behavior take over or specify if needed.
                        
                        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

                        val responseCode = conn.responseCode
                        if (responseCode == 200) {
                             val response = conn.inputStream.bufferedReader().use { it.readText() }
                             // Response is usually the hash or json with hash?
                             // TorrServer Add returns the TorrentStatus usually or just hash?
                             // Let's assume the response is the TorrentStatus JSON.
                             // We need to extract the hash.
                             val jsonResponse = JSONObject(response)
                             val hash = jsonResponse.optString("hash")
                             
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.success(hash)
                             }
                        } else {
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.error("ADD_ERROR", "Server returned $responseCode", null)
                             }
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            result.error("ADD_ERROR", e.message, null)
                        }
                    }
                }
            }
            "getTorrentStatus" -> {
                val hash = call.argument<String>("hash")
                if (hash == null) {
                    result.error("INVALID_ARGS", "Hash is null", null)
                    return
                }

                thread {
                    try {
                        val url = URL("http://127.0.0.1:$serverPort/torrents")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")

                        val jsonBody = JSONObject()
                        jsonBody.put("action", "get") 
                        jsonBody.put("hash", hash)
                        
                        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

                        if (conn.responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().use { it.readText() }
                            // Map JSON to Map for Flutter
                            val jsonObject = JSONObject(response)
                            val map = jsonToMap(jsonObject)
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.success(map)
                            }
                        } else {
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.error("STATUS_ERROR", "Server returned ${conn.responseCode}", null)
                             }
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            result.error("STATUS_ERROR", e.message, null)
                        }
                    }
                }
            }
            "getPlatformVersion" -> {
                 result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            if (value is JSONObject) {
                map[key] = jsonToMap(value)
            } else if (value is org.json.JSONArray) {
                map[key] = jsonArrayToList(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonArrayToList(array: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            if (value is JSONObject) {
                list.add(jsonToMap(value))
            } else if (value is org.json.JSONArray) {
                list.add(jsonArrayToList(value))
            } else {
                list.add(value)
            }
        }
        return list
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
