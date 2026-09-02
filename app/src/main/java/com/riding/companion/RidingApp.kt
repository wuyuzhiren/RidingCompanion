package com.riding.companion

import android.app.Application
import android.content.Context
import android.util.Log
import com.riding.companion.data.AppConfig
import com.riding.companion.music.MusicController
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class RidingApp : Application() {

    companion object {
        lateinit var instance: RidingApp
            private set

        /** 崩溃日志路径（App 专属外部存储，文件管理器可见） */
        fun crashFile(ctx: Context): File =
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "crash.log")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
        try {
            AppConfig.init(this)
            MusicController.init(this)
        } catch (e: Exception) {
            Log.e("RidingCompanion", "init error", e)
        }
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    append("time=").append(System.currentTimeMillis()).append('\n')
                    append("thread=").append(thread.name).append('\n')
                    append("msg=").append(throwable.message).append('\n')
                    append(sw.toString())
                }
                crashFile(this).writeText(text)
                Log.e("RidingCompanion", "CRASH: ${throwable.message}", throwable)
            } catch (_: Exception) {
            }
            default?.uncaughtException(thread, throwable)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
