package com.riding.companion.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.riding.companion.R
import com.riding.companion.databinding.ItemChatMsgBinding

data class ChatItem(val isUser: Boolean, var text: String)

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    val items = mutableListOf<ChatItem>()

    class VH(val binding: ItemChatMsgBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChatMsgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.bubble.text = item.text
        if (item.isUser) {
            b.msgRow.gravity = Gravity.END
            b.bubble.setBackgroundResource(R.drawable.bg_bubble_user)
            b.bubble.setTextColor(Color.WHITE)
        } else {
            b.msgRow.gravity = Gravity.START
            b.bubble.setBackgroundResource(R.drawable.bg_bubble_ai)
            b.bubble.setTextColor(Color.parseColor("#1A1A1A"))
        }
    }

    override fun getItemCount(): Int = items.size

    fun add(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun updateLast(text: String) {
        if (items.isNotEmpty()) {
            items[items.size - 1].text = text
            notifyItemChanged(items.size - 1)
        }
    }
}
