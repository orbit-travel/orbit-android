package com.pnu.orbit.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.pnu.orbit.R
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TravelRecordViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = RepositoryProvider.tripRepository(application)

    val trips: LiveData<UiState<List<Trip>>> = repository.observeTrips()
        .map { trips ->
            if (trips.isEmpty()) UiState.Empty else UiState.Success(trips)
        }
        .catch { error ->
            emit(UiState.Error(application.getString(R.string.record_error_load), error))
        }
        .asLiveData()

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteTrip(tripId)
            }
        }
    }

    fun loadTripGeometry(
        tripId: Long,
        onLoaded: (List<TransportSegment>, List<TravelPhoto>) -> Unit,
    ) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                repository.getSegments(tripId) to repository.getPhotos(tripId)
            }
            onLoaded(data.first, data.second)
        }
    }
}
