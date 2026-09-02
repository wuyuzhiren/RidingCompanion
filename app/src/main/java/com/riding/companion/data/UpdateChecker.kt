package com.riding.companion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置更新：从 GitHub Releases 拉取最新版本，比较版本号，下载 APK 并触发安装。
 * 仓库: github.com/wuyuzhiren/RidingCompanion （由 GitHub Actions 自动构建发版）
 */
object UpdateChecker {

    const val REPO_OWNER = "wuyuzhiren"
    const val REPO_NAME = "RidingCompanion"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val notes: String
    )

    /** 调用 GitHub Releases API 获取最新版本（公开仓库免鉴权） */
    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "RidingCompanion")
                if (conn.responseCode != 200) return@withContext null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val jo = JSONObject(text)
                val tag = jo.optString("tag_name", "").removePrefix("v")
                if (tag.isBlank()) return@withContext null
                val assets = jo.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrEmpty()) return@withContext null
                UpdateInfo(
                    versionName = tag,
                    versionCode = parseBuildCode(jo.optString("body", "")),
                    apkUrl = apkUrl,
                    notes = jo.optString("body", "").take(600)
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseBuildCode(body: String): Int =
        Regex("build\\s*[（(]?\\s*([0-9]+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** 语义化版本比较：remote > local 返回 true */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').mapNotNull { it.toIntOrNull() }
        val l = local.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    /** 下载 APK 到文件（跟随重定向） */
    suspend fun download(apkUrl: String, dest: File) = withContext(Dispatchers.IO) {
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "RidingCompanion")
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("下载失败，HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 通过系统安装器安装 APK */
    fun install(context: Context, apkFile: File): Boolean {
        return try {
            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
