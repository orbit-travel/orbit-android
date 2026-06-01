package com.pnu.orbit.ui.planner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.common.UiState
import com.pnu.orbit.util.DemoFallbacks

class TravelPlannerViewModel : ViewModel() {
    private val _plan = MutableLiveData<UiState<TravelPlan>>(UiState.Empty)
    val plan: LiveData<UiState<TravelPlan>> = _plan

    fun generateFallbackPlan(destination: String, days: Int, style: String) {
        _plan.value = UiState.Loading
        _plan.value = UiState.Success(DemoFallbacks.samplePlan(destination, days, style))
    }
}
