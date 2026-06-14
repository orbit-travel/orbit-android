package com.pnu.orbit.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.addtrip.RangeCalendarView
import com.pnu.orbit.ui.common.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarConfirmFragment : Fragment() {

    private val viewModel: TravelPlannerViewModel by viewModels({ requireParentFragment() })

    private lateinit var btnBackToGenerated: ImageButton
    private lateinit var tvConfirmDestination: TextView
    private lateinit var tvConfirmDuration: TextView
    private lateinit var tvConfirmStartDate: TextView
    private lateinit var calendarMonthTitle: TextView
    private lateinit var buttonPreviousMonth: MaterialButton
    private lateinit var buttonNextMonth: MaterialButton
    private lateinit var confirmCalendarView: RangeCalendarView
    private lateinit var btnFinalizePlan: MaterialButton

    private var displayedMonthMillis = System.currentTimeMillis()
    private var selectedStartDateMillis: Long? = null
    private val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
    private val displayDateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN)
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar_confirm, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBackToGenerated = view.findViewById(R.id.btnBackToGenerated)
        tvConfirmDestination = view.findViewById(R.id.tvConfirmDestination)
        tvConfirmDuration = view.findViewById(R.id.tvConfirmDuration)
        tvConfirmStartDate = view.findViewById(R.id.tvConfirmStartDate)
        calendarMonthTitle = view.findViewById(R.id.calendarMonthTitle)
        buttonPreviousMonth = view.findViewById(R.id.buttonPreviousMonth)
        buttonNextMonth = view.findViewById(R.id.buttonNextMonth)
        confirmCalendarView = view.findViewById(R.id.confirmCalendarView)
        btnFinalizePlan = view.findViewById(R.id.btnFinalizePlan)

        updateMonthHeader()

        btnBackToGenerated.setOnClickListener {
            viewModel.navigateToGenerated()
        }

        buttonPreviousMonth.setOnClickListener { moveDisplayedMonth(-1) }
        buttonNextMonth.setOnClickListener { moveDisplayedMonth(1) }

        viewModel.plan.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) {
                val plan = state.data
                tvConfirmDestination.text = "목적지: ${plan.destination}"
                tvConfirmDuration.text = "기간: ${plan.days}일간 (${plan.style})"
                setupCalendarRangeListener(plan)
            }
        }

        btnFinalizePlan.setOnClickListener {
            val startMillis = selectedStartDateMillis
            if (startMillis != null) {
                val startDateStr = dbDateFormat.format(Date(startMillis))
                viewModel.finalizeAndSavePlan(startDateStr)
                Toast.makeText(requireContext(), "여행 계획이 최종 확정되어 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMonthHeader() {
        calendarMonthTitle.text = monthFormat.format(Date(displayedMonthMillis))
        confirmCalendarView.setMonth(displayedMonthMillis)
    }

    private fun moveDisplayedMonth(offset: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = displayedMonthMillis
            add(Calendar.MONTH, offset)
        }
        displayedMonthMillis = cal.timeInMillis
        updateMonthHeader()
    }

    private fun setupCalendarRangeListener(plan: TravelPlan) {
        confirmCalendarView.onDateClicked = { clickedMillis ->
            selectedStartDateMillis = clickedMillis
            tvConfirmStartDate.text = "시작일: ${displayDateFormat.format(Date(clickedMillis))}"

            // End date = Start date + (days - 1) days
            val cal = Calendar.getInstance().apply {
                timeInMillis = clickedMillis
                add(Calendar.DAY_OF_MONTH, plan.days - 1)
            }
            val endMillis = cal.timeInMillis
            confirmCalendarView.setRange(clickedMillis, endMillis)
            btnFinalizePlan.isEnabled = true
        }
    }
}
