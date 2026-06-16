package com.pnu.orbit.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.pnu.orbit.R
import com.pnu.orbit.data.local.entity.SavedTravelPlanEntity
import com.pnu.orbit.ui.addtrip.RangeCalendarView.PlannedDateDecoration
import com.pnu.orbit.ui.addtrip.RangeCalendarView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TravelCalendarFragment : Fragment() {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })

    private lateinit var calendarMonthTitle: TextView
    private lateinit var travelCalendarView: RangeCalendarView

    private var displayedMonthMillis = System.currentTimeMillis()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarMonthTitle = view.findViewById(R.id.calendarMonthTitle)
        travelCalendarView = view.findViewById(R.id.travelCalendarView)

        updateMonthHeader()

        view.findViewById<MaterialButton>(R.id.buttonPreviousMonth).setOnClickListener { moveDisplayedMonth(-1) }
        view.findViewById<MaterialButton>(R.id.buttonNextMonth).setOnClickListener { moveDisplayedMonth(1) }
        view.findViewById<ExtendedFloatingActionButton>(R.id.btnAiRecommend).setOnClickListener {
            viewModel.navigateToGenerating()
        }

        viewModel.savedPlans.observe(viewLifecycleOwner) { plans ->
            renderPlans(plans.orEmpty())
        }

        travelCalendarView.onDateClicked = { clickedMillis ->
            val matchedPlan = findPlanForDate(clickedMillis, viewModel.savedPlans.value.orEmpty())
            if (matchedPlan != null) {
                viewModel.loadSavedPlan(matchedPlan)
            } else {
                Toast.makeText(requireContext(), "No saved itinerary for this date.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSavedPlans()
    }

    private fun updateMonthHeader() {
        calendarMonthTitle.text = monthFormat.format(Date(displayedMonthMillis))
        travelCalendarView.setMonth(displayedMonthMillis)
    }

    private fun moveDisplayedMonth(offset: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = displayedMonthMillis
            add(Calendar.MONTH, offset)
        }
        displayedMonthMillis = cal.timeInMillis
        updateMonthHeader()
    }

    private fun renderPlans(plans: List<SavedTravelPlanEntity>) {
        val decorations = mutableMapOf<Long, PlannedDateDecoration>()
        plans.forEach { plan ->
            getDatesForPlan(plan).forEach { date ->
                decorations[date] = PlannedDateDecoration(
                    title = plan.destination,
                    color = plan.color.takeIf { it != 0 } ?: fallbackColor(plan.id),
                )
            }
        }
        travelCalendarView.setPlannedDateDecorations(decorations)
    }

    private fun getDatesForPlan(plan: SavedTravelPlanEntity): List<Long> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startCalendar = Calendar.getInstance()
        val date = runCatching { sdf.parse(plan.startDate) }.getOrNull() ?: return emptyList()
        startCalendar.time = date

        return (0 until plan.days).map {
            val millis = startCalendar.timeInMillis
            startCalendar.add(Calendar.DAY_OF_MONTH, 1)
            millis
        }
    }

    private fun findPlanForDate(clickedMillis: Long, plans: List<SavedTravelPlanEntity>): SavedTravelPlanEntity? {
        val clickedDay = startOfDay(clickedMillis)
        return plans.firstOrNull { plan ->
            getDatesForPlan(plan).map(::startOfDay).contains(clickedDay)
        }
    }

    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun fallbackColor(id: Long): Int {
        val palette = listOf(
            0xFF64D2FF.toInt(),
            0xFFFFD166.toInt(),
            0xFF98DFAF.toInt(),
            0xFFFF6B6B.toInt(),
            0xFFD68CFC.toInt(),
            0xFFF472B6.toInt(),
        )
        return palette[(id % palette.size).toInt().coerceAtLeast(0)]
    }
}
