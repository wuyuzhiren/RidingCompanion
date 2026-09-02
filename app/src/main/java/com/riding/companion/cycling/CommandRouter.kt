package com.riding.companion.cycling

import android.content.Context
import com.riding.companion.control.SystemMediaControl

/**
 * 骑行模式本地指令路由：
 * 先做精确包含匹配，再做编辑距离模糊匹配（相似度 >= 70% 即触发），
 * 适配风噪下识别偏差，实现“高置信度直执”。
 */
object CommandRouter {

    enum class Cmd(val label: String) {
        PLAY("播放音乐"),
        PAUSE("暂停"),
        NEXT("下一首"),
        PREV("上一首"),
        VOL_UP("音量调大"),
        VOL_DOWN("音量调小")
    }

    private data class Spec(val cmd: Cmd, val keywords: List<String>)

    private const val FUZZY_THRESHOLD = 0.7f

    private val specs = listOf(
        Spec(Cmd.PLAY, listOf("播放", "开始播放", "继续播放", "放歌", "放音乐", "播歌", "放首歌")),
        Spec(Cmd.PAUSE, listOf("暂停", "停止播放", "停一下", "别放了", "暂停播放", "不放了")),
        Spec(Cmd.NEXT, listOf("下一首", "切歌", "换一首", "下一曲", "换歌")),
        Spec(Cmd.PREV, listOf("上一首", "上一曲", "返回上一首")),
        Spec(Cmd.VOL_UP, listOf("调大音量", "音量加大", "声音大点", "大声一点", "音量调大", "大声点")),
        Spec(Cmd.VOL_DOWN, listOf("调小音量", "音量减小", "声音小点", "小声一点", "音量调小", "小声点"))
    )

    fun match(text: String): Cmd? {
        val t = text.trim()
        if (t.isEmpty()) return null
        // 精确包含优先
        for (s in specs) {
            for (k in s.keywords) {
                if (t.contains(k)) return s.cmd
            }
        }
        // 模糊匹配
        var best: Pair<Cmd, Float>? = null
        for (s in specs) {
            for (k in s.keywords) {
                val sim = similarity(t, k)
                if (sim >= FUZZY_THRESHOLD && (best == null || sim > best.second)) {
                    best = s.cmd to sim
                }
            }
        }
        return best?.first
    }

    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / maxLen
    }

    fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    fun execute(ctx: Context, cmd: Cmd) {
        when (cmd) {
            Cmd.PLAY -> SystemMediaControl.sendMediaAction(ctx, SystemMediaControl.Action.PLAY)
            Cmd.PAUSE -> SystemMediaControl.sendMediaAction(ctx, SystemMediaControl.Action.PAUSE)
            Cmd.NEXT -> SystemMediaControl.sendMediaAction(ctx, SystemMediaControl.Action.NEXT)
            Cmd.PREV -> SystemMediaControl.sendMediaAction(ctx, SystemMediaControl.Action.PREV)
            Cmd.VOL_UP -> SystemMediaControl.volumeUp(ctx)
            Cmd.VOL_DOWN -> SystemMediaControl.volumeDown(ctx)
        }
    }
}
