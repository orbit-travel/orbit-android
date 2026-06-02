package com.pnu.orbit.ui.addtrip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pnu.orbit.R
import com.pnu.orbit.data.repository.RepositoryProvider
import com.pnu.orbit.domain.model.NewTravelPhoto
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TransportType
import com.pnu.orbit.domain.model.Trip
import com.pnu.orbit.ml.FallbackPhotoClassifier
import com.pnu.orbit.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class AddTripViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = RepositoryProvider.tripRepository(application)
    private val metadataReader = PhotoMetadataReader(application)
    private val classifier = FallbackPhotoClassifier(application)

    private val _dateRange = MutableLiveData(
        DateRangeDraft(displayedMonthMillis = startOfMonth(System.currentTimeMillis())),
    )
    val dateRange: LiveData<DateRangeDraft> = _dateRange

    private val _timeline = MutableLiveData<List<TimelineDraft>>(
        listOf(TransportTimelineDraft(TransportSegmentDraft(draftId = newDraftId()))),
    )
    val timeline: LiveData<List<TimelineDraft>> = _timeline

    private val _saveState = MutableLiveData<UiState<Long>>(UiState.Empty)
    val saveState: LiveData<UiState<Long>> = _saveState

    fun moveDisplayedMonth(delta: Int) {
        val current = _dateRange.value ?: return
        _dateRange.value = current.copy(
            displayedMonthMillis = shiftMonth(current.displayedMonthMillis, delta),
        )
    }

    fun onDateClicked(dateMillis: Long) {
        val current = _dateRange.value ?: return
        val clicked = startOfDay(dateMillis)
        val start = current.startDateMillis
        val end = current.endDateMillis
        val next = when {
            start == null || end != null -> current.copy(
                startDateMillis = clicked,
                endDateMillis = null,
            )
            clicked < start -> current.copy(
                startDateMillis = clicked,
                endDateMillis = start,
            )
            else -> current.copy(endDateMillis = clicked)
        }
        _dateRange.value = next
    }

    fun addSegment() {
        val current = _timeline.value.orEmpty()
        _timeline.value = current + TransportTimelineDraft(
            TransportSegmentDraft(draftId = newDraftId()),
        )
    }

    fun addPhotoBlock(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val newBlock = withContext(Dispatchers.IO) {
                val photos = uris.mapIndexed { index, uri ->
                    val draft = metadataReader.read(uri, fallbackOrder = index)
                    draft.copy(tag = classifier.classify(uri))
                }.sortedWith(
                    compareBy<PhotoDraft> { it.takenAt ?: Long.MAX_VALUE }
                        .thenBy { it.draftId },
                )
                PhotoTimelineDraft(
                    PhotoBlockDraft(
                        draftId = newDraftId(),
                        photos = photos,
                    ),
                )
            }
            _timeline.value = _timeline.value.orEmpty() + newBlock
        }
    }

    fun updateSegment(updated: TransportSegmentDraft) {
        _timeline.value = _timeline.value.orEmpty().map { item ->
            if (item is TransportTimelineDraft && item.segment.draftId == updated.draftId) {
                TransportTimelineDraft(updated)
            } else {
                item
            }
        }
    }

    fun updatePhoto(updated: PhotoDraft) {
        _timeline.value = _timeline.value.orEmpty().map { item ->
            if (item is PhotoTimelineDraft) {
                item.copy(
                    photoBlock = item.photoBlock.copy(
                        photos = item.photoBlock.photos.map { photo ->
                            if (photo.draftId == updated.draftId) updated else photo
                        },
                    ),
                )
            } else {
                item
            }
        }
    }

    fun deleteTimelineItem(itemId: Long) {
        val current = _timeline.value.orEmpty()
        val item = current.firstOrNull { it.draftId == itemId } ?: return
        if (item is TransportTimelineDraft && current.count { it is TransportTimelineDraft } <= 1) {
            return
        }
        _timeline.value = current.filterNot { it.draftId == itemId }
    }

    fun saveTrip(title: String) {
        val cleanTitle = title.trim()
        val range = _dateRange.value
        val timelineItems = _timeline.value.orEmpty()
        val segmentDrafts = timelineItems.filterIsInstance<TransportTimelineDraft>().map { it.segment }

        when {
            cleanTitle.isBlank() -> {
                _saveState.value = UiState.Error(appString(R.string.save_error_title))
                return
            }
            range?.startDateMillis == null || range.endDateMillis == null -> {
                _saveState.value = UiState.Error(appString(R.string.save_error_dates))
                return
            }
            segmentDrafts.any { it.departureName.isBlank() || it.arrivalName.isBlank() } -> {
                _saveState.value = UiState.Error(appString(R.string.save_error_segment))
                return
            }
            segmentDrafts.any {
                it.departureLat == null || it.departureLng == null ||
                    it.arrivalLat == null || it.arrivalLng == null
            } -> {
                _saveState.value = UiState.Error(appString(R.string.save_error_segment_coordinates))
                return
            }
        }

        _saveState.value = UiState.Loading
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val normalizedSegments = segmentDrafts.mapIndexed { index, draft ->
                        TransportSegment(
                            type = draft.type,
                            departureName = draft.departureName,
                            departureLat = draft.departureLat,
                            departureLng = draft.departureLng,
                            arrivalName = draft.arrivalName,
                            arrivalLat = draft.arrivalLat,
                            arrivalLng = draft.arrivalLng,
                            sortOrder = index,
                        )
                    }
                    val photos = buildPhotosForSave(timelineItems)
                    val trip = Trip(
                        id = 0L,
                        title = cleanTitle,
                        startPlace = normalizedSegments.first().departureName,
                        destination = normalizedSegments.last().arrivalName,
                        startDate = minOf(range.startDateMillis, range.endDateMillis),
                        endDate = maxOf(range.startDateMillis, range.endDateMillis),
                        coverPhotoUri = photos.firstOrNull()?.uri,
                        memo = null,
                    )
                    repository.addTrip(trip, normalizedSegments, photos)
                }
            }.onSuccess { tripId ->
                _saveState.value = UiState.Success(tripId)
            }.onFailure { error ->
                _saveState.value = UiState.Error(appString(R.string.save_error_generic), error)
            }
        }
    }

    private fun buildPhotosForSave(timelineItems: List<TimelineDraft>): List<NewTravelPhoto> {
        val photos = mutableListOf<NewTravelPhoto>()
        var currentSegmentSortOrder = 0
        timelineItems.forEach { item ->
            when (item) {
                is TransportTimelineDraft -> {
                    currentSegmentSortOrder = timelineItems
                        .takeWhile { it.draftId != item.draftId }
                        .count { it is TransportTimelineDraft }
                }
                is PhotoTimelineDraft -> {
                    item.photoBlock.photos.forEach { draft ->
                        photos.add(
                            NewTravelPhoto(
                                segmentSortOrder = currentSegmentSortOrder,
                                uri = draft.uri.toString(),
                                takenAt = draft.takenAt,
                                lat = draft.lat,
                                lng = draft.lng,
                                locationName = draft.locationName,
                                comment = draft.comment.ifBlank { null },
                                tag = draft.tag,
                            ),
                        )
                    }
                }
            }
        }
        return photos.sortedWith(
            compareBy<NewTravelPhoto> { it.takenAt ?: Long.MAX_VALUE }
                .thenBy { it.uri },
        )
    }

    private fun appString(id: Int): String = getApplication<Application>().getString(id)

    private fun newDraftId(): Long = System.nanoTime()

    private fun startOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.timeInMillis = millis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startOfMonth(millis: Long): Long {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.timeInMillis = millis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun shiftMonth(millis: Long, delta: Int): Long {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.timeInMillis = millis
        calendar.add(Calendar.MONTH, delta)
        return startOfMonth(calendar.timeInMillis)
    }
}
