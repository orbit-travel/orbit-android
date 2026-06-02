package com.pnu.orbit.ui.record

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Trip

class TripPreviewAdapter(
    private val onTripClick: (Trip) -> Unit,
    private val onEditClick: (Trip) -> Unit,
    private val onDeleteClick: (Trip) -> Unit,
) : RecyclerView.Adapter<TripPreviewAdapter.ViewHolder>() {
    private val items = mutableListOf<Trip>()

    fun submitList(newItems: List<Trip>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
        return ViewHolder(view, onTripClick, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onTripClick: (Trip) -> Unit,
        private val onEditClick: (Trip) -> Unit,
        private val onDeleteClick: (Trip) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tripTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tripSubtitle)
        private val editButton: TextView = itemView.findViewById(R.id.buttonEditTrip)
        private val deleteButton: TextView = itemView.findViewById(R.id.buttonDeleteTrip)

        fun bind(trip: Trip) {
            title.text = trip.title
            subtitle.text = itemView.context.getString(
                R.string.trip_subtitle,
                trip.destination,
                trip.photoCount,
                trip.memo ?: itemView.context.getString(R.string.trip_memo_empty),
            )
            itemView.setOnClickListener { onTripClick(trip) }
            editButton.setOnClickListener { onEditClick(trip) }
            deleteButton.setOnClickListener { onDeleteClick(trip) }
        }
    }
}
