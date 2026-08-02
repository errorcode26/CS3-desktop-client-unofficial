import 'dart:async';
import 'dart:io';
import 'package:background_downloader/background_downloader.dart';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:collection/collection.dart';
import 'package:permission_handler/permission_handler.dart'
    hide PermissionStatus;
import 'package:device_info_plus/device_info_plus.dart';

import '../domain/entity/multimedia_item.dart';
import '../router/app_router.dart';
import '../storage/storage_service.dart';
import '../network/dio_client_provider.dart';

part 'download_service.g.dart';

@Riverpod(keepAlive: true)
DownloadService downloadService(Ref ref) {
  final service = DownloadService(ref);
  // Cancel the FileDownloader stream subscription when the ProviderScope is
  // disposed (e.g. on app restart). Without this the subscription outlives the
  // scope and the next DownloadService.init() throws "Stream already listened".
  ref.onDispose(service.dispose);
  return service;
}

class DownloadProgressData {
  final String taskId;
  final double progress;
  final double networkSpeed; // MB/s
  final Duration timeRemaining;
  final int totalSize; // Bytes
  final TaskStatus status;

  DownloadProgressData({
    required this.taskId,
    required double progress,
    required this.networkSpeed,
    required this.timeRemaining,
    required this.status,
    this.totalSize = -1,
  }) : progress = progress.clamp(0.0, 1.0);

  String get downloadedSizeString {
    if (totalSize <= 0) return "Calculating...";
    if (progress <= 0) return "0 MB";
    final double downloaded = (totalSize * progress) / (1024 * 1024);
    if (downloaded > 1024) {
      return "${(downloaded / 1024).toStringAsFixed(2)} GB";
    }
    return "${downloaded.toStringAsFixed(2)} MB";
  }

  String get totalSizeString {
    if (totalSize <= 0) return "Unknown";
    final double total = totalSize / (1024 * 1024);
    if (total > 1024) return "${(total / 1024).toStringAsFixed(2)} GB";
    return "${total.toStringAsFixed(2)} MB";
  }

  String get speedString {
    if (status == TaskStatus.paused) return "Paused";
    if (progress >= 1.0) return "Done";
    if (networkSpeed < 0) return "Calculating...";
    if (networkSpeed == 0) return "0 MB/s";

    if (networkSpeed < 1.0) {
      return "${(networkSpeed * 1024).toStringAsFixed(2)} KB/s";
    }
    return "${networkSpeed.toStringAsFixed(2)} MB/s";
  }

  String get timeRemainingString {
    if (status == TaskStatus.paused) return "---";
    if (progress >= 1.0) return "Finished";
    if (timeRemaining.inSeconds <= 0) return "Calculating...";
    if (timeRemaining.inHours > 0) {
      return "${timeRemaining.inHours}h ${timeRemaining.inMinutes % 60}m remaining";
    }
    if (timeRemaining.inMinutes > 0) {
      return "${timeRemaining.inMinutes}m ${timeRemaining.inSeconds % 60}s remaining";
    }
    return "${timeRemaining.inSeconds}s remaining";
  }
}

@Riverpod(keepAlive: true)
class DownloadProgressNotifier extends _$DownloadProgressNotifier {
  @override
  Map<String, DownloadProgressData> build() => {};

  void update(String url, DownloadProgressData data) {
    state = {...state, url: data};
  }

  void remove(String url) {
    state = {...state}..remove(url);
  }
}

@Riverpod(keepAlive: true)
class ActiveDownloadsNotifier extends _$ActiveDownloadsNotifier {
  @override
  Set<String> build() => {};

  void add(String url) => state = {...state, url};
  void remove(String url) => state = {...state}..remove(url);
}

class DownloadService {
  // FileDownloader().updates is a single-subscription stream that rejects
  // re-subscription even after cancel. Subscribe once as a static bridge so
  // each DownloadService instance can listen via the broadcast proxy instead.
  static StreamSubscription<TaskUpdate>? _fdSubscription;
  static final _sharedEvents = StreamController<TaskUpdate>.broadcast();

