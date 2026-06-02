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
import com.pnu.orbit.domain.model.TravelPhoto
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
    private val photoStore = com.pnu.orbit.data.local.PhotoFileStore(application)

    private val draftIdCounter = java.util.concurrent.atomic.AtomicLong(System.nanoTime())

    private var editingTripId: Long? = null

    private val _editTitle = MutableLiveData<String?>()
    val editTitle: LiveData<String?> = _editTitle

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

    fun addAccommodation() {
        val current = _timeline.value.orEmpty()
        _timeline.value = current + TransportTimelineDraft(
            TransportSegmentDraft(draftId = newDraftId(), type = TransportType.ACCOMMODATION),
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

    fun loadForEdit(tripId: Long) {
        if (tripId <= 0L || editingTripId == tripId) return
        editingTripId = tripId
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val trip = repository.getTrip(tripId)
                val segments = repository.getSegments(tripId).sortedBy { it.sortOrder }
                val photos = repository.getPhotos(tripId)
                Triple(trip, segments, photos)
            }
            val trip = loaded.first ?: run {
                editingTripId = null
                return@launch
            }
            val segments = loaded.second
            val photos = loaded.third

            _editTitle.value = trip.title
            _dateRange.value = DateRangeDraft(
                displayedMonthMillis = startOfMonth(trip.startDate),
                startDateMillis = trip.startDate,
                endDateMillis = trip.endDate,
            )
            _timeline.value = buildTimelineForEdit(segments, photos)
        }
    }

    private fun buildTimelineForEdit(
        segments: List<TransportSegment>,
        photos: List<TravelPhoto>,
    ): List<TimelineDraft> {
        val items = mutableListOf<TimelineDraft>()
        val photosBySegment = photos.groupBy { it.segmentId }

        photosBySegment[null]?.takeIf { it.isNotEmpty() }?.let { orphanPhotos ->
            items.add(PhotoTimelineDraft(PhotoBlockDraft(newDraftId(), orphanPhotos.map { it.toDraft() })))
        }

        if (segments.isEmpty()) {
            if (items.isEmpty()) {
                items.add(TransportTimelineDraft(TransportSegmentDraft(draftId = newDraftId())))
            }
            return items
        }

        segments.forEach { segment ->
            items.add(
                TransportTimelineDraft(
                    TransportSegmentDraft(
                        draftId = newDraftId(),
                        type = segment.type,
                        departureName = segment.departureName,
                        departureLat = segment.departureLat,
                        departureLng = segment.departureLng,
                        arrivalName = segment.arrivalName,
                        arrivalLat = segment.arrivalLat,
                        arrivalLng = segment.arrivalLng,
                    ),
                ),
            )
            photosBySegment[segment.id]?.takeIf { it.isNotEmpty() }?.let { segmentPhotos ->
                items.add(
                    PhotoTimelineDraft(
                        PhotoBlockDraft(newDraftId(), segmentPhotos.map { it.toDraft() }),
                    ),
                )
            }
        }
        return items
    }

    private fun TravelPhoto.toDraft(): PhotoDraft = PhotoDraft(
        draftId = newDraftId(),
        uri = Uri.parse(uri),
        takenAt = takenAt,
        lat = lat,
        lng = lng,
        locationName = locationName,
        comment = comment.orEmpty(),
        tag = tag,
    )

    fun saveTrip(title: String) {
        val cleanTitle = title.trim()
        val range = _dateRange.value
        val timelineItems = _timeline.value.orEmpty()
        val segmentDrafts = timelineItems
            .filterIsInstance<TransportTimelineDraft>()
            .map { it.segment }
            .map { draft ->
                // Accommodation is a single place; mirror it into the arrival fields so the
                // shared validation and storage (which expect both endpoints) are satisfied.
                if (draft.type == TransportType.ACCOMMODATION) {
                    draft.copy(
                        arrivalName = draft.departureName,
                        arrivalLat = draft.departureLat,
                        arrivalLng = draft.departureLng,
                    )
                } else {
                    draft
                }
            }

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
                    val currentEditId = editingTripId
                    val trip = Trip(
                        id = currentEditId ?: 0L,
                        title = cleanTitle,
                        startPlace = normalizedSegments.firstOrNull()?.departureName.orEmpty(),
                        destination = normalizedSegments.lastOrNull()?.arrivalName.orEmpty(),
                        startDate = minOf(range.startDateMillis, range.endDateMillis),
                        endDate = maxOf(range.startDateMillis, range.endDateMillis),
                        coverPhotoUri = photos.firstOrNull()?.uri,
                        memo = null,
                    )
                    if (currentEditId != null) {
                        repository.updateTrip(trip, normalizedSegments, photos)
                        currentEditId
                    } else {
                        repository.addTrip(trip, normalizedSegments, photos)
                    }
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
                        val rawUri = draft.uri.toString()
                        val storedUri = if (photoStore.isPersisted(rawUri)) {
                            rawUri
                        } else {
                            photoStore.persist(draft.uri) ?: rawUri
                        }
                        photos.add(
                            NewTravelPhoto(
                                segmentSortOrder = currentSegmentSortOrder,
                                uri = storedUri,
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

    private fun newDraftId(): Long = draftIdCounter.incrementAndGet()

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
