package app.overbay.swiftfox.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var currentTitle: String = "Playing media"
    private var currentHost: String = "Swiftfox"
    private var isPlaying: Boolean = false
    private var isPrivate: Boolean = false
    private var isForeground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        runningInstance = WeakReference(this)
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSession(this, "SwiftfoxMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val controller = activeController
                    if (controller == null) {
                        stopForegroundAndSelf()
                        return
                    }
                    if (!isForeground) {
                        promoteToForegroundSafe(buildNotification(playingOverride = true))
                    }
                    controller.onMediaCommand(MediaCommand.PLAY)
                }

                override fun onPause() {
                    activeController?.onMediaCommand(MediaCommand.PAUSE)
                }

                override fun onStop() {
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
                applyStateUpdate(
                    rawTitle = nextTitle,
                    rawHost = nextHost,
                    playing = nextPlaying,
                    privateTab = nextPrivate,
                    allowStartForeground = nextPlaying || startedViaForegroundIntent
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
                val targetPlaying = !isPlaying
                if ((targetPlaying || startedViaForegroundIntent) && !isForeground) {
                    promoteToForegroundSafe(buildNotification(playingOverride = targetPlaying))
                }
                controller.onMediaCommand(
                    if (isPlaying) MediaCommand.PAUSE else MediaCommand.PLAY
                )
            }
            ACTION_STOP -> {
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
        allowStartForeground: Boolean
    ) {
        isPrivate = privateTab
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
        if (isPlaying) {
            requestAudioFocus()
        } else {
            abandonAudioFocus()
        }
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
            if (!isPlaying && isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        isForeground = false
        if (runningInstance?.get() === this) {
            runningInstance = null
        }
        abandonAudioFocus()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        abandonAudioFocus()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        // Avoid abandoning and re-requesting focus when already held (e.g. metadata updates)
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    activeController?.onMediaCommand(MediaCommand.PAUSE)
                }
            }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        val req = focusRequest ?: return
        audioManager?.abandonAudioFocusRequest(req)
        focusRequest = null
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
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
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
            .setDeleteIntent(stopIntent)
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
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"
        private const val EXTRA_IS_PRIVATE = "extra_is_private"
        private const val EXTRA_EXPECT_FOREGROUND = "extra_expect_foreground"

        @Volatile
        var activeController: MediaCommandController? = null

        @Volatile
        private var isServiceRunning: Boolean = false

        @Volatile
        private var runningInstance: WeakReference<BackgroundMediaService>? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        fun updateState(
            context: Context,
            title: String,
            host: String,
            isPlaying: Boolean,
            isPrivate: Boolean = false
        ) {
            if (!isPlaying && !isServiceRunning) return
            val existing = runningInstance?.get()
            if (existing != null && (!isPlaying || existing.isForeground)) {
                // Update active service directly on Main thread so background track transitions
                // and pauses avoid Android 12-15 background startForegroundService restrictions.
                mainHandler.post {
                    runningInstance?.get()?.applyStateUpdate(
                        rawTitle = title,
                        rawHost = host,
                        playing = isPlaying,
                        privateTab = isPrivate,
                        allowStartForeground = isPlaying
                    )
                }
                return
            }
            if (existing != null && isPlaying) {
                // Service is still alive (detached after pause); try promoting directly on Main
                // and fall back cleanly to posting updated notification if FGS start is restricted.
                mainHandler.post {
                    runningInstance?.get()?.applyStateUpdate(
                        rawTitle = title,
                        rawHost = host,
                        playing = true,
                        privateTab = isPrivate,
                        allowStartForeground = true
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
                putExtra(EXTRA_EXPECT_FOREGROUND, isPlaying)
            }
            runCatching {
                if (isPlaying) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BackgroundMediaService::class.java))
            }
        }
    }
}
