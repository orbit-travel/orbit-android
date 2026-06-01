package com.pnu.orbit.ui.record

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.ui.common.UiState
import com.pnu.orbit.util.DemoFallbacks

class TravelRecordViewModel : ViewModel() {
    private val _trips = MutableLiveData<UiState<List<Trip>>>(UiState.Empty)
    val trips: LiveData<UiState<List<Trip>>> = _trips

    fun loadFallbackTrips() {
        val fallbackTrips = DemoFallbacks.sampleTrips()
        _trips.value = if (fallbackTrips.isEmpty()) UiState.Empty else UiState.Success(fallbackTrips)
    }
}
