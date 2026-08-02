import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:collection/collection.dart';
import '../player_controller.dart';
import 'hotstar_player_style.dart';

class SubtitleCue {
  final int startTimeMs;
  final int durationMs;
  final List<String> text;

  SubtitleCue({
    required this.startTimeMs,
    required this.durationMs,
    required this.text,
  });

  int get endTimeMs => startTimeMs + durationMs;
  Duration get startTime => Duration(milliseconds: startTimeMs);
  Duration get endTime => Duration(milliseconds: endTimeMs);
  Duration get duration => Duration(milliseconds: durationMs);
}

/// Parses SubRip (.srt) and WebVTT (.vtt) file content into subtitle cues.
List<SubtitleCue> parseSubtitle(String content) {
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
              i--; // Rewind to process timestamp
              break;
            }
            // Remove HTML/VTT formatting tags
            final cleaned = textLine.replaceAll(RegExp(r'<[^>]*>'), '');
            if (cleaned.isNotEmpty) {
              textLines.add(cleaned);
            }
            i++;
          }
          if (textLines.isNotEmpty) {
            final durationMs = end.inMilliseconds - start.inMilliseconds;
            cues.add(
              SubtitleCue(
                startTimeMs: start.inMilliseconds,
                durationMs: durationMs > 0 ? durationMs : 1000,
                text: textLines,
              ),
            );
          }
        }
      }
    }
    i++;
  }
  return cues;
}

class SubtitleSyncDialog extends ConsumerStatefulWidget {
  final bool wasPlaying;

  const SubtitleSyncDialog({super.key, required this.wasPlaying});

  @override
  ConsumerState<SubtitleSyncDialog> createState() => _SubtitleSyncDialogState();
}

class _SubtitleSyncDialogState extends ConsumerState<SubtitleSyncDialog> {
  List<SubtitleCue> _cues = [];
  bool _loadingCues = false;
  String? _errorMessage;

  int _offsetMs = 0;
  late final TextEditingController _inputController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  // Explicit FocusNodes for stepper & action buttons to route TV/D-pad focus manually and safely
  final FocusNode _subtractMoreFocusNode = FocusNode();
  final FocusNode _subtractFocusNode = FocusNode();
  final FocusNode _inputFocusNode = FocusNode();
  final FocusNode _addFocusNode = FocusNode();
  final FocusNode _addMoreFocusNode = FocusNode();
  final FocusNode _applyFocusNode = FocusNode();
  final FocusNode _resetFocusNode = FocusNode();
  final FocusNode _cancelFocusNode = FocusNode();

  bool _keyboardActive = false;

  // ValueNotifier is used for efficient partial list updates (only visible/changed items rebuild)
  final ValueNotifier<Duration> _timeNotifier = ValueNotifier<Duration>(
    Duration.zero,
  );
  Duration _pausedPosition = Duration.zero;
  String _hintText = "No subtitle delay";

  @override
  void initState() {
    super.initState();

    final controller = ref.read(playerControllerProvider.notifier);
    final playerState = ref.read(playerControllerProvider);

    // Pause player because the subtitles cannot be continuously updated to follow playback
    // While the dialog is open, the player freezes so the user can inspect individual subtitle cues and work out the offset.
    if (widget.wasPlaying) {
      unawaited(controller.pause());
    }

    // Determine player paused position safely without throwing LateInitializationError
    Duration currentPos = Duration.zero;
    try {
      if (playerState.useExoPlayer) {
        currentPos = Duration(
          milliseconds: controller.videoViewController?.position.value ?? 0,
        );
      } else {
        currentPos = controller.player.state.position;
      }
    } catch (e) {
      debugPrint("[SubtitleSync] Error getting player position: $e");
    }
    _pausedPosition = currentPos;

    // Negation is applied: user-facing offset (ms) is -subtitleDelay (seconds) * 1000
    _offsetMs = (-playerState.subtitleDelay * 1000).round();
    _inputController.text = _offsetMs.toString();

    // Initialize alignment position: player position minus the existing offset
    final initialSubtitlePosition = _pausedPosition.inMilliseconds - _offsetMs;
    _timeNotifier.value = Duration(milliseconds: initialSubtitlePosition);

    _updateHintText();

    _inputFocusNode.addListener(() {
      if (!_inputFocusNode.hasFocus && mounted) {
        setState(() {
          _keyboardActive = false;
        });
      }
    });

    // Explicit D-pad navigation key routing
    KeyEventResult handleFocusTraversal(
      KeyEvent event, {
      FocusNode? left,
      FocusNode? right,
      FocusNode? up,
      FocusNode? down,
      VoidCallback? onSelect,
    }) {
      if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
        return KeyEventResult.ignored;
      }
      final key = event.logicalKey;
      if (key == LogicalKeyboardKey.arrowLeft && left != null) {
        left.requestFocus();
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowRight && right != null) {
        right.requestFocus();
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowUp && up != null) {
        up.requestFocus();
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowDown && down != null) {
        down.requestFocus();
        return KeyEventResult.handled;
      }
      if ((key == LogicalKeyboardKey.select ||
              key == LogicalKeyboardKey.enter) &&
          onSelect != null) {
        onSelect();
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }

    _subtractMoreFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      right: _subtractFocusNode,
      down: _applyFocusNode,
      onSelect: () => _changeBy(-1000),
    );

