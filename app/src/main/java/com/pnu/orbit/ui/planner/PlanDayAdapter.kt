package com.pnu.orbit.ui.planner

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.TimeType

class PlanDayAdapter(
    private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
    private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
    private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
    private val onAddPlace: (dayNum: Int) -> Unit,
    private val onCardClick: (Attraction) -> Unit,
    private val onDetailsClick: (Attraction) -> Unit,
    private val onReorder: (dayNum: Int, fromIndex: Int, toIndex: Int) -> Unit,
) : RecyclerView.Adapter<PlanDayAdapter.ViewHolder>() {
    private val items = mutableListOf<DayPlan>()
    private var editable: Boolean = true

    fun submitList(newItems: List<DayPlan>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setEditable(enabled: Boolean) {
        editable = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plan_day, parent, false)
        return ViewHolder(
            itemView = view,
            onDelete = onDelete,
            onTimeUpdated = onTimeUpdated,
            onMoveDay = onMoveDay,
            onAddPlace = onAddPlace,
            onCardClick = onCardClick,
            onDetailsClick = onDetailsClick,
            onReorder = onReorder,
            isEditable = { editable },
        ) { items.size }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
        private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
        private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
        private val onAddPlace: (dayNum: Int) -> Unit,
        private val onCardClick: (Attraction) -> Unit,
        private val onDetailsClick: (Attraction) -> Unit,
        private val onReorder: (dayNum: Int, fromIndex: Int, toIndex: Int) -> Unit,
        private val isEditable: () -> Boolean,
        private val getTotalDays: () -> Int,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dayTitle)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.attractionsRecyclerView)
        private val btnAddPlace: Button = itemView.findViewById(R.id.btnAddPlace)
        private var attractionAdapter: AttractionAdapter? = null
        private var touchHelper: ItemTouchHelper? = null
        private var dragStartIndex: Int? = null
        private var currentDay: Int = 1

        fun bind(plan: DayPlan) {
            currentDay = plan.day
            title.text = "Day ${plan.day}"
            if (recyclerView.layoutManager == null) {
                recyclerView.layoutManager = LinearLayoutManager(itemView.context)
                recyclerView.itemAnimator = null
            }

            val adapter = attractionAdapter ?: AttractionAdapter(
                onDelete = onDelete,
                onTimeUpdated = onTimeUpdated,
                onMoveDay = onMoveDay,
                onCardClick = onCardClick,
                onDetailsClick = onDetailsClick,
                isEditable = isEditable,
                onStartDrag = { holder -> touchHelper?.startDrag(holder) },
            ).also {
                attractionAdapter = it
                recyclerView.adapter = it
            }
            adapter.configure(dayNum = plan.day, totalDays = getTotalDays())
            adapter.submitList(plan.attractions)

            if (touchHelper == null) {
                touchHelper = ItemTouchHelper(
                    object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                    override fun getMovementFlags(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return if (isEditable()) {
                            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                        } else {
                            0
                        }
                    }

                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder,
                    ): Boolean {
                        val from = viewHolder.bindingAdapterPosition
                        val to = target.bindingAdapterPosition
                        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                        if (dragStartIndex == null) dragStartIndex = from
                        adapter.moveItem(from, to)
                        return true
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                    override fun isLongPressDragEnabled(): Boolean = false
                    
                    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                        super.onSelectedChanged(viewHolder, actionState)
                        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                            dragStartIndex = viewHolder.bindingAdapterPosition
                        }
                    }

                    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                        super.clearView(recyclerView, viewHolder)
                        val from = dragStartIndex
                        val to = viewHolder.bindingAdapterPosition
                        dragStartIndex = null
                        if (
                            from != null &&
                            from != RecyclerView.NO_POSITION &&
                            to != RecyclerView.NO_POSITION &&
                            from != to
                        ) {
                            onReorder(currentDay, from, to)
                        }
                    }
                    },
                )
                touchHelper?.attachToRecyclerView(recyclerView)
            }

            btnAddPlace.visibility = if (isEditable()) View.VISIBLE else View.GONE
            btnAddPlace.setOnClickListener {
                onAddPlace(plan.day)
            }
        }
    }
}

