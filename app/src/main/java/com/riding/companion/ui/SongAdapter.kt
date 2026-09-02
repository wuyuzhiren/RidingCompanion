package com.riding.companion.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riding.companion.databinding.ItemSongBinding
import com.riding.companion.music.Song

class SongAdapter(
    private val onPlay: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    val songs = mutableListOf<Song>()

    class VH(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = songs[position]
        holder.binding.songTitle.text = song.title
        holder.binding.songUrl.text = song.url
        holder.binding.root.setOnClickListener { onPlay(position) }
        holder.binding.btnRemove.setOnClickListener { onRemove(position) }
    }

    override fun getItemCount(): Int = songs.size

    fun refresh(newList: List<Song>) {
        songs.clear()
        songs.addAll(newList)
        notifyDataSetChanged()
    }
}
