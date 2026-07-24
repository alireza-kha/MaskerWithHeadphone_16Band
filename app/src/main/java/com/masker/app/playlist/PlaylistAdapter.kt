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
    private val onSelectionModeChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    /** فقط وقتی واقعاً تغییر کند رفرش می‌شود، تا هر بار به‌روزرسانی نوار پیشرفت (هر ۵۰۰ میلی‌ثانیه)
     * باعث ری‌استارت شدن انیمیشن «رقص نور» نشود */
    var playingTrackId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** شناسه‌های آهنگ‌های انتخاب‌شده با چک‌باکس روی هر آیتم، برای «حذف انتخاب‌شده‌ها» */
    private val selectedIds = mutableSetOf<String>()

    /** فقط با نگه‌داشتن طولانی روی یک آهنگ فعال می‌شود؛ در غیر این صورت نه چک‌باکس‌ها و نه
     * منوی انتخاب دیده نمی‌شوند و ضربه ساده روی هر آهنگ فقط آن را پخش می‌کند */
    var selectionModeActive = false
        private set

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.map { it.id })
        notifyDataSetChanged()
    }

    /** خروج کامل از حالت انتخاب: هم تیک‌ها پاک می‌شوند، هم چک‌باکس‌ها و منو دوباره پنهان می‌شوند */
    fun exitSelectionMode() {
        selectedIds.clear()
        if (!selectionModeActive) return
        selectionModeActive = false
        onSelectionModeChanged(false)
        notifyDataSetChanged()
    }

    /** پس از حذف موفق آهنگ‌های انتخاب‌شده هم باید از حالت انتخاب خارج شویم */
    fun onItemsDeleted() {
        exitSelectionMode()
    }

    private fun toggleSelection(trackId: String) {
        if (!selectedIds.add(trackId)) selectedIds.remove(trackId)
    }

    private fun handleLongPress(position: Int) {
        val trackId = items.getOrNull(position)?.id ?: return
        if (!selectionModeActive) {
            selectionModeActive = true
            selectedIds.add(trackId)
            onSelectionModeChanged(true)
        } else {
            toggleSelection(trackId)
        }
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

        b.trackSelectCheckbox.visibility = if (selectionModeActive) View.VISIBLE else View.GONE
        b.trackSelectCheckbox.setOnCheckedChangeListener(null)
        b.trackSelectCheckbox.isChecked = item.id in selectedIds
        b.trackSelectCheckbox.setOnCheckedChangeListener { _, _ ->
            toggleSelection(item.id)
        }

        b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selectionModeActive) {
                toggleSelection(items[pos].id)
                notifyItemChanged(pos)
            } else {
                onClick(pos)
            }
        }
        b.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) handleLongPress(pos)
            true
        }
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
