package com.pnu.orbit.ui.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.domain.model.PlannerRequest
import com.pnu.orbit.domain.model.TravelPlan
import com.pnu.orbit.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TravelPlannerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = RepositoryProvider.plannerRepository(application)

    private val _plan = MutableLiveData<UiState<TravelPlan>>(UiState.Empty)
    val plan: LiveData<UiState<TravelPlan>> = _plan

    fun generatePlan(destination: String, days: Int, style: String) {
        _plan.value = UiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repository.createPlan(
                        PlannerRequest(
                            destination = destination,
                            days = days,
                            style = style
                        )
                    )
                }
            }.getOrElse {
                com.pnu.orbit.util.DemoFallbacks.samplePlan(destination, days, style)
            }
            _plan.value = UiState.Success(result)
        }
    }
}
