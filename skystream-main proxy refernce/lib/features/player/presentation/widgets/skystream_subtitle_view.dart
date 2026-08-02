import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:media_kit/media_kit.dart';
import 'package:video_view/video_view.dart' as vv;
import 'package:dio/dio.dart';
import 'package:collection/collection.dart';
import '../player_controller.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../../settings/presentation/player_settings_provider.dart';
import 'subtitle_sync_dialog.dart';

TextStyle _getSubtitleTextStyle(String? fontFamily, TextStyle baseStyle) {
  if (fontFamily == null) return baseStyle;
  switch (fontFamily.toLowerCase()) {
    case 'open sans':
      return GoogleFonts.openSans(textStyle: baseStyle);
    case 'poppins':
      return GoogleFonts.poppins(textStyle: baseStyle);
    case 'ubuntu':
      return GoogleFonts.ubuntu(textStyle: baseStyle);
    default:
      return baseStyle.copyWith(fontFamily: fontFamily);
  }
}

class SkyStreamSubtitleView extends ConsumerStatefulWidget {
  final Player player;
  final vv.VideoController? videoViewController;
  final bool useExoPlayer;
  final bool controlsVisible;

  const SkyStreamSubtitleView({
    super.key,
    required this.player,
    this.videoViewController,
    required this.useExoPlayer,
    required this.controlsVisible,
  });

  @override
  ConsumerState<SkyStreamSubtitleView> createState() =>
      _SkyStreamSubtitleViewState();
}

class _SkyStreamSubtitleViewState extends ConsumerState<SkyStreamSubtitleView> {
  List<SubtitleCue> _cues = [];
  String? _loadedUrl;
  bool _loading = false;
  StreamSubscription<Duration>? _positionSub;
  Duration _currentPosition = Duration.zero;
  bool _customFontLoaded = false;

  @override
  void initState() {
    super.initState();
    _listenPosition();
    _loadCues();
    _loadCustomFontIfNeeded();
  }

