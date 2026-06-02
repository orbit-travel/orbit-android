package com.pnu.orbit.ui.addtrip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pnu.orbit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PolaroidPhotoAdapter(
    private val onPhotoChanged: (PhotoDraft) -> Unit,
    private val onPhotoLocationRequested: (PhotoDraft) -> Unit,
) : RecyclerView.Adapter<PolaroidPhotoAdapter.ViewHolder>() {
    private val photos = mutableListOf<PhotoDraft>()
    private var recyclerView: RecyclerView? = null
    private var notifyPosted = false

    init {
        setHasStableIds(true)
    }

    fun submitList(newPhotos: List<PhotoDraft>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChangedSafely()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView == recyclerView) {
            this.recyclerView = null
        }
    }

    override fun getItemId(position: Int): Long = photos[position].draftId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_polaroid_photo, parent, false)
        return ViewHolder(view, onPhotoChanged, onPhotoLocationRequested)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    private fun notifyDataSetChangedSafely() {
        val attachedRecyclerView = recyclerView
        val shouldPost = attachedRecyclerView != null &&
            (attachedRecyclerView.isComputingLayout ||
                attachedRecyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE)

        if (!shouldPost) {
            notifyPosted = false
            notifyDataSetChanged()
            return
        }

        if (notifyPosted) return
        notifyPosted = true
        attachedRecyclerView.post {
            notifyPosted = false
            notifyDataSetChangedSafely()
        }
    }

    class ViewHolder(
        itemView: View,
        private val onPhotoChanged: (PhotoDraft) -> Unit,
        private val onPhotoLocationRequested: (PhotoDraft) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.polaroidImage)
        private val comment: EditText = itemView.findViewById(R.id.inputPhotoComment)
        private val takenTime: TextView = itemView.findViewById(R.id.photoTakenTimeText)
        private val location: TextView = itemView.findViewById(R.id.photoLocationText)
        private val locationButton: Button = itemView.findViewById(R.id.buttonSelectPhotoLocation)

        fun bind(photo: PhotoDraft) {
            Glide.with(image)
                .load(photo.uri)
                .fitCenter()
                .into(image)

            bindComment(photo)
            takenTime.text = photo.takenAt?.let {
                itemView.context.getString(R.string.photo_taken_time_label, dateFormat.format(Date(it)))
            } ?: itemView.context.getString(R.string.photo_taken_time_unknown)
            location.text = photo.locationName?.let {
                itemView.context.getString(R.string.photo_location_label, it)
            } ?: if (photo.lat != null && photo.lng != null) {
                itemView.context.getString(R.string.photo_location_gps, photo.lat, photo.lng)
            } else {
                itemView.context.getString(R.string.photo_location_unknown)
            }
            locationButton.setOnClickListener { onPhotoLocationRequested(photo) }
        }

        private fun bindComment(photo: PhotoDraft) {
            comment.onFocusChangeListener = null
            if (comment.text.toString() != photo.comment) {
                comment.setText(photo.comment)
            }
            comment.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val updatedComment = comment.text.toString()
                    if (updatedComment != photo.comment) {
                        itemView.post {
                            onPhotoChanged(photo.copy(comment = updatedComment))
                        }
                    }
                }
            }
        }

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        }
    }
}
