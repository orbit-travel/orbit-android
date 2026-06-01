package com.pnu.orbit.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.pnu.orbit.R
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.common.UiState

class TravelPlannerFragment : Fragment() {
    private val viewModel: TravelPlannerViewModel by viewModels()
    private val adapter = PlanDayAdapter()
    private lateinit var statusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_travel_planner, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusText = view.findViewById(R.id.plannerStatus)
        view.findViewById<ViewPager2>(R.id.dayPlanPager).adapter = adapter

        view.findViewById<Button>(R.id.buttonGeneratePlan).setOnClickListener {
            val destination = view.findViewById<EditText>(R.id.inputDestination).text.toString()
            val days = view.findViewById<EditText>(R.id.inputDays).text.toString().toIntOrNull() ?: 1
            val style = view.findViewById<EditText>(R.id.inputStyle).text.toString()
            viewModel.generateFallbackPlan(destination, days, style)
        }

        viewModel.plan.observe(viewLifecycleOwner) { state -> renderState(state) }
        viewModel.generateFallbackPlan(destination = "부산", days = 2, style = "culture")
    }

    private fun renderState(state: UiState<TravelPlan>) {
        when (state) {
            UiState.Empty -> {
                adapter.submitList(emptyList())
                statusText.text = "도착지와 일수를 입력하면 AI 계획을 생성합니다."
            }
            is UiState.Error -> statusText.text = state.message
            UiState.Loading -> statusText.text = "여행 계획 생성 중..."
            is UiState.Success -> {
                adapter.submitList(state.data.dayPlans)
                statusText.text = if (state.data.isFallback) {
                    "API 실패/미연결 시 표시할 fallback 계획입니다."
                } else {
                    "AI API 결과를 표시 중입니다."
                }
            }
        }
    }
}