  @override
  void didUpdateWidget(covariant SkyStreamSubtitleView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.useExoPlayer != widget.useExoPlayer ||
        oldWidget.videoViewController != widget.videoViewController) {
      _cleanupPositionListener();
      _listenPosition();
    }
    _loadCues();
    _loadCustomFontIfNeeded();
  }

  void _cleanupPositionListener() {
    _positionSub?.cancel();
    _positionSub = null;
    try {
      widget.videoViewController?.position.removeListener(
        _onExoPositionChanged,
      );
    } catch (_) {}
  }

  void _listenPosition() {
    if (widget.useExoPlayer && widget.videoViewController != null) {
      widget.videoViewController!.position.addListener(_onExoPositionChanged);
      _currentPosition = Duration(
        milliseconds: widget.videoViewController!.position.value,
      );
    } else {
      _positionSub = widget.player.stream.position.listen((pos) {
        if (mounted) {
          setState(() {
            _currentPosition = pos;
          });
        }
      });
    }
  }

  void _onExoPositionChanged() {
    if (mounted && widget.videoViewController != null) {
      setState(() {
        _currentPosition = Duration(
          milliseconds: widget.videoViewController!.position.value,
        );
      });
    }
  }

  @override
  void dispose() {
    _cleanupPositionListener();
    super.dispose();
  }

  String? _getSubtitleUrl() {
    final playerState = ref.read(playerControllerProvider);
    final id = widget.useExoPlayer
        ? widget.videoViewController?.overrideSubtitle.value
        : widget.player.state.track.subtitle.id;

    if (id == null || id == 'no' || id == 'auto') return null;

    if (id.startsWith('external:')) {
      return id.substring('external:'.length);
    }
    if (id.startsWith('http://') ||
        id.startsWith('https://') ||
        id.startsWith('file://')) {
      return id;
    }

    for (final sub in playerState.externalSubtitles) {
      if (id.contains(sub.url) || sub.url.contains(id)) {
        return sub.url;
      }
    }

    final cleanTrackLabel = widget.useExoPlayer
        ? ""
        : (widget.player.state.track.subtitle.title?.toLowerCase() ?? "");
    for (final sub in playerState.externalSubtitles) {
      final cleanSubLabel = sub.label.toLowerCase();
      if (cleanTrackLabel.contains(cleanSubLabel) ||
          cleanSubLabel.contains(cleanTrackLabel)) {
        return sub.url;
      }
    }

    if (playerState.externalSubtitles.isNotEmpty) {
      if (playerState.externalSubtitles.length == 1) {
        return playerState.externalSubtitles.first.url;
      }
      if (cleanTrackLabel.contains('external') ||
          cleanTrackLabel.contains('srt') ||
          cleanTrackLabel.contains('vtt')) {
        return playerState.externalSubtitles.first.url;
      }
    }

    return null;
  }

  Future<void> _loadCustomFontIfNeeded() async {
    final settings = ref.read(playerSettingsProvider).value;
    if (settings == null) return;

    final path = settings.subTypefaceFilePath;
    if (path != null && path.isNotEmpty && !_customFontLoaded) {
      try {
        final file = File(path);
        if (await file.exists()) {
          final bytes = await file.readAsBytes();
          final fontLoader = FontLoader('CustomSubtitleFont');
          fontLoader.addFont(Future.value(ByteData.sublistView(bytes)));
          await fontLoader.load();
          if (mounted) {
            setState(() {
              _customFontLoaded = true;
            });
          }
        }
      } catch (e) {
        debugPrint("Failed to load custom font: $e");
      }
    }
  }

  Future<void> _loadCues() async {
    final url = _getSubtitleUrl();
    if (url == null) {
      if (_loadedUrl != null || _cues.isNotEmpty) {
        setState(() {
          _cues = [];
          _loadedUrl = null;
        });
      }
      return;
    }

    final settings =
        ref.read(playerSettingsProvider).value ?? const PlayerSettings();
    final cacheKey =
        "${url}_${settings.subRemoveBloat}_${settings.subRemoveCaptions}_${settings.subUpperCase}";

    if (cacheKey == _loadedUrl && !_loading) return;

    _loadedUrl = cacheKey;
    _loading = true;

    try {
      String content = '';
      if (url.startsWith('http://') || url.startsWith('https://')) {
        final response = await Dio().get<String>(
          url,
          options: Options(responseType: ResponseType.plain),
        );
        content = response.data ?? '';
      } else {
        var filePath = url;
        if (filePath.startsWith('file://')) {
          filePath = Uri.parse(filePath).toFilePath();
        }
        final file = File(filePath);
        if (await file.exists()) {
          content = await file.readAsString();
        }
      }

      final parsed = _parseAndCleanSubtitles(
        content,
        removeBloat: settings.subRemoveBloat,
        removeCaptions: settings.subRemoveCaptions,
        upperCase: settings.subUpperCase,
      );

      if (mounted && cacheKey == _loadedUrl) {
        setState(() {
          _cues = parsed;
          _loading = false;
        });
      }
    } catch (e) {
      debugPrint("Failed to load/parse subtitle cues: $e");
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  List<SubtitleCue> _parseAndCleanSubtitles(
    String content, {
    required bool removeBloat,
    required bool removeCaptions,
    required bool upperCase,
  }) {
    final bloatRegex = [
      RegExp(r'opensubtitles', caseSensitive: false),
      RegExp(r'www\.opensubtitles\.(org|com|net)', caseSensitive: false),
      RegExp(r'subtitles by', caseSensitive: false),
      RegExp(r'translated by', caseSensitive: false),
      RegExp(r'corrected by', caseSensitive: false),
      RegExp(r'sync(hronized)? by', caseSensitive: false),
      RegExp(r'support us', caseSensitive: false),
      RegExp(r'advertise your product', caseSensitive: false),
      RegExp(r'visit www', caseSensitive: false),
      RegExp(r'opensubtitles\.org', caseSensitive: false),
    ];

    final captionsRegex = RegExp(r'(-?\s*)[\[({][^\])}]*[\])}]\s*');

    final List<SubtitleCue> cues = [];
    final lines = content.replaceAll('\r\n', '\n').split('\n');

    Duration? parseTimestamp(String timestamp) {
      try {
        final cleaned = timestamp.trim().replaceAll(',', '.');
        final match = RegExp(
          r'(?:(\d+):)?(\d+):(\d+)(?:\.(\d+))?',
        ).firstMatch(cleaned);
        if (match != null) {
          final hoursStr = match.group(1);
          final minutesStr = match.group(2);
          final secondsStr = match.group(3);
          final millisecondsStr = match.group(4);

          final hours = hoursStr != null ? int.parse(hoursStr) : 0;
          final minutes = int.parse(minutesStr!);
          final seconds = int.parse(secondsStr!);

          int milliseconds = 0;
          if (millisecondsStr != null) {
            String msStr = millisecondsStr;
            if (msStr.length > 3) {
              msStr = msStr.substring(0, 3);
            }
            while (msStr.length < 3) {
              msStr += '0';
            }
            milliseconds = int.parse(msStr);
          }

          return Duration(
            hours: hours,
            minutes: minutes,
            seconds: seconds,
            milliseconds: milliseconds,
          );
        }
      } catch (_) {}
      return null;
    }

    int i = 0;
    while (i < lines.length) {
      final line = lines[i].trim();
      if (line.isEmpty) {
        i++;
        continue;
      }

      if (line.contains('-->')) {
        final parts = line.split('-->');
        if (parts.length >= 2) {
          final startStr = parts[0].trim();
          final endStr = parts[1].trim().split(RegExp(r'\s+'))[0].trim();

          final start = parseTimestamp(startStr);
          final end = parseTimestamp(endStr);

          if (start != null && end != null) {
            final List<String> textLines = [];
            i++;
            while (i < lines.length) {
              final textLine = lines[i].trim();
              if (textLine.isEmpty) {
                break;
              }
              if (textLine.contains('-->')) {
                i--; // Rewind
                break;
              }
              var cleaned = textLine.replaceAll(RegExp(r'<[^>]*>'), '');

              if (removeBloat) {
                bool isBloat = false;
                for (final regex in bloatRegex) {
                  if (regex.hasMatch(cleaned)) {
                    isBloat = true;
                    break;
                  }
                }
                if (isBloat) {
                  cleaned = "";
                }
              }

              if (cleaned.isNotEmpty) {
                textLines.add(cleaned);
              }
              i++;
            }
            if (textLines.isNotEmpty) {
              final durationMs = end.inMilliseconds - start.inMilliseconds;

              final processedLines = textLines
                  .map((textLine) {
                    var processed = textLine;
                    if (removeCaptions) {
                      processed = processed.replaceAll(captionsRegex, "");
                    }
                    if (upperCase) {
                      processed = processed.toUpperCase();
                    }
                    return processed.trim();
                  })
                  .where((line) => line.isNotEmpty)
                  .toList();

              if (processedLines.isNotEmpty) {
                cues.add(
                  SubtitleCue(
                    startTimeMs: start.inMilliseconds,
                    durationMs: durationMs > 0 ? durationMs : 1000,
                    text: processedLines,
                  ),
                );
              }
            }
          }
        }
      }
      i++;
    }
    return cues;
  }

  @override
  Widget build(BuildContext context) {
    final url = _getSubtitleUrl();
    if (url == null || _cues.isEmpty) return const SizedBox.shrink();

    final settings =
        ref.watch(playerSettingsProvider).value ?? const PlayerSettings();

    // Calculate position with sync delay (in seconds)
    final delayMs =
        (ref.watch(playerControllerProvider.select((s) => s.subtitleDelay)) *
                1000)
            .round();
    final adjustedPositionMs = _currentPosition.inMilliseconds + delayMs;

    final activeCue = _cues.firstWhereOrNull((cue) {
      final start = cue.startTimeMs;
      final end = cue.startTimeMs + cue.durationMs;
      return adjustedPositionMs >= start && adjustedPositionMs <= end;
    });

    if (activeCue == null) return const SizedBox.shrink();

    // Map font family
    String? fontFamily;
    const List<String> builtInFonts = [
      'Normal (system sans-serif)',
      'Trebuchet MS',
      'Netflix Sans',
      'Google Sans',
      'Open Sans',
      'Futura',
      'Consola',
      'Gotham',
      'Lucida Grande',
      'STIX General',
      'Times New Roman',
      'Verdana',
      'Ubuntu',
      'Comic Sans',
      'Poppins',
    ];

    if (settings.subTypefaceFilePath != null && _customFontLoaded) {
      fontFamily = 'CustomSubtitleFont';
    } else if (settings.subTypeface != null &&
        settings.subTypeface! >= 0 &&
        settings.subTypeface! < builtInFonts.length) {
      if (settings.subTypeface == 0) {
        fontFamily = null;
      } else {
        fontFamily = builtInFonts[settings.subTypeface!];
      }
    }

    final fontSize = settings.subFixedTextSize ?? 22.0;

    final baseStyle = TextStyle(
      fontSize: fontSize,
      fontWeight: settings.subBold ? FontWeight.bold : FontWeight.normal,
      fontStyle: settings.subItalic ? FontStyle.italic : FontStyle.normal,
      color: Color(settings.subForegroundColor),
    );

    final textStyle = _getSubtitleTextStyle(fontFamily, baseStyle);

    final edgeColor = Color(settings.subEdgeColor);

    final alignmentCode = settings.subAlignment ?? 2;
    final alignment = switch (alignmentCode) {
      1 => Alignment.bottomLeft,
      3 => Alignment.bottomRight,
      4 => Alignment.centerLeft,
      5 => Alignment.center,
      6 => Alignment.centerRight,
      7 => Alignment.topLeft,
      8 => Alignment.topCenter,
      9 => Alignment.topRight,
      _ => Alignment.bottomCenter, // 2
    };

    final crossAxisAlignment = switch (alignmentCode) {
      1 || 4 || 7 => CrossAxisAlignment.start,
      3 || 6 || 9 => CrossAxisAlignment.end,
      _ => CrossAxisAlignment.center,
    };

    final textAlign = switch (alignmentCode) {
      1 || 4 || 7 => TextAlign.left,
      3 || 6 || 9 => TextAlign.right,
      _ => TextAlign.center,
    };

    Widget buildTextLine(String line) {
      final List<Widget> children = [];

      // Edge type outline
      if (settings.subEdgeType == 1) {
        children.add(
          Text(
            line,
            style: textStyle.copyWith(
              color: null,
              foreground: Paint()
                ..style = PaintingStyle.stroke
                ..strokeWidth = settings.subEdgeSize ?? 2.0
                ..color = edgeColor,
            ),
            textAlign: textAlign,
          ),
        );
      }

      List<Shadow>? shadows;
      if (settings.subEdgeType == 2) {
        shadows = [
          Shadow(
            offset: const Offset(-1, -1),
            color: edgeColor.withValues(alpha: 0.5),
          ),
          Shadow(
            offset: const Offset(1, 1),
            color: Colors.white.withValues(alpha: 0.5),
          ),
        ];
      } else if (settings.subEdgeType == 3) {
        shadows = [
          Shadow(offset: const Offset(2, 2), blurRadius: 2.0, color: edgeColor),
        ];
      } else if (settings.subEdgeType == 4) {
        shadows = [
          Shadow(offset: const Offset(1, 1), color: edgeColor),
          Shadow(
            offset: const Offset(2, 2),
            color: edgeColor.withValues(alpha: 0.5),
          ),
        ];
      }

      children.add(
        Text(
          line,
          style: textStyle.copyWith(shadows: shadows),
          textAlign: textAlign,
        ),
      );

      Widget resultLine = Stack(children: children);

      final bgColor = Color(settings.subBackgroundColor);
      if (bgColor.a > 0 && settings.subBackgroundOpacity > 0) {
        final paddingVal = 2.0 + (settings.subBackgroundRadius ?? 0.0) * 0.5;
        resultLine = Container(
          padding: EdgeInsets.symmetric(horizontal: paddingVal, vertical: 2.0),
          decoration: BoxDecoration(
            color: bgColor.withValues(alpha: settings.subBackgroundOpacity),
            borderRadius: settings.subBackgroundRadius != null
                ? BorderRadius.circular(settings.subBackgroundRadius!)
                : BorderRadius.zero,
          ),
          child: resultLine,
        );
      }

      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 2.0),
        child: resultLine,
      );
    }

    return Positioned.fill(
      child: SafeArea(
        top: alignment.y < 0,
        bottom: alignment.y > 0,
        child: Padding(
          padding: EdgeInsets.only(
            left: 20.0,
            right: 20.0,
            top: 0.0,
            bottom: alignment.y > 0
                ? (widget.useExoPlayer
                      ? 20.0
                      : (widget.controlsVisible ? 60.0 : 20.0))
                : 0.0,
          ),
          child: Align(
            alignment: alignment,
            child: Transform.translate(
              offset: Offset(
                0.0,
                alignment.y >= 0
                    ? -settings.subElevation.toDouble()
                    : settings.subElevation.toDouble(),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: crossAxisAlignment,
                children: activeCue.text.map(buildTextLine).toList(),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
