package com.riding.companion.cycling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.riding.companion.MainActivity
import com.riding.companion.R
import com.riding.companion.control.SystemMediaControl
import com.riding.companion.data.AppConfig
import com.riding.companion.music.MusicController

/**
 * 骑行模式保活前台服务：锁屏常驻通知，降低系统查杀概率；
 * 通知栏可直接播放/暂停、关闭骑行模式。
 */
class CyclingService : Service() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.riding.companion.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.riding.companion.action.STOP_CYCLING"
        const val CHANNEL = "cycling"
        const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, CyclingService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CyclingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        // 注意：不在骑行模式启动时提前拉起音乐服务，
        // 避免“前台服务已启动但未播放、未调用 startForeground”被系统判定为违规。
        // 音乐服务会在用户真正播放音乐时由 MusicController.loadAndPlay 自动启动。
        val autoVol = AppConfig.cyclingAutoVolume
        if (autoVol > 0) {
            SystemMediaControl.setVolume(this, autoVol)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                MusicController.playPause()
                BeepHelper.beep(this)
                notificationManager().notify(NOTIF_ID, buildNotification())
            }
            ACTION_STOP -> {
                AppConfig.cyclingMode = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL,
                getString(R.string.notification_channel_cycling),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager().createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ppIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CyclingService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, CyclingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_cycling_title))
            .setContentText(getString(R.string.notification_cycling_text))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "播放/暂停", ppIntent)
            .addAction(0, "关闭", stopIntent)
            .build()
    }
}