    _subtractFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      left: _subtractMoreFocusNode,
      right: _inputFocusNode,
      down: _applyFocusNode,
      onSelect: () => _changeBy(-100),
    );

    _inputFocusNode.onKeyEvent = (node, event) {
      if (!_keyboardActive) {
        return handleFocusTraversal(
          event,
          left: _subtractFocusNode,
          right: _addFocusNode,
          down: _applyFocusNode,
          onSelect: () => setState(() => _keyboardActive = true),
        );
      }
      return KeyEventResult.ignored;
    };

    _addFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      left: _inputFocusNode,
      right: _addMoreFocusNode,
      down: _applyFocusNode,
      onSelect: () => _changeBy(100),
    );

    _addMoreFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      left: _addFocusNode,
      down: _applyFocusNode,
      onSelect: () => _changeBy(1000),
    );

    _applyFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      right: _resetFocusNode,
      up: _inputFocusNode,
      onSelect: _applyAndDismiss,
    );

    _resetFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      left: _applyFocusNode,
      right: _cancelFocusNode,
      up: _inputFocusNode,
      onSelect: _resetAndDismiss,
    );

    _cancelFocusNode.onKeyEvent = (node, event) => handleFocusTraversal(
      event,
      left: _resetFocusNode,
      up: _inputFocusNode,
      onSelect: _exitDialog,
    );

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadSubtitles();
    });
  }

  @override
  void dispose() {
    _inputController.dispose();
    _scrollController.dispose();
    _timeNotifier.dispose();
    _subtractMoreFocusNode.dispose();
    _subtractFocusNode.dispose();
    _inputFocusNode.dispose();
    _addFocusNode.dispose();
    _addMoreFocusNode.dispose();
    _applyFocusNode.dispose();
    _resetFocusNode.dispose();
    _cancelFocusNode.dispose();
    super.dispose();
  }

  String? _getSubtitleUrl() {
    final controller = ref.read(playerControllerProvider.notifier);
    final playerState = ref.read(playerControllerProvider);
    final snapshot = controller.getTrackSelectionSnapshot();
    if (snapshot.subtitlesOffSelected) return null;
    final selectedTrack = snapshot.subtitleTracks.firstWhereOrNull(
      (t) => t.selected,
    );
    if (selectedTrack == null) return null;
    final id = selectedTrack.id;

    // 1. Direct ID formats (media_kit / online loaded tracks)
    if (id.startsWith('external:')) {
      return id.substring('external:'.length);
    }
    if (id.startsWith('http://') ||
        id.startsWith('https://') ||
        id.startsWith('file://')) {
      return id;
    }

    // 2. Match by URL containing ID or vice-versa
    for (final sub in playerState.externalSubtitles) {
      if (id.contains(sub.url) || sub.url.contains(id)) {
        return sub.url;
      }
    }

    // 3. Match by label similarity
    final cleanTrackLabel = selectedTrack.label.toLowerCase();
    for (final sub in playerState.externalSubtitles) {
      final cleanSubLabel = sub.label.toLowerCase();
      if (cleanTrackLabel.contains(cleanSubLabel) ||
          cleanSubLabel.contains(cleanTrackLabel)) {
        return sub.url;
      }
    }

    // 4. Fallback: If only one external subtitle is active in state and we selected a subtitle
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

  Future<void> _loadSubtitles() async {
    final subtitleUrl = _getSubtitleUrl();
    if (subtitleUrl == null) {
      setState(() {
        _cues = [];
        _errorMessage = "No subtitles loaded yet";
      });
      return;
    }

    setState(() {
      _loadingCues = true;
      _errorMessage = null;
    });

    try {
      String content = '';
      if (subtitleUrl.startsWith('http://') ||
          subtitleUrl.startsWith('https://')) {
        final response = await Dio().get<String>(
          subtitleUrl,
          options: Options(responseType: ResponseType.plain),
        );
        content = response.data ?? '';
      } else {
        String filePath = subtitleUrl;
        if (filePath.startsWith('file://')) {
          filePath = Uri.parse(filePath).toFilePath();
        }
        final file = File(filePath);
        if (await file.exists()) {
          content = await file.readAsString();
        } else {
          throw Exception("File not found at path: $filePath");
        }
      }

      final parsed = parseSubtitle(content);
      setState(() {
        _cues = parsed;
        _loadingCues = false;
      });

      // Auto-scroll to first active cue on open
      final initialSubtitlePosition =
          _pausedPosition.inMilliseconds - _offsetMs;
      final firstSubtitleIndex = _getLatestActiveItem(
        Duration(milliseconds: initialSubtitlePosition),
      );
      _scrollToPosition(firstSubtitleIndex);
    } catch (e) {
      setState(() {
        _loadingCues = false;
        _errorMessage = "Failed to load subtitle file cues: $e";
      });
    }
  }

  int _getLatestActiveItem(Duration position) {
    final posMs = position.inMilliseconds;
    final idx = _cues.lastIndexWhere((cue) => posMs >= cue.startTimeMs);
    return idx != -1 ? idx : 0;
  }

  void _scrollToPosition(int index) {
    if (_cues.isEmpty) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          (index * 80.0) - 100.0,
          duration: const Duration(milliseconds: 200),
          curve: Curves.decelerate,
        );
      }
    });
  }

  void _onOffsetChanged(int newOffsetMs) {
    _offsetMs = newOffsetMs;

    // Live update position: player position minus the existing offset
    final totalPositionMs = _pausedPosition.inMilliseconds - _offsetMs;
    _timeNotifier.value = Duration(milliseconds: totalPositionMs);

    // Auto-scroll to position on change
    final activeIndex = _getLatestActiveItem(
      Duration(milliseconds: totalPositionMs),
    );
    _scrollToPosition(activeIndex);

    _updateHintText();
  }

  void _updateOffset(int newValue) {
    setState(() {
      _offsetMs = newValue;
      _inputController.text = _offsetMs.toString();
    });
    _onOffsetChanged(_offsetMs);
  }

  void _changeBy(int amount) {
    _updateOffset(_offsetMs + amount);
  }

  void _updateHintText() {
    setState(() {
      if (_offsetMs > 0) {
        _hintText =
            "Use this if the subtitles are shown $_offsetMs ms too early";
      } else if (_offsetMs < 0) {
        _hintText = "Use this if subtitles are shown ${-_offsetMs} ms too late";
      } else {
        _hintText = "No subtitle delay";
      }
    });
  }

  void _applyAndDismiss() {
    final controller = ref.read(playerControllerProvider.notifier);
    final delaySeconds = -_offsetMs / 1000.0;
    unawaited(controller.setSubtitleDelay(delaySeconds));
    unawaited(controller.seekRelative(const Duration(milliseconds: 1)));
    _exitDialog();
  }

  void _resetAndDismiss() {
    final controller = ref.read(playerControllerProvider.notifier);
    unawaited(controller.setSubtitleDelay(0.0));
    unawaited(controller.seekRelative(const Duration(milliseconds: 1)));
    _exitDialog();
  }

  void _exitDialog() {
    Navigator.pop(context);
    if (widget.wasPlaying) {
      unawaited(ref.read(playerControllerProvider.notifier).play());
    }
  }

  Widget _buildLeftPanel() {
    if (_loadingCues) {
      return const Center(
        child: CircularProgressIndicator(color: HotstarPlayerStyle.accent),
      );
    }

    final hasNoCues = _errorMessage != null || _cues.isEmpty;

    return Container(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: [
          // Subtitle list view
          if (!hasNoCues)
            ListView.builder(
              controller: _scrollController,
              itemCount: _cues.length,
              itemExtent: 80.0,
              itemBuilder: (context, index) {
                final cue = _cues[index];
                return SubtitleCueRow(
                  cue: cue,
                  timeNotifier: _timeNotifier,
                  onTap: () {
                    // Calculate required offset: pausedPosition - cueStartTime
                    final calculatedOffset =
                        _pausedPosition.inMilliseconds - cue.startTimeMs;
                    _updateOffset(calculatedOffset);
                  },
                );
              },
            ),

          // No subtitles loaded notice
          if (hasNoCues)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  _errorMessage ?? "No subtitles loaded yet",
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.7),
                    fontSize: 18,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildRightPanel() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Scrollable top content centered vertically in the middle of the available screen height
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                return SingleChildScrollView(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight: constraints.maxHeight,
                    ),
                    child: IntrinsicHeight(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Title (centered text)
                          const Center(
                            child: Text(
                              "Subtitle delay",
                              style: TextStyle(
                                color: HotstarPlayerStyle.primaryText,
                                fontSize: 24,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(height: 8),

                          // Subtitle hint text (centered text)
                          Center(
                            child: Text(
                              _hintText,
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: Colors.white.withValues(alpha: 0.7),
                                fontSize: 14,
                              ),
                            ),
                          ),
                          const SizedBox(height: 32),

                          // Stepper row: [<<]  [-]  [EditText]  [+]  [>>]
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              // subtitle_offset_subtract_more (<<, -1000ms)
                              IconButton(
                                focusNode: _subtractMoreFocusNode,
                                icon: const Icon(
                                  Icons.keyboard_double_arrow_left_rounded,
                                ),
                                color: Colors.white,
                                iconSize: 28,
                                onPressed: () => _changeBy(-1000),
                                tooltip: "-1000 ms",
                              ),
                              // subtitle_offset_subtract (-, -100ms)
                              IconButton(
                                focusNode: _subtractFocusNode,
                                icon: const Icon(Icons.remove_rounded),
                                color: Colors.white,
                                iconSize: 28,
                                onPressed: () => _changeBy(-100),
                                tooltip: "-100 ms",
                              ),
                              const SizedBox(width: 8),

                              // subtitle_offset_input (EditText)
                              SizedBox(
                                width: 100,
                                child: TextField(
                                  focusNode: _inputFocusNode,
                                  controller: _inputController,
                                  readOnly: !_keyboardActive,
                                  showCursor: true,
                                  keyboardType:
                                      const TextInputType.numberWithOptions(
                                        signed: true,
                                        decimal: false,
                                      ),
                                  inputFormatters: [
                                    FilteringTextInputFormatter.allow(
                                      RegExp(r'^-?\d*'),
                                    ),
                                  ],
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 20,
                                    fontWeight: FontWeight.bold,
                                  ),
                                  decoration: const InputDecoration(
                                    hintText: "1000 ms",
                                    hintStyle: TextStyle(color: Colors.white30),
                                    contentPadding: EdgeInsets.symmetric(
                                      vertical: 8,
                                    ),
                                    enabledBorder: UnderlineInputBorder(
                                      borderSide: BorderSide(
                                        color: Colors.white38,
                                      ),
                                    ),
                                    focusedBorder: UnderlineInputBorder(
                                      borderSide: BorderSide(
                                        color: HotstarPlayerStyle.accent,
                                      ),
                                    ),
                                  ),
                                  onTap: () {
                                    setState(() {
                                      _keyboardActive = true;
                                    });
                                  },
                                  onSubmitted: (_) {
                                    setState(() {
                                      _keyboardActive = false;
                                    });
                                  },
                                  onChanged: (val) {
                                    final parsed = int.tryParse(val) ?? 0;
                                    _offsetMs = parsed;
                                    _onOffsetChanged(_offsetMs);
                                  },
                                ),
                              ),
                              const SizedBox(width: 8),

                              // subtitle_offset_add (+, +100ms)
                              IconButton(
                                focusNode: _addFocusNode,
                                icon: const Icon(Icons.add_rounded),
                                color: Colors.white,
                                iconSize: 28,
                                onPressed: () => _changeBy(100),
                                tooltip: "+100 ms",
                              ),
                              // subtitle_offset_add_more (>>, +1000ms)
                              IconButton(
                                focusNode: _addMoreFocusNode,
                                icon: const Icon(
                                  Icons.keyboard_double_arrow_right_rounded,
                                ),
                                color: Colors.white,
                                iconSize: 28,
                                onPressed: () => _changeBy(1000),
                                tooltip: "+1000 ms",
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 24),

          // Pinned bottom Action bar (Apply, Reset, Cancel horizontal row)
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  focusNode: _applyFocusNode,
                  autofocus: true, // Receives initial focus when opening dialog
                  onPressed: _applyAndDismiss,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: HotstarPlayerStyle.accent,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    "Apply",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  focusNode: _resetFocusNode,
                  onPressed: _resetAndDismiss,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white,
                    side: const BorderSide(color: Colors.white38),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    "Reset",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  focusNode: _cancelFocusNode,
                  onPressed: _exitDialog,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white70,
                    side: const BorderSide(color: Colors.white24),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: const Text(
                    "Cancel",
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HotstarPlayerStyle.background,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: _exitDialog,
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: OrientationBuilder(
            builder: (context, orientation) {
              if (orientation == Orientation.landscape) {
                return Row(
                  children: [
                    Expanded(flex: 1, child: _buildLeftPanel()),
                    const SizedBox(width: 24),
                    Expanded(flex: 1, child: _buildRightPanel()),
                  ],
                );
              } else {
                return Column(
                  children: [
                    Expanded(flex: 1, child: _buildLeftPanel()),
                    const SizedBox(height: 16),
                    Expanded(flex: 1, child: _buildRightPanel()),
                  ],
                );
              }
            },
          ),
        ),
      ),
    );
  }
}

