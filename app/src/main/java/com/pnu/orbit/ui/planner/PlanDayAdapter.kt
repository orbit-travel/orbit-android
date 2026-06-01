package com.pnu.orbit.ui.planner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
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
        private val body: TextView = itemView.findViewById(R.id.dayBody)

        fun bind(plan: DayPlan) {
            title.text = "Day ${plan.day}"
            body.text = """
                Morning: ${plan.morning}
                Lunch: ${plan.lunch}
                Afternoon: ${plan.afternoon}
                Evening: ${plan.evening}
            """.trimIndent()
        }
    }
}
