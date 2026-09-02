package com.riding.companion.cycling

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object BeepHelper {
    fun beep(ctx: Context, tone: Int = ToneGenerator.TONE_PROP_BEEP2) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(tone, 120)
            Thread {
                try {
                    Thread.sleep(250)
                    tg.release()
                } catch (_: Exception) {
                }
            }.start()
        } catch (_: Exception) {
        }
    }
}
