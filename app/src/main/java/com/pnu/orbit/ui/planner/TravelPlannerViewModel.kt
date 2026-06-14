package com.pnu.orbit.ui.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.data.mapper.toSavedEntity
import com.pnu.orbit.data.local.entity.SavedTravelPlanEntity
import com.pnu.orbit.domain.model.Attraction
import com.pnu.orbit.domain.model.DayPlan
import com.pnu.orbit.domain.model.PlannerRequest
import com.pnu.orbit.domain.model.TimeType
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

    private val _navigationState = MutableLiveData<PlannerNavigation>(PlannerNavigation.CALENDAR)
    val navigationState: LiveData<PlannerNavigation> = _navigationState

    private val _savedPlans = MutableLiveData<List<SavedTravelPlanEntity>>(emptyList())
    val savedPlans: LiveData<List<SavedTravelPlanEntity>> = _savedPlans

    init {
        loadSavedPlans()
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
        _navigationState.value = PlannerNavigation.CALENDAR
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
                _navigationState.value = PlannerNavigation.GENERATED
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
                _navigationState.value = PlannerNavigation.GENERATED
            } catch (e: Exception) {
                _plan.value = UiState.Error(e.message ?: "Failed to generate plan from Gemini API")
            }
        }
    }

    fun moveAttractionUp(dayNum: Int, attractionSeq: Int) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == dayNum) {
                    val list = dp.attractions.toMutableList()
                    val index = list.indexOfFirst { it.sequence == attractionSeq }
                    if (index > 0) {
                        val temp = list[index]
                        list[index] = list[index - 1]
                        list[index - 1] = temp
                        val updatedList = list.mapIndexed { i, attraction ->
                            attraction.copy(sequence = i + 1)
                        }
                        dp.copy(attractions = updatedList)
                    } else {
                        dp
                    }
                } else {
                    dp
                }
            }
            val updatedPlan = plan.copy(dayPlans = updatedDayPlans)
            saveAndPostPlan(updatedPlan)
        }
    }

    fun moveAttractionDown(dayNum: Int, attractionSeq: Int) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == dayNum) {
                    val list = dp.attractions.toMutableList()
                    val index = list.indexOfFirst { it.sequence == attractionSeq }
                    if (index in 0 until list.size - 1) {
                        val temp = list[index]
                        list[index] = list[index + 1]
                        list[index + 1] = temp
                        val updatedList = list.mapIndexed { i, attraction ->
                            attraction.copy(sequence = i + 1)
                        }
                        dp.copy(attractions = updatedList)
                    } else {
                        dp
                    }
                } else {
                    dp
                }
            }
            val updatedPlan = plan.copy(dayPlans = updatedDayPlans)
            saveAndPostPlan(updatedPlan)
        }
    }

    fun deleteAttraction(dayNum: Int, attractionSeq: Int) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == dayNum) {
                    val list = dp.attractions.filter { it.sequence != attractionSeq }
                    val updatedList = list.mapIndexed { i, attraction ->
                        attraction.copy(sequence = i + 1)
                    }
                    dp.copy(attractions = updatedList)
                } else {
                    dp
                }
            }
            val updatedPlan = plan.copy(dayPlans = updatedDayPlans)
            saveAndPostPlan(updatedPlan)
        }
    }

    fun updateAttractionTime(
        dayNum: Int,
        attractionSeq: Int,
        timeType: TimeType,
        preciseStart: String?,
        preciseEnd: String?,
        approxHours: Double?
    ) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == dayNum) {
                    val updatedList = dp.attractions.map { attr ->
                        if (attr.sequence == attractionSeq) {
                            attr.copy(
                                timeType = timeType,
                                preciseStartTime = preciseStart,
                                preciseEndTime = preciseEnd,
                                approxHours = approxHours
                            )
                        } else {
                            attr
                        }
                    }
                    dp.copy(attractions = updatedList)
                } else {
                    dp
                }
            }
            val updatedPlan = plan.copy(dayPlans = updatedDayPlans)
            saveAndPostPlan(updatedPlan)
        }
    }

    private fun saveAndPostPlan(updatedPlan: TravelPlan) {
        _plan.value = UiState.Success(updatedPlan)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val database = com.pnu.orbit.data.local.db.OrbitDatabase.getInstance(getApplication())
                    database.planDao().insertPlan(updatedPlan.toEntity())
                } catch (e: Exception) {
                    android.util.Log.e("TravelPlannerViewModel", "Failed to update plan in database", e)
                }
            }
        }
    }

    fun moveAttractionToDay(currentDayNum: Int, attractionSeq: Int, targetDayNum: Int) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            var attractionToMove: Attraction? = null

            // 1. Remove the attraction from the current day and capture it
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == currentDayNum) {
                    val target = dp.attractions.find { it.sequence == attractionSeq }
                    if (target != null) {
                        attractionToMove = target
                    }
                    val list = dp.attractions.filter { it.sequence != attractionSeq }
                    val updatedList = list.mapIndexed { i, attraction ->
                        attraction.copy(sequence = i + 1)
                    }
                    dp.copy(attractions = updatedList)
                } else {
                    dp
                }
            }

            val movingItem = attractionToMove
            if (movingItem != null) {
                // 2. Insert the attraction into the target day
                val finalDayPlans = updatedDayPlans.map { dp ->
                    if (dp.day == targetDayNum) {
                        val list = dp.attractions.toMutableList()
                        val newItem = movingItem.copy(
                            sequence = list.size + 1,
                            timeType = TimeType.NONE,
                            preciseStartTime = null,
                            preciseEndTime = null,
                            approxHours = null
                        )
                        list.add(newItem)
                        dp.copy(attractions = list)
                    } else {
                        dp
                    }
                }
                val updatedPlan = plan.copy(dayPlans = finalDayPlans)
                saveAndPostPlan(updatedPlan)
            }
        }
    }

    fun addCustomAttraction(dayNum: Int, name: String, categoryName: String, lat: Double, lng: Double) {
        val state = _plan.value
        if (state is UiState.Success) {
            val plan = state.data
            val updatedDayPlans = plan.dayPlans.map { dp ->
                if (dp.day == dayNum) {
                    val list = dp.attractions.toMutableList()
                    val newSeq = list.size + 1
                    val newAttr = Attraction(
                        sequence = newSeq,
                        name = name,
                        description = "사용자 추가 장소 ($categoryName)",
                        imageUrl = "", // empty string so it falls back to the demo image
                        latitude = lat,
                        longitude = lng,
                        timeType = TimeType.NONE
                    )
                    list.add(newAttr)
                    dp.copy(attractions = list)
                } else {
                    dp
                }
            }
            val updatedPlan = plan.copy(dayPlans = updatedDayPlans)
            saveAndPostPlan(updatedPlan)
        }
    }

    fun navigateToCalendar() {
        _navigationState.value = PlannerNavigation.CALENDAR
    }

    fun navigateToGenerating() {
        _navigationState.value = PlannerNavigation.GENERATING
    }

    fun navigateToGenerated() {
        _navigationState.value = PlannerNavigation.GENERATED
    }

    fun navigateToConfirm() {
        _navigationState.value = PlannerNavigation.CONFIRM
    }

    fun loadSavedPlans() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val database = com.pnu.orbit.data.local.db.OrbitDatabase.getInstance(getApplication())
                    val list = database.savedTravelPlanDao().getAllSavedPlans()
                    _savedPlans.postValue(list)
                } catch (e: Exception) {
                    android.util.Log.e("TravelPlannerViewModel", "Failed to load saved plans", e)
                }
            }
        }
    }

    fun finalizeAndSavePlan(startDate: String) {
        val currentPlanState = _plan.value
        if (currentPlanState is UiState.Success) {
            val travelPlan = currentPlanState.data
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val database = com.pnu.orbit.data.local.db.OrbitDatabase.getInstance(getApplication())
                        val savedEntity = travelPlan.toSavedEntity(startDate)
                        database.savedTravelPlanDao().insertSavedPlan(savedEntity)
                        
                        // Delete temporary plan from plans table
                        database.planDao().deletePlanById(travelPlan.id)
                    } catch (e: Exception) {
                        android.util.Log.e("TravelPlannerViewModel", "Failed to save finalized plan", e)
                    }
                }
                startNewPlan()
                loadSavedPlans()
            }
        }
    }

    fun loadSavedPlan(savedPlanEntity: SavedTravelPlanEntity) {
        viewModelScope.launch {
            val domainPlan = withContext(Dispatchers.IO) {
                savedPlanEntity.toDomain()
            }
            _plan.value = UiState.Success(domainPlan)
            _navigationState.value = PlannerNavigation.GENERATED
        }
    }
}

enum class PlannerNavigation {
    CALENDAR,
    GENERATING,
    GENERATED,
    CONFIRM
}

