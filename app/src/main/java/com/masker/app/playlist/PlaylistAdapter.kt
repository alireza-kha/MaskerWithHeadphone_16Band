package com.masker.app.playlist

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.recyclerview.widget.RecyclerView
import com.masker.app.R
import com.masker.app.databinding.ItemPlaylistTrackBinding

class PlaylistAdapter(
    private val items: MutableList<PlaylistTrack>,
    private val onClick: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    /** فقط وقتی واقعاً تغییر کند رفرش می‌شود، تا هر بار به‌روزرسانی نوار پیشرفت (هر ۵۰۰ میلی‌ثانیه)
     * باعث ری‌استارت شدن انیمیشن «رقص نور» نشود */
    var playingTrackId: String? = null
        set(value) {
            if (field == value) return
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
        val context = holder.itemView.context

        b.trackTitleText.text = item.title
        val isPlaying = item.id == playingTrackId
        b.trackTitleText.setTypeface(null, if (isPlaying) Typeface.BOLD else Typeface.NORMAL)

        val thumbnail = PlaylistThumbnails.loadBitmap(context, item.id)
        if (thumbnail != null) {
            val rounded = RoundedBitmapDrawableFactory.create(context.resources, thumbnail)
            rounded.isCircular = true
            b.trackThumbnailImage.setImageDrawable(rounded)
        } else {
            b.trackThumbnailImage.setImageResource(R.drawable.ic_music_note_placeholder)
        }

        if (isPlaying) {
            b.trackPlayingIndicator.visibility = View.VISIBLE
            b.trackPlayingIndicator.start()
        } else {
            b.trackPlayingIndicator.visibility = View.GONE
            b.trackPlayingIndicator.stop()
        }

        b.root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        b.removeTrackButton.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.binding.trackPlayingIndicator.stop()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<PlaylistTrack>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
