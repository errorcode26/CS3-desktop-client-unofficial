import 'dart:async';
import 'package:flutter/widgets.dart';
import 'package:cached_network_image/cached_network_image.dart';

class ImageUtils {
  /// Resolves the image from the given URL and determines if it is portrait (height >= width).
  /// Returns `true` by default if the URL is empty or the image fails to load.
  static Future<bool> isImagePortrait(String url) {
    if (url.isEmpty) return Future.value(true);

    final completer = Completer<bool>();
    final stream = CachedNetworkImageProvider(
      url,
    ).resolve(const ImageConfiguration());

    late final ImageStreamListener listener;
    listener = ImageStreamListener(
      (ImageInfo info, bool _) {
        if (!completer.isCompleted) {
          // It's portrait if height is greater than or equal to width
          completer.complete(info.image.height >= info.image.width);
        }
        stream.removeListener(listener);
      },
      onError: (dynamic exception, StackTrace? stackTrace) {
        if (!completer.isCompleted) {
          completer.complete(true); // default to portrait on error
        }
        stream.removeListener(listener);
      },
    );

    stream.addListener(listener);
    return completer.future;
  }
}
