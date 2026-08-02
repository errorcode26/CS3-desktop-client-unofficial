import 'package:talker_flutter/talker_flutter.dart';
import 'package:flutter/foundation.dart';

/// Global Talker instance for logging across the app.
final talker = TalkerFlutter.init(
  settings: TalkerSettings(enabled: kDebugMode),
);
