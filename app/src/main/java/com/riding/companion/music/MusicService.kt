package com.riding.companion.music

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 音乐播放前台服务：基于 Media3 ExoPlayer + MediaSessionService，
 * 自带系统媒体会话 / 锁屏控件 / 通知栏控制。
 */
class MusicService : MediaSessionService() {

    lateinit var player: ExoPlayer
        private set
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        createChannels()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .build()
        MusicController.onPlayerReady(this)
    }

    override fun onGetSession(controller: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        MusicController.onPlayerDestroy()
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val musicChannel = android.app.NotificationChannel(
                "media_session_service",
                getString(com.riding.companion.R.string.notification_channel_music),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(musicChannel)
            val legacyChannel = android.app.NotificationChannel(
                "player_service",
                getString(com.riding.companion.R.string.notification_channel_music),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(legacyChannel)
        }
    }
}