class SubtitleCueRow extends StatelessWidget {
  final SubtitleCue cue;
  final ValueNotifier<Duration> timeNotifier;
  final VoidCallback onTap;

  const SubtitleCueRow({
    super.key,
    required this.cue,
    required this.timeNotifier,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Duration>(
      valueListenable: timeNotifier,
      builder: (context, time, child) {
        final timeMs = time.inMilliseconds;
        final startTime = cue.startTimeMs;
        final endTime = cue.endTimeMs;

        final isStarted = timeMs >= startTime;
        final isActive = timeMs >= startTime && timeMs < endTime;

        final double targetAlpha = isStarted ? 1.0 : 0.5;

        double progress = 0.0;
        if (isActive) {
          final duration = cue.durationMs;
          if (duration > 0) {
            progress = (timeMs - startTime) / duration;
            progress = progress.clamp(0.0, 1.0);
          }
        }

        return InkWell(
          onTap: onTap,
          child: AnimatedOpacity(
            opacity: targetAlpha,
            duration: const Duration(milliseconds: 200),
            curve: Curves.decelerate,
            child: Container(
              height: 80.0,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              alignment: Alignment.centerLeft,
              decoration: const BoxDecoration(
                border: Border(
                  bottom: BorderSide(color: Colors.white10, width: 0.5),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Expanded(
                    child: Text(
                      cue.text.join('\n'),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        height: 1.1,
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Visibility(
                    visible: isActive,
                    maintainSize: true,
                    maintainAnimation: true,
                    maintainState: true,
                    child: TweenAnimationBuilder<double>(
                      tween: Tween<double>(begin: 0.0, end: progress),
                      duration: const Duration(milliseconds: 200),
                      curve: Curves.decelerate,
                      builder: (context, val, child) {
                        return LinearProgressIndicator(
                          value: val,
                          minHeight: 4,
                          backgroundColor: Colors.white12,
                          valueColor: const AlwaysStoppedAnimation<Color>(
                            HotstarPlayerStyle.accent,
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
