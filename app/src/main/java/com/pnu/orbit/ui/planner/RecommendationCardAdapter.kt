package com.pnu.orbit.ui.planner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Attraction

class RecommendationCardAdapter(
    private val onSelectToggled: (Attraction, Boolean) -> Unit
) : RecyclerView.Adapter<RecommendationCardAdapter.ViewHolder>() {

    private val items = mutableListOf<Attraction>()
    private var selectedItems = setOf<Attraction>()

    fun submitList(newItems: List<Attraction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateSelected(newSelected: Set<Attraction>) {
        selectedItems = newSelected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], selectedItems.contains(items[position]), onSelectToggled)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.recommendationName)
        private val description: TextView = itemView.findViewById(R.id.recommendationDescription)
        private val image: ImageView = itemView.findViewById(R.id.recommendationImage)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)
        private val btnApprove: MaterialButton = itemView.findViewById(R.id.btnApprove)
        private val selectionIndicator: TextView = itemView.findViewById(R.id.selectionIndicator)

        fun bind(
            attraction: Attraction,
            isSelected: Boolean,
            onSelectToggled: (Attraction, Boolean) -> Unit
        ) {
            name.text = attraction.name
            description.text = attraction.description

            val category = determineCategory(attraction.name, attraction.description)
            val details = getCategoryDetails(category)

            image.setImageResource(details.first)
            image.setColorFilter(details.second)
            name.setTextColor(details.second)

            // Render select state
            if (isSelected) {
                selectionIndicator.visibility = View.VISIBLE
                btnApprove.text = "선택됨"
                btnReject.visibility = View.VISIBLE
            } else {
                selectionIndicator.visibility = View.GONE
                btnApprove.text = "O 선택"
                // Let's show btnReject always as an action to deselect/reject
            }

            btnApprove.setOnClickListener {
                onSelectToggled(attraction, true)
            }

            btnReject.setOnClickListener {
                onSelectToggled(attraction, false)
            }
        }
    }
}
