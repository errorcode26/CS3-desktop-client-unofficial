import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_torrent_server/flutter_torrent_server.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _serverStatus = 'Not Started';
  final _flutterTorrentServerPlugin = FlutterTorrentServer();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String serverStatus;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      final port = await _flutterTorrentServerPlugin.start();
      serverStatus = 'Server running on port: $port';
    } on PlatformException {
      serverStatus = 'Failed to start server.';
    } catch (e) {
      serverStatus = 'Error: $e';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _serverStatus = serverStatus;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Torrent Server Example')),
        body: Center(child: Text('Status: $_serverStatus\n')),
        floatingActionButton: FloatingActionButton(
          onPressed: initPlatformState,
          child: const Icon(Icons.refresh),
        ),
      ),
    );
  }
}
