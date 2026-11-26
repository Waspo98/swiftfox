package app.overbay.swiftfox.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import app.overbay.swiftfox.MainActivity
import app.overbay.swiftfox.R
import java.lang.ref.WeakReference

class BackgroundMediaService : Service() {

    private lateinit var mediaSession: MediaSession

    private var currentTitle: String = "Playing media"
    private var currentHost: String = "Swiftfox"
    private var isPlaying: Boolean = false
    private var pendingPlayConfirmation: Boolean = false
    private var isPrivate: Boolean = false
    private var currentWebAppId: String? = null
    @Volatile
    private var isForeground: Boolean = false
    private var pendingPlayRevertRunnable: Runnable? = null
    private var dismissReceiverRegistered: Boolean = false

    private val dismissNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_DISMISS_BROADCAST) return
            cancelPendingPlayRevert()
            pendingPlayConfirmation = false
            activeController?.onMediaCommand(MediaCommand.STOP)
            stopForegroundAndSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        isStartPending = false
        runningInstance = WeakReference(this)
        createNotificationChannel()
        runCatching {
            ContextCompat.registerReceiver(
                this,
                dismissNotificationReceiver,
                IntentFilter(ACTION_DISMISS_BROADCAST),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            dismissReceiverRegistered = true
        }

        mediaSession = MediaSession(this, "SwiftfoxMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val controller = activeController
                    if (controller == null) {
                        stopForegroundAndSelf()
                        return
                    }
                    beginPendingPlay()
                    controller.onMediaCommand(MediaCommand.PLAY)
                }

                override fun onPause() {
                    cancelPendingPlayRevert()
                    pendingPlayConfirmation = false
                    activeController?.onMediaCommand(MediaCommand.PAUSE)
                }

                override fun onStop() {
                    cancelPendingPlayRevert()
                    pendingPlayConfirmation = false
                    activeController?.onMediaCommand(MediaCommand.STOP)
                    stopForegroundAndSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedViaForegroundIntent =
            intent?.getBooleanExtra(EXTRA_EXPECT_FOREGROUND, false) == true
        when (intent?.action) {
            ACTION_UPDATE -> {
                val nextTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val nextHost = intent.getStringExtra(EXTRA_HOST).orEmpty()
                val nextPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val nextPrivate = intent.getBooleanExtra(EXTRA_IS_PRIVATE, false)
                val nextWebAppId = intent.getStringExtra(EXTRA_WEB_APP_ID)?.takeIf { it.isNotBlank() }
                applyStateUpdate(
                    rawTitle = nextTitle,
                    rawHost = nextHost,
                    playing = nextPlaying,
                    privateTab = nextPrivate,
                    webAppId = nextWebAppId,
                    allowStartForeground = nextPlaying,
                    mustFulfillForegroundContract = startedViaForegroundIntent
                )
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                val controller = activeController
                if (controller == null) {
                    // Satisfy startForeground contract if launched via PendingIntent.getForegroundService
                    // before immediately cleaning up the orphaned notification.
                    if (startedViaForegroundIntent && !isForeground) {
                        promoteToForegroundSafe(buildNotification(playingOverride = false))
                    }
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
                val command = resolveToggleCommand(
                    isPlaying = isPlaying,
                    pendingPlayConfirmation = pendingPlayConfirmation
                )
                if (command == MediaCommand.PLAY) {
                    beginPendingPlay()
                    controller.onMediaCommand(MediaCommand.PLAY)
                } else {
                    cancelPendingPlayRevert()
                    pendingPlayConfirmation = false
                    if (startedViaForegroundIntent && !isForeground) {
                        promoteToForegroundSafe(buildNotification(playingOverride = false))
                    }
                    if (!isPlaying && isForeground) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        isForeground = false
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, buildNotification(playingOverride = false))
                    }
                    controller.onMediaCommand(MediaCommand.PAUSE)
                }
            }
            ACTION_STOP -> {
                cancelPendingPlayRevert()
                pendingPlayConfirmation = false
                if (startedViaForegroundIntent && !isForeground) {
                    promoteToForegroundSafe(buildNotification(playingOverride = false))
                }
                activeController?.onMediaCommand(MediaCommand.STOP)
                stopForegroundAndSelf()
            }
            else -> {
                if (startedViaForegroundIntent && !isForeground) {
                    promoteToForegroundSafe(buildNotification(playingOverride = false))
                    stopForegroundAndSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun cancelPendingPlayRevert() {
        pendingPlayRevertRunnable?.let(mainHandler::removeCallbacks)
        pendingPlayRevertRunnable = null
    }

    private fun beginPendingPlay() {
        cancelPendingPlayRevert()
        pendingPlayConfirmation = true
        val notification = buildNotification(playingOverride = true)
        if (isForeground || !promoteToForegroundSafe(notification)) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
        val revert = Runnable {
            pendingPlayRevertRunnable = null
            if (!pendingPlayConfirmation || isPlaying) return@Runnable
            pendingPlayConfirmation = false
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(playingOverride = false))
        }
        pendingPlayRevertRunnable = revert
        mainHandler.postDelayed(revert, PLAY_CONFIRM_TIMEOUT_MS)
    }

    private fun promoteToForegroundSafe(notification: Notification): Boolean {
        return runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            isForeground = true
            true
        }.getOrElse {
            isForeground = false
            false
        }
    }

    private fun applyStateUpdate(
        rawTitle: String,
        rawHost: String,
        playing: Boolean,
        privateTab: Boolean,
        webAppId: String? = null,
        allowStartForeground: Boolean,
        mustFulfillForegroundContract: Boolean = false
    ) {
        cancelPendingPlayRevert()
        pendingPlayConfirmation = false
        isPrivate = privateTab
        currentWebAppId = if (privateTab) null else webAppId?.takeIf { it.isNotBlank() }
        isPlaying = playing
        currentTitle = if (privateTab) {
            "Private media"
        } else {
            rawTitle.ifBlank { "Playing media" }
        }
        currentHost = if (privateTab) {
            "Swiftfox Private"
        } else {
            rawHost.ifBlank { "Swiftfox" }
        }
        // Let Chromium WebView's AudioFocusDelegate own Android OS audio focus directly;
        // requesting a second AUDIOFOCUS_GAIN here revokes WebView's focus (AUDIOFOCUS_LOSS)
        // and immediately pauses playback right after play starts.
        updateMediaSession()
        val notification = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isPlaying && allowStartForeground) {
            if (isForeground) {
                nm.notify(NOTIFICATION_ID, notification)
            } else if (!promoteToForegroundSafe(notification)) {
                nm.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (!isPlaying && mustFulfillForegroundContract && !isForeground) {
                // Satisfy Android's startForegroundService contract before detaching to paused state
                promoteToForegroundSafe(notification)
            }
            if (!isPlaying && isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelPendingPlayRevert()
        pendingPlayConfirmation = false
        if (dismissReceiverRegistered) {
            dismissReceiverRegistered = false
            runCatching { unregisterReceiver(dismissNotificationReceiver) }
        }
        isServiceRunning = false
        isStartPending = false
        isForeground = false
        if (runningInstance?.get() === this) {
            runningInstance = null
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        cancelPendingPlayRevert()
        pendingPlayConfirmation = false
        isServiceRunning = false
        isStartPending = false
        if (runningInstance?.get() === this) {
            runningInstance = null
        }
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun updateMediaSession() {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (isPlaying) 1f else 0f
            )
            .build()
        mediaSession.setPlaybackState(state)
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, currentHost)
                .build()
        )
    }

    private fun buildNotification(playingOverride: Boolean? = null): Notification {
        val effectivePlaying = playingOverride ?: isPlaying
        val targetAppId = currentWebAppId
        val launchIntent = if (targetAppId != null) {
            Intent(this, app.overbay.swiftfox.ui.webapps.WebAppActivity::class.java).apply {
                action = app.overbay.swiftfox.features.webapps.WebAppShortcutManager.ACTION_LAUNCH_WEB_APP
                data = android.net.Uri.parse("swiftfox-pwa://app/$targetAppId")
                putExtra(app.overbay.swiftfox.features.webapps.WebAppShortcutManager.EXTRA_WEB_APP_ID, targetAppId)
                flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = if (effectivePlaying) {
            PendingIntent.getService(
                this,
                1,
                Intent(this, BackgroundMediaService::class.java)
                    .setAction(ACTION_TOGGLE_PLAY_PAUSE)
                    .putExtra(EXTRA_EXPECT_FOREGROUND, false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // When paused (detached from foreground), Play must startForegroundService on Android 12-15
            PendingIntent.getForegroundService(
                this,
                1,
                Intent(this, BackgroundMediaService::class.java)
                    .setAction(ACTION_TOGGLE_PLAY_PAUSE)
                    .putExtra(EXTRA_EXPECT_FOREGROUND, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopIntent = if (effectivePlaying) {
            PendingIntent.getService(
                this,
                2,
                Intent(this, BackgroundMediaService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_EXPECT_FOREGROUND, false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getForegroundService(
                this,
                2,
                Intent(this, BackgroundMediaService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_EXPECT_FOREGROUND, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val deleteIntent = PendingIntent.getBroadcast(
            this,
            3,
            Intent(ACTION_DISMISS_BROADCAST).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseRes = if (effectivePlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (effectivePlaying) "Pause" else "Play"

        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseRes),
            playPauseLabel,
            toggleIntent
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopIntent
        ).build()

        val visibility = if (isPrivate) {
            Notification.VISIBILITY_SECRET
        } else {
            Notification.VISIBILITY_PUBLIC
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_swiftfox)
            .setContentTitle(currentTitle)
            .setContentText(currentHost)
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(effectivePlaying)
            .setVisibility(visibility)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.media_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    enum class MediaCommand { PLAY, PAUSE, STOP }

    fun interface MediaCommandController {
        fun onMediaCommand(command: MediaCommand)
    }

    companion object {
        private const val CHANNEL_ID = "swiftfox_media_playback"
        private const val NOTIFICATION_ID = 4109
        private const val ACTION_UPDATE = "app.overbay.swiftfox.media.UPDATE"
        private const val ACTION_TOGGLE_PLAY_PAUSE = "app.overbay.swiftfox.media.TOGGLE"
        private const val ACTION_STOP = "app.overbay.swiftfox.media.STOP"
        private const val ACTION_DISMISS_BROADCAST = "app.overbay.swiftfox.media.DISMISS"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"
        private const val EXTRA_IS_PRIVATE = "extra_is_private"
        private const val EXTRA_WEB_APP_ID = "extra_web_app_id"
        private const val EXTRA_EXPECT_FOREGROUND = "extra_expect_foreground"
        private const val PLAY_CONFIRM_TIMEOUT_MS = 3_000L

        internal fun resolveToggleCommand(
            isPlaying: Boolean,
            pendingPlayConfirmation: Boolean
        ): MediaCommand = if (isPlaying || pendingPlayConfirmation) {
            MediaCommand.PAUSE
        } else {
            MediaCommand.PLAY
        }

        @Volatile
        var activeController: MediaCommandController? = null

        @Volatile
        private var activeOwner: Any? = null

        @Volatile
        private var isServiceRunning: Boolean = false

        @Volatile
        private var isStartPending: Boolean = false

        @Volatile
        private var runningInstance: WeakReference<BackgroundMediaService>? = null

        private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

        fun claimPlayback(
            owner: Any,
            context: Context,
            title: String,
            host: String,
            isPlaying: Boolean,
            isPrivate: Boolean = false,
            webAppId: String? = null,
            controller: MediaCommandController
        ) {
            claimControllerForOwner(owner = owner, controller = controller)
            updateState(
                context = context,
                title = title,
                host = host,
                isPlaying = isPlaying,
                isPrivate = isPrivate,
                webAppId = webAppId
            )
        }

        fun updatePlaybackIfOwner(
            owner: Any,
            context: Context,
            title: String,
            host: String,
            isPlaying: Boolean,
            isPrivate: Boolean = false,
            webAppId: String? = null
        ) {
            if (activeOwner !== owner) return
            updateState(
                context = context,
                title = title,
                host = host,
                isPlaying = isPlaying,
                isPrivate = isPrivate,
                webAppId = webAppId
            )
        }

        fun releasePlaybackOwner(
            owner: Any,
            context: Context,
            stopService: Boolean
        ) {
            if (!releaseControllerIfOwner(owner)) return
            if (stopService) {
                stop(context)
            }
        }

        internal fun claimControllerForOwner(
            owner: Any,
            controller: MediaCommandController
        ) {
            activeOwner = owner
            activeController = controller
        }

        internal fun releaseControllerIfOwner(owner: Any): Boolean {
            if (activeOwner !== owner) return false
            activeOwner = null
            activeController = null
            return true
        }

        fun isOwner(owner: Any): Boolean = activeOwner === owner

        fun updateState(
            context: Context,
            title: String,
            host: String,
            isPlaying: Boolean,
            isPrivate: Boolean = false,
            webAppId: String? = null
        ) {
            if (!isPlaying && !isServiceRunning && !isStartPending) return
            val existing = runningInstance?.get()
            if (existing != null) {
                // Update active service directly on Main thread so background track transitions
                // and pauses avoid Android 12-15 background startForegroundService restrictions.
                mainHandler.post {
                    runningInstance?.get()?.applyStateUpdate(
                        rawTitle = title,
                        rawHost = host,
                        playing = isPlaying,
                        privateTab = isPrivate,
                        webAppId = webAppId,
                        allowStartForeground = isPlaying
                    )
                }
                return
            }
            val intent = Intent(context, BackgroundMediaService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_IS_PRIVATE, isPrivate)
                if (!webAppId.isNullOrBlank()) {
                    putExtra(EXTRA_WEB_APP_ID, webAppId)
                }
                putExtra(EXTRA_EXPECT_FOREGROUND, isPlaying)
            }
            runCatching {
                if (isPlaying) {
                    isStartPending = true
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                if (isPlaying) isStartPending = false
            }
        }

        fun stop(context: Context) {
            isStartPending = false
            runCatching {
                context.stopService(Intent(context, BackgroundMediaService::class.java))
            }
        }
    }
}
