package com.masker.app.playlist

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.masker.app.databinding.ItemPlaylistTrackBinding

class PlaylistAdapter(
    private val items: MutableList<PlaylistTrack>,
    private val onClick: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    var playingTrackId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class ViewHolder(val binding: ItemPlaylistTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.trackTitleText.text = item.title
        val isPlaying = item.id == playingTrackId
        b.trackTitleText.setTypeface(null, if (isPlaying) Typeface.BOLD else Typeface.NORMAL)
        b.root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        b.removeTrackButton.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<PlaylistTrack>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
