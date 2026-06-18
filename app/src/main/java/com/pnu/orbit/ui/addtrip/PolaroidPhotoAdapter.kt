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
import com.pnu.orbit.domain.model.PhotoTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PolaroidPhotoAdapter(
    private val onPhotoChanged: (PhotoDraft) -> Unit,
    private val onPhotoLocationRequested: (PhotoDraft) -> Unit,
    private val onPhotoReplaceRequested: (PhotoDraft) -> Unit,
    private val onPhotoDeleteRequested: (PhotoDraft) -> Unit,
    private val onUsePreviousLocationRequested: (PhotoDraft) -> Unit,
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
        return ViewHolder(
            view,
            onPhotoChanged,
            onPhotoLocationRequested,
            onPhotoReplaceRequested,
            onPhotoDeleteRequested,
            onUsePreviousLocationRequested,
        )
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
        private val onPhotoReplaceRequested: (PhotoDraft) -> Unit,
        private val onPhotoDeleteRequested: (PhotoDraft) -> Unit,
        private val onUsePreviousLocationRequested: (PhotoDraft) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.polaroidImage)
        private val tagChip: TextView = itemView.findViewById(R.id.photoTagChip)
        private val comment: EditText = itemView.findViewById(R.id.inputPhotoComment)
        private val takenTime: TextView = itemView.findViewById(R.id.photoTakenTimeText)
        private val location: TextView = itemView.findViewById(R.id.photoLocationText)
        private val locationButton: Button = itemView.findViewById(R.id.buttonSelectPhotoLocation)
        private val usePreviousLocationButton: Button = itemView.findViewById(R.id.buttonUsePreviousLocation)
        private val changeButton: Button = itemView.findViewById(R.id.buttonChangePhoto)
        private val deleteButton: Button = itemView.findViewById(R.id.buttonDeletePhoto)

        /** The currently-bound photo, kept fresh so comment commits use the latest draft state. */
        private var boundPhoto: PhotoDraft? = null

        fun bind(photo: PhotoDraft) {
            boundPhoto = photo
            loadImage(photo)
            bindTag(photo.tag)
            // Tapping the photo toggles fill (centre-crop) vs fit (whole photo, black bars). No extra
            // button, per the polaroid concept.
            image.setOnClickListener { toggleCropMode() }

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
            // Commit any in-progress note before launching another screen / mutating the list so the
            // typed text is never dropped by the rebind that follows the structural change.
            locationButton.setOnClickListener {
                commitComment()
                boundPhoto?.let(onPhotoLocationRequested)
            }
            usePreviousLocationButton.setOnClickListener {
                commitComment()
                boundPhoto?.let(onUsePreviousLocationRequested)
            }
            changeButton.setOnClickListener {
                commitComment()
                boundPhoto?.let(onPhotoReplaceRequested)
            }
            deleteButton.setOnClickListener {
                boundPhoto?.let(onPhotoDeleteRequested)
            }
        }

        /** Shows the on-device ML scene tag as an overlay chip; hidden when UNKNOWN. */
        private fun bindTag(tag: PhotoTag) {
            val labelRes = when (tag) {
                PhotoTag.BUILDINGS -> R.string.photo_tag_buildings
                PhotoTag.FOREST -> R.string.photo_tag_forest
                PhotoTag.GLACIER -> R.string.photo_tag_glacier
                PhotoTag.MOUNTAIN -> R.string.photo_tag_mountain
                PhotoTag.SEA -> R.string.photo_tag_sea
                PhotoTag.STREET -> R.string.photo_tag_street
                PhotoTag.UNKNOWN -> null
            }
            if (labelRes == null) {
                tagChip.visibility = View.GONE
            } else {
                tagChip.setText(labelRes)
                tagChip.visibility = View.VISIBLE
            }
        }

        private fun bindComment(photo: PhotoDraft) {
            comment.onFocusChangeListener = null
            if (comment.text.toString() != photo.comment) {
                comment.setText(photo.comment)
            }
            comment.setOnFocusChangeListener { _, hasFocus ->
                // Focus can be lost while the list is rebinding (view recycling); defer the commit so
                // we never mutate the timeline re-entrantly during a layout pass.
                if (!hasFocus) itemView.post { commitComment() }
            }
        }

        private fun loadImage(photo: PhotoDraft) {
            image.scaleType = if (photo.cropToFill) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            val request = Glide.with(image).load(photo.uri)
            if (photo.cropToFill) request.centerCrop() else request.fitCenter()
            request.into(image)
        }

        /** Flips fill <-> fit for this photo, keeping any in-progress note. */
        private fun toggleCropMode() {
            val photo = boundPhoto ?: return
            val updatedComment = comment.text.toString()
            val toggled = photo.copy(
                comment = if (updatedComment != photo.comment) updatedComment else photo.comment,
                cropToFill = !photo.cropToFill,
            )
            boundPhoto = toggled
            loadImage(toggled) // immediate visual feedback before the list rebind
            onPhotoChanged(toggled)
        }

        /** Pushes the EditText content into the draft immediately (synchronously). */
        private fun commitComment() {
            val photo = boundPhoto ?: return
            val updatedComment = comment.text.toString()
            if (updatedComment != photo.comment) {
                val updated = photo.copy(comment = updatedComment)
                boundPhoto = updated
                onPhotoChanged(updated)
            }
        }

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        }
    }
}
