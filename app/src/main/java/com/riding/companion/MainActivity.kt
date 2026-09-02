package com.riding.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riding.companion.audio.VoiceController
import com.riding.companion.databinding.ActivityMainBinding
import com.riding.companion.ui.ChatFragment
import com.riding.companion.ui.MusicFragment
import com.riding.companion.ui.SettingsFragment
import com.riding.companion.ui.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chatFragment = ChatFragment()
    private val musicFragment = MusicFragment()
    private val settingsFragment = SettingsFragment()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        applySystemBarInsets()
        showLastCrashIfAny()

        try {
            VoiceController.init(this)
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, chatFragment, "chat")
                    .add(R.id.fragment_container, musicFragment, "music")
                    .add(R.id.fragment_container, settingsFragment, "settings")
                    .hide(musicFragment)
                    .hide(settingsFragment)
                    .commit()
            }

            binding.bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_chat -> showFragment(chatFragment)
                    R.id.nav_music -> showFragment(musicFragment)
                    R.id.nav_settings -> showFragment(settingsFragment)
                }
                true
            }

            requestNeededPermissions()
            autoCheckUpdate()
        } catch (e: Exception) {
            Log.e("RidingCompanion", "MainActivity init error", e)
            MaterialAlertDialogBuilder(this)
                .setTitle("初始化出错")
                .setMessage("${e.javaClass.simpleName}: ${e.message}")
                .setPositiveButton("知道了", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun autoCheckUpdate() {
        lifecycleScope.launch {
            delay(4000)
            if (!isFinishing && !isDestroyed) {
                UpdateManager.checkForUpdate(this@MainActivity, manual = false)
            }
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun showLastCrashIfAny() {
        try {
            val f = RidingApp.crashFile(this)
            if (f.exists()) {
                val text = f.readText().trim()
                if (text.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("上次运行崩溃信息（已自动记录）")
                        .setMessage(text.take(2500))
                        .setPositiveButton("清除并继续") { _, _ -> f.delete() }
                        .setNegativeButton("保留", null)
                        .setCancelable(false)
                        .show()
                } else {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showFragment(target: Fragment) {
        val ft = supportFragmentManager.beginTransaction()
        listOf(chatFragment, musicFragment, settingsFragment).forEach {
            if (it === target) ft.show(it) else ft.hide(it)
        }
        ft.commit()
    }

    private fun requestNeededPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            permissionLauncher.launch(need.toTypedArray())
        }
    }

    override fun onDestroy() {
        VoiceController.shutdown()
        super.onDestroy()
    }
}
