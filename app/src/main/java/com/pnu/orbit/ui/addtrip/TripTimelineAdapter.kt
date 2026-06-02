package com.pnu.orbit.ui.addtrip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TransportType

class TripTimelineAdapter(
    private val callbacks: Callbacks,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<TimelineDraft>()
    private var segmentCount: Int = 0
    private var recyclerView: RecyclerView? = null
    private var notifyPosted = false

    init {
        setHasStableIds(true)
    }

    fun submitList(newItems: List<TimelineDraft>) {
        items.clear()
        items.addAll(newItems)
        segmentCount = newItems.count { it is TransportTimelineDraft }
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

    override fun getItemId(position: Int): Long = items[position].draftId

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is TransportTimelineDraft -> VIEW_TYPE_TRANSPORT
            is PhotoTimelineDraft -> VIEW_TYPE_PHOTOS
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TRANSPORT -> TransportViewHolder(
                inflater.inflate(R.layout.item_transport_segment, parent, false),
                callbacks,
            )
            else -> PhotoBlockViewHolder(
                inflater.inflate(R.layout.item_photo_block, parent, false),
                callbacks,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when {
            holder is TransportViewHolder && item is TransportTimelineDraft -> {
                val segmentIndex = items.take(position + 1).count { it is TransportTimelineDraft }
                holder.bind(item.segment, segmentIndex, segmentCount)
            }
            holder is PhotoBlockViewHolder && item is PhotoTimelineDraft -> {
                holder.bind(item.photoBlock)
            }
        }
    }

    override fun getItemCount(): Int = items.size

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

    class TransportViewHolder(
        itemView: View,
        private val callbacks: Callbacks,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.segmentTitle)
        private val group: RadioGroup = itemView.findViewById(R.id.transportTypeGroup)
        private val radioFlight: RadioButton = itemView.findViewById(R.id.radioFlight)
        private val radioTrain: RadioButton = itemView.findViewById(R.id.radioTrain)
        private val radioCar: RadioButton = itemView.findViewById(R.id.radioCar)
        private val departure: Button = itemView.findViewById(R.id.buttonSelectDeparture)
        private val arrival: Button = itemView.findViewById(R.id.buttonSelectArrival)
        private val delete: Button = itemView.findViewById(R.id.buttonDeleteSegment)

        fun bind(segment: TransportSegmentDraft, segmentIndex: Int, segmentCount: Int) {
            val context = itemView.context
            group.setOnCheckedChangeListener(null)

            if (segment.type == TransportType.ACCOMMODATION) {
                title.text = context.getString(R.string.segment_stay_label, segmentIndex)
                group.visibility = View.GONE
                arrival.visibility = View.GONE
                departure.text = segment.departureName.takeIf { it.isNotBlank() }?.let {
                    context.getString(R.string.segment_place_selected, it)
                } ?: context.getString(R.string.segment_place_empty)
            } else {
                title.text = context.getString(R.string.segment_label, segmentIndex)
                group.visibility = View.VISIBLE
                arrival.visibility = View.VISIBLE
                when (segment.type) {
                    TransportType.TRAIN -> radioTrain.isChecked = true
                    TransportType.CAR -> radioCar.isChecked = true
                    else -> radioFlight.isChecked = true
                }
                group.setOnCheckedChangeListener { _, checkedId ->
                    val type = when (checkedId) {
                        R.id.radioTrain -> TransportType.TRAIN
                        R.id.radioCar -> TransportType.CAR
                        else -> TransportType.FLIGHT
                    }
                    callbacks.onTransportTypeChanged(segment.copy(type = type))
                }
                departure.text = segment.departureName.takeIf { it.isNotBlank() }?.let {
                    context.getString(R.string.segment_departure_selected, it)
                } ?: context.getString(R.string.segment_departure_empty)
                arrival.text = segment.arrivalName.takeIf { it.isNotBlank() }?.let {
                    context.getString(R.string.segment_arrival_selected, it)
                } ?: context.getString(R.string.segment_arrival_empty)
            }

            departure.setOnClickListener {
                callbacks.onTransportEndpointRequested(segment, isDeparture = true)
            }
            arrival.setOnClickListener {
                callbacks.onTransportEndpointRequested(segment, isDeparture = false)
            }
            delete.isEnabled = segmentCount > 1
            delete.alpha = if (segmentCount > 1) 1f else 0.45f
            delete.setOnClickListener {
                callbacks.onTimelineItemDeleted(segment.draftId)
            }
        }
    }

    class PhotoBlockViewHolder(
        itemView: View,
        private val callbacks: Callbacks,
    ) : RecyclerView.ViewHolder(itemView) {
        private val pager: ViewPager2 = itemView.findViewById(R.id.photoBlockPager)
        private val empty: TextView = itemView.findViewById(R.id.photoBlockEmpty)
        private val delete: Button = itemView.findViewById(R.id.buttonDeletePhotoBlock)
        private val adapter = PolaroidPhotoAdapter(
            onPhotoChanged = callbacks::onPhotoChanged,
            onPhotoLocationRequested = callbacks::onPhotoLocationRequested,
        )

        init {
            pager.adapter = adapter
            pager.offscreenPageLimit = 1
        }

        fun bind(block: PhotoBlockDraft) {
            adapter.submitList(block.photos)
            empty.visibility = if (block.photos.isEmpty()) View.VISIBLE else View.GONE
            delete.setOnClickListener {
                callbacks.onTimelineItemDeleted(block.draftId)
            }
        }
    }

    interface Callbacks {
        fun onTransportTypeChanged(segment: TransportSegmentDraft)
        fun onTransportEndpointRequested(segment: TransportSegmentDraft, isDeparture: Boolean)
        fun onPhotoChanged(photo: PhotoDraft)
        fun onPhotoLocationRequested(photo: PhotoDraft)
        fun onTimelineItemDeleted(itemId: Long)
    }

    companion object {
        private const val VIEW_TYPE_TRANSPORT = 1
        private const val VIEW_TYPE_PHOTOS = 2
    }
}
