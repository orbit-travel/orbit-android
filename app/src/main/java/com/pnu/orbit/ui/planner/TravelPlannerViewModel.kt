package com.pnu.orbit.ui.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan
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

    private val _recommendations = MutableLiveData<UiState<List<Attraction>>>(UiState.Empty)
    val recommendations: LiveData<UiState<List<Attraction>>> = _recommendations

    private val _selectedRecommendations = MutableLiveData<Set<Attraction>>(emptySet())
    val selectedRecommendations: LiveData<Set<Attraction>> = _selectedRecommendations

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val database = com.pnu.orbit.data.local.db.OrbitDatabase.getInstance(application)
                    val planDao = database.planDao()
                    planDao.getAllPlans().forEach { entity ->
                        val plan = entity.toDomain()
                        if (plan.isFallback) {
                            planDao.deletePlanById(entity.id)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TravelPlannerViewModel", "Failed to clean up fallback plans", e)
                }
            }
        }
    }

    fun startNewPlan() {
        _plan.value = UiState.Empty
        _recommendations.value = UiState.Empty
        _selectedRecommendations.value = emptySet()
    }

    fun fetchRecommendations(destination: String, style: String, latitude: Double, longitude: Double, radiusKm: Double) {
        _recommendations.value = UiState.Loading
        _selectedRecommendations.value = emptySet()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getRecommendations(
                        destination = destination,
                        style = style,
                        latitude = latitude,
                        longitude = longitude,
                        radiusKm = radiusKm
                    )
                }
                _recommendations.value = UiState.Success(result)
            } catch (e: Exception) {
                _recommendations.value = UiState.Error(e.message ?: "Failed to get recommendations from Gemini")
            }
        }
    }

    fun toggleRecommendationSelected(attraction: Attraction, selected: Boolean) {
        val current = _selectedRecommendations.value.orEmpty().toMutableSet()
        if (selected) {
            current.add(attraction)
        } else {
            current.remove(attraction)
        }
        _selectedRecommendations.value = current
    }

    fun confirmAndBuildPlan(selected: List<Attraction>, destination: String, days: Int, style: String) {
        _plan.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val dayPlans = (1..days).map { dayNum ->
                        val chunk = selected.filterIndexed { index, _ -> (index % days) + 1 == dayNum }
                        val attractionsWithSeq = chunk.mapIndexed { seqIndex, attr ->
                            attr.copy(sequence = seqIndex + 1)
                        }
                        DayPlan(day = dayNum, attractions = attractionsWithSeq)
                    }
                    val plan = TravelPlan(
                        id = 0L,
                        destination = destination,
                        days = days,
                        style = style,
                        dayPlans = dayPlans,
                        createdAt = System.currentTimeMillis(),
                        isFallback = false
                    )
                    val database = com.pnu.orbit.data.local.db.OrbitDatabase.getInstance(getApplication())
                    val savedId = database.planDao().insertPlan(plan.toEntity())
                    plan.copy(id = savedId)
                }
                _plan.value = UiState.Success(result)
            } catch (e: Exception) {
                _plan.value = UiState.Error(e.message ?: "Failed to generate plan")
            }
        }
    }

    fun generatePlan(destination: String, days: Int, style: String, latitude: Double? = null, longitude: Double? = null) {
        _plan.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.createPlan(
                        PlannerRequest(
                            destination = destination,
                            days = days,
                            style = style,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }
                _plan.value = UiState.Success(result)
            } catch (e: Exception) {
                _plan.value = UiState.Error(e.message ?: "Failed to generate plan from Gemini API")
            }
        }
    }
}
