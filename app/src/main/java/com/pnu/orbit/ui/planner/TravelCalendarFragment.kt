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
import com.pnu.orbit.ui.addtrip.RangeCalendarView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TravelCalendarFragment : Fragment() {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })

    private lateinit var tvTitle: TextView
    private lateinit var calendarMonthTitle: TextView
    private lateinit var buttonPreviousMonth: MaterialButton
    private lateinit var buttonNextMonth: MaterialButton
    private lateinit var travelCalendarView: RangeCalendarView
    private lateinit var btnAiRecommend: ExtendedFloatingActionButton

    private var displayedMonthMillis = System.currentTimeMillis()
    private val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_travel_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvTitle)
        calendarMonthTitle = view.findViewById(R.id.calendarMonthTitle)
        buttonPreviousMonth = view.findViewById(R.id.buttonPreviousMonth)
        buttonNextMonth = view.findViewById(R.id.buttonNextMonth)
        travelCalendarView = view.findViewById(R.id.travelCalendarView)
        btnAiRecommend = view.findViewById(R.id.btnAiRecommend)

        updateMonthHeader()

        buttonPreviousMonth.setOnClickListener { moveDisplayedMonth(-1) }
        buttonNextMonth.setOnClickListener { moveDisplayedMonth(1) }

        btnAiRecommend.setOnClickListener {
            viewModel.navigateToGenerating()
        }

        viewModel.savedPlans.observe(viewLifecycleOwner) { plans ->
            renderPlans(plans.orEmpty())
        }

        travelCalendarView.onDateClicked = { clickedMillis ->
            val plans = viewModel.savedPlans.value.orEmpty()
            val matchedPlan = findPlanForDate(clickedMillis, plans)
            if (matchedPlan != null) {
                viewModel.loadSavedPlan(matchedPlan)
            } else {
                Toast.makeText(requireContext(), "이 날짜에는 등록된 여행 계획이 없습니다.", Toast.LENGTH_SHORT).show()
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
        val allPlannedDates = mutableSetOf<Long>()
        plans.forEach { plan ->
            allPlannedDates.addAll(getDatesForPlan(plan))
        }
        travelCalendarView.setPlannedDates(allPlannedDates)
    }

    private fun getDatesForPlan(plan: SavedTravelPlanEntity): List<Long> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startCalendar = Calendar.getInstance()
        try {
            val date = sdf.parse(plan.startDate)
            if (date != null) {
                startCalendar.time = date
            }
        } catch (e: Exception) {
            return emptyList()
        }
        val dates = mutableListOf<Long>()
        for (i in 0 until plan.days) {
            dates.add(startCalendar.timeInMillis)
            startCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    private fun findPlanForDate(clickedMillis: Long, plans: List<SavedTravelPlanEntity>): SavedTravelPlanEntity? {
        val clickedCal = Calendar.getInstance().apply {
            timeInMillis = clickedMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val clickedDay = clickedCal.timeInMillis

        for (plan in plans) {
            val dates = getDatesForPlan(plan)
            val dateDayList = dates.map {
                Calendar.getInstance().apply {
                    timeInMillis = it
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            if (dateDayList.contains(clickedDay)) {
                return plan
            }
        }
        return null
    }
}