  final Ref _ref;
  final Dio _dio;
  final Set<String> _cancellingUrls = {};
  final _updatesController = StreamController<TaskUpdate>.broadcast();
  StreamSubscription<TaskUpdate>? _updatesSubscription;
  bool _isInitialized = false;

  DownloadService(this._ref) : _dio = _ref.read(dioClientProvider);

  Stream<TaskUpdate> get updates => _updatesController.stream;

  void dispose() {
    _updatesSubscription?.cancel();
    _updatesController.close();
    // Do NOT cancel _fdSubscription — it matches FileDownloader()'s singleton
    // lifetime and cannot be re-subscribed after cancellation.
  }

  Future<void> init() async {
    if (_isInitialized) {
      if (kDebugMode) debugPrint('[DownloadService] Already initialized.');
      return;
    }
    // 1. Configure the downloader (chainable API)
    await FileDownloader()
        .configure(
          globalConfig: [(Config.requestTimeout, const Duration(seconds: 100))],
          androidConfig: [(Config.runInForeground, Config.always)],
          iOSConfig: [(Config.excludeFromCloudBackup, Config.always)],
        )
        .then((result) => debugPrint('Configuration result = $result'));

    // 2. Register callbacks and configure notifications
    final notificationConfig = TaskNotification(
      '{displayName}',
      Platform.isIOS
          ? 'Downloading...'
          : '{progress} • {networkSpeed} • {timeRemaining}',
    );

    FileDownloader()
        .registerCallbacks(
          taskNotificationTapCallback: _myNotificationTapCallback,
        )
        .configureNotification(
          running: notificationConfig,
          complete: const TaskNotification(
            '{displayName}',
            'Download finished',
          ),
          error: const TaskNotification('{displayName}', 'Download failed'),
          paused: const TaskNotification('{displayName}', 'Download paused'),
          progressBar: !Platform.isIOS,
        )
        .configureNotificationForGroup(
          'downloads',
          running: notificationConfig,
          complete: const TaskNotification(
            '{displayName}',
            'Download finished',
          ),
          error: const TaskNotification('{displayName}', 'Download failed'),
          paused: const TaskNotification('{displayName}', 'Download paused'),
          progressBar: !Platform.isIOS,
        );

    // 3. Re-check Permission status (native API)
    final status = await FileDownloader().permissions.status(
      PermissionType.notifications,
    );
    if (status != PermissionStatus.granted) {
      await FileDownloader().permissions.request(PermissionType.notifications);
    }

    // 4. Bridge FileDownloader updates into a shared broadcast stream (once),
    //    then let this instance listen to that broadcast proxy.
    _fdSubscription ??= FileDownloader().updates.listen(_sharedEvents.add);
    _updatesSubscription = _sharedEvents.stream.listen((update) {
      _updatesController.add(update);
      final trackingUrl = update.task.metaData.isNotEmpty
          ? update.task.metaData
          : update.task.url;

      if (_cancellingUrls.contains(trackingUrl)) return;

      switch (update) {
        case TaskProgressUpdate():
          final current = _ref.read(downloadProgressProvider)[trackingUrl];

          // If we already marked it as complete/failed, ignore lingering progress updates
          if (current != null &&
              (current.status == TaskStatus.complete ||
                  current.status == TaskStatus.failed)) {
            return;
          }

          final progressData = DownloadProgressData(
            taskId: update.task.taskId,
            progress: update.progress >= 0
                ? update.progress
                : (current?.progress ?? 0),
            networkSpeed: update.networkSpeed,
            timeRemaining: update.timeRemaining,
            totalSize: update.expectedFileSize > 0
                ? update.expectedFileSize
                : (current?.totalSize ?? -1),
            status: TaskStatus.running,
          );

          // Only add to active downloads if it's not finished
          if (update.progress < 1.0) {
            _ref.read(activeDownloadsProvider.notifier).add(trackingUrl);
          } else {
            // Force removal from active downloads if it's hitting 100%
            _ref.read(activeDownloadsProvider.notifier).remove(trackingUrl);
          }

          _ref
              .read(downloadProgressProvider.notifier)
              .update(trackingUrl, progressData);

        case TaskStatusUpdate():
          if (kDebugMode) {
            debugPrint(
              '[DownloadService] Status: ${update.status} for $trackingUrl',
            );
          }
          // Update status in progress map
          final current = _ref.read(downloadProgressProvider)[trackingUrl];
          if (current != null) {
            _ref
                .read(downloadProgressProvider.notifier)
                .update(
                  trackingUrl,
                  DownloadProgressData(
                    taskId: current.taskId,
                    progress: current.progress,
                    networkSpeed: update.status == TaskStatus.running
                        ? current.networkSpeed
                        : 0,
                    timeRemaining: update.status == TaskStatus.running
                        ? current.timeRemaining
                        : Duration.zero,
                    totalSize: current.totalSize,
                    status: update.status,
                  ),
                );
          }
          _handleStatusUpdate(update, trackingUrl);
      }
    });

    // 5. Catch up on any running tasks and database tracking
    await FileDownloader().trackTasks();
    await FileDownloader().start();

    // 6. Bridge Database Records to Riverpod (Persistence after restart)
    final records = await FileDownloader().database.allRecords();
    for (final record in records) {
      // Only recover paused tasks here.
      // Active/Enqueued tasks will be automatically picked up by FileDownloader().updates
      // if they are still running or re-started by trackTasks().
      if (record.status == TaskStatus.paused) {
        final trackingUrl = record.task.metaData.isNotEmpty
            ? record.task.metaData
            : record.task.url;

        _ref.read(activeDownloadsProvider.notifier).add(trackingUrl);
        _ref
            .read(downloadProgressProvider.notifier)
            .update(
              trackingUrl,
              DownloadProgressData(
                taskId: record.task.taskId,
                progress: record.progress,
                networkSpeed: 0,
                timeRemaining: Duration.zero,
                status: record.status,
                totalSize: record.expectedFileSize,
              ),
            );
      }
    }

    if (Platform.isIOS) {
      await FileDownloader().resumeFromBackground();
    }

    _isInitialized = true;
  }

