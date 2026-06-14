package com.pnu.orbit.ui.planner

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.TimeType

class PlanDayAdapter(
    private val onMoveUp: (dayNum: Int, sequence: Int) -> Unit,
    private val onMoveDown: (dayNum: Int, sequence: Int) -> Unit,
    private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
    private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
    private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
    private val onAddPlace: (dayNum: Int) -> Unit,
    private val onCardClick: (Attraction) -> Unit
) : RecyclerView.Adapter<PlanDayAdapter.ViewHolder>() {
    private val items = mutableListOf<DayPlan>()

    fun submitList(newItems: List<DayPlan>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plan_day, parent, false)
        return ViewHolder(view, onMoveUp, onMoveDown, onDelete, onTimeUpdated, onMoveDay, onAddPlace, onCardClick) { items.size }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onMoveUp: (dayNum: Int, sequence: Int) -> Unit,
        private val onMoveDown: (dayNum: Int, sequence: Int) -> Unit,
        private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
        private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
        private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
        private val onAddPlace: (dayNum: Int) -> Unit,
        private val onCardClick: (Attraction) -> Unit,
        private val getTotalDays: () -> Int
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dayTitle)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.attractionsRecyclerView)
        private val btnAddPlace: Button = itemView.findViewById(R.id.btnAddPlace)

        fun bind(plan: DayPlan) {
            title.text = "Day ${plan.day}"
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
            val adapter = AttractionAdapter(
                dayNum = plan.day,
                totalDays = getTotalDays(),
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete,
                onTimeUpdated = onTimeUpdated,
                onMoveDay = onMoveDay,
                onCardClick = onCardClick
            )
            recyclerView.adapter = adapter
            adapter.submitList(plan.attractions)

            btnAddPlace.setOnClickListener {
                onAddPlace(plan.day)
            }
        }
    }
}

