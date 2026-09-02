package com.riding.companion.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riding.companion.R
import com.riding.companion.data.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

/**
 * 内置更新流程：检查 GitHub 最新版 → 弹窗提示 → 下载 → 系统安装。
 */
object UpdateManager {

    private var downloading = false

    fun checkForUpdate(activity: AppCompatActivity, manual: Boolean) {
        if (downloading) return
        activity.lifecycleScope.launch {
            val local = currentVersion(activity)
            val remote = UpdateChecker.checkLatest()
            if (remote == null) {
                if (manual) {
                    Toast.makeText(activity, "检查更新失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            if (!UpdateChecker.isNewer(remote.versionName, local)) {
                if (manual) {
                    Toast.makeText(activity, R.string.update_none, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            showUpdateDialog(activity, remote)
        }
    }

    private fun currentVersion(activity: AppCompatActivity): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    private fun showUpdateDialog(activity: AppCompatActivity, info: UpdateChecker.UpdateInfo) {
        val local = currentVersion(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_found_title, info.versionName))
            .setMessage("当前版本 v$local\n\n${info.notes.ifBlank { "新版本已发布" }}")
            .setPositiveButton(R.string.update_download) { _, _ -> doDownload(activity, info) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun doDownload(activity: AppCompatActivity, info: UpdateChecker.UpdateInfo) {
        if (!canInstall(activity)) {
            promptInstallPermission(activity)
            return
        }
        downloading = true
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_downloading)
            .setMessage("请稍候…")
            .setCancelable(false)
            .show()
        activity.lifecycleScope.launch {
            try {
                val dest = File(
                    activity.getExternalFilesDir("updates") ?: activity.filesDir,
                    "update.apk"
                )
                UpdateChecker.download(info.apkUrl, dest)
                progress.dismiss()
                downloading = false
                Toast.makeText(activity, R.string.update_downloaded, Toast.LENGTH_SHORT).show()
                UpdateChecker.install(activity, dest)
            } catch (e: Exception) {
                progress.dismiss()
                downloading = false
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_fail, e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun canInstall(activity: AppCompatActivity): Boolean =
        if (Build.VERSION.SDK_INT >= 26) activity.packageManager.canRequestPackageInstalls()
        else true

    private fun promptInstallPermission(activity: AppCompatActivity) {
        Toast.makeText(activity, R.string.update_install_permission, Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