  /// Process tapping on a notification
  void _myNotificationTapCallback(
    Task task,
    NotificationType notificationType,
  ) {
    if (kDebugMode) {
      debugPrint(
        '[DownloadService] Tapped $notificationType for ${task.taskId}',
      );
    }
    // Navigate to the Downloads tab (LibraryScreen)
    _ref.read(appRouterProvider).go('/library');
  }

  void _handleStatusUpdate(TaskStatusUpdate update, String trackingUrl) {
    if (update.status == TaskStatus.complete) {
      _ref.read(activeDownloadsProvider.notifier).remove(trackingUrl);
      _ref.read(downloadProgressProvider.notifier).remove(trackingUrl);
    } else if (update.status == TaskStatus.failed ||
        update.status == TaskStatus.canceled) {
      _ref.read(activeDownloadsProvider.notifier).remove(trackingUrl);
      _ref.read(downloadProgressProvider.notifier).remove(trackingUrl);

      if (update.status == TaskStatus.canceled) {
        // Cleanup database and metadata for cancelled tasks
        FileDownloader().database.deleteRecordWithId(update.task.taskId);
        _ref
            .read(storageServiceProvider)
            .removeDownloadMetadata(update.task.taskId);
      }
    }
  }

  Future<void> cancelDownload(String taskId, String trackingUrl) async {
    _cancellingUrls.add(trackingUrl);
    try {
      await FileDownloader().cancelTasksWithIds([taskId]);
      _ref.read(activeDownloadsProvider.notifier).remove(trackingUrl);
      _ref.read(downloadProgressProvider.notifier).remove(trackingUrl);

      // Proactive cleanup
      await FileDownloader().database.deleteRecordWithId(taskId);
      await _ref.read(storageServiceProvider).removeDownloadMetadata(taskId);
    } finally {
      // Small delay to let final updates clear
      Future.delayed(const Duration(milliseconds: 500), () {
        _cancellingUrls.remove(trackingUrl);
      });
    }
  }

