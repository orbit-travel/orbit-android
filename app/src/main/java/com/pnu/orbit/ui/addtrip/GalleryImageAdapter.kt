package com.pnu.orbit.ui.addtrip

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pnu.orbit.R

/**
 * Grid adapter for the MediaStore-backed gallery picker. Single-select mode returns immediately on
 * tap; multi-select toggles up to [maxCount] and the host confirms via the Done button.
 */
class GalleryImageAdapter(
    private val maxCount: Int,
    private val onSinglePicked: (Uri) -> Unit,
    private val onSelectionChanged: () -> Unit,
    private val onMaxReached: () -> Unit,
) : RecyclerView.Adapter<GalleryImageAdapter.ViewHolder>() {

    private val images = mutableListOf<Uri>()
    private val selected = LinkedHashSet<Uri>()

    val selectedUris: List<Uri> get() = selected.toList()
    val selectedCount: Int get() = selected.size

    fun submit(uris: List<Uri>) {
        images.clear()
        images.addAll(uris)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount(): Int = images.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.galleryImage)
        private val scrim: View = itemView.findViewById(R.id.gallerySelectionScrim)
        private val badge: View = itemView.findViewById(R.id.gallerySelectionBadge)

        fun bind(uri: Uri) {
            Glide.with(image).load(uri).centerCrop().into(image)
            val isSelected = selected.contains(uri)
            scrim.visibility = if (isSelected) View.VISIBLE else View.GONE
            badge.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(uri) }
        }

        private fun onClick(uri: Uri) {
            if (maxCount <= 1) {
                onSinglePicked(uri)
                return
            }
            if (selected.contains(uri)) {
                selected.remove(uri)
            } else {
                if (selected.size >= maxCount) {
                    onMaxReached()
                    return
                }
                selected.add(uri)
            }
            notifyItemChanged(bindingAdapterPosition)
            onSelectionChanged()
        }
    }
}