class AttractionAdapter(
    private val dayNum: Int,
    private val totalDays: Int,
    private val onMoveUp: (dayNum: Int, sequence: Int) -> Unit,
    private val onMoveDown: (dayNum: Int, sequence: Int) -> Unit,
    private val onDelete: (dayNum: Int, sequence: Int) -> Unit,
    private val onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
    private val onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
    private val onCardClick: (Attraction) -> Unit
) : RecyclerView.Adapter<AttractionAdapter.ViewHolder>() {
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
        holder.bind(
            attraction = items[position],
            dayNum = dayNum,
            totalDays = totalDays,
            isFirst = position == 0,
            isLast = position == items.size - 1,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            onTimeUpdated = onTimeUpdated,
            onMoveDay = onMoveDay,
            onCardClick = onCardClick
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sequence: TextView = itemView.findViewById(R.id.attractionSequence)
        private val name: TextView = itemView.findViewById(R.id.attractionName)
        private val image: ImageView = itemView.findViewById(R.id.attractionImage)
        
        // Time Planning components
        private val tvModePrecise: TextView = itemView.findViewById(R.id.tvModePrecise)
        private val tvModeApprox: TextView = itemView.findViewById(R.id.tvModeApprox)
        private val tvTimeValue: TextView = itemView.findViewById(R.id.tvTimeValue)

        // Action Buttons
        private val btnMoveUp: ImageButton = itemView.findViewById(R.id.btnMoveUp)
        private val btnMoveDown: ImageButton = itemView.findViewById(R.id.btnMoveDown)
        private val btnMoveDay: ImageButton = itemView.findViewById(R.id.btnMoveDay)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(
            attraction: Attraction,
            dayNum: Int,
            totalDays: Int,
            isFirst: Boolean,
            isLast: Boolean,
            onMoveUp: (dayNum: Int, sequence: Int) -> Unit,
            onMoveDown: (dayNum: Int, sequence: Int) -> Unit,
            onDelete: (dayNum: Int, sequence: Int) -> Unit,
            onTimeUpdated: (dayNum: Int, sequence: Int, timeType: TimeType, start: String?, end: String?, approx: Double?) -> Unit,
            onMoveDay: (dayNum: Int, sequence: Int, targetDayNum: Int) -> Unit,
            onCardClick: (Attraction) -> Unit
        ) {
            sequence.text = attraction.sequence.toString()
            name.text = attraction.name

            // Hook click listener on card view to focus map camera
            itemView.setOnClickListener { onCardClick(attraction) }

            // 1. Categorization & Local Vector Pictogram loading
            val category = determineCategory(attraction.name, attraction.description)
            val details = getCategoryDetails(category)
            
            // Render local pictogram with specific category color tint
            image.setImageResource(details.first)
            image.setColorFilter(details.second)

            // Tint the attraction name text color to match the category
            name.setTextColor(details.second)

            // 2. Action buttons click listeners
            btnMoveUp.setOnClickListener { onMoveUp(dayNum, attraction.sequence) }
            btnMoveDown.setOnClickListener { onMoveDown(dayNum, attraction.sequence) }
            btnDelete.setOnClickListener { onDelete(dayNum, attraction.sequence) }

            // Enable/Disable buttons based on position
            btnMoveUp.isEnabled = !isFirst
            btnMoveUp.alpha = if (!isFirst) 1.0f else 0.4f
            btnMoveDown.isEnabled = !isLast
            btnMoveDown.alpha = if (!isLast) 1.0f else 0.4f

            // btnMoveDay click listener (Show list of other days to move to)
            val context = itemView.context
            btnMoveDay.setOnClickListener {
                if (totalDays <= 1) {
                    AlertDialog.Builder(context)
                        .setTitle("알림")
                        .setMessage("이동할 수 있는 다른 일차가 존재하지 않습니다.")
                        .setPositiveButton("확인", null)
                        .show()
                } else {
                    val otherDays = (1..totalDays).filter { it != dayNum }
                    val items = otherDays.map { "${it}일차" }.toTypedArray()

                    AlertDialog.Builder(context)
                        .setTitle("이동할 일차 선택")
                        .setItems(items) { _, which ->
                            val selectedDay = otherDays[which]
                            onMoveDay(dayNum, attraction.sequence, selectedDay)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }

            // 3. Render Time Selection States
            val selectedBg = ContextCompat.getDrawable(context, R.drawable.bg_time_chip_selected)
            val unselectedBg = ContextCompat.getDrawable(context, R.drawable.bg_time_chip_unselected)
            val selectedColor = ContextCompat.getColor(context, R.color.orbit_space)
            val unselectedColor = ContextCompat.getColor(context, R.color.orbit_text_secondary)

            when (attraction.timeType) {
                TimeType.PRECISE -> {
                    tvModePrecise.background = selectedBg
                    tvModePrecise.setTextColor(selectedColor)
                    tvModeApprox.background = unselectedBg
                    tvModeApprox.setTextColor(unselectedColor)

                    val startTime = attraction.preciseStartTime ?: "09:00"
                    val endTime = attraction.preciseEndTime ?: "10:00"
                    tvTimeValue.text = "$startTime ~ $endTime"
                }
                TimeType.APPROXIMATE -> {
                    tvModePrecise.background = unselectedBg
                    tvModePrecise.setTextColor(unselectedColor)
                    tvModeApprox.background = selectedBg
                    tvModeApprox.setTextColor(selectedColor)

                    val hours = attraction.approxHours ?: 1.0
                    tvTimeValue.text = "약 ${hours}시간"
                }
                TimeType.NONE -> {
                    tvModePrecise.background = unselectedBg
                    tvModePrecise.setTextColor(unselectedColor)
                    tvModeApprox.background = unselectedBg
                    tvModeApprox.setTextColor(unselectedColor)
                    tvTimeValue.text = "시간 입력하기"
                }
            }

            // 4. Click Listeners for Time Modes
            tvModePrecise.setOnClickListener {
                if (attraction.timeType != TimeType.PRECISE) {
                    val defaultStart = attraction.preciseStartTime ?: "09:00"
                    val defaultEnd = attraction.preciseEndTime ?: "10:00"
                    onTimeUpdated(dayNum, attraction.sequence, TimeType.PRECISE, defaultStart, defaultEnd, null)
                }
            }

            tvModeApprox.setOnClickListener {
                if (attraction.timeType != TimeType.APPROXIMATE) {
                    val defaultApprox = attraction.approxHours ?: 1.0
                    onTimeUpdated(dayNum, attraction.sequence, TimeType.APPROXIMATE, null, null, defaultApprox)
                }
            }

            // 5. Click Listener for Time Value (Open Picker Dialogs)
            tvTimeValue.setOnClickListener {
                when (attraction.timeType) {
                    TimeType.PRECISE -> {
                        // Open start time picker
                        val startParts = (attraction.preciseStartTime ?: "09:00").split(":")
                        val startH = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                        val startM = startParts.getOrNull(1)?.toIntOrNull() ?: 0

                        val startPicker = TimePickerDialog(context, { _, sh, sm ->
                            val startTimeStr = String.format("%02d:%02d", sh, sm)
                            
                            // Open end time picker after start time is selected
                            val endParts = (attraction.preciseEndTime ?: "10:00").split(":")
                            val endH = endParts.getOrNull(0)?.toIntOrNull() ?: ((sh + 1) % 24)
                            val endM = endParts.getOrNull(1)?.toIntOrNull() ?: sm

                            TimePickerDialog(context, { _, eh, em ->
                                val endTimeStr = String.format("%02d:%02d", eh, em)
                                onTimeUpdated(dayNum, attraction.sequence, TimeType.PRECISE, startTimeStr, endTimeStr, null)
                            }, endH, endM, true).apply {
                                setTitle("종료 시간 선택")
                                show()
                            }
                        }, startH, startM, true)
                        startPicker.setTitle("시작 시간 선택")
                        startPicker.show()
                    }
                    TimeType.APPROXIMATE -> {
                        // Open Choice Dialog for hours
                        val options = arrayOf(
                            "약 0.5시간", "약 1.0시간", "약 1.5시간", "약 2.0시간",
                            "약 2.5시간", "약 3.0시간", "약 4.0시간", "약 5.0시간"
                        )
                        val values = doubleArrayOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0)
                        
                        AlertDialog.Builder(context)
                            .setTitle("대략적인 시간 선택")
                            .setItems(options) { _, which ->
                                onTimeUpdated(dayNum, attraction.sequence, TimeType.APPROXIMATE, null, null, values[which])
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }
                    TimeType.NONE -> {
                        // Show options
                        val choices = arrayOf("정확한 시간 계획 설정", "대략적인 계획 설정")
                        AlertDialog.Builder(context)
                            .setTitle("시간 계획 방식 선택")
                            .setItems(choices) { _, which ->
                                if (which == 0) {
                                    onTimeUpdated(dayNum, attraction.sequence, TimeType.PRECISE, "09:00", "10:00", null)
                                } else {
                                    onTimeUpdated(dayNum, attraction.sequence, TimeType.APPROXIMATE, null, null, 1.0)
                                }
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }
                }
            }
        }
    }
}

// ==========================================
// Category & Pictogram Utilities
// ==========================================
enum class AttractionCategory(val displayName: String) {
    ACTIVITY("놀거리"),
    CULTURE("문화 및 명소"),
    RESTAURANT("식당"),
    NATURE("자연 경관"),
    ACCOMMODATION("숙소")
}

fun getCategoryDetails(category: AttractionCategory): Triple<Int, Int, String> {
    return when (category) {
        AttractionCategory.ACTIVITY -> Triple(R.drawable.ic_picto_activity, 0xFFFF6B6B.toInt(), "#FF6B6B")
        AttractionCategory.CULTURE -> Triple(R.drawable.ic_picto_culture, 0xFFD68CFC.toInt(), "#D68CFC")
        AttractionCategory.RESTAURANT -> Triple(R.drawable.ic_picto_restaurant, 0xFF64D2FF.toInt(), "#64D2FF")
        AttractionCategory.NATURE -> Triple(R.drawable.ic_picto_nature, 0xFF98DFAF.toInt(), "#98DFAF")
        AttractionCategory.ACCOMMODATION -> Triple(R.drawable.ic_picto_accommodation, 0xFFFFD166.toInt(), "#FFD166")
    }
}

fun determineCategory(name: String, description: String): AttractionCategory {
    val text = "$name $description".lowercase()
    return when {
        text.contains("숙소") || text.contains("호텔") || text.contains("펜션") || text.contains("게스트하우스") || text.contains("리조트") || text.contains("민박") || text.contains("모텔") || text.contains("에어비앤비") || text.contains("야영") || text.contains("캠핑") -> AttractionCategory.ACCOMMODATION
        text.contains("식당") || text.contains("맛집") || text.contains("음식") || text.contains("레스토랑") || text.contains("요리") || text.contains("식사") || text.contains("점심") || text.contains("저녁") || text.contains("갈비") || text.contains("밥") || text.contains("구이") || text.contains("푸드") || text.contains("바베큐") || text.contains("카페") || text.contains("커피") || text.contains("디저트") || text.contains("cafe") || text.contains("coffee") || text.contains("에스프레소") -> AttractionCategory.RESTAURANT
        text.contains("자연 경관") || text.contains("자연") || text.contains("공원") || text.contains("해변") || text.contains("바다") || text.contains("산") || text.contains("계곡") || text.contains("숲") || text.contains("강") || text.contains("호수") || text.contains("섬") || text.contains("해수욕장") || text.contains("산책") || text.contains("폭포") || text.contains("정원") || text.contains("경관") || text.contains("전망대") -> AttractionCategory.NATURE
        text.contains("문화 및 명소") || text.contains("문화") || text.contains("명소") || text.contains("박물관") || text.contains("미술관") || text.contains("성") || text.contains("유적") || text.contains("사찰") || text.contains("절") || text.contains("궁궐") || text.contains("타워") || text.contains("역사") || text.contains("성당") || text.contains("교회") || text.contains("전시") || text.contains("랜드마크") -> AttractionCategory.CULTURE
        else -> AttractionCategory.ACTIVITY
    }
}
