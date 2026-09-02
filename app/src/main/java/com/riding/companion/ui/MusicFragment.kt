package com.riding.companion.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riding.companion.R
import com.riding.companion.control.SystemMediaControl
import com.riding.companion.databinding.FragmentMusicBinding
import com.riding.companion.music.MusicController
import com.riding.companion.music.MusicRepository
import com.riding.companion.music.Song

class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    private var listenerAdded = false
    private var volumeSyncing = false

    private val adapter = SongAdapter(
        onPlay = { i -> MusicController.loadAndPlay(MusicRepository.load(requireContext()), i) },
        onRemove = { i ->
            val list = MusicRepository.load(requireContext())
            if (i in list.indices) {
                list.removeAt(i)
                MusicRepository.save(requireContext(), list)
                refreshSongs()
            }
        }
    )

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) addSongFromUri(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.songList.layoutManager = LinearLayoutManager(requireContext())
        binding.songList.adapter = adapter
        refreshSongs()
        setupControls()
        if (!listenerAdded) {
            MusicController.addListener { updatePlayerUI() }
            listenerAdded = true
        }
        updatePlayerUI()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            if (MusicController.hasMedia()) {
                MusicController.playPause()
            } else {
                val songs = MusicRepository.load(requireContext())
                if (songs.isNotEmpty()) MusicController.loadAndPlay(songs, 0)
                else showAddHint()
            }
        }
        binding.btnPrev.setOnClickListener { MusicController.prev() }
        binding.btnNext.setOnClickListener { MusicController.next() }

        binding.btnAddUrl.setOnClickListener { showAddUrlDialog() }
        binding.btnAddLocal.setOnClickListener {
            filePicker.launch(arrayOf("audio/*", "application/ogg", "application/x-flac"))
        }

        // 系统音量
        val max = SystemMediaControl.getMaxVolume(requireContext())
        binding.volumeSeek.max = max
        syncVolume()
        binding.volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !volumeSyncing) {
                    SystemMediaControl.setVolume(requireContext(), progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.btnVolUp.setOnClickListener {
            SystemMediaControl.volumeUp(requireContext())
            syncVolume()
        }
        binding.btnVolDown.setOnClickListener {
            SystemMediaControl.volumeDown(requireContext())
            syncVolume()
        }
    }

    private fun syncVolume() {
        volumeSyncing = true
        binding.volumeSeek.progress = SystemMediaControl.getVolume(requireContext())
        volumeSyncing = false
    }

    private fun refreshSongs() {
        adapter.refresh(MusicRepository.load(requireContext()))
    }

    private fun updatePlayerUI() {
        _binding?.let { b ->
            b.nowPlayingTitle.text =
                if (MusicController.hasMedia() && MusicController.currentTitle().isNotEmpty())
                    MusicController.currentTitle()
                else getString(R.string.music_none)
            b.btnPlayPause.setImageResource(
                if (MusicController.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
            )
            syncVolume()
        }
    }

    private fun showAddUrlDialog() {
        val input = EditText(requireContext())
        input.hint = getString(R.string.music_add_url_hint)
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.music_add_url)
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val songs = MusicRepository.load(requireContext())
                    songs.add(Song(url.substringAfterLast('/').ifBlank { url }, url))
                    MusicRepository.save(requireContext(), songs)
                    refreshSongs()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddHint() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.music_playlist)
            .setMessage("播放列表还是空的，请先“添加网址”或“从本地选择”音乐。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun addSongFromUri(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        var name = uri.lastPathSegment ?: "本地音乐"
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx) ?: name
                }
            }
        } catch (_: Exception) {
        }
        val songs = MusicRepository.load(requireContext())
        songs.add(Song(name, uri.toString()))
        MusicRepository.save(requireContext(), songs)
        refreshSongs()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let { updatePlayerUI() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
