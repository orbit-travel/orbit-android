package com.pnu.orbit.ui.planner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan

class PlanDayAdapter : RecyclerView.Adapter<PlanDayAdapter.ViewHolder>() {
    private val items = mutableListOf<DayPlan>()

    fun submitList(newItems: List<DayPlan>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plan_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dayTitle)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.attractionsRecyclerView)

        fun bind(plan: DayPlan) {
            title.text = "Day ${plan.day}"
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
            val adapter = AttractionAdapter()
            recyclerView.adapter = adapter
            @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
            adapter.submitList(plan.attractions?.mapNotNull { it } ?: emptyList())
        }
    }
}

class AttractionAdapter : RecyclerView.Adapter<AttractionAdapter.ViewHolder>() {
    private val items = mutableListOf<Attraction>()

    fun submitList(newItems: List<Attraction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attraction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sequence: TextView = itemView.findViewById(R.id.attractionSequence)
        private val name: TextView = itemView.findViewById(R.id.attractionName)
        private val description: TextView = itemView.findViewById(R.id.attractionDescription)
        private val image: ImageView = itemView.findViewById(R.id.attractionImage)

        fun bind(attraction: Attraction) {
            sequence.text = attraction.sequence.toString()
            name.text = attraction.name
            description.text = attraction.description

            val fallbackUrl = "https://loremflickr.com/320/240/travel,${attraction.name.replace(" ", "")}"
            val imageUrlToLoad = attraction.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl

            Glide.with(itemView.context)
                .load(imageUrlToLoad)
                .placeholder(R.color.orbit_surface_light)
                .error(R.color.orbit_surface_light)
                .centerCrop()
                .into(image)
        }
    }
}