  Future<void> pauseDownload(String taskId) async {
    final task = await FileDownloader().taskForId(taskId);
    if (task is DownloadTask) {
      await FileDownloader().pause(task);
    }
  }

  Future<void> resumeDownload(String taskId) async {
    final task = await FileDownloader().taskForId(taskId);
    if (task is DownloadTask) {
      await FileDownloader().resume(task);
    }
  }

  Future<DownloadMetadata?> getMetadata(
    String url, {
    Map<String, String>? headers,
  }) async {
    try {
      // 1. Try HEAD request first
      int? size;
      String? mimeType;

      try {
        final response = await _dio
            .head<dynamic>(
              url,
              options: Options(headers: headers, followRedirects: true),
            )
            .timeout(const Duration(seconds: 10));

        final contentLength = response.headers.value('content-length');
        if (contentLength != null) {
          size = int.tryParse(contentLength);
        }
        mimeType = response.headers.value('content-type');
      } catch (e) {
        // HEAD failed, will try GET fallback
      }

      // 2. Fallback to GET with Range if size unknown
      if (size == null) {
        try {
          final getResponse = await _dio
              .get<dynamic>(
                url,
                options: Options(
                  headers: {...?headers, 'Range': 'bytes=0-0'},
                  followRedirects: true,
                ),
              )
              .timeout(const Duration(seconds: 10));

          final rangeContentLength = getResponse.headers.value('content-range');
          if (rangeContentLength != null) {
            final totalSize = rangeContentLength.split('/').last;
            size = int.tryParse(totalSize);
          }
          mimeType ??= getResponse.headers.value('content-type');
        } catch (e) {
          // GET fallback failed
        }
      }

      return DownloadMetadata(size: size, mimeType: mimeType);
    } catch (e) {
      return null;
    }
  }

