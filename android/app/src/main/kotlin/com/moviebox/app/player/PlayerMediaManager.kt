package com.moviebox.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages the MediaSession, system media notification, and background playback
 * controls (lockscreen, Bluetooth, and notifications).
 */
@UnstableApi
class PlayerMediaManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val title: String,
    private val season: Int,
    private val episode: Int,
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var mediaSession: MediaSession? = null
    private var receiver: BroadcastReceiver? = null

    init {
        createNotificationChannel()
        setupMediaSession()
        registerMediaReceiver()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "MovieBox active playback controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        val session = MediaSession(context, "MovieBoxSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    player.play()
                }

                override fun onPause() {
                    player.pause()
                }

                override fun onSeekTo(pos: Long) {
                    player.seekTo(pos)
                }

                override fun onFastForward() {
                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration))
                }

                override fun onRewind() {
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                }

                override fun onStop() {
                    player.pause()
                }
            })
            isActive = true
        }
        mediaSession = session
    }

    private fun registerMediaReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_REWIND)
            addAction(ACTION_FORWARD)
        }

        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    ACTION_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    ACTION_FORWARD -> {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration))
                    }
                }
                updateNotification()
            }
        }
        receiver = r

        ContextCompat.registerReceiver(
            context,
            r,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun updateNotification() {
        val session = mediaSession ?: return
        val isPlaying = player.isPlaying

        // Update PlaybackState on MediaSession for lock screen and Bluetooth controls
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_STOP,
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                player.currentPosition,
                player.playbackParameters.speed,
            )
        session.setPlaybackState(stateBuilder.build())

        // Update MediaMetadata
        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration)
        if (season > 0 || episode > 0) {
            metaBuilder.putString(
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                "Season $season · Episode $episode",
            )
        }
        session.setMetadata(metaBuilder.build())

        // Build system MediaStyle notification
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val playPauseIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName),
            flag,
        )
        val rewindIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(ACTION_REWIND).setPackage(context.packageName),
            flag,
        )
        val forwardIntent = PendingIntent.getBroadcast(
            context,
            3,
            Intent(ACTION_FORWARD).setPackage(context.packageName),
            flag,
        )

        val activityIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            flag,
        )

        val notifBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        notifBuilder
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setContentTitle(title)
            .setContentText(if (season > 0 || episode > 0) "Season $season · Episode $episode" else "Now Playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(activityIntent)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_rew),
                    "Rewind 10s",
                    rewindIntent,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    ),
                    if (isPlaying) "Pause" else "Play",
                    playPauseIntent,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_media_ff),
                    "Forward 10s",
                    forwardIntent,
                ).build(),
            )

        notificationManager.notify(NOTIFICATION_ID, notifBuilder.build())
    }

    fun release() {
        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        receiver = null

        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null

        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "moviebox_playback_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_PLAY_PAUSE = "com.moviebox.app.action.PLAY_PAUSE"
        const val ACTION_REWIND = "com.moviebox.app.action.REWIND"
        const val ACTION_FORWARD = "com.moviebox.app.action.FORWARD"
    }
}