class AttractionAdapter(
    private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
    private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
    private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
    private val onCardClick: (Attraction) -> Unit,
    private val onDetailsClick: (Attraction) -> Unit,
    private val isEditable: () -> Boolean,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<AttractionAdapter.ViewHolder>() {
    private val items = mutableListOf<Attraction>()
    private var dayNum: Int = 1
    private var totalDays: Int = 1

    fun configure(dayNum: Int, totalDays: Int) {
        this.dayNum = dayNum
        this.totalDays = totalDays
    }

    fun submitList(newItems: List<Attraction>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in items.indices || toIndex !in items.indices) return
        val moving = items.removeAt(fromIndex)
        items.add(toIndex, moving)
        for (index in items.indices) {
            items[index] = items[index].copy(sequence = index + 1)
        }
        notifyItemMoved(fromIndex, toIndex)
        notifyItemRangeChanged(minOf(fromIndex, toIndex), kotlin.math.abs(fromIndex - toIndex) + 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attraction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            attraction = items[position],
            dayNum = dayNum,
            totalDays = totalDays,
            onDelete = onDelete,
            onTimeUpdated = onTimeUpdated,
            onMoveDay = onMoveDay,
            onCardClick = onCardClick,
            onDetailsClick = onDetailsClick,
            isEditable = isEditable,
            onStartDrag = onStartDrag,
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sequence: TextView = itemView.findViewById(R.id.attractionSequence)
        private val dragHandle: View = itemView.findViewById(R.id.dragHandle)
        private val name: TextView = itemView.findViewById(R.id.attractionName)
        private val description: TextView = itemView.findViewById(R.id.attractionDescription)
        private val tvTimeValue: TextView = itemView.findViewById(R.id.tvTimeValue)
        private val btnClearTime: TextView = itemView.findViewById(R.id.btnClearTime)
        private val btnMoveDay: ImageButton = itemView.findViewById(R.id.btnMoveDay)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val btnPlaceDetails: MaterialButton = itemView.findViewById(R.id.btnPlaceDetails)

        fun bind(
            attraction: Attraction,
            dayNum: Int,
            totalDays: Int,
            onDelete: (dayNum: Int, sequence: Int) -> Unit,
            onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
            onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
            onCardClick: (Attraction) -> Unit,
            onDetailsClick: (Attraction) -> Unit,
            isEditable: () -> Boolean,
            onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        ) {
            sequence.text = attraction.sequence.toString()
            name.text = attraction.name
            description.text = attraction.description

            val details = getCategoryDetails(determineCategory(attraction.name, attraction.description))
            name.setTextColor(details.second)

            itemView.setOnClickListener { onCardClick(attraction) }
            btnPlaceDetails.setOnClickListener { onDetailsClick(attraction) }
            btnDelete.setOnClickListener { onDelete(dayNum, attraction.sequence) }
            btnDelete.visibility = if (isEditable()) View.VISIBLE else View.GONE
            btnMoveDay.visibility = if (isEditable()) View.VISIBLE else View.GONE
            dragHandle.visibility = if (isEditable()) View.VISIBLE else View.INVISIBLE
            dragHandle.setOnTouchListener { _, event ->
                if (isEditable() && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }

            btnMoveDay.setOnClickListener {
                if (isEditable()) {
                    showMoveDayDialog(
                        totalDays = totalDays,
                        currentDay = dayNum,
                        sequence = attraction.sequence,
                        onMoveDay = onMoveDay,
                    )
                }
            }

            renderTimeState(attraction)
            if (isEditable()) {
                tvTimeValue.isEnabled = true
                bindTimeActions(attraction, dayNum, onTimeUpdated)
            } else {
                tvTimeValue.isEnabled = false
                btnClearTime.visibility = View.GONE
            }
        }

        private fun showMoveDayDialog(
            totalDays: Int,
            currentDay: Int,
            sequence: Int,
            onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
        ) {
            val context = itemView.context
            if (totalDays <= 1) {
                AlertDialog.Builder(context)
                    .setTitle("Notice")
                    .setMessage("There is no other day to move this place to.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val otherDays = (1..totalDays).filter { it != currentDay }
            val labels = otherDays.map { "Day $it" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Move to day")
                .setItems(labels) { _, which ->
                    onMoveDay(currentDay, sequence, otherDays[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun renderTimeState(attraction: Attraction) {
            val context = itemView.context
            val selectedBg = ContextCompat.getDrawable(context, R.drawable.bg_time_chip_selected)
            val unselectedBg = ContextCompat.getDrawable(context, R.drawable.bg_time_chip_unselected)
            val selectedColor = ContextCompat.getColor(context, R.color.orbit_space)

            when (attraction.timeType) {
                TimeType.PRECISE -> {
                    tvTimeValue.background = selectedBg
                    tvTimeValue.setTextColor(selectedColor)
                    tvTimeValue.text = formatVisitTime(
                        attraction.preciseStartTime,
                        attraction.preciseEndTime,
                    )
                    btnClearTime.visibility = View.VISIBLE
                }
                TimeType.APPROXIMATE,
                TimeType.NONE -> {
                    tvTimeValue.background = unselectedBg
                    tvTimeValue.setTextColor(ContextCompat.getColor(context, R.color.white))
                    tvTimeValue.text = "Set time"
                    btnClearTime.visibility = View.GONE
                }
            }
        }

        private fun bindTimeActions(
            attraction: Attraction,
            dayNum: Int,
            onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
        ) {
            tvTimeValue.setOnClickListener {
                showVisitTimePicker(attraction, dayNum, onTimeUpdated)
            }
            btnClearTime.setOnClickListener {
                onTimeUpdated(dayNum, attraction.sequence, TimeType.NONE, null, null, null)
            }
        }

        private fun showVisitTimePicker(
            attraction: Attraction,
            dayNum: Int,
            onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
        ) {
            val context = itemView.context
            val startParts = parseTime(attraction.preciseStartTime) ?: (9 to 0)
            val endParts = parseTime(attraction.preciseEndTime) ?: (10 to 0)
            val minuteLabels = arrayOf("00", "10", "20", "30", "40", "50")
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(24, 12, 24, 0)
            }
            val startHourPicker = NumberPicker(context).apply {
                minValue = 0
                maxValue = 23
                value = startParts.first
                setFormatter { value -> value.toString().padStart(2, '0') }
                wrapSelectorWheel = true
            }
            val startMinutePicker = NumberPicker(context).apply {
                minValue = 0
                maxValue = minuteLabels.lastIndex
                displayedValues = minuteLabels
                value = (startParts.second / 10).coerceIn(0, minuteLabels.lastIndex)
                wrapSelectorWheel = true
            }
            val separator = TextView(context).apply {
                text = "-"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(8, 0, 8, 0)
            }
            val endHourPicker = NumberPicker(context).apply {
                minValue = 0
                maxValue = 23
                value = endParts.first
                setFormatter { value -> value.toString().padStart(2, '0') }
                wrapSelectorWheel = true
            }
            val endMinutePicker = NumberPicker(context).apply {
                minValue = 0
                maxValue = minuteLabels.lastIndex
                displayedValues = minuteLabels
                value = (endParts.second / 10).coerceIn(0, minuteLabels.lastIndex)
                wrapSelectorWheel = true
            }
            row.addView(startHourPicker)
            row.addView(startMinutePicker)
            row.addView(separator)
            row.addView(endHourPicker)
            row.addView(endMinutePicker)

            AlertDialog.Builder(context)
                .setTitle("Visit time")
                .setView(row)
                .setNegativeButton("Clear") { _, _ ->
                    onTimeUpdated(dayNum, attraction.sequence, TimeType.NONE, null, null, null)
                }
                .setNeutralButton("Cancel", null)
                .setPositiveButton("Set") { _, _ ->
                    val start = formatTime(startHourPicker.value, startMinutePicker.value * 10)
                    val end = formatTime(endHourPicker.value, endMinutePicker.value * 10)
                    onTimeUpdated(dayNum, attraction.sequence, TimeType.PRECISE, start, end, null)
                }
                .show()
        }

        private fun formatVisitTime(start: String?, end: String?): String =
            if (!start.isNullOrBlank() && !end.isNullOrBlank()) "$start - $end" else "Set time"

        private fun parseTime(value: String?): Pair<Int, Int>? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
            val minute = parts[1].toIntOrNull()?.let { (it / 10).coerceIn(0, 5) * 10 } ?: return null
            return hour to minute
        }

        private fun formatTime(hour: Int, minute: Int): String =
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
}

enum class AttractionCategory(val displayName: String) {
    ACTIVITY("Activity"),
    CULTURE("Culture"),
    RESTAURANT("Restaurant"),
    NATURE("Nature"),
    ACCOMMODATION("Accommodation"),
}

fun getCategoryDetails(category: AttractionCategory): Triple<Int, Int, String> =
    when (category) {
        AttractionCategory.ACTIVITY -> Triple(R.drawable.ic_picto_activity, 0xFFFF6B6B.toInt(), "#FF6B6B")
        AttractionCategory.CULTURE -> Triple(R.drawable.ic_picto_culture, 0xFFD68CFC.toInt(), "#D68CFC")
        AttractionCategory.RESTAURANT -> Triple(R.drawable.ic_picto_restaurant, 0xFF64D2FF.toInt(), "#64D2FF")
        AttractionCategory.NATURE -> Triple(R.drawable.ic_picto_nature, 0xFF98DFAF.toInt(), "#98DFAF")
        AttractionCategory.ACCOMMODATION -> Triple(R.drawable.ic_picto_accommodation, 0xFFFFD166.toInt(), "#FFD166")
    }

fun determineCategory(name: String, description: String): AttractionCategory {
    val text = "$name $description".lowercase()
    return when {
        listOf("hotel", "lodging", "stay", "resort", "hostel", "accommodation").any(text::contains) -> AttractionCategory.ACCOMMODATION
        listOf("restaurant", "cafe", "coffee", "food", "market", "dining", "lunch", "dinner").any(text::contains) -> AttractionCategory.RESTAURANT
        listOf("park", "beach", "mountain", "garden", "river", "lake", "forest", "view").any(text::contains) -> AttractionCategory.NATURE
        listOf("museum", "gallery", "temple", "shrine", "church", "palace", "history", "landmark").any(text::contains) -> AttractionCategory.CULTURE
        else -> AttractionCategory.ACTIVITY
    }
}
