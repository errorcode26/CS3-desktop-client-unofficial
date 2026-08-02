package dev.akash.skystream

import android.content.Intent
import android.net.Uri
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.app.PictureInPictureParams
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : FlutterActivity() {
    private val CHANNEL = "dev.akash.skystream.player/pip"
    private val TV_CHANNEL = "dev.akash.skystream/tv_channel"
    private val PLAYER_CHANNEL = "dev.akash.skystream/external_player"

    private var isPlaying = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        
        // PiP Channel
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "enterPip") {
                val playing = call.argument<Boolean>("isPlaying") ?: false
                this.isPlaying = playing // Sync state immediately
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    updatePipActions()
                    val builder = PictureInPictureParams.Builder()
                    builder.setActions(createPipActions())
                    enterPictureInPictureMode(builder.build())
                    result.success(null)
                } else {
                    result.error("UNSUPPORTED", "PIP not supported", null)
                }
            } else if (call.method == "setPipState") {
                // Flutter tells us if playing or not
                val playing = call.argument<Boolean>("isPlaying") ?: false
                // Always update state and force refresh actions
                // The user reported sync issues, so we shouldn't skip update if values match
                this.isPlaying = playing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    updatePipActions()
                }
                result.success(null)
            } else {
                result.notImplemented()
            }
        }

        // Android TV Channel
        MethodChannel(messenger, TV_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "createTvChannel" -> {
                    TvChannelUtils.createTvChannel(this)
                    result.success(null)
                }
                "addPrograms" -> {
                    // Start a background thread or coroutine ideally, but for now simple invocation
                    // The TvUtils methods do ContentProvider ops which should be background, but strict mode might complain. 
                    // Given this is a demo clone, running on UI thread (MethodChannel default) might cause minor frame drop but is simplest.
                    // Ideally use Thread { ... }.start() if heavy.
                    Thread {
                        val channelId = TvChannelUtils.getChannelId(this, getString(R.string.app_name))
                        if (channelId != null) {
                             val items = call.argument<List<Map<String, Any>>>("programs") ?: emptyList()
                             TvChannelUtils.addPrograms(this, channelId, items)
                             runOnUiThread { result.success(null) }
                        } else {
                             // Try to create channel if missing?
                             TvChannelUtils.createTvChannel(this)
                             val newId = TvChannelUtils.getChannelId(this, getString(R.string.app_name))
                             if (newId != null) {
                                  val items = call.argument<List<Map<String, Any>>>("programs") ?: emptyList()
                                  TvChannelUtils.addPrograms(this, newId, items)
                                  runOnUiThread { result.success(null) }
                             } else {
                                  runOnUiThread { result.error("NO_CHANNEL", "Channel not found and creation failed", null) }
                             }
                        }
                    }.start()
                }
                "deleteStoredPrograms" -> {
                    Thread {
                        TvChannelUtils.deleteStoredPrograms(this)
                        runOnUiThread { result.success(null) }
                    }.start()
                }
                else -> result.notImplemented()
            }
        }

        // External Player Channel — uses native Intent to avoid Uri.parse() issues
        MethodChannel(messenger, PLAYER_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "launchVideoInPlayer") {
                val videoUrl = call.argument<String>("url") ?: run {
                    result.error("INVALID_ARGS", "url is required", null)
                    return@setMethodCallHandler
                }
                val packageName = call.argument<String>("package")
                val mimeType = call.argument<String>("mimeType") ?: "video/*"
                val title = call.argument<String>("title")

                try {
                    val uri = if (videoUrl.startsWith("file://") || (videoUrl.startsWith("/") && File(videoUrl).exists())) {
                        val filePath = if (videoUrl.startsWith("file://")) {
                            videoUrl.substring(7)
                        } else {
                            videoUrl
                        }
                        FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileProvider", File(filePath))
                    } else {
                        Uri.parse(videoUrl)
                    }

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (!packageName.isNullOrEmpty()) setPackage(packageName)
                        if (!title.isNullOrEmpty()) {
                            putExtra("title", title)
                            putExtra("android.intent.extra.TITLE", title)
                        }
                    }
                    startActivity(intent)
                    result.success(true)
                } catch (e: android.content.ActivityNotFoundException) {
                    result.success(false) // Player not installed / not found
                } catch (e: Exception) {
                    result.error("LAUNCH_ERROR", e.message, null)
                }
            } else {
                result.notImplemented()
            }
        }
    }
    
    // Action Constants
    private val ACTION_MEDIA_CONTROL = "media_control"
    private val EXTRA_CONTROL_TYPE = "control_type"
    private val CONTROL_TYPE_PLAY = 1
    private val CONTROL_TYPE_PAUSE = 2
    private val CONTROL_TYPE_REWIND = 3
    private val CONTROL_TYPE_FORWARD = 4

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_MEDIA_CONTROL) {
                val type = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
                val method = when (type) {
                    CONTROL_TYPE_PLAY -> "play"
                    CONTROL_TYPE_PAUSE -> "pause"
                    CONTROL_TYPE_REWIND -> "seekBackward"
                    CONTROL_TYPE_FORWARD -> "seekForward"
                    else -> null
                }
                if (method != null) {
                    flutterEngine?.dartExecutor?.binaryMessenger?.let {
                        MethodChannel(it, CHANNEL).invokeMethod(method, null)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = android.content.IntentFilter(ACTION_MEDIA_CONTROL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }

    private fun createPipActions(): List<android.app.RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val actions = mutableListOf<android.app.RemoteAction>()

        // 1. Rewind (Use custom 10s icon)
        val rewindIntent = android.content.Intent(ACTION_MEDIA_CONTROL).apply {
            putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_REWIND)
        }
        val rewindPendingIntent = android.app.PendingIntent.getBroadcast(
            this, CONTROL_TYPE_REWIND, rewindIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val rewindIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_replay_10)
        actions.add(android.app.RemoteAction(rewindIcon, "Rewind", "Rewind 10s", rewindPendingIntent))

        // 2. Play/Pause
        val playPauseIntent = android.content.Intent(ACTION_MEDIA_CONTROL).apply {
            putExtra(EXTRA_CONTROL_TYPE, if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY)
        }
        // Unique Request Code is Critical
        val playPauseReqCode = if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY
        val playPausePendingIntent = android.app.PendingIntent.getBroadcast(
            this, playPauseReqCode, playPauseIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIconIdx = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIcon = android.graphics.drawable.Icon.createWithResource(this, playPauseIconIdx)
        actions.add(android.app.RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPausePendingIntent))

        // 3. Forward (Use custom 10s icon)
        val forwardIntent = android.content.Intent(ACTION_MEDIA_CONTROL).apply {
            putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_FORWARD)
        }
        val forwardPendingIntent = android.app.PendingIntent.getBroadcast(
            this, CONTROL_TYPE_FORWARD, forwardIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_forward_10)
        actions.add(android.app.RemoteAction(forwardIcon, "Forward", "Forward 10s", forwardPendingIntent))

        return actions
    }

    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(PictureInPictureParams.Builder()
                .setActions(createPipActions())
                .build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        io.flutter.plugin.common.MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, CHANNEL)
            .invokeMethod("pipModeChanged", isInPictureInPictureMode)
    }
}
