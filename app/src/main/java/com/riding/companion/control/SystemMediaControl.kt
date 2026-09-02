package com.riding.companion.control

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.view.KeyEvent
import androidx.media3.session.MediaButtonReceiver
import com.riding.companion.music.MusicController

/**
 * 系统媒体控制：
 * 1. 本 App 播放器有媒体 → 直接控制本 App；
 * 2. 否则控制系统当前活跃媒体会话（第三方音乐 APP）；
 * 3. 都没有 → 派发媒体按键事件。
 */
object SystemMediaControl {

    enum class Action { PLAY, PAUSE, NEXT, PREV, PLAY_PAUSE }

    fun getMaxVolume(ctx: Context): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun getVolume(ctx: Context): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(ctx: Context, level: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, max), 0)
    }

    fun volumeUp(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
    }

    fun volumeDown(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
    }

    fun sendMediaAction(ctx: Context, action: Action) {
        // 1. 本 App 播放器优先
        if (MusicController.hasMedia()) {
            when (action) {
                Action.PLAY -> if (!MusicController.isPlaying()) MusicController.playPause()
                Action.PAUSE -> if (MusicController.isPlaying()) MusicController.playPause()
                Action.PLAY_PAUSE -> MusicController.playPause()
                Action.NEXT -> MusicController.next()
                Action.PREV -> MusicController.prev()
            }
            return
        }

        // 2. 跨 APP：系统活跃媒体会话
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val cpn = ComponentName(ctx, MediaButtonReceiver::class.java)
                val controllers = msm.getActiveSessions(cpn)
                val target = controllers.firstOrNull { c -> c.playbackState?.state == 3 }
                    ?: controllers.firstOrNull()
                if (target != null) {
                    val tc = target.transportControls
                    when (action) {
                        Action.PLAY -> tc.play()
                        Action.PAUSE -> tc.pause()
                        Action.PLAY_PAUSE ->
                            if (target.playbackState?.state == 3) tc.pause() else tc.play()
                        Action.NEXT -> tc.skipToNext()
                        Action.PREV -> tc.skipToPrevious()
                    }
                    return
                }
            } catch (_: Exception) {
            }
        }

        // 3. 兜底：派发媒体按键
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (action) {
            Action.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            Action.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Action.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            Action.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Action.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
