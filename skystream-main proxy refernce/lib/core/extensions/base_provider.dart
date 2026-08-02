import 'package:dio/dio.dart';
import '../domain/entity/multimedia_item.dart';

enum ProviderType { movie, series, anime, livestream, other }

abstract class SkyStreamProvider {
  /// Unique Package Name (from plugin.json)
  String get packageName;

  /// Display Name
  String get name;
  String get mainUrl;
  String get version;
  List<String> get languages;
  Set<ProviderType> get supportedTypes;
  bool get hasSearch => true;
  bool get isDebug => packageName.endsWith('.debug');

  /// Cancel any pending JS eval for this provider so the queue isn't blocked
  /// by a stale IIFE load after the triggering search was abandoned.
  /// The provider resets itself so the next search retries cleanly.
  void cancelInit() {}

  // Key methods providers must implement
  Future<List<MultimediaItem>> search(String query, {CancelToken? cancelToken});
  // Returns categorized content (Section Name -> Items)
  Future<Map<String, List<MultimediaItem>>> getHome();
  Future<MultimediaItem> getDetails(String url);

  // Returns list of video streams (urls)
  Future<List<StreamResult>> loadStreams(String url);
}
