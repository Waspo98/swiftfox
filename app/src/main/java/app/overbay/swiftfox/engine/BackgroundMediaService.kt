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
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.overbay.swiftfox.MainActivity
import app.overbay.swiftfox.R

class BackgroundMediaService : Service() {

    private lateinit var mediaSession: MediaSession
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var currentTitle: String = "Playing media"
    private var currentHost: String = "Swiftfox"
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSession(this, "SwiftfoxMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    activeController?.onMediaCommand(MediaCommand.PLAY)
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
        when (intent?.action) {
            ACTION_UPDATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Playing media" }
                currentHost = intent.getStringExtra(EXTRA_HOST).orEmpty().ifBlank { "Swiftfox" }
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                if (isPlaying) requestAudioFocus() else abandonAudioFocus()
                updateMediaSession()
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                activeController?.onMediaCommand(
                    if (isPlaying) MediaCommand.PAUSE else MediaCommand.PLAY
                )
            }
            ACTION_STOP -> {
                activeController?.onMediaCommand(MediaCommand.STOP)
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        abandonAudioFocus()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
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
                1f
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

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundMediaService::class.java).setAction(ACTION_TOGGLE_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, BackgroundMediaService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

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

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(currentTitle)
            .setContentText(currentHost)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
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

        @Volatile
        var activeController: MediaCommandController? = null

        fun updateState(context: Context, title: String, host: String, isPlaying: Boolean) {
            val intent = Intent(context, BackgroundMediaService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BackgroundMediaService::class.java))
            }
        }
    }
}