  Future<bool> startDownload({
    required String url,
    required String filename,
    required String directory, // Relative for mobile/mac, absolute for others
    required MultimediaItem item,
    Episode? episode,
    String? trackingUrl,
    Map<String, String>? headers,
  }) async {
    if (kDebugMode) {
      debugPrint('[DownloadService] startDownload called');
      debugPrint('[DownloadService] - URL: $url');
      debugPrint('[DownloadService] - Tracking URL: $trackingUrl');
      debugPrint('[DownloadService] - Filename: $filename');
      debugPrint('[DownloadService] - Directory: $directory');
    }

    // Industry Standard: Ask for battery optimization when a real download starts
    await requestIgnoreBatteryOptimizations();

    // Request permission on Android (Version Aware)
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt >= 30) {
        // For Android 11+, request MANAGE_EXTERNAL_STORAGE to allow native C++ players (media_kit)
        // to bypass FUSE directory depth limits for deeply nested series folders
        final status = await Permission.manageExternalStorage.status;
        if (!status.isGranted) {
          await Permission.manageExternalStorage.request();
        }
      } else {
        // For Android 10 and below, request standard storage permission
        await Permission.storage.request();
      }
    }

    final isAndroid = Platform.isAndroid;
    final isIOS = Platform.isIOS;

    // Prevention: Check if task is ALREADY running (using database for robustness)
    final records = await FileDownloader().database.allRecords();
    final existingRecord = records.firstWhereOrNull(
      (r) =>
          (r.status == TaskStatus.enqueued ||
              r.status == TaskStatus.running ||
              r.status == TaskStatus.paused) &&
          (r.task.metaData.isNotEmpty ? r.task.metaData : r.task.url) ==
              (trackingUrl ?? url),
    );

    if (existingRecord != null) {
      if (kDebugMode) {
        debugPrint(
          '[DownloadService] Task already exists in database with status: ${existingRecord.status}',
        );
      }

      // If it was paused, resume it!
      if (existingRecord.status == TaskStatus.paused) {
        if (kDebugMode) {
          debugPrint('[DownloadService] Auto-resuming paused task.');
        }
        if (existingRecord.task is DownloadTask) {
          await FileDownloader().resume(existingRecord.task as DownloadTask);
        }
      }

      _ref.read(activeDownloadsProvider.notifier).add(trackingUrl ?? url);
      return true;
    }

    // Path Logic:
    // Android/Desktop: use BaseDirectory.root with absolute path.
    // iOS: use BaseDirectory.applicationDocuments with relative path for sandbox safety.
    BaseDirectory baseDir;
    String taskDirectory;

    if (isIOS) {
      baseDir = BaseDirectory.applicationDocuments;
      // On iOS, 'directory' (from getDownloadPath(absolute: false)) is relative: "Skystream/Title"
      taskDirectory = directory;
    } else {
      // Android, Windows, macOS, Linux: use absolute paths with BaseDirectory.root
      baseDir = BaseDirectory.root;
      if (isAndroid) {
        taskDirectory = p.join(await _getPublicDownloadsPath(), directory);
      } else {
        // Desktop: directory is already absolute (e.g. /Users/akash/Downloads/Skystream/Title)
        taskDirectory = directory;
      }
    }

    final task = DownloadTask(
      url: url,
      filename: filename,
      displayName: filename,
      baseDirectory: baseDir,
      directory: taskDirectory,
      headers: headers ?? {},
      updates: Updates.statusAndProgress,
      retries: 3, // Align with example
      allowPause: true,
      metaData: trackingUrl ?? url,
    );

    if (kDebugMode) debugPrint('[DownloadService] Enqueuing task...');

    // Create the directory if it doesn't exist
    final String fullDirPath;
    if (isIOS) {
      final docsDir = await getApplicationDocumentsDirectory();
      fullDirPath = p.join(docsDir.path, taskDirectory);
    } else {
      // Android/Desktop: taskDirectory is already absolute
      fullDirPath = taskDirectory;
    }

    final dir = Directory(fullDirPath);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }

    final success = await FileDownloader().enqueue(task);
    if (kDebugMode) debugPrint('[DownloadService] Enqueue result: $success');

    if (success) {
      _ref.read(activeDownloadsProvider.notifier).add(trackingUrl ?? url);
      // Save metadata for offline support
      await _ref
          .read(storageServiceProvider)
          .saveDownloadMetadata(task.taskId, item, episode: episode);
    }
    return success;
  }

  Future<String> getDownloadPath(
    MultimediaItem? item, {
    Episode? episode,
    bool absolute = false,
  }) async {
    final dir =
        await getDownloadsDirectory() ??
        await getApplicationDocumentsDirectory();
    final sanitizedTitle =
        item?.title.replaceAll(RegExp(r'[^\w\s-]'), '').trim() ?? "Unknown";

    String path;
    final publicDir = await _getPublicDownloadsPath();

    if (Platform.isAndroid || Platform.isIOS) {
      path = p.join("Skystream", sanitizedTitle);
      if (absolute) {
        path = p.join(publicDir, path);
      }
    } else {
      path = p.join(dir.path, "Skystream", sanitizedTitle);
    }

    // Add Season subdirectory if it's a series and we have an episode
    if (item != null &&
        episode != null &&
        item.contentType != MultimediaContentType.movie) {
      // Logic: If there's more than one season in the details, use subdirectories
      final seasonCount =
          item.episodes?.map((e) => e.season).toSet().length ?? 0;
      if (seasonCount > 1) {
        path = p.join(path, "Season ${episode.season}");
      }
    }

    return path;
  }

  Future<File?> getDownloadedFile(
    MultimediaItem item, {
    Episode? episode,
  }) async {
    final directoryPath = await getDownloadPath(
      item,
      episode: episode,
      absolute: true,
    );
    final directory = Directory(directoryPath);
    if (!await directory.exists()) return null;

    final sanitizedTitle = item.title
        .replaceAll(RegExp(r'[^\w\s-]'), '')
        .trim();
    String baseName;
    if (episode != null && item.contentType != MultimediaContentType.movie) {
      final sanitizedEpName = episode.name
          .replaceAll(RegExp(r'[^\w\s-]'), '')
          .trim();
      baseName = "S${episode.season}-E${episode.episode} $sanitizedEpName";
    } else {
      baseName = sanitizedTitle;
    }

    // Check common extensions
    final extensions = ['.mp4', '.mkv', '.webm', '.avi'];
    for (final ext in extensions) {
      final file = File(p.join(directoryPath, '$baseName$ext'));
      if (await file.exists()) {
        return file;
      }
    }

    return null;
  }

  // Check if battery optimizations are ignored
  Future<bool> isIgnoringBatteryOptimizations() async {
    if (!Platform.isAndroid) return true;
    return await Permission.ignoreBatteryOptimizations.isGranted;
  }

  // Request user to disable battery optimizations for persistent downloads
  Future<void> requestIgnoreBatteryOptimizations() async {
    if (!Platform.isAndroid) return;

    final status = await Permission.ignoreBatteryOptimizations.status;
    if (!status.isGranted) {
      if (kDebugMode) {
        debugPrint('[DownloadService] Requesting ignore battery optimizations');
      }
      await Permission.ignoreBatteryOptimizations.request();
    }
  }

  Future<bool> deleteDownloadedFile(File file) async {
    try {
      if (await file.exists()) {
        final parentDir = file.parent;
        await file.delete();
        // Recursively cleanup empty parent folders
        await _deleteEmptyParentDirectories(parentDir);
        return true;
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('[DownloadService] Error deleting file: $e');
      }
    }
    return false;
  }

  Future<void> _deleteEmptyParentDirectories(Directory directory) async {
    try {
      // 1. Safety check: Only delete if it's within a 'Skystream' folder
      if (!directory.path.contains('Skystream')) return;

      // 2. Stop at the main 'Skystream' root to avoid deleting the base app directory
      if (directory.path.endsWith('Skystream') ||
          directory.path.endsWith('Skystream/')) {
        return;
      }

      if (await directory.exists()) {
        // 3. Get non-hidden entities
        final List<FileSystemEntity> entities = await directory
            .list()
            .where(
              (entity) => !entity.path
                  .split(Platform.pathSeparator)
                  .last
                  .startsWith('.'),
            )
            .toList();

        if (entities.isEmpty) {
          await directory.delete();
          // 4. Recurse to parent
          await _deleteEmptyParentDirectories(directory.parent);
        }
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('[DownloadService] Error deleting empty folder: $e');
      }
    }
  }

  Future<String> _getPublicDownloadsPath() async {
    if (Platform.isAndroid) {
      return "/storage/emulated/0/Download";
    }
    if (Platform.isIOS) {
      final dir = await getApplicationDocumentsDirectory();
      return dir.path;
    }
    final dir =
        await getDownloadsDirectory() ??
        await getApplicationDocumentsDirectory();
    return dir.path;
  }
}

class DownloadMetadata {
  final int? size;
  final String? mimeType;

  DownloadMetadata({this.size, this.mimeType});

  String get sizeString {
    if (size == null) return "Unknown size";
    final double mb = size! / (1024 * 1024);
    if (mb > 1024) {
      return "${(mb / 1024).toStringAsFixed(2)} GB";
    }
    return "${mb.toStringAsFixed(2)} MB";
  }
}
