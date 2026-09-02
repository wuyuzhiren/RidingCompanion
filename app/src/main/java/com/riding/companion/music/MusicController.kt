package com.riding.companion.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.riding.companion.data.AppConfig

/**
 * 音乐控制器：与 MusicService 绑定，向 UI / 语音指令 / 骑行模式暴露统一接口。
 */
object MusicController {

    private var app: Context? = null
    private var service: MusicService? = null
    private var connected = false
    private var pending: (() -> Unit)? = null
    private val listeners = mutableListOf<() -> Unit>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connected = true
            pending?.let { pending = null; it() }
            notifyChange()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    fun ensureStarted() {
        val ctx = app ?: return
        ContextCompat.startForegroundService(ctx, Intent(ctx, MusicService::class.java))
        if (!connected) {
            try {
                ctx.bindService(Intent(ctx, MusicService::class.java), connection, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {
            }
        }
    }

    fun onPlayerReady(svc: MusicService) {
        service = svc
        pending?.let { pending = null; it() }
        notifyChange()
    }

    fun onPlayerDestroy() {
        service = null
        notifyChange()
    }

    private fun player(): ExoPlayer? = service?.player

    fun hasMedia(): Boolean = player()?.mediaItemCount?.let { it > 0 } ?: false

    fun loadAndPlay(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        ensureStarted()
        val p = player()
        if (p == null) {
            pending = { loadAndPlay(songs, startIndex) }
            return
        }
        val items = songs.map { MediaItem.fromUri(it.url) }
        p.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        p.prepare()
        p.playWhenReady = true
        notifyChange()
    }

    fun playPause() {
        val p = player()
        if (p != null) {
            if (p.isPlaying) p.pause() else p.play()
            notifyChange()
            return
        }
        // 音乐服务尚未就绪：若本地有播放列表则直接载入并播放
        val ctx = app ?: return
        val songs = MusicRepository.load(ctx)
        if (songs.isNotEmpty()) {
            loadAndPlay(songs, 0)
        }
    }

    fun next() {
        player()?.seekToNextMediaItem()
        notifyChange()
    }

    fun prev() {
        player()?.seekToPreviousMediaItem()
        notifyChange()
    }

    fun isPlaying(): Boolean = player()?.isPlaying == true

    fun currentTitle(): String {
        val p = player() ?: return ""
        val item = p.currentMediaItem ?: return ""
        val title = item.mediaMetadata.title?.toString()
        val desc = item.mediaMetadata.description?.toString()
        return when {
            !title.isNullOrEmpty() -> title
            !desc.isNullOrEmpty() -> desc
            else -> item.mediaId
        }
    }

    /** TTS 说话时把音乐音量闪避到设置值，说完恢复。 */
    fun setDuck(on: Boolean) {
        val p = player() ?: return
        p.volume = if (on) AppConfig.duckLevel.coerceIn(0, 100) / 100f else 1f
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun notifyChange() {
        listeners.forEach { runCatching { it() } }
    }
}
